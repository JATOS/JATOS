package general

import com.fasterxml.jackson.core.{JsonLocation, JsonParseException}
import com.fasterxml.jackson.databind.JsonMappingException
import exceptions.gui.{AuthException, HttpException, ImportExportException, JatosException, JatosGuiException, ValidationException}
import exceptions.publix.{InternalServerErrorPublixException, PublixException}
import general.common.ApiEnvelope
import ApiEnvelope.ErrorCode
import play.api.Logger
import play.api.http.HttpErrorHandler
import play.api.mvc.Results._
import play.api.mvc._
import play.mvc.Http
import utils.common.Helpers

import java.io.IOException
import javax.inject.{Inject, Singleton}
import javax.naming.NamingException
import scala.concurrent._
import scala.jdk.CollectionConverters._

@Singleton
class ErrorHandler @Inject()() extends HttpErrorHandler {

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
    val api = isApi(request)

    val result: Result = throwable match {
      case e: JatosGuiException =>
        logger.info(s"JatosGuiException during call ${request.method} ${request.uri}: ${e.getMessage}")
        e.getSimpleResult.asScala()

      case e: InternalServerErrorPublixException =>
        logger.error(s"InternalServerErrorPublixException during call ${request.method} ${request.uri}: ${e.getMessage}")
        getErrorResult(e.getHttpStatus, e.getMessage, request)

      case e: PublixException =>
        logger.info(s"PublixException during call ${request.method} ${request.uri}: ${e.getMessage}")
        getErrorResult(e.getHttpStatus, e.getMessage, request)

      case e: AuthException =>
        logger.info(s"AuthException during call ${request.method} ${request.uri}: ${e.getMessage}")
        if (api) Unauthorized(e.asApiJsValue())
        else Unauthorized(e.getMessage)

      case e: HttpException =>
        logger.info(s"HttpException during call ${request.method} ${request.uri}: ${e.getMessage}")
        if (api) Status(e.getStatus)(e.asApiJsValue())
        else Status(e.getStatus)(e.getMessage)

      case e: ValidationException =>
        logger.info(s"ValidationException during call ${request.method} ${request.uri}: ${e.getMessage}")
        if (api) BadRequest(e.asApiJsValue())
        else BadRequest(e.getMessage)

      case e: ImportExportException =>
        logger.info(s"ImportExportException during call ${request.method} ${request.uri}: ${e.getMessage}")
        if (api) BadRequest(e.asApiJsValue())
        else BadRequest(e.getMessage)

      case e: JatosException =>
        logger.info(s"JatosException during call ${request.method} ${request.uri}: ${e.getMessage}")
        if (api) InternalServerError(e.asApiJsValue())
        else InternalServerError(e.getMessage)

      case e: NamingException =>
        logger.info(s"LDAP error during call ${request.method} ${request.uri}", throwable)
        if (api) InternalServerError(ApiEnvelope.wrap("LDAP error", ErrorCode.LDAP_ERROR).asJsValue())
        else InternalServerError(s"LDAP error: ${e.getCause}")

      case e: JsonMappingException =>
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
        if (api) BadRequest(ApiEnvelope.wrap(msg, ErrorCode.INVALID_JSON).asJsValue())
        else BadRequest(msg)

      case e: JsonParseException =>
        val loc: JsonLocation = e.getLocation
        val where =
          if (loc != null && loc.getLineNr > 0 && loc.getColumnNr > 0)
            s" at line ${loc.getLineNr}, column ${loc.getColumnNr}"
          else
            ""
        val msg = s"Invalid JSON$where: ${e.getOriginalMessage}"
        logger.info(s"${request.method} ${request.uri} - $msg")
        if (api) BadRequest(ApiEnvelope.wrap(msg, ErrorCode.INVALID_JSON).asJsValue())
        else BadRequest(msg)

      case e: IOException =>
        logger.info(s"${request.method} ${request.uri} - ${e.getMessage}")
        if (api) InternalServerError(ApiEnvelope.wrap("IO error: " + e.getMessage, ErrorCode.IO_ERROR).asJsValue())
        else InternalServerError(e.getMessage)

      case _ =>
        logger.error(s"Internal JATOS error: ${throwable.getCause}", throwable)
        val msg = s"Internal JATOS error during ${request.method} ${request.uri}. Check logs to get more information."
        if (Helpers.isHtmlRequest(request.asJava)) InternalServerError(views.html.error.render(msg))
        else if (api) InternalServerError(ApiEnvelope.wrap("Unexpected error", ErrorCode.UNEXPECTED_ERROR).asJsValue())
        else InternalServerError(msg)
    }

    Future.successful(result)
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

  private def getErrorResult(status: Int, msg: String, request: RequestHeader): Result = {
    if (Helpers.isHtmlRequest(request.asJava)) Status(status)(views.html.publix.error.render(msg))
    if (isApi(request)) Status(status)(ApiEnvelope.wrap("Unexpected error", ErrorCode.UNEXPECTED_ERROR).asJsValue())
    else Status(status)(msg)
  }

}
