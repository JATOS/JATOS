package group

import group.GroupDispatcher.GroupActionJsonKey.Action
import org.apache.pekko.actor.{Actor, ActorRef}
import group.GroupDispatcher._
import play.api.libs.json.{JsObject, Json}

import javax.inject.Inject

/**
 * GroupChannelActor is a Pekko Actor that represents the group channel's WebSocket. A group channel is a WebSocket
 * connecting a client who's running a study with the JATOS server.
 *
 * A GroupChannelActor is only opened after a study run (identified by a StudyResult) joined a group, which is done in
 * the GroupAdministration. Group data (e.g. who is member) are persisted in a GroupResult entity. A GroupChannelActor
 * is closed after the StudyResult left the GroupResult.
 *
 * A GroupChannelActor belongs to a GroupDispatcher. A GroupChannelActor is created by the GroupChannel service. It
 * registers and unregisters itself with the GroupDispatcher.
 */
class GroupChannelActor @Inject()(out: ActorRef,
                                  studyResultId: Long,
                                  private var groupResultId: Long,
                                  groupDispatcher: GroupDispatcher) extends Actor {

  private var registered = false

  val pong: JsObject = Json.obj("heartbeat" -> "pong")

  override def postStop(): Unit = {
    if (registered) groupDispatcher.unregisterChannel(groupResultId, studyResultId)
  }

  override def toString: String = studyResultId.toString

  def setGroupResultId(groupResultId: Long): Unit = this.groupResultId = groupResultId

  def receive: Receive = {

    case msg: JsObject if (msg \ Action.toString).asOpt[String].contains(GroupAction.Ready.toString) =>
      // If we receive a "Ready" message, register with the GroupDispatcher
      if (!registered) {
        registered = true
        groupDispatcher.registerChannel(groupResultId, studyResultId, this)
      }

    case msg: JsObject if msg.keys.contains("heartbeat") =>
      // If we receive a heartbeat ping, answer directly with a pong
      out ! pong

    case json: JsObject =>
      // If we receive a JsonNode (only from the client) wrap it in a GroupMsg and forward it to
      // the GroupDispatcher
      groupDispatcher.handleGroupMsg(GroupMsg(json), groupResultId, studyResultId, self)

    case msg: GroupMsg =>
      // If we receive a GroupMsg (only from the GroupDispatcher) send the wrapped JsonNode to
      // the client
      out ! msg.json
  }

}
