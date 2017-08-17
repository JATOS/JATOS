package batch

import javax.inject.Inject

import akka.actor.{Actor, ActorRef, PoisonPill, Props}
import batch.BatchDispatcher._
import play.api.libs.json.JsObject

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
object BatchChannelActor {
  def props(out: ActorRef, studyResultId: Long, batchDispatcher: ActorRef) =
    Props(new BatchChannelActor(out, studyResultId, batchDispatcher))
}

class BatchChannelActor @Inject()(out: ActorRef,
                                  studyResultId: Long,
                                  batchDispatcher: ActorRef) extends Actor {

  override def preStart() = {
    batchDispatcher ! RegisterChannel(studyResultId)
  }

  override def postStop() = {
    batchDispatcher ! UnregisterChannel(studyResultId)
  }

  def receive = {
    case msg: JsObject =>
      // If we receive an JSON object (can only come from the client) wrap it in a
      // BatchMsg and forward it to the BatchDispatcher
      batchDispatcher ! BatchMsg(msg)
    case msg: BatchMsg =>
      // If we receive a BatchMsg (can only come from the BatchDispatcher)
      // send the unwrapped JSON to the client
      out ! msg.json
    case PoisonChannel =>
      // Kill this batch channel
      self ! PoisonPill
  }

}
