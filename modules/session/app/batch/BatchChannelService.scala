package batch

import javax.inject.{Inject, Named, Singleton}

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.stream.Materializer
import akka.util.Timeout
import models.common.StudyResult
import play.api.Logger
import play.api.libs.json.JsValue
import play.api.libs.streams.ActorFlow
import play.api.mvc.{Results, WebSocket}
import batch.BatchDispatcher.PoisonChannel
import batch.BatchDispatcherRegistry.{GetOrCreate, ItsThisOne}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

/**
  * Service class that handles of opening and closing of batch channels with
  * Akka. This class is the public interface to use the batch channel.
  *
  * @author Kristian Lange (2015 - 2017)
  */
@Singleton
class BatchChannelService @Inject()(implicit system: ActorSystem,
                                    materializer: Materializer,
                                    @Named("batch-dispatcher-registry-actor") batchDispatcherRegistry: ActorRef) {

  private val logger: Logger = Logger(this.getClass)

  /**
    * Time to wait for an answer after asking an Akka actor
    */
  implicit val timeout: Timeout = 5.seconds

  /**
    * Opens a new batch channel WebSocket for the given StudyResult.
    */
  def openBatchChannel(studyResult: StudyResult): WebSocket = {
    WebSocket.acceptOrResult[JsValue, JsValue] { _ =>
      Future.successful(
        if (studyResult == null)
          Left(Results.InternalServerError)
        else {
          try {
            val batch = studyResult.getBatch
            // Get the BatchDispatcher that will handle this batch.
            val batchDispatcher = getOrCreateBatchDispatcher(batch.getId)
            // If this BatchDispatcher already has a batch channel for this
            // StudyResult, close the old one before opening a new one.
            closeBatchChannel(studyResult.getId, batchDispatcher)
            //WebSocketBuilder.withBatchChannel(studyResult.getId, batchDispatcher)
            Right(ActorFlow.actorRef {
              out => BatchChannel.props(out, studyResult.getId, batchDispatcher)
            })
          } catch {
            case e: Exception =>
              logger.error("Exception during opening of batch channel", e)
              Left(Results.InternalServerError)
          }
        }
      )
    }
  }

  /**
    * Asks the BatchDispatcherRegistry to get or create the batch dispatcher for
    * the given ID. It waits until it receives an answer. The answer is an
    * ActorRef (type BatchDispatcher).
    */
  private def getOrCreateBatchDispatcher(batchId: Long): ActorRef = {
    val future = batchDispatcherRegistry ? GetOrCreate(batchId)
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
