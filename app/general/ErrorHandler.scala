package general

import exceptions.gui.{AuthException, HttpException, JatosException, JatosGuiException}
import exceptions.publix.{InternalServerErrorPublixException, PublixException}
import models.gui.ApiEnvelope
import models.gui.ApiEnvelope.ErrorCode
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
          if (api) BadRequest(ApiEnvelope.wrap(message, ErrorCode.CLIENT_ERROR).asJsValue())
          else BadRequest("Bad request: $message")

        case Http.Status.NOT_FOUND =>
          logger.info(s"Not found: Requested page  ${request.uri} couldn't be found.")
          if (api) NotFound(ApiEnvelope.wrap(s"Requested page ${request.uri} couldn't be found.", ErrorCode.NOT_FOUND).asJsValue())
          else NotFound(s"Requested page ${request.uri} couldn't be found.")

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
        logger.info(s"JatosGuiException during call ${request.uri}: ${e.getMessage}")
        e.getSimpleResult.asScala()

      case e: InternalServerErrorPublixException =>
        logger.error(s"InternalServerErrorPublixException during call ${request.uri}: ${e.getMessage}")
        getErrorResult(e.getHttpStatus, e.getMessage, request)

      case e: PublixException =>
        logger.info(s"PublixException during call ${request.uri}: ${e.getMessage}")
        getErrorResult(e.getHttpStatus, e.getMessage, request)

      case e: AuthException =>
        logger.info(e.getMessage)
        if (api) Forbidden(e.asApiJsValue())
        else Forbidden(e.getMessage)

      case e: HttpException =>
        logger.info(s"Exception during call ${request.uri}: ${e.getMessage}")
        if (api) Status(e.getStatus)(e.asApiJsValue())
        else Status(e.getStatus)(e.getMessage)

      case e: JatosException =>
        logger.info(s"Exception during call ${request.uri}: ${e.getMessage}")
        if (api) InternalServerError(e.asApiJsValue())
        else InternalServerError(e.getMessage)

      case e: NamingException =>
        logger.error("LDAP error", throwable)
        if (api) InternalServerError(ApiEnvelope.wrap("LDAP error", ErrorCode.LDAP_ERROR).asJsValue())
        else InternalServerError(s"LDAP error: ${e.getCause}")

      case e: IOException =>
        logger.info(e.getMessage)
        if (api) InternalServerError(ApiEnvelope.wrap("IO error", ErrorCode.UNEXPECTED_ERROR).asJsValue())
        else InternalServerError(e.getMessage)

      case _ =>
        logger.error(s"Internal JATOS error: ${throwable.getCause}", throwable)
        val msg = s"Internal JATOS error during ${request.uri}. Check logs to get more information."
        if (Helpers.isHtmlRequest(request)) InternalServerError(views.html.error.render(msg))
        else if (api) InternalServerError(ApiEnvelope.wrap("Unexpected error", ErrorCode.UNEXPECTED_ERROR).asJsValue())
        else InternalServerError(msg)
    }

    Future.successful(result)
  }

  private def isApi(request: RequestHeader): Boolean = request.path.contains("/jatos/api/")

  private def getErrorResult(status: Int, msg: String, request: RequestHeader): Result = {
    if (Helpers.isHtmlRequest(request)) Status(status)(views.html.publix.error.render(msg))
    if (isApi(request)) Status(status)(ApiEnvelope.wrap("Unexpected error", ErrorCode.UNEXPECTED_ERROR).asJsValue())
    else Status(status)(msg)
  }

}
