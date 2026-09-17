package batch

import batch.BatchDispatcher.BatchActionJsonKey.Action
import batch.BatchDispatcher._
import org.apache.pekko.actor.{Actor, ActorRef}
import play.api.libs.json.{JsObject, Json}

import javax.inject.Inject

/**
 * BatchChannelActor is a Pekko Actor that represents the batch channel's WebSocket. A batch channel is a WebSocket
 * connecting a client who's running a study with the JATOS server.
 *
 * A BatchChannelActor is always opened during initialization of jatos.js (where a GroupChannelActor is opened only
 * after the group was joined).
 *
 * A BatchChannelActor belongs to a BatchDispatcher. A BatchChannelActor is created by the BatchChannel service and
 * registers itself to its BatchDispatcher. It closes if the WebSocket is closed or externally, by the BatchDispatcher.
 * While closing down, it unregisters from the BatchDispatcher.
 */
class BatchChannelActor @Inject()(out: ActorRef,
                                  studyResultId: Long,
                                  batchId: Long,
                                  batchDispatcher: BatchDispatcher) extends Actor {

  private var registered = false

  override def postStop(): Unit = {
    if (registered) batchDispatcher.unregisterChannel(batchId, studyResultId)
  }

  val pong: JsObject = Json.obj("heartbeat" -> "pong")

  def receive: Receive = {

    case msg: JsObject if (msg \ Action.toString).asOpt[String].contains(BatchAction.Ready.toString) =>
      // If we receive a "Ready" message, register with the BatchDispatcher
      if (!registered) {
        registered = true
        batchDispatcher.registerChannel(batchId, studyResultId, self)
      }

    case msg: JsObject if msg.keys.contains("heartbeat") =>
      // If we receive a heartbeat ping, answer directly with a pong
      out ! pong

    case msg: JsObject =>
      // If we receive an JSON object (can only come from the client), wrap it in a
      // BatchMsg and forward it to the BatchDispatcher
      batchDispatcher.handleActionMsg(BatchMsg(msg), batchId, studyResultId, self)

    case msg: BatchMsg =>
      // If we receive a BatchMsg (can only come from the BatchDispatcher),
      // send the unwrapped JSON to the client
      out ! msg.json
  }

}
