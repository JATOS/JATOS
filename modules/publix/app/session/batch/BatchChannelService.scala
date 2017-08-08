package session.batch

import javax.inject.{Inject, Named, Singleton}

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import com.fasterxml.jackson.databind.JsonNode
import exceptions.publix.InternalServerErrorPublixException
import models.common.StudyResult
import play.mvc.LegacyWebSocket
import session.WebSocketBuilder
import session.batch.BatchDispatcher.PoisonChannel
import session.batch.BatchDispatcherRegistry.{GetOrCreate, ItsThisOne}

import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * Service class that handles of opening and closing of batch channels with
  * Akka. This class is the public interface to use the batch channel.
  *
  * @author Kristian Lange (2015 - 2017)
  */
@Singleton
class BatchChannelService @Inject()(@Named("batch-dispatcher-registry-actor") batchDispatcherRegistry: ActorRef) {

  /**
    * Time to wait for an answer after asking an Akka actor
    */
  implicit val timeout: Timeout = 5.seconds

  /**
    * Opens a new batch channel WebSocket for the given StudyResult.
    */
  @throws[InternalServerErrorPublixException]
  def openBatchChannel(studyResult: StudyResult): LegacyWebSocket[JsonNode] = {
    val batch = studyResult.getBatch
    // Get the BatchDispatcher that will handle this batch.
    val batchDispatcher = getOrCreateBatchDispatcher(batch.getId)
    // If this BatchDispatcher already has a batch channel for this
    // StudyResult, close the old one before opening a new one.
    closeBatchChannel(studyResult.getId, batchDispatcher)
    WebSocketBuilder.withBatchChannel(studyResult.getId, batchDispatcher)
  }

  /**
    * Asks the BatchDispatcherRegistry to get or create the batch dispatcher for
    * the given ID. It waits until it receives an answer. The answer is an
    * ActorRef (type BatchDispatcher).
    */
  @throws[InternalServerErrorPublixException]
  private def getOrCreateBatchDispatcher(batchId: Long): ActorRef = {
    val future = batchDispatcherRegistry ? GetOrCreate(batchId)
    try
      Await.result(future, timeout.duration).asInstanceOf[ItsThisOne].dispatcher
    catch {
      case e: Exception => throw new InternalServerErrorPublixException(e.getMessage)
    }
  }

  /**
    * Closes the batch channel that belongs to the given study result ID and is
    * managed by the given BatchDispatcher. Waits until it receives a result
    * from the BatchDispatcher actor. It returns true if the BatchChannel
    * was managed by the BatchDispatcher and was successfully removed from the
    * BatchDispatcher, false otherwise (it was probably never managed by the
    * dispatcher).
    */
  @throws[InternalServerErrorPublixException]
  private def closeBatchChannel(studyResultId: Long, batchDispatcher: ActorRef) = {
    val future = batchDispatcher ? PoisonChannel(studyResultId)
    try
      Await.result(future, timeout.duration).asInstanceOf[Boolean]
    catch {
      case e: Exception => throw new InternalServerErrorPublixException(e.getMessage)
    }
  }

}
