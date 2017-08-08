package session.batch

import javax.inject.Inject

import akka.actor.{Actor, ActorRef, PoisonPill, Props}
import com.fasterxml.jackson.databind.node.ObjectNode
import session.batch.BatchDispatcher._

/**
  * BatchChannel is an Akka Actor that represents the batch channel's WebSocket.
  * A batch channel is a WebSocket connecting a client who's running a study with
  * the JATOS server.
  *
  * A BatchChannel is always opened during initialization of jatos.js.
  *
  * A BatchChannel belongs to a BatchDispatcher. A BatchChannel is created by the
  * BatchChannelService and registers itself by sending a RegisterChannel message
  * to its BatchDispatcher. It closes down after receiving a PoisonChannel
  * message or if the WebSocket is closed. While closing down it unregisters from
  * the BatchDispatcher by sending a UnregisterChannel message. A BatchChannel
  * can, if it's told to, reassign itself to a different BatchDispatcher.
  *
  * @author Kristian Lange (2017)
  */
object BatchChannel {
  def props(out: ActorRef, studyResultId: Long, batchDispatcher: ActorRef) =
    Props(new BatchChannel(out, studyResultId, batchDispatcher))
}

class BatchChannel @Inject()(out: ActorRef,
                             studyResultId: Long,
                             batchDispatcher: ActorRef) extends Actor {

  override def preStart() = {
    batchDispatcher ! RegisterChannel(studyResultId)
  }

  override def postStop() = {
    batchDispatcher ! UnregisterChannel(studyResultId)
  }

  def receive = {
    case msg: ObjectNode =>
      // If we receive a JsonNode (can only come from the client) wrap it in a
      // BatchActionMsg and forward it to the BatchDispatcher
      batchDispatcher ! BatchActionMsg(msg)
    case msg: BatchActionMsg =>
      // If we receive a BatchActionMsg (can only come from the BatchDispatcher)
      // send the unwrapped JsonNode to the client
      out ! msg.jsonNode
    case PoisonChannel =>
      // Kill this batch channel
      self ! PoisonPill
  }

}
