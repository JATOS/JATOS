package controllers.publix

import daos.common.StudyResultDao
import daos.common.worker.WorkerType
import exceptions.common.{BadRequestException, ForbiddenException, NotFoundException}
import general.common.Http.Context
import general.common.IOExecutor
import models.common.StudyResult
import play.api.Logger
import play.api.libs.json.JsValue
import play.api.mvc._
import play.db.jpa.JPAApi
import utils.common.Helpers

import javax.inject.{Inject, Singleton}
import scala.compat.java8.FunctionConverters.asJavaFunction
import scala.concurrent.Future

/**
 * This class intercepts a request before it gets to the BatchChannel or GroupChannel. It has
 * several purposes: exception handling, final WebSocket creation, and matching to the right
 * worker type.
 */
@Singleton
class ChannelInterceptor @Inject()(components: ControllerComponents,
                                   jpa: JPAApi,
                                   studyResultDao: StudyResultDao,
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
                                   mTGroupChannel: MTGroupChannel,
                                   ioExecutor: IOExecutor)
  extends AbstractController(components) {

  private val logger: Logger = Logger(this.getClass)

  /**
   * HTTP type: WebSocket
   *
   * Endpoint that opens a WebSocket for the batch channel that is used to exchange data (batch
   * session data) between study runs of a batch. All batch session data are stored in a Batch
   * model, and the batch channels will be handled by a BatchDispatcher which uses Akka.
   *
   * @param studyResultUuid Study result's UUID
   * @return WebSocket that transports JSON strings.
   */
  def openBatch(studyResultUuid: String): WebSocket =
    WebSocket.acceptOrResult[JsValue, JsValue] { implicit request =>
      inIOContext {
        jpa.withTransaction(asJavaFunction(_ => {
          try {
            val studyResult = fetchStudyResult(studyResultUuid)
            studyResult.getWorkerType match {
              case WorkerType.JATOS => Right(jatosBatchChannel.open(studyResult))
              case WorkerType.PERSONAL_SINGLE => Right(personalSingleBatchChannel.open(studyResult))
              case WorkerType.PERSONAL_MULTIPLE => Right(personalMultipleBatchChannel.open(studyResult))
              case WorkerType.GENERAL_SINGLE => Right(generalSingleBatchChannel.open(studyResult))
              case WorkerType.GENERAL_MULTIPLE => Right(generalMultipleBatchChannel.open(studyResult))
              case WorkerType.MT_SANDBOX => Right(mTBatchChannel.open(studyResult))
              case WorkerType.MT => Right(mTBatchChannel.open(studyResult))
              case _ => Left(Results.BadRequest)
            }
          } catch {
            // Due to returning a WebSocket, we can't throw a PublixExceptions like with other publix endpoints
            case e: NotFoundException =>
              logger.info(s".open: ${e.getMessage}")
              Left(Results.NotFound)
            case e: ForbiddenException =>
              logger.info(s".open: ${e.getMessage}")
              Left(Results.Forbidden)
            case e: BadRequestException =>
              logger.info(s".open: ${e.getMessage}")
              Left(Results.BadRequest)
            case e: Exception =>
              logger.error(".open: Exception during opening of batch channel", e)
              Left(Results.InternalServerError)
          }
        }))
      }
    }

  /**
   * HTTP type: WebSocket
   *
   * Let the worker (actually it's StudyResult) join a group (actually a GroupResult) and open a
   * WebSocket (group channel). Only works if this study is a group study. All group data are
   * stored in a GroupResult, and the group channels will be handled by a GroupDispatcher which
   * uses Akka.
   *
   * @param studyResultUuid Study result's UUID
   * @return WebSocket that transfers JSON
   */
  def joinGroup(studyResultUuid: String): WebSocket =
    WebSocket.acceptOrResult[JsValue, JsValue] { implicit request =>
      inIOContext {
        try {
          val studyResult = fetchStudyResultAndInitLazy(studyResultUuid)
          studyResult.getWorkerType match {
            case WorkerType.JATOS =>
              jatosGroupChannel.join(studyResult)
              Right(jatosGroupChannel.open(studyResult))
            case WorkerType.PERSONAL_SINGLE =>
              personalSingleGroupChannel.join(studyResult)
              Right(personalSingleGroupChannel.open(studyResult))
            case WorkerType.PERSONAL_MULTIPLE =>
              personalMultipleGroupChannel.join(studyResult)
              Right(personalMultipleGroupChannel.open(studyResult))
            case WorkerType.GENERAL_SINGLE =>
              generalSingleGroupChannel.join(studyResult)
              Right(generalSingleGroupChannel.open(studyResult))
            case WorkerType.GENERAL_MULTIPLE =>
              generalMultipleGroupChannel.join(studyResult)
              Right(generalMultipleGroupChannel.open(studyResult))
            case WorkerType.MT_SANDBOX =>
              mTGroupChannel.join(studyResult)
              Right(mTGroupChannel.open(studyResult))
            case WorkerType.MT =>
              mTGroupChannel.join(studyResult)
              Right(mTGroupChannel.open(studyResult))
            case _ => Left(Results.BadRequest)
          }
        } catch {
          // Due to returning a WebSocket we can't throw a PublixExceptions like with other publix endpoints
          case e: NotFoundException =>
            logger.info(s".join: ${e.getMessage}")
            Left(Results.NotFound)
          case e: ForbiddenException =>
            logger.info(s".join: ${e.getMessage}")
            Left(Results.Forbidden)
          case e: BadRequestException =>
            logger.info(s".join: ${e.getMessage}")
            Left(Results.BadRequest)
          case e: Exception =>
            logger.error(".join: Exception during opening of group channel", e)
            Left(Results.InternalServerError)
        }
      }
    }

  /**
   * HTTP type: Ajax GET request
   *
   * Try to find a different group for this StudyResult. It reuses the already opened group
   * channel and just reassigns it to a different group (or in more detail to a different
   * GroupResult and GroupDispatcher). If it is successful, it returns a 200 (OK) HTTP status
   * code. If it can't find any other group, it returns a 204 (NO CONTENT) HTTP status code.
   *
   * @param studyResultUuid Study result's UUID
   * @return Result
   */
  def reassignGroup(studyResultUuid: String): Action[AnyContent] = Action.async { implicit request =>
    inIOContext {
      try {
        val studyResult = fetchStudyResultAndInitLazy(studyResultUuid)
        studyResult.getWorkerType match {
          case WorkerType.JATOS => jatosGroupChannel.reassign(studyResult)
          case WorkerType.PERSONAL_SINGLE => personalSingleGroupChannel.reassign(studyResult)
          case WorkerType.PERSONAL_MULTIPLE => personalMultipleGroupChannel.reassign(studyResult)
          case WorkerType.GENERAL_SINGLE => generalSingleGroupChannel.reassign(studyResult)
          case WorkerType.GENERAL_MULTIPLE => generalMultipleGroupChannel.reassign(studyResult)
          case WorkerType.MT_SANDBOX => mTGroupChannel.reassign(studyResult)
          case WorkerType.MT => mTGroupChannel.reassign(studyResult)
          case _ => Results.BadRequest
        }
      } catch {
        case e: ForbiddenException =>
          logger.info(s".reassignGroup: ${e.getMessage}")
          Forbidden
        case e: BadRequestException =>
          logger.info(s".reassignGroup: ${e.getMessage}")
          BadRequest
        case e: Exception =>
          logger.error(".reassignGroup: Exception during reassigning a group channel", e)
          InternalServerError
      }
    }
  }

  /**
   * HTTP type: Ajax GET request
   *
   * Let the worker leave the group (actually a GroupResult) he joined before and closes the
   * group channel. Only works if this study is a group study.
   *
   * @param studyResultUuid Study result's UUID
   * @return Result
   */
  def leaveGroup(studyResultUuid: String): Action[AnyContent] = Action.async { implicit request =>
    inIOContext {
      jpa.withTransaction(asJavaFunction(_ => {
        try {
          val studyResult = fetchStudyResult(studyResultUuid)
          studyResult.getWorkerType match {
            case WorkerType.JATOS => jatosGroupChannel.leave(studyResult)
            case WorkerType.PERSONAL_SINGLE => personalSingleGroupChannel.leave(studyResult)
            case WorkerType.PERSONAL_MULTIPLE => personalMultipleGroupChannel.leave(studyResult)
            case WorkerType.GENERAL_SINGLE => generalSingleGroupChannel.leave(studyResult)
            case WorkerType.GENERAL_MULTIPLE => generalMultipleGroupChannel.leave(studyResult)
            case WorkerType.MT_SANDBOX => mTGroupChannel.leave(studyResult)
            case WorkerType.MT => mTGroupChannel.leave(studyResult)
            case _ => Results.BadRequest
          }
        } catch {
          case e: ForbiddenException =>
            logger.info(s".leaveGroup: ${e.getMessage}")
            Forbidden
          case e: BadRequestException =>
            logger.info(s".leaveGroup: ${e.getMessage}")
            BadRequest
          case e: Exception =>
            logger.error(".leaveGroup: Exception during leaving a group channel", e)
            InternalServerError
        }
      }))
    }
  }

  /**
   * Helper to wrap logic in the IOExecutor and set up the HTTP Context.
   * Works for both standard Actions (returning Result) and WebSockets (returning Either).
   */
  private def inIOContext[T](block: => T)(implicit request: RequestHeader): Future[T] = {
    Future {
      try {
        Context.setCurrent(new Context(request.asJava))
        block
      } finally {
        Context.clear()
      }
    }(ioExecutor)
  }

  private def fetchStudyResult(uuid: String) = {
    if (uuid == null || uuid == "undefined") throw new ForbiddenException("Error getting study result UUID")
    val srOptional = studyResultDao.findByUuid(uuid)
    if (!srOptional.isPresent) throw new BadRequestException("Study result " + uuid + " doesn't exist.")
    srOptional.get()
  }

  private def fetchStudyResultAndInitLazy(uuid: String): StudyResult = {
    jpa.withTransaction(asJavaFunction(_ => {
      val studyResult = fetchStudyResult(uuid)
      Helpers.initializeAndUnproxy(studyResult.getBatch, studyResult.getHistoryGroupResult)
      if (studyResult.getStudy != null) {
        Helpers.initializeAndUnproxy(studyResult.getStudy, studyResult.getStudy.getUserList)
      }
      if (studyResult.getActiveGroupResult != null) {
        Helpers.initializeAndUnproxy(studyResult.getActiveGroupResult, studyResult.getActiveGroupResult.getActiveMemberList)
      }
      studyResult
    }))
  }

}
