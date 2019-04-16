package controllers.publix

import controllers.publix.actionannotation.PublixAccessLoggingAction.PublixAccessLogging
import exceptions.publix.{BadRequestPublixException, ForbiddenPublixException, NotFoundPublixException, PublixException}
import javax.inject.{Inject, Singleton}
import models.common.workers._
import play.api.Logger
import play.api.libs.json.JsValue
import play.api.mvc._
import play.core.j.JavaHelpers
import play.db.jpa.JPAApi
import play.libs.concurrent.HttpExecutionContext
import services.publix.idcookie.IdCookieService

import scala.compat.java8.FunctionConverters.asJavaSupplier
import scala.concurrent.Future

/**
  * This class intercepts a request before it gets to the BatchChannel or GroupChannel. It has
  * several purposes: exception handling, final WebSocket creation, and matching to the right
  * worker type.
  */
@Singleton
@PublixAccessLogging
class ChannelInterceptor @Inject()(components: ControllerComponents,
                                   idCookieService: IdCookieService,
                                   jpa: JPAApi,
                                   httpExecutionContext: HttpExecutionContext,
                                   jatosBatchChannel: JatosBatchChannel,
                                   personalSingleBatchChannel: PersonalSingleBatchChannel,
                                   personalMultipleBatchChannel: PersonalMultipleBatchChannel,
                                   generalSingleBatchChannel: GeneralSingleBatchChannel,
                                   generalMultipleBatchChannel: GeneralMultipleBatchChannel,
                                   mTBatchChannel: MTBatchChannel,
                                   jatosGroupChannel: JatosGroupChannel,
                                   personalSingleGroupChannel: PersonalSingleGroupChannel,
                                   personalMultipleGroupChannel: PersonalMultipleGroupChannel,
                                   generalSingleGroupChannel: GeneralSingleGroupChannel,
                                   generalMultipleGroupChannel: GeneralMultipleGroupChannel,
                                   mTGroupChannel: MTGroupChannel)
  extends AbstractController(components) {

  private val logger: Logger = Logger(this.getClass)

  /**
    * HTTP type: WebSocket
    *
    * Endpoint that opens a WebSocket for the batch channel that is used to exchange data (batch
    * session data) between study runs of a batch. All batch session data are stored in a Batch
    * model and the batch channels will be handled by a BatchDispatcher which uses Akka.
    *
    * @param studyId       Study's ID
    * @param studyResultId StudyResult's ID
    * @return WebSocket that transports JSON strings.
    */
  def openBatch(studyId: Long, studyResultId: Long): WebSocket =
    WebSocket.acceptOrResult[JsValue, JsValue] { request =>

      Future.successful({
        // Set Http.Context used in Play with Java. Needed by IdCookieService
        play.mvc.Http.Context.current.set(play.core.j.JavaHelpers.createJavaContext(request, JavaHelpers.createContextComponents()))
        val idCookie = idCookieService.getIdCookie(studyResultId)

        jpa.withTransaction(asJavaSupplier(() =>
          try {
            idCookie.getWorkerType match {
              case JatosWorker.WORKER_TYPE =>
                Right(jatosBatchChannel.open(studyId, studyResultId))
              case PersonalSingleWorker.WORKER_TYPE =>
                Right(personalSingleBatchChannel.open(studyId, studyResultId))
              case PersonalMultipleWorker.WORKER_TYPE =>
                Right(personalMultipleBatchChannel.open(studyId, studyResultId))
              case GeneralSingleWorker.WORKER_TYPE =>
                Right(generalSingleBatchChannel.open(studyId, studyResultId))
              case GeneralMultipleWorker.WORKER_TYPE =>
                Right(generalMultipleBatchChannel.open(studyId, studyResultId))
              case MTSandboxWorker.WORKER_TYPE =>
                Right(mTBatchChannel.open(studyId, studyResultId))
              case MTWorker.WORKER_TYPE =>
                Right(mTBatchChannel.open(studyId, studyResultId))
              case _ => Left(Results.BadRequest)
            }
          } catch {
            // Due to returning a WebSocket we can't throw a PublixExceptions like
            // with other publix endpoints
            case e: NotFoundPublixException =>
              logger.info(s".open: ${e.getMessage}")
              Left(Results.NotFound)
            case e: ForbiddenPublixException =>
              logger.info(s".open: ${e.getMessage}")
              Left(Results.Forbidden)
            case e: BadRequestPublixException =>
              logger.info(s".open: ${e.getMessage}")
              Left(Results.BadRequest)
            case e: Exception =>
              logger.error(".open: Exception during opening of batch channel", e)
              Left(Results.InternalServerError)
          }
        ))
      })
    }

  /**
    * HTTP type: WebSocket
    *
    * Let the worker (actually it's StudyResult) join a group (actually a GroupResult) and open a
    * WebSocket (group channel). Only works if this study is a group study. All group data are
    * stored in a GroupResult and the group channels will be handled by a GroupDispatcher which
    * uses Akka.
    *
    * @param studyId       studyId Study's ID
    * @param studyResultId StudyResult's ID
    * @return WebSocket that transfers JSON
    */
  def joinGroup(studyId: Long, studyResultId: Long): WebSocket =
    WebSocket.acceptOrResult[JsValue, JsValue] {
      request =>

        Future.successful({
          // Set Http.Context used in Play with Java. Needed by IdCookieService
          play.mvc.Http.Context.current.set(play.core.j.JavaHelpers.createJavaContext(request, JavaHelpers.createContextComponents()))
          val idCookie = idCookieService.getIdCookie(studyResultId)

          try {
            idCookie.getWorkerType match {
              case JatosWorker.WORKER_TYPE =>
                val studyResult = jpa.withTransaction(asJavaSupplier(() =>
                  jatosGroupChannel.join(studyId, studyResultId)
                ))
                Right(jatosGroupChannel.open(studyResult))
              case PersonalSingleWorker.WORKER_TYPE =>
                val studyResult = jpa.withTransaction(asJavaSupplier(() =>
                  personalSingleGroupChannel.join(studyId, studyResultId)
                ))
                Right(personalSingleGroupChannel.open(studyResult))
              case PersonalMultipleWorker.WORKER_TYPE =>
                val studyResult = jpa.withTransaction(asJavaSupplier(() =>
                  personalMultipleGroupChannel.join(studyId, studyResultId)
                ))
                Right(personalMultipleGroupChannel.open(studyResult))
              case GeneralSingleWorker.WORKER_TYPE =>
                val studyResult = jpa.withTransaction(asJavaSupplier(() =>
                  generalSingleGroupChannel.join(studyId, studyResultId)
                ))
                Right(generalSingleGroupChannel.open(studyResult))
              case GeneralMultipleWorker.WORKER_TYPE =>
                val studyResult = jpa.withTransaction(asJavaSupplier(() =>
                  generalMultipleGroupChannel.join(studyId, studyResultId)
                ))
                Right(generalMultipleGroupChannel.open(studyResult))
              case MTSandboxWorker.WORKER_TYPE =>
                val studyResult = jpa.withTransaction(asJavaSupplier(() =>
                  mTGroupChannel.join(studyId, studyResultId)
                ))
                Right(mTGroupChannel.open(studyResult))
              case MTWorker.WORKER_TYPE =>
                val studyResult = jpa.withTransaction(asJavaSupplier(() =>
                  mTGroupChannel.join(studyId, studyResultId)
                ))
                Right(mTGroupChannel.open(studyResult))
              case _ => Left(Results.BadRequest)
            }
          } catch {
            // Due to returning a WebSocket we can't throw a PublixExceptions like
            // with other publix endpoints
            case e: NotFoundPublixException =>
              logger.info(s".open: ${e.getMessage}")
              Left(Results.NotFound)
            case e: ForbiddenPublixException =>
              logger.info(s".open: ${e.getMessage}")
              Left(Results.Forbidden)
            case e: BadRequestPublixException =>
              logger.info(s".open: ${e.getMessage}")
              Left(Results.BadRequest)
            case e: Exception =>
              logger.error(".open: Exception during opening of group channel", e)
              Left(Results.InternalServerError)
          }
        })
    }

  /**
    * HTTP type: Ajax GET request
    *
    * Try to find a different group for this StudyResult. It reuses the already opened group
    * channel and just reassigns it to a different group (or in more detail to a different
    * GroupResult and GroupDispatcher). If it is successful it returns an 200 (OK) HTTP status
    * code. If it can't find any other group it returns a 204 (NO CONTENT) HTTP status code.
    *
    * @param studyId       studyId Study's ID
    * @param studyResultId StudyResult's ID
    * @return Result
    * @throws PublixException will be handled in the global ErrorHandler
    */
  @throws(classOf[PublixException])
  def reassignGroup(studyId: Long, studyResultId: Long): Action[AnyContent] = Action {
    request =>
      // Set Http.Context used in Play with Java. Needed by IdCookieService
      play.mvc.Http.Context.current.set(play.core.j.JavaHelpers.createJavaContext(request, JavaHelpers.createContextComponents()))
      val idCookie = idCookieService.getIdCookie(studyResultId)

      jpa.withTransaction(asJavaSupplier(() => {

        idCookie.getWorkerType match {
          case JatosWorker.WORKER_TYPE =>
            jatosGroupChannel.reassign(studyId, studyResultId)
          case PersonalSingleWorker.WORKER_TYPE =>
            personalSingleGroupChannel.reassign(studyId, studyResultId)
          case PersonalMultipleWorker.WORKER_TYPE =>
            personalMultipleGroupChannel.reassign(studyId, studyResultId)
          case GeneralSingleWorker.WORKER_TYPE =>
            generalSingleGroupChannel.reassign(studyId, studyResultId)
          case GeneralMultipleWorker.WORKER_TYPE =>
            generalMultipleGroupChannel.reassign(studyId, studyResultId)
          case MTSandboxWorker.WORKER_TYPE =>
            mTGroupChannel.reassign(studyId, studyResultId)
          case MTWorker.WORKER_TYPE =>
            mTGroupChannel.reassign(studyId, studyResultId)
          case _ => Results.BadRequest
        }
      }))
  }

  /**
    * HTTP type: Ajax GET request
    *
    * Let the worker leave the group (actually a GroupResult) he joined before and closes the
    * group channel. Only works if this study is a group study.
    *
    * @param studyId       studyId Study's ID
    * @param studyResultId StudyResult's ID
    * @return Result
    * @throws PublixException will be handled in the global ErrorHandler
    */
  @throws(classOf[PublixException])
  def leaveGroup(studyId: Long, studyResultId: Long): Action[AnyContent] = Action {
    request =>
      // Set Http.Context used in Play with Java. Needed by IdCookieService
      play.mvc.Http.Context.current.set(play.core.j.JavaHelpers.createJavaContext(request, JavaHelpers.createContextComponents()))
      val idCookie = idCookieService.getIdCookie(studyResultId)

      jpa.withTransaction(asJavaSupplier(() => {

        idCookie.getWorkerType match {
          case JatosWorker.WORKER_TYPE =>
            jatosGroupChannel.leave(studyId, studyResultId)
          case PersonalSingleWorker.WORKER_TYPE =>
            personalSingleGroupChannel.leave(studyId, studyResultId)
          case PersonalMultipleWorker.WORKER_TYPE =>
            personalMultipleGroupChannel.leave(studyId, studyResultId)
          case GeneralSingleWorker.WORKER_TYPE =>
            generalSingleGroupChannel.leave(studyId, studyResultId)
          case GeneralMultipleWorker.WORKER_TYPE =>
            generalMultipleGroupChannel.leave(studyId, studyResultId)
          case MTSandboxWorker.WORKER_TYPE =>
            mTGroupChannel.leave(studyId, studyResultId)
          case MTWorker.WORKER_TYPE =>
            mTGroupChannel.leave(studyId, studyResultId)
          case _ => Results.BadRequest
        }
      }))
  }

}
