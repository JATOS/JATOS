package general

import javax.inject.{Inject, Singleton}
import exceptions.gui.{AuthException, BadRequestException, ForbiddenException, JatosGuiException, NotFoundException}
import exceptions.publix.{InternalServerErrorPublixException, PublixException}

import javax.naming.NamingException
import play.api.Logger
import play.api.http.HttpErrorHandler
import play.api.mvc.Results._
import play.api.mvc._
import play.mvc.Http
import utils.common.Helpers

import java.io.IOException
import scala.concurrent._

@Singleton
class ErrorHandler @Inject()() extends HttpErrorHandler {

  private val logger: Logger = Logger(this.getClass)

  def onClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] = {
    // Log error messages and show some message - but don't
    // show any longer message (e.g. with stack trace) to a worker
    Future.successful(
      statusCode match {
        case Http.Status.BAD_REQUEST =>
          logger.info(s"bad request: $message")
          BadRequest("bad request")
        case Http.Status.NOT_FOUND =>
          logger.info(s"not found: Requested page  ${request.uri} couldn't be found.")
          NotFound(s"Requested page ${request.uri} couldn't be found.")
        case Http.Status.FORBIDDEN =>
          logger.info(s"forbidden: $message")
          Forbidden("You're not allowed to access this resource.")
        case Http.Status.REQUEST_ENTITY_TOO_LARGE =>
          logger.info(s"request entity too large: $message")
          Status(statusCode)("Request entity too large: You probably tried to upload a file that is too large")
        case _ =>
          logger.warn(s"HTTP status code $statusCode: $message")
          Status(statusCode)(s"JATOS error: $statusCode")
      }
    )
  }

  def onServerError(request: RequestHeader, throwable: Throwable): Future[Result] = {
    // We use Play's onServerError() to catch JATOS' JatosGuiExceptions and
    // PublixException. Those exceptions come with a their own result. We
    // log the exception and show this result.
    Future.successful(
      throwable match {
        case e: JatosGuiException =>
          logger.info(s"JatosGuiException during call ${request.uri}: ${e.getMessage}")
          e.getSimpleResult.asScala()
        case e: InternalServerErrorPublixException =>
          logger.error(s"InternalServerErrorPublixException during call ${request.uri}: ${e.getMessage}")
          getErrorResult(e.getHttpStatus, e.getMessage, request)
        case e: PublixException =>
          logger.info(s"PublixException during call ${request.uri}: ${e.getMessage}")
          getErrorResult(e.getHttpStatus, e.getMessage, request)
        case e: NamingException =>
          logger.error("LDAP error", throwable)
          InternalServerError(s"LDAP error: ${e.getCause}")
        case e: BadRequestException =>
          logger.info(s"BadRequestException during call ${request.uri}: ${e.getMessage}")
          BadRequest(e.getMessage)
        case e: ForbiddenException =>
          logger.info(s"ForbiddenException during call ${request.uri}: ${e.getMessage}")
          Forbidden(e.getMessage)
        case e: NotFoundException =>
          logger.info(s"NotFoundException during call ${request.uri}: ${e.getMessage}")
          NotFound(e.getMessage)
        case e: AuthException =>
          logger.info(e.getMessage)
          Forbidden(e.getMessage)
        case e: IOException =>
          logger.info(e.getMessage)
          InternalServerError(e.getMessage)
        case _ =>
          logger.error(s"Internal JATOS error: ${throwable.getCause}", throwable)
          val msg = s"Internal JATOS error during ${request.uri}. Check logs to get more information."
          if (Helpers.isHtmlRequest(request)) InternalServerError(views.html.error.render(msg))
          else InternalServerError(msg)
      }
    )
  }

  private def getErrorResult(status: Int, msg: String, request: RequestHeader): Result = {
    if (Helpers.isHtmlRequest(request)) Status(status)(views.html.publix.error.render(msg))
    else Status(status)(msg)
  }

}
