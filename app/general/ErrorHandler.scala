package general

import com.fasterxml.jackson.core.{JsonLocation, JsonParseException}
import com.fasterxml.jackson.databind.JsonMappingException
import exceptions.common._
import exceptions.publix._
import general.common.ApiEnvelope
import general.common.ApiEnvelope.ErrorCode
import http.common.HttpUtils
import play.api.Logger
import play.api.http.HttpErrorHandler
import play.api.mvc.Results._
import play.api.mvc._
import play.mvc.Http
import services.publix.idcookie.exceptions.{IdCookieAlreadyExistsException, IdCookieCollectionFullException, IdCookieNotFoundException}
import utils.common.StringUtils

import java.io.IOException
import javax.inject.{Inject, Singleton}
import javax.naming.NamingException
import scala.concurrent._
import scala.jdk.CollectionConverters._

@Singleton
class ErrorHandler @Inject() extends HttpErrorHandler {

  private val logger: Logger = Logger(this.getClass)

  def onClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] = {
    val api = isApi(request)

    // Log error messages and show some message - but don't show any longer message (e.g., with stack trace)
    Future.successful(
      statusCode match {
        case Http.Status.BAD_REQUEST =>
          logger.info(s"Bad request: $message")
          if (looksLikeInvalidJson(request, message)) {
            if (api) BadRequest(ApiEnvelope.wrap("Invalid JSON. Please check the syntax (quotes, commas, braces).", ErrorCode.INVALID_JSON).asJsValue())
            else BadRequest("Invalid JSON")
          } else {
            if (api) BadRequest(ApiEnvelope.wrap(message, ErrorCode.CLIENT_ERROR).asJsValue())
            else BadRequest("Bad request: " + message)
          }

        case Http.Status.NOT_FOUND =>
          val msg = s"Requested page ${request.method} ${request.uri} couldn't be found."
          logger.info(s"Not found: $msg")
          if (api) NotFound(ApiEnvelope.wrap(msg, ErrorCode.NOT_FOUND).asJsValue())
          else NotFound(msg)

        case Http.Status.FORBIDDEN =>
          logger.info(s"Forbidden: $message")
          if (api) Forbidden(ApiEnvelope.wrap("You're not allowed to access this resource.", ErrorCode.NO_ACCESS).asJsValue())
          else Forbidden("You're not allowed to access this resource.")

        case Http.Status.REQUEST_ENTITY_TOO_LARGE =>
          logger.info(s"Request entity too large: $message")
          if (api) Status(statusCode)(ApiEnvelope.wrap("Request entity too large", ErrorCode.TOO_LARGE).asJsValue())
          else Status(statusCode)("Request entity too large: You probably tried to upload a file that is too large")

        case _ =>
          logger.warn(s"HTTP status code $statusCode: $message")
          if (api) Status(statusCode)(ApiEnvelope.wrap(message, ErrorCode.CLIENT_ERROR).asJsValue())
          else Status(statusCode)(s"JATOS error: HTTP status code $statusCode: $message.")
      }
    )
  }

  def onServerError(request: RequestHeader, throwable: Throwable): Future[Result] = {
    val result: Result = throwable match {

      case e: InternalServerErrorException =>
        logger.error(logPrefix(request, e), e)
        // Do not send the actual exception message to the client - it might contain sensitive information
        errorResult(
          request,
          InternalServerError,
          "Unexpected error - check logs to get more information.",
          ErrorCode.UNEXPECTED_ERROR
        )

      case e: HttpException =>
        logger.info(s"${logPrefix(request, e)} (${e.getHttpStatus}): ${e.getMessage}")
        errorResult(request, Status(e.getHttpStatus), e.getMessage, e.getErrorCode)

      case e: AuthException =>
        infoResultLogged(request, e, Unauthorized)

      case e: IdCookieAlreadyExistsException =>
        infoResultLogged(request, e, BadRequest)

      case e: IdCookieCollectionFullException =>
        errorResultLogged(request, e, InternalServerError)

      case e: IdCookieNotFoundException =>
        infoResultLogged(request, e, BadRequest)

      case e: ValidationException =>
        infoResultLogged(request, e, BadRequest)

      case e: ImportExportException =>
        errorResultLogged(request, e, BadRequest)

      case e: JatosException =>
        handleJatosException(request, e)

      case e: JsonMappingException =>
        generateJsonMappingError(request, e)

      case e: JsonParseException =>
        generateJsonParseError(request, e)

      case e: IOException =>
        logger.error(s"${request.method} ${request.uri} - ${e.getMessage}", e)
        errorResult(request, InternalServerError, s"IO error: ${e.getMessage}", ErrorCode.IO_ERROR)

      case e: ForbiddenReloadException =>
        finishStudy(e.getUuid, successful = false, e.getMessage)

      case e: ForbiddenNonLinearFlowException =>
        finishStudy(e.getUuid, successful = false, e.getMessage)

      case e: JatosComponentRunFinishedException =>
        finishStudy(e.getUuid, successful = true, null)

      case _ =>
        logger.error(s"Internal JATOS error: ${throwable.getCause}", throwable)
        errorResult(
          request,
          InternalServerError,
          "Unexpected error - check logs to get more information.",
          ErrorCode.UNEXPECTED_ERROR
        )
    }

    Future.successful(result)
  }

  /**
   * Handle all JatosExceptions with a 'cause'
   */
  private def handleJatosException(request: RequestHeader, e: JatosException): Result = {
    Option(e.getCause) match {
      case Some(cause: NamingException) =>
        logger.info(s"LDAP error during call ${request.method} ${request.uri}", cause)
        errorResult(request, InternalServerError, s"LDAP error: ${cause.getCause}", ErrorCode.LDAP_ERROR)

      case Some(cause: JsonMappingException) =>
        generateJsonMappingError(request, cause)

      case Some(cause: JsonParseException) =>
        generateJsonParseError(request, cause)

      case Some(cause: IOException) =>
        logger.error(s"${request.method} ${request.uri}", cause)
        errorResult(request, InternalServerError, cause.getMessage, ErrorCode.IO_ERROR)

      case _ =>
        logger.error(s"${logPrefix(request, e)}: ${e.getMessage}")
        errorResult(request, InternalServerError, e.getMessage, e.getErrorCode)
    }
  }

  private def infoResultLogged(request: RequestHeader, e: JatosException, status: Status): Result = {
    logger.info(s"${logPrefix(request, e)}: ${e.getMessage}")
    errorResult(request, status, e.getMessage, e.getErrorCode)
  }

  private def errorResultLogged(request: RequestHeader, e: JatosException, status: Status): Result = {
    logger.error(s"${logPrefix(request, e)}: ${e.getMessage}")
    errorResult(request, status, e.getMessage, e.getErrorCode)
  }

  private def logPrefix(request: RequestHeader, e: Throwable): String =
    s"${e.getClass.getSimpleName} during call ${request.method} ${request.uri}"

  private def finishStudy(uuid: String, successful: Boolean, message: String): Result =
    Redirect(controllers.publix.routes.PublixInterceptor.finishStudy(uuid, successful, message))

  private def generateJsonMappingError(request: RequestHeader, e: JsonMappingException) = {
    val path: String = Option(e.getPath)
      .map(_.asScala.toSeq)
      .getOrElse(Seq.empty)
      .flatMap { ref =>
        Option(ref.getFieldName)
          .orElse {
            val idx = ref.getIndex
            if (idx >= 0) Some(s"[$idx]") else None
          }
      }
      .mkString(".")
    val where = if (path.nonEmpty) s" in field '$path': " else ": "
    val msg = s"Invalid JSON$where${Option(e.getOriginalMessage).getOrElse("JSON mapping error")}"

    logger.info(s"${request.method} ${request.uri} - $msg")
    errorResult(request, BadRequest, msg, ErrorCode.INVALID_JSON)
  }

  private def generateJsonParseError(request: RequestHeader, e: JsonParseException) = {
    val loc: JsonLocation = e.getLocation
    val where =
      if (loc != null && loc.getLineNr > 0 && loc.getColumnNr > 0)
        s" at line ${loc.getLineNr}, column ${loc.getColumnNr}"
      else
        ""
    val msg = s"Invalid JSON$where: ${e.getOriginalMessage}"

    logger.info(s"${request.method} ${request.uri} - $msg")
    errorResult(request, BadRequest, msg, ErrorCode.INVALID_JSON)
  }

  private def isApi(request: RequestHeader): Boolean = request.path.contains("/jatos/api/")

  private def looksLikeInvalidJson(request: RequestHeader, message: String): Boolean = {
    val isJsonRequest =
      request.contentType.exists(_.equalsIgnoreCase("application/json")) ||
        request.headers.get("Content-Type").exists(_.toLowerCase.startsWith("application/json"))
    isJsonRequest && {
      val m = Option(message).getOrElse("").toLowerCase
      m.contains("invalid json") ||
        m.contains("malformed json") ||
        m.contains("json parse") ||
        m.contains("unexpected character") ||
        m.contains("unexpected end-of-input") ||
        m.contains("error decoding json body") ||
        m.contains("jsonparseexception")
    }
  }

  private def errorResult(request: RequestHeader, status: Status, msg: String, errorCode: ErrorCode): Result = {
    val html = HttpUtils.isHtmlRequest(request.asJava)
    val api = isApi(request)

    if (html) {
      status(views.html.publix.error.render(msg))
    } else if (api) {
      status(ApiEnvelope.wrap(msg, errorCode).asJsValue())
    } else {
      status(msg)
    }
  }
}
