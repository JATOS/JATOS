package general

import javax.inject.{Inject, Singleton}

import exceptions.gui.JatosGuiException
import exceptions.publix.{InternalServerErrorPublixException, PublixException}
import play.api.Logger
import play.api.http.HttpErrorHandler
import play.api.mvc.Results._
import play.api.mvc._
import play.mvc.Http
import utils.common.HttpUtils

import scala.concurrent._

@Singleton
class ErrorHandler @Inject()() extends HttpErrorHandler {

  private val logger: Logger = Logger(this.getClass)

  def onClientError(request: RequestHeader, statusCode: Int, message: String) = {
    // Log error messages and show some message - but don't
    // show any longer message (e.g. with stack trace) to a worker
    Future.successful(
      statusCode match {
        case Http.Status.BAD_REQUEST =>
          logger.info(s".onClientError - bad request: $message")
          BadRequest("bad request")
        case Http.Status.NOT_FOUND =>
          logger.info(s".onClientError - not found: Requested page  ${request.uri} couldn't be found.")
          NotFound(s"Requested page ${request.uri} couldn't be found.")
        case Http.Status.FORBIDDEN =>
          logger.info(s".onClientError - forbidden: $message")
          Forbidden("You're not allowed to access this resource.")
        case Http.Status.REQUEST_ENTITY_TOO_LARGE =>
          logger.info(s".onClientError - request entity too large: $message")
          Status(statusCode)("Request entity too large: You probably tried  to upload a file that is too large")
        case _ =>
          logger.warn(s".onClientError - HTTP status code $statusCode: $message")
          Status(statusCode)(s"JATOS error: $statusCode")
      }
    )
  }

  def onServerError(request: RequestHeader, throwable: Throwable) = {
    // We use Play's onServerError() to catch JATOS' JatosGuiExceptions and
    // PublixException. Those exceptions come with a their own result. We
    // log the exception and show this result.
    Future.successful(
      throwable match {
        case e: JatosGuiException =>
          logger.info(s".onServerError - JatosGuiException during call ${request.uri}: ${e.getMessage}")
          e.getSimpleResult.asScala()
        case e: InternalServerErrorPublixException =>
          logger.error(s".onServerError - InternalServerErrorPublixException during call ${request.uri}: ${e.getMessage}")
          e.getSimpleResult.asScala()
        case e: PublixException =>
          logger.info(s".onServerError - PublixException during call ${request.uri}: ${e.getMessage}")
          e.getSimpleResult.asScala()
        case _ =>
          logger.error(".onServerError - Internal JATOS error", throwable)
          val msg = s"Internal JATOS error during ${request.uri}. Check logs to get more information."
          if (HttpUtils.isAjax) InternalServerError(msg)
          else InternalServerError(views.html.error.render(msg))
      }
    )
  }

}
