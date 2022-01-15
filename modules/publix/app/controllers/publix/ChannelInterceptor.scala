package controllers.publix

import controllers.publix.actionannotation.PublixAccessLoggingAction.PublixAccessLogging
import daos.common.StudyResultDao
import exceptions.publix.{BadRequestPublixException, ForbiddenPublixException, NotFoundPublixException, PublixException}
import models.common.workers._
import play.api.Logger
import play.api.libs.json.JsValue
import play.api.mvc._
import play.core.j.JavaHelpers
import play.db.jpa.JPAApi
import utils.common.Helpers

import javax.inject.{Inject, Singleton}
import scala.compat.java8.FunctionConverters.asJavaSupplier
import scala.concurrent.Future

/**
  * This class intercepts a request before it gets to the BatchChannel or GroupChannel. It has
  * several purposes: exception handling, final WebSocket creation, and matching to the right
  * worker type.
  */
//noinspection ScalaDeprecation
@Singleton
@PublixAccessLogging
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
    * @param studyResultUuid Study reslt's UUID
    * @return WebSocket that transports JSON strings.
    */
  def openBatch(studyResultUuid: String): WebSocket =
    WebSocket.acceptOrResult[JsValue, JsValue] { implicit request =>

      Future.successful({
        // Set Http.Context used in Play with Java. Needed by IdCookieService
        play.mvc.Http.Context.current.set(play.core.j.JavaHelpers.createJavaContext(request, JavaHelpers.createContextComponents()))

        jpa.withTransaction(asJavaSupplier(() => {
          try {
            val studyResult = fetchStudyResult(studyResultUuid)
            studyResult.getWorkerType match {
              case JatosWorker.WORKER_TYPE => Right(jatosBatchChannel.open(studyResult))
              case PersonalSingleWorker.WORKER_TYPE => Right(personalSingleBatchChannel.open(studyResult))
              case PersonalMultipleWorker.WORKER_TYPE => Right(personalMultipleBatchChannel.open(studyResult))
              case GeneralSingleWorker.WORKER_TYPE => Right(generalSingleBatchChannel.open(studyResult))
              case GeneralMultipleWorker.WORKER_TYPE => Right(generalMultipleBatchChannel.open(studyResult))
              case MTSandboxWorker.WORKER_TYPE => Right(mTBatchChannel.open(studyResult))
              case MTWorker.WORKER_TYPE => Right(mTBatchChannel.open(studyResult))
              case _ => Left(Results.BadRequest)
            }
          } catch {
            // Due to returning a WebSocket we can't throw a PublixExceptions like with other publix endpoints
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
    * @param studyResultUuid Study reslt's UUID
    * @return WebSocket that transfers JSON
    */
  def joinGroup(studyResultUuid: String): WebSocket =
    WebSocket.acceptOrResult[JsValue, JsValue] { implicit request =>

      Future.successful({
        // Set Http.Context used in Play with Java. Needed by IdCookieService
        play.mvc.Http.Context.current.set(play.core.j.JavaHelpers.createJavaContext(request, JavaHelpers.createContextComponents()))

        try {
          val studyResult = fetchStudyResultAndInitLazy(studyResultUuid)
          studyResult.getWorkerType match {
            case JatosWorker.WORKER_TYPE =>
              jatosGroupChannel.join(studyResult)
              Right(jatosGroupChannel.open(studyResult))
            case PersonalSingleWorker.WORKER_TYPE =>
              personalSingleGroupChannel.join(studyResult)
              Right(personalSingleGroupChannel.open(studyResult))
            case PersonalMultipleWorker.WORKER_TYPE =>
              personalMultipleGroupChannel.join(studyResult)
              Right(personalMultipleGroupChannel.open(studyResult))
            case GeneralSingleWorker.WORKER_TYPE =>
              generalSingleGroupChannel.join(studyResult)
              Right(generalSingleGroupChannel.open(studyResult))
            case GeneralMultipleWorker.WORKER_TYPE =>
              generalMultipleGroupChannel.join(studyResult)
              Right(generalMultipleGroupChannel.open(studyResult))
            case MTSandboxWorker.WORKER_TYPE =>
              mTGroupChannel.join(studyResult)
              Right(mTGroupChannel.open(studyResult))
            case MTWorker.WORKER_TYPE =>
              mTGroupChannel.join(studyResult)
              Right(mTGroupChannel.open(studyResult))
            case _ => Left(Results.BadRequest)
          }
        } catch {
          // Due to returning a WebSocket we can't throw a PublixExceptions like with other publix endpoints
          case e: NotFoundPublixException =>
            logger.info(s".join: ${e.getMessage}")
            Left(Results.NotFound)
          case e: ForbiddenPublixException =>
            logger.info(s".join: ${e.getMessage}")
            Left(Results.Forbidden)
          case e: BadRequestPublixException =>
            logger.info(s".join: ${e.getMessage}")
            Left(Results.BadRequest)
          case e: Exception =>
            logger.error(".join: Exception during opening of group channel", e)
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
    * @param studyResultUuid Study reslt's UUID
    * @return Result
    * @throws PublixException will be handled in the global ErrorHandler
    */
  @throws(classOf[PublixException])
  def reassignGroup(studyResultUuid: String): Action[AnyContent] = Action { implicit request =>
    // Set Http.Context used in Play with Java. Needed by IdCookieService
    play.mvc.Http.Context.current.set(play.core.j.JavaHelpers.createJavaContext(request, JavaHelpers.createContextComponents()))

    try {
      val studyResult = fetchStudyResultAndInitLazy(studyResultUuid)
      studyResult.getWorkerType match {
        case JatosWorker.WORKER_TYPE => jatosGroupChannel.reassign(studyResult)
        case PersonalSingleWorker.WORKER_TYPE => personalSingleGroupChannel.reassign(studyResult)
        case PersonalMultipleWorker.WORKER_TYPE => personalMultipleGroupChannel.reassign(studyResult)
        case GeneralSingleWorker.WORKER_TYPE => generalSingleGroupChannel.reassign(studyResult)
        case GeneralMultipleWorker.WORKER_TYPE => generalMultipleGroupChannel.reassign(studyResult)
        case MTSandboxWorker.WORKER_TYPE => mTGroupChannel.reassign(studyResult)
        case MTWorker.WORKER_TYPE => mTGroupChannel.reassign(studyResult)
        case _ => Results.BadRequest
      }
    } catch {
      case e: ForbiddenPublixException =>
        logger.info(s".reassignGroup: ${e.getMessage}")
        Forbidden
      case e: BadRequestPublixException =>
        logger.info(s".reassignGroup: ${e.getMessage}")
        BadRequest
      case e: Exception =>
        logger.error(".reassignGroup: Exception during reassigning a group channel", e)
        InternalServerError
    }
  }

  /**
    * HTTP type: Ajax GET request
    *
    * Let the worker leave the group (actually a GroupResult) he joined before and closes the
    * group channel. Only works if this study is a group study.
    *
    * @param studyResultUuid Study reslt's UUID
    * @return Result
    * @throws PublixException will be handled in the global ErrorHandler
    */
  @throws(classOf[PublixException])
  def leaveGroup(studyResultUuid: String): Action[AnyContent] = Action { implicit request =>
    // Set Http.Context used in Play with Java. Needed by IdCookieService
    play.mvc.Http.Context.current.set(play.core.j.JavaHelpers.createJavaContext(request, JavaHelpers.createContextComponents()))

    jpa.withTransaction(asJavaSupplier(() => {
      try {
        val studyResult = fetchStudyResult(studyResultUuid)
        studyResult.getWorkerType match {
          case JatosWorker.WORKER_TYPE => jatosGroupChannel.leave(studyResult)
          case PersonalSingleWorker.WORKER_TYPE => personalSingleGroupChannel.leave(studyResult)
          case PersonalMultipleWorker.WORKER_TYPE => personalMultipleGroupChannel.leave(studyResult)
          case GeneralSingleWorker.WORKER_TYPE => generalSingleGroupChannel.leave(studyResult)
          case GeneralMultipleWorker.WORKER_TYPE => generalMultipleGroupChannel.leave(studyResult)
          case MTSandboxWorker.WORKER_TYPE => mTGroupChannel.leave(studyResult)
          case MTWorker.WORKER_TYPE => mTGroupChannel.leave(studyResult)
          case _ => Results.BadRequest
        }
      } catch {
        case e: ForbiddenPublixException =>
          logger.info(s".leaveGroup: ${e.getMessage}")
          Forbidden
        case e: BadRequestPublixException =>
          logger.info(s".leaveGroup: ${e.getMessage}")
          BadRequest
        case e: Exception =>
          logger.error(".leaveGroup: Exception during leaving a group channel", e)
          InternalServerError
      }
    }))
  }

  @throws[ForbiddenPublixException]
  @throws[BadRequestPublixException]
  private def fetchStudyResult(uuid: String) = {
    if (uuid == null || uuid == "undefined") throw new ForbiddenPublixException("Error getting study result UUID")
    val srOptional = studyResultDao.findByUuid(uuid)
    if (!srOptional.isPresent) throw new BadRequestPublixException("Study result " + uuid + " doesn't exist.")
    srOptional.get()
  }

  @throws[ForbiddenPublixException]
  @throws[BadRequestPublixException]
  private def fetchStudyResultAndInitLazy(uuid: String) = {
    jpa.withTransaction(asJavaSupplier(() => {
      val studyResult = fetchStudyResult(uuid)
      Helpers.initializeAndUnproxy(studyResult.getBatch, studyResult.getHistoryGroupResult)
      if (studyResult.getStudy != null) {
        Helpers.initializeAndUnproxy(studyResult.getStudy, studyResult.getStudy.getUserList)
      }
      if (studyResult.getActiveGroupResult != null) {
        Helpers.initializeAndUnproxy(studyResult.getActiveGroupResult, studyResult.getActiveGroupResult.getActiveMemberList)
      }
      if (studyResult.getWorker != null) {
        Helpers.initializeAndUnproxy(studyResult.getWorker.getStudyResultList)
      }
      studyResult
    }))
  }

}
