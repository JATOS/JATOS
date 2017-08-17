package controllers.publix

import javax.inject.{Inject, Named, Singleton}

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.stream.Materializer
import akka.util.Timeout
import batch.BatchChannelActor
import batch.BatchDispatcher.PoisonChannel
import batch.BatchDispatcherRegistry.{GetOrCreate, ItsThisOne}
import exceptions.publix.{BadRequestPublixException, ForbiddenPublixException, NotFoundPublixException}
import models.common.workers._
import play.api.Logger
import play.api.libs.streams.ActorFlow
import play.api.mvc._
import services.publix.idcookie.IdCookieService
import services.publix.workers._
import services.publix.{PublixUtils, StudyAuthorisation}

import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * Abstract class that handles opening of the batch channel. It has concrete
  * implementations for each worker type.
  *
  * @param publixUtils
  * @param studyAuthorisation
  * @tparam A
  */
abstract class BatchChannel[A <: Worker](publixUtils: PublixUtils[A],
                                         studyAuthorisation: StudyAuthorisation[A])
  extends Controller {

  private val logger: Logger = Logger(this.getClass)

  @Inject
  implicit var system: ActorSystem = null

  @Inject
  implicit var materializer: Materializer = null

  @Inject
  var idCookieService: IdCookieService = null

  @Inject
  @Named("batch-dispatcher-registry-actor")
  var dispatcherRegistry: ActorRef = null

  /**
    * Time to wait for an answer after asking an Akka actor
    */
  implicit val timeout: Timeout = 5.seconds

  def open(studyId: Long, studyResultId: Long) = try {

    val idCookie = idCookieService.getIdCookie(studyResultId)
    val worker: A = publixUtils.retrieveTypedWorker(idCookie.getWorkerId)
    val study = publixUtils.retrieveStudy(studyId)
    val batch = publixUtils.retrieveBatch(idCookie.getBatchId)
    studyAuthorisation.checkWorkerAllowedToDoStudy(worker, study, batch)
    val studyResult = publixUtils.retrieveStudyResult(worker, study, studyResultId)

    // Get the BatchDispatcher that will handle this batch.
    val batchDispatcher = getOrCreateBatchDispatcher(batch.getId)
    // If this BatchDispatcher already has a batch channel for this
    // StudyResult, close the old one before opening a new one.
    closeBatchChannel(studyResult.getId, batchDispatcher)
    Right(ActorFlow.actorRef {
      out => BatchChannelActor.props(out, studyResult.getId, batchDispatcher)
    })

  } catch {
    // Due to returning a WebSocket we can't throw exceptions like with a Result
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

  /**
    * Asks the BatchDispatcherRegistry to get or create a batch dispatcher for
    * the given ID. It waits until it receives an answer. The answer is an
    * ActorRef (to a BatchDispatcher).
    */
  private def getOrCreateBatchDispatcher(batchId: Long): ActorRef = {
    val future = dispatcherRegistry ? GetOrCreate(batchId)
    Await.result(future, timeout.duration).asInstanceOf[ItsThisOne].dispatcher
  }

  /**
    * Closes the batch channel that belongs to the given study result ID and is
    * managed by the given BatchDispatcher. Waits until it receives a result
    * from the BatchDispatcher actor. It returns true if the BatchChannel
    * was managed by the BatchDispatcher and was successfully removed from the
    * BatchDispatcher, false otherwise (it was probably never managed by the
    * dispatcher).
    */
  private def closeBatchChannel(studyResultId: Long, batchDispatcher: ActorRef) = {
    val future = batchDispatcher ? PoisonChannel(studyResultId)
    Await.result(future, timeout.duration).asInstanceOf[Boolean]
  }
}

@Singleton
class GeneralSingleBatchChannel @Inject()(publixUtils: GeneralSinglePublixUtils,
                                          studyAuthorisation: GeneralSingleStudyAuthorisation)
  extends BatchChannel[GeneralSingleWorker](publixUtils, studyAuthorisation)

@Singleton
class JatosBatchChannel @Inject()(publixUtils: JatosPublixUtils,
                                  studyAuthorisation: JatosStudyAuthorisation)
  extends BatchChannel[JatosWorker](publixUtils, studyAuthorisation)

@Singleton
class MTBatchChannel @Inject()(publixUtils: MTPublixUtils,
                               studyAuthorisation: MTStudyAuthorisation)
  extends BatchChannel[MTWorker](publixUtils, studyAuthorisation)

//TODO Do we need MTSandboxChannel?

@Singleton
class PersonalMultipleBatchChannel @Inject()(publixUtils: PersonalMultiplePublixUtils,
                                             studyAuthorisation: PersonalMultipleStudyAuthorisation)
  extends BatchChannel[PersonalMultipleWorker](publixUtils, studyAuthorisation)

@Singleton
class PersonalSingleBatchChannel @Inject()(publixUtils: PersonalSinglePublixUtils,
                                           studyAuthorisation: PersonalSingleStudyAuthorisation)
  extends BatchChannel[PersonalSingleWorker](publixUtils, studyAuthorisation)