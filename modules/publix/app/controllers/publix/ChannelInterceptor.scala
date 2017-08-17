package controllers.publix

import javax.inject.{Inject, Singleton}

import models.common.workers._
import play.api.libs.json.JsValue
import play.api.mvc.{Controller, Results, WebSocket}
import play.db.jpa.{JPAApi, Transactional}
import services.publix.idcookie.IdCookieService

import scala.compat.java8.FunctionConverters.asJavaSupplier
import scala.concurrent.Future

@Singleton
class ChannelInterceptor @Inject()(idCookieService: IdCookieService,
                                   jpa: JPAApi,
                                   generalSingleBatchChannel: GeneralSingleBatchChannel,
                                   jatosBatchChannel: JatosBatchChannel,
                                   mTBatchChannel: MTBatchChannel,
                                   personalMultipleBatchChannel: PersonalMultipleBatchChannel,
                                   personalSingleBatchChannel: PersonalSingleBatchChannel)
  extends Controller {

  /**
    * HTTP type: WebSocket
    *
    * Opens a WebSocket for the batch channel that is used to exchange data
    * (batch session data) between study runs of a batch. All batch session
    * data are stored in a Batch model and the batch channels will be handled
    * by a BatchDispatcher which uses Akka.
    *
    * @param studyId       Study's ID
    * @param studyResultId StudyResult's ID
    * @return WebSocket that transports JSON strings.
    */
  @Transactional
  def openBatch(studyId: Long, studyResultId: Long): WebSocket =
  WebSocket.acceptOrResult[JsValue, JsValue] { request =>

    // Set Http.Context used in Play with Java. Needed by IdCookieService
    play.mvc.Http.Context.current.set(play.core.j.JavaHelpers.createJavaContext(request))
    val idCookie = idCookieService.getIdCookie(studyResultId)

    Future.successful({
      // Set Http.Context again because it's within Future
      play.mvc.Http.Context.current.set(play.core.j.JavaHelpers.createJavaContext(request))
      jpa.withTransaction(asJavaSupplier(() =>

        idCookie.getWorkerType match {
          case MTWorker.WORKER_TYPE => mTBatchChannel.open(studyId, studyResultId)
          case MTSandboxWorker.WORKER_TYPE => mTBatchChannel.open(studyId, studyResultId)
          case JatosWorker.WORKER_TYPE => jatosBatchChannel.open(studyId, studyResultId)
          case PersonalMultipleWorker.WORKER_TYPE => personalMultipleBatchChannel.open(studyId, studyResultId)
          case PersonalSingleWorker.WORKER_TYPE => personalSingleBatchChannel.open(studyId, studyResultId)
          case GeneralSingleWorker.WORKER_TYPE => generalSingleBatchChannel.open(studyId, studyResultId)
          case _ => Left(Results.BadRequest)

        }))
    })
  }
}
