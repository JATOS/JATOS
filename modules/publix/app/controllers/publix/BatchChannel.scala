package controllers.publix

import org.apache.pekko.actor.{ActorSystem, Props}
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Flow
import org.apache.pekko.util.Timeout
import batch.{BatchChannelActor, BatchDispatcherRegistry}
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
  private var batchDispatcherRegistry: BatchDispatcherRegistry = _

  /**
   * Time to wait for an answer after asking a Pekko actor
   */
  implicit val timeout: Timeout = 30.seconds

  /**
   * HTTP endpoint that opens a batch channel and returns a Pekko stream Flow that will be turned
   * into WebSocket. In case of an error/ problem, a PublixException is thrown.
   */
  def open(studyResult: StudyResult): Flow[Any, Nothing, _] = {
    logger.info(s".open: studyResult ${studyResult.getId}")
    val worker = studyResult.getWorker.asInstanceOf[A]
    val study = studyResult.getStudy
    val batch = studyResult.getBatch
    studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study, batch)

    // To be sure, check if there is already a batch channel and close the old one before opening a new one.
    batchDispatcherRegistry.closeBatchChannel(batch.getId, studyResult.getId)

    // Get the BatchDispatcher that will handle this batch.
    val batchDispatcher = batchDispatcherRegistry.getOrRegister(batch.getId)
    ActorFlow.actorRef { out => Props(new BatchChannelActor(out, studyResult.getId, batchDispatcher)) }
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
