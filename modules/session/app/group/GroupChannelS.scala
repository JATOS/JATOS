package group

import javax.inject.Inject

import akka.actor.{Actor, ActorRef, PoisonPill, Props}
import group.GroupDispatcherS._
import play.api.libs.json.JsObject

/**
  * GroupChannel is an Akka Actor that represents the group channel's WebSocket.
  * A group channel is a WebSocket connecting a client who's running a study with
  * the JATOS server.
  *
  * A GroupChannel is only be opened after a StudyResult joined a group, which is
  * done in the GroupAdministration. Group data (e.g. who's member) are persisted
  * in a GroupResult entity. A GroupChannel is closed after the StudyResult left
  * the GroupResult.
  *
  * A GroupChannel belongs to a GroupDispatcher. A GroupChannel is created by the
  * GroupChannelService and registers itself by sending a RegisterChannel message
  * to its GroupDispatcher. It closes down after receiving a PoisonChannel
  * message or if the WebSocket is closed. While closing down it unregisters from
  * the GroupDispatcher by sending a UnregisterChannel message. A GroupChannel
  * can, if it's told to, reassign itself to a different GroupDispatcher.
  *
  * @author Kristian Lange (2015, 2017)
  */
object GroupChannelS {

  def props(out: ActorRef, studyResultId: Long, groupDispatcher: ActorRef): Props =
    Props(new GroupChannelS(out, studyResultId, groupDispatcher))

}

class GroupChannelS @Inject()(out: ActorRef,
                              studyResultId: Long,
                              var groupDispatcher: ActorRef) extends Actor {

  override def preStart() = {
    groupDispatcher ! RegisterChannel(studyResultId)
  }

  override def postStop() = {
    groupDispatcher ! UnregisterChannel(studyResultId)
  }

  def receive = {
    case json: JsObject =>
      // If we receive a JsonNode (only from the client) wrap it in a
      // GroupMsg and forward it to the GroupDispatcher
      groupDispatcher ! GroupMsg(json)
    case msg: GroupMsg =>
      // If we receive a GroupMsg (only from the GroupDispatcher) send
      // the wrapped JsonNode to the client
      out ! msg.json
    case rc: ReassignChannel =>
      // This group channel has to reassign to a different dispatcher
      groupDispatcher ! UnregisterChannel(studyResultId)
      groupDispatcher = rc.differentGroupDispatcher
      groupDispatcher ! RegisterChannel(studyResultId)
    case PoisonChannel =>
      // Kill this group channel
      self ! PoisonPill
  }

}
