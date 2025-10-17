package controllers.publix

import akka.actor.{ActorSystem, Props}
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import akka.util.Timeout
import batch.{BatchChannelActor, BatchDispatcherRegistry}
import exceptions.publix.PublixException
import models.common.StudyResult
import models.common.workers._
import play.api.Logger
import play.api.libs.streams.ActorFlow
import play.api.mvc._
import services.publix.StudyAuthorisation
import services.publix.idcookie.IdCookieService
import services.publix.workers._

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration._

/**
  * Abstract class that handles the opening of the batch channel. It has concrete implementations for
  * each worker type.
  */
abstract class BatchChannel[A <: Worker](components: ControllerComponents,
                                         studyAuthorisation: StudyAuthorisation)
  extends AbstractController(components) {

  private val logger: Logger = Logger(this.getClass)

  @Inject
  implicit var system: ActorSystem = _

  @Inject
  implicit var materializer: Materializer = _

  @Inject
  var idCookieService: IdCookieService = _

  @Inject
  var batchDispatcherRegistry: BatchDispatcherRegistry = _

  /**
    * Time to wait for an answer after asking an Akka actor
    */
  implicit val timeout: Timeout = 30.seconds

  /**
    * HTTP endpoint that opens a batch channel and returns an Akka stream Flow that will be turned
    * into WebSocket. In case of an error/ problem, a PublixException is thrown.
    */
  @throws(classOf[PublixException])
  def open(studyResult: StudyResult)(implicit request: RequestHeader): Flow[Any, Nothing, _] = {
    logger.info(s".open: studyResult ${studyResult.getId}")
    val worker = studyResult.getWorker.asInstanceOf[A]
    val study = studyResult.getStudy
    val batch = studyResult.getBatch
    studyAuthorisation.checkWorkerAllowedToDoStudy(request.withBody().session.asJava, worker, study, batch)

    // To be sure, check if there is already a batch channel and close the old one before opening a new one.
    closeBatchChannel(batch.getId, studyResult.getId)

    // Get the BatchDispatcher that will handle this batch.
    val batchDispatcher = batchDispatcherRegistry.getOrRegister(batch.getId)
    ActorFlow.actorRef { out => Props(new BatchChannelActor(out, studyResult.getId, batchDispatcher)) }
  }

  /**
    * Closes the batch channel that belongs to the given study result ID.
    */
  private def closeBatchChannel(batchId: Long, studyResultId: Long): Unit = {
    val batchDispatcherOption = batchDispatcherRegistry.get(batchId)
    if (batchDispatcherOption.isDefined) {
      batchDispatcherOption.get.poisonChannel(studyResultId)
    }
  }

}

@Singleton
class JatosBatchChannel @Inject()(components: ControllerComponents,
                                  studyAuthorisation: JatosStudyAuthorisation)
  extends BatchChannel[JatosWorker](components, studyAuthorisation)

@Singleton
class PersonalSingleBatchChannel @Inject()(components: ControllerComponents,
                                           studyAuthorisation: PersonalSingleStudyAuthorisation)
  extends BatchChannel[PersonalSingleWorker](components, studyAuthorisation)

@Singleton
class PersonalMultipleBatchChannel @Inject()(components: ControllerComponents,
                                             studyAuthorisation: PersonalMultipleStudyAuthorisation)
  extends BatchChannel[PersonalMultipleWorker](components, studyAuthorisation)

@Singleton
class GeneralSingleBatchChannel @Inject()(components: ControllerComponents,
                                          studyAuthorisation: GeneralSingleStudyAuthorisation)
  extends BatchChannel[GeneralSingleWorker](components, studyAuthorisation)

@Singleton
class GeneralMultipleBatchChannel @Inject()(components: ControllerComponents,
                                            studyAuthorisation: GeneralMultipleStudyAuthorisation)
  extends BatchChannel[GeneralMultipleWorker](components, studyAuthorisation)

// Handles both MTWorker and MTSandboxWorker
@Singleton
class MTBatchChannel @Inject()(components: ControllerComponents,
                               studyAuthorisation: MTStudyAuthorisation)
  extends BatchChannel[MTWorker](components, studyAuthorisation)
