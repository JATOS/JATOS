package group

import javax.inject.Inject
import akka.actor.{Actor, ActorRef, PoisonPill, Props}
import group.GroupDispatcher._
import play.api.libs.json.{JsObject, Json}

/**
  * GroupChannelActor is an Akka Actor that represents the group channel's WebSocket. A group
  * channel is a WebSocket connecting a client who's running a study with the JATOS server.
  *
  * A GroupChannelActor is only be opened after a study run (identified by a StudyResult) joined
  * a group, which is done in the GroupAdministration. Group data (e.g. who's member) are persisted
  * in a GroupResult entity. A GroupChannelActor is closed after the StudyResult left the
  * GroupResult.
  *
  * A GroupChannelActor belongs to a GroupDispatcher. A GroupChannelActor is created by the
  * GroupChannel service and registers itself by sending a RegisterChannel message to its
  * GroupDispatcher. It closes down after receiving a PoisonChannel message or if the WebSocket
  * is closed. While closing down it unregisters from the GroupDispatcher by sending a
  * UnregisterChannel message. A GroupChannelActor can, if it's told to, reassign itself to a
  * different GroupDispatcher.
  *
  * @author Kristian Lange
  */
object GroupChannelActor {
  def props(out: ActorRef, studyResultId: Long, groupDispatcher: ActorRef): Props =
    Props(new GroupChannelActor(out, studyResultId, groupDispatcher))
}

class GroupChannelActor @Inject()(out: ActorRef,
                                  studyResultId: Long,
                                  var groupDispatcher: ActorRef) extends Actor {

  val pong: JsObject = Json.obj("heartbeat" -> "pong")

  override def preStart(): Unit = groupDispatcher ! RegisterChannel(studyResultId)

  override def postStop(): Unit = groupDispatcher ! UnregisterChannel(studyResultId)

  def receive: Receive = {
    case msg: JsObject if msg.keys.contains("heartbeat") =>
      // If we receive a heartbeat ping, answer directly with a pong
      out ! pong
    case json: JsObject =>
      // If we receive a JsonNode (only from the client) wrap it in a GroupMsg and forward it to
      // the GroupDispatcher
      groupDispatcher ! GroupMsg(json)
    case msg: GroupMsg =>
      // If we receive a GroupMsg (only from the GroupDispatcher) send the wrapped JsonNode to
      // the client
      out ! msg.json
    case rc: ReassignChannel =>
      // This group channel has to reassign to a different dispatcher
      groupDispatcher ! UnregisterChannel(studyResultId)
      groupDispatcher = rc.differentGroupDispatcher
      groupDispatcher ! RegisterChannel(studyResultId)
    case _: PoisonChannel =>
      // Kill this group channel actor
      self ! PoisonPill
  }

}
