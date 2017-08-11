package group

import javax.inject.Inject

import akka.actor.{Actor, ActorRef, PoisonPill}
import com.google.inject.assistedinject.Assisted
import general.ChannelRegistry
import group.GroupDispatcherS.TellWhom.TellWhom
import group.GroupDispatcherS._
import play.api.Logger
import play.api.libs.json.{JsObject, Json}

/**
  * A GroupDispatcher is an Akka Actor responsible for distributing messages
  * (GroupMsg) within a group. Thus it is the central class handling a group.
  *
  * A GroupDispatcher only handles the GroupChannels but is not responsible for
  * the actual joining of a GroupResult. This is done prior to creating a
  * GroupDispatcher by the GroupAdministration which persists all data in a
  * GroupResult. Who's member in a group is defined by the GroupResult.
  *
  * A GroupChannel is only opened after a StudyResult joined a GroupResult, which
  * is done in the GroupAdministration. Group data (e.g. who's member) are
  * persisted in a GroupResult entity. A GroupChannel is closed after the
  * StudyResult left the group.
  *
  * A GroupChannel registers in a GroupDispatcher by sending the RegisterChannel
  * message and unregisters by sending a UnregisterChannel message.
  *
  * A new GroupDispatcher is created by the GroupDispatcherRegistry. If a
  * GroupDispatcher has no more members it closes itself.
  *
  * A GroupDispatcher handles all messages specified in the
  * GroupDispatcherProtocol. There are fundamentally three different message
  * types: 1) group session patches, 2) broadcast messages, and 3) direct
  * messages intended for a certain group member.
  *
  * The group session patches are JSON Patches after RFC 6902 and used to
  * describe changes in the group session data. The session data are stored in
  * the GroupResult.
  *
  * @author Kristian Lange (2015, 2017)
  */
object GroupDispatcherS {

  trait Factory {
    def apply(dispatcherRegistry: ActorRef, actionHandler: GroupActionHandlerS,
              actionMsgBuilder: GroupActionMsgBuilderS, batchId: Long): Actor
  }

  /**
    * Message to a GroupDispatcher. The GroupDispatcher will tell all other
    * members of its group about the new member. This will NOT open a new group
    * channel (a group channel is opened by the WebSocketBuilder and registers
    * only with a GroupDispatcher).
    */
  case class Joined(studyResultId: Long)

  /**
    * Message to a GroupDispatcher. The GroupDispatcher will just tell all
    * other members of its GroupResult about the left member. This will NOT
    * close the group channel (a group channel is closed by sending a
    * PoisonChannel message.
    */
  case class Left(studyResultId: Long)

  /**
    * Message a GroupChannel can send to register in a GroupDispatcher.
    */
  case class RegisterChannel(studyResultId: Long)

  /**
    * Message an GroupChannel can send to its GroupDispatcher to indicate it's
    * closure.
    */
  case class UnregisterChannel(studyResultId: Long)

  /**
    * Message to signal that a GroupChannel has to change its GroupDispatcher.
    * It originates in the GroupChannelService and send to the GroupDispatcher
    * who currently handles the GroupChannel. There it is forwarded to the
    * actual GroupChannel.
    */
  case class ReassignChannel(studyResultId: Long, differentGroupDispatcher: ActorRef)

  /**
    * Message that forces a GroupChannel to close itself. Send to a
    * GroupDispatcher it will be forwarded to the right GroupChannel.
    */
  case class PoisonChannel(studyResultId: Long)


  object TellWhom extends Enumeration {
    type TellWhom = Value
    val All, AllButSender, SenderOnly, Unknown = Value
  }

  object GroupAction extends Enumeration {
    type GroupAction = Value
    val Joined = Value("JOINED") // Signals to every group member that a new member joined
    val Left = Value("LEFT") // Signals to every member that a member left
    val Opened = Value("OPENED") // // Signals to every member that a new group channel opened
    val Closed = Value("CLOSED") // // Signals to every member that a group channel was closed
    val Session = Value("SESSION") // Signals this message contains a group session update
    val SessionAck = Value("SESSION_ACK") // Signals that the session update was successful
    val SessionFail = Value("SESSION_FAIL") // Signals that the session update failed
    val Fixed = Value("FIXED") // Signals that this group is now fixed (no new members)
    val Error = Value("ERROR") // Used to send an error back to the sender
  }

  /**
    * Strings used as keys in the group action JSON
    */
  object GroupActionJsonKey extends Enumeration {
    type GroupActionKey = Value
    val Action = Value("action") // JSON key name for an action (mandatory for an GroupActionMsg)
    val Recipient = Value("recipient") // JSON key name to store the recipient of a group msg
    val GroupResultId = Value("groupResultId") // JSON key name for the group result ID
    val GroupState = Value("groupState") // JSON key name containing the GroupState
    val MemberId = Value("memberId") // JSON key name containing the group member ID (which is the study result ID)
    val Members = Value("members") // JSON key name containing all active members of the group defined by their study result ID
    val Channels = Value("channels") // JSON key name containing all open group channels defined by their study result ID
    val SessionData = Value("sessionData") // JSON key name for session data (must be accompanied with a session version)
    val SessionPatches = Value("sessionPatches") // JSON key name for a session patches (must be accompanied with a session version)
    val SessionVersion = Value("sessionVersion") // JSON key name for the batch session version (always together with either session data or patches)
    val ErrorMsg = Value("errorMsg") // JSON key name for an error message
  }

  /**
    * Message format used for communication in the group channel between the
    * GroupDispatcher and the group members. A GroupMsg contains a JSON node.
    * If the JSON node has a key named 'recipient' the message is intended for
    * only one group member - otherwise it's a broadcast message.
    *
    * For system messages the special GroupActionMsg is used.
    */
  //TODO
  /**
    * Message used for an action message. It has a JSON string and the JSON
    * contains an 'action' field. Additionally it can be addressed with TellWhom.
    */
  case class GroupMsg(json: JsObject, tellWhom: TellWhom = TellWhom.Unknown)

}

class GroupDispatcherS @Inject()(@Assisted dispatcherRegistry: ActorRef,
                                 @Assisted actionHandler: GroupActionHandlerS,
                                 @Assisted actionMsgBuilder: GroupActionMsgBuilderS,
                                 @Assisted groupResultId: Long) extends Actor {

  private val logger: Logger = Logger(this.getClass)

  private val channelRegistry = new ChannelRegistry

  override def postStop() = {
    dispatcherRegistry ! UnregisterChannel(groupResultId)
  }

  def receive = {
    case groupMsg: GroupMsg =>
      // We got a GroupMsg from a client
      handleGroupMsg(groupMsg)
    case Joined(studyResultId: Long) =>
      // A member joined
      joined(studyResultId);
    case Left(studyResultId: Long) =>
      // A member left
      left(studyResultId)
    case RegisterChannel(studyResultId: Long) =>
      // A GroupChannel wants to register
      registerChannel(studyResultId)
    case UnregisterChannel(studyResultId: Long) =>
      // A GroupChannel wants to unregister
      unregisterChannel(studyResultId)
    case rc: ReassignChannel =>
      // A GroupChannel has to be reassigned
      reassignChannel(rc)
    case p: PoisonChannel =>
      // Comes from GroupChannelService: close a group channel
      poisonChannel(p)
  }

  /**
    * Handle a GroupMsg received from a client. What to do with it depends on
    * the JSON inside the GroupMsg. It can be an group action msg, a direct or a
    * broadcast msg.
    */
  private def handleGroupMsg(msg: GroupMsg) = {
    logger.debug(s".handleGroupMsg: groupResultId $groupResultId, groupMsg ${Json.stringify(msg.json)}")
    val actionValueOpt = (msg.json \ GroupActionJsonKey.Action.toString).asOpt[String]
    if (actionValueOpt.isDefined)
      handleGroupActionMsg(msg) // We have a group action message
    else if (msg.json.keys.contains(GroupActionJsonKey.Recipient.toString)) {
      // We have a message intended for only one recipient (direct msg)
      val recipientOpt = (msg.json \ GroupActionJsonKey.Recipient.toString).asOpt[Long]
      tellRecipientOnly(msg, recipientOpt.get)
    }
    else
      tellAllButSender(msg) // We have broadcast msg: Tell everyone except the sender
  }

  private def handleGroupActionMsg(msg: GroupMsg) = {

  }

  /**
    * Registers the given channel and sends an OPENED action group message to
    * everyone in this group.
    */
  private def registerChannel(studyResultId: Long) = {
    logger.debug(s".registerChannel: groupResultId $groupResultId, studyResultId $studyResultId")
    channelRegistry.register(studyResultId, sender)
    val msg1 = actionMsgBuilder.build(groupResultId, studyResultId,
      channelRegistry, true, GroupAction.Opened, TellWhom.SenderOnly)
    val msg2 = actionMsgBuilder.build(groupResultId, studyResultId,
      channelRegistry, false, GroupAction.Opened, TellWhom.AllButSender)
    tellActionMsg(List(msg1, msg2))
  }

  /**
    * Unregisters the given channel and sends an CLOSED action group message to
    * everyone in this group. Then if the group is now empty it sends a
    * PoisonPill to this GroupDispatcher itself.
    */
  private def unregisterChannel(studyResultId: Long) = {
    logger.debug(s".unregisterChannel: groupResultId $groupResultId, studyResultId $studyResultId")

    // Only unregister GroupChannel if it's the one from the sender (there
    // might be a new GroupChannel for the same StudyResult after a reload)
    if (channelRegistry.containsStudyResult(studyResultId)
      && channelRegistry.getChannel(studyResultId) == sender) {
      channelRegistry.unregister(studyResultId)
      val msg = actionMsgBuilder.build(groupResultId, studyResultId,
        channelRegistry, false, GroupAction.Closed, TellWhom.AllButSender)
      tellActionMsg(List(msg))
    }

    // Tell this dispatcher to kill itself if it has no more members
    if (channelRegistry.isEmpty) self ! PoisonPill
  }

  /**
    * Forwards this ReassignChannel message to the right group channel.
    */
  private def reassignChannel(reassignChannel: ReassignChannel) = {
    logger.debug(s".reassignChannel: groupResultId $groupResultId, studyResultId ${reassignChannel.studyResultId}")
    val groupChannelOption = channelRegistry.getChannel(reassignChannel.studyResultId)
    if (groupChannelOption.nonEmpty)
      groupChannelOption.get forward reassignChannel
    else {
      val errorMsg = s"StudyResult with ID ${reassignChannel.studyResultId} not handled by GroupDispatcher for GroupResult with ID $groupResultId."
      val groupActionMsg = actionMsgBuilder.buildError(groupResultId, errorMsg, TellWhom.SenderOnly)
      tellSenderOnly(groupActionMsg)
    }
  }

  /**
    * Tells the GroupChannel to close itself. The GroupChannel then sends a
    * ChannelClosed back to this GroupDispatcher during postStop and then we
    * can remove the channel from the group registry and tell all other members
    * about it. Also send false back to the sender (GroupChannelService) if the
    * GroupChannel wasn't handled by this GroupDispatcher.
    */
  private def poisonChannel(poison: PoisonChannel) = {
    logger.debug(s".poisonGroupChannel: groupResultId $groupResultId, studyResultId ${poison.studyResultId}")
    val groupChannelOption = channelRegistry.getChannel(poison.studyResultId)
    if (groupChannelOption.nonEmpty) {
      groupChannelOption.get forward poison
      tellSenderOnly(true)
    }
    else tellSenderOnly(false)
  }

  /**
    * Send the JOINED group action message to all group members. Who's joined
    * the group is specified in the given Joined object.
    */
  private def joined(studyResultId: Long) {
    logger.debug(s".joined: groupResultId $groupResultId studyResultId $studyResultId")
    val msg = actionMsgBuilder.build(groupResultId, studyResultId,
      channelRegistry, false, GroupAction.Joined, TellWhom.AllButSender)
    tellAllButSender(msg)
  }

  /**
    * Send the LEFT group action message to all group members. Who's left the
    * group is specified in the given Left object.
    */
  private def left(studyResultId: Long) = {
    logger.debug(s".left: groupResultId $groupResultId, studyResultId $studyResultId")
    val msg = actionMsgBuilder.build(groupResultId, studyResultId, channelRegistry, false, GroupAction.Left, TellWhom.AllButSender)
    tellAllButSender(msg)
  }

  /**
    * Sends the message only to the recipient specified by the given study
    * result ID.
    */
  private def tellRecipientOnly(msg: GroupMsg, recipientStudyResultId: Long) {
    logger.debug(s".tellRecipientOnly: groupResultId $groupResultId, recipientStudyResultId $recipientStudyResultId, msg ${Json.stringify(msg.json)}")
    val groupChannel = channelRegistry.getChannel(recipientStudyResultId)
    if (groupChannel.isDefined)
      groupChannel.get ! msg
    else {
      val errorMsg = s"Recipient ${recipientStudyResultId} isn't member of this group."
      logger.debug(s".tellRecipientOnly: groupResultId $groupResultId, errorMsg $errorMsg")
      val groupActionMsg = actionMsgBuilder.buildError(groupResultId, errorMsg,
        TellWhom.SenderOnly)
      tellActionMsg(List(groupActionMsg))
    }
  }

  private def tellActionMsg(msgList: List[GroupMsg]) = {
    msgList.foreach(msg =>
      msg.tellWhom match {
        case TellWhom.All => tellAll(msg)
        case TellWhom.SenderOnly => tellSenderOnly(msg)
        case TellWhom.AllButSender => tellAllButSender(msg)
        case _ => logger.warn(s".tellActionMsg: no TellWhom specified")
      }
    )
  }

  /**
    * Sends the message to everyone in channelRegistry.
    */
  private def tellAll(msg: GroupMsg) = {
    logger.debug(s".tellAll: groupResultId $groupResultId, msg ${Json.stringify(msg.json)}")
    for (actorRef <- channelRegistry.getAllChannels) {
      actorRef ! (msg, self)
    }
  }

  /**
    * Sends the message to everyone in the group registry except the sender of
    * this message.
    */
  private def tellAllButSender(msg: GroupMsg) = {
    logger.debug(s".tellAllButSender: groupResultId $groupResultId, msg ${Json.stringify(msg.json)}")
    for (actorRef <- channelRegistry.getAllChannels) {
      if (actorRef != sender) actorRef ! (msg, self)
    }
  }

  /**
    * Sends the message only to the sender.
    */
  private def tellSenderOnly(msg: GroupMsg) = {
    logger.debug(s".tellSenderOnly: groupResultId $groupResultId, msg ${Json.stringify(msg.json)}")
    sender ! msg
  }

  /**
    * Sends the message only to the sender.
    */
  private def tellSenderOnly(msg: Any) = {
    logger.debug(s".tellSenderOnly: groupResultId $groupResultId, msg $msg")
    sender ! msg
  }

}
