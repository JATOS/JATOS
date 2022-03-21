package group

import akka.actor.{Actor, ActorRef, PoisonPill}
import com.google.inject.assistedinject.Assisted
import general.ChannelRegistry
import group.GroupDispatcher.TellWhom.TellWhom
import group.GroupDispatcher._
import group.GroupDispatcherRegistry.Unregister

import javax.inject.Inject
import play.api.Logger
import play.api.libs.json.Reads._
import play.api.libs.json.{JsObject, Json}

/**
  * A GroupDispatcher is an Akka Actor responsible for distributing messages (GroupMsg) within a
  * group. Thus it is the central class handling a group.
  *
  * A GroupDispatcher only handles the GroupChannels but is not responsible for the actual
  * joining of a GroupResult. This is done prior to creating a GroupDispatcher by the
  * GroupAdministration which persists all data in a GroupResult. Who's member in a group is
  * ultimately defined by the GroupResult.
  *
  * A GroupChannelActor is only opened after a StudyResult joined a GroupResult, which is done in
  * the GroupAdministration. Group data (e.g. who's member) are persisted in a GroupResult entity.
  * A GroupChannelActor is closed after the StudyResult left the group.
  *
  * A GroupChannelActor registers in a GroupDispatcher by sending the RegisterChannel message and
  * unregisters by sending a UnregisterChannel message.
  *
  * A new GroupDispatcher is created by the GroupDispatcherRegistry. If a GroupDispatcher has no
  * more members it closes itself.
  *
  * A GroupDispatcher handles all messages specified in the GroupDispatcherProtocol. There are
  * fundamentally three different message types: 1) group session patches, 2) broadcast messages,
  * and 3) direct messages for a particular group member.
  *
  * The group session patches are JSON Patches after RFC 6902 and used to describe changes in the
  * group session data. The session data are stored in the GroupResult.
  *
  * @author Kristian Lange (2015, 2017)
  */
object GroupDispatcher {

  trait Factory {
    def apply(dispatcherRegistry: ActorRef, actionHandler: GroupActionHandler,
              actionMsgBuilder: GroupActionMsgBuilder, groupResultId: Long): Actor
  }

  /**
    * Message to a GroupDispatcher. The GroupDispatcher will tell all other members of its group
    * about the new member. This msg is NOT responsible for joining a group or opening a new
    * group channel. It merely advises the GroupDispatcher to tell all group members about the
    * newly joined member.
    */
  case class JoinedGroup(studyResultId: Long)

  /**
    * Message to a GroupDispatcher. The GroupDispatcher will just tell all other members of its
    * GroupResult about the left member. This msg is NOT responsible for leaving a group or
    * closing a group channel. It merely advises the GroupDispatcher to tell all group members
    * about the left member.
    */
  case class LeftGroup(studyResultId: Long)

  /**
    * Message a GroupChannelActor can send to register in a GroupDispatcher.
    */
  case class RegisterChannel(studyResultId: Long)

  /**
    * Message an GroupChannelActor can send to its GroupDispatcher to indicate it's closure.
    */
  case class UnregisterChannel(studyResultId: Long)

  /**
    * Message to signal that a GroupChannelActor has to change its GroupDispatcher. It originates
    * in the GroupChannel service and send to the GroupDispatcher who currently handles the
    * GroupChannelActor. There it is forwarded to the actual GroupChannelActor.
    */
  case class ReassignChannel(studyResultId: Long, differentGroupDispatcher: ActorRef)

  /**
    * Message that forces a GroupChannelActor to close itself. Send to a
    * GroupDispatcher it will be forwarded to the right GroupChannelActor.
    */
  case class PoisonChannel(studyResultId: Long)

  case class PoisonEmptyDispatcher()


  object TellWhom extends Enumeration {
    type TellWhom = Value
    val All, AllButSender, SenderOnly, Unknown = Value
  }

  //noinspection TypeAnnotation
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
  //noinspection TypeAnnotation
  object GroupActionJsonKey extends Enumeration {
    type GroupActionKey = Value
    // Action (mandatory for an action GroupMsg)
    val Action = Value("action")
    // Recipient of a group msg
    val Recipient = Value("recipient")
    // Group result ID
    val GroupResultId = Value("groupResultId")
    // GroupState
    val GroupState = Value("groupState")
    // Group member ID (which is equal to the study result ID)
    val MemberId = Value("memberId")
    // All active members of the group defined by their study result ID
    val Members = Value("members")
    // All open group channels defined by their study result ID
    val Channels = Value("channels")
    // Session data (must be accompanied with a session version)
    val SessionData = Value("sessionData")
    // Session patches (must be accompanied with a session version)
    val SessionPatches = Value("sessionPatches")
    // Identifier of an session action (mandatory)
    val SessionActionId = Value("sessionActionId")
    // Batch session version (mandatory for session data or patches)
    val SessionVersion = Value("sessionVersion")
    // Defines if we check the version before applying the patch
    val SessionVersioning = Value("sessionVersioning")
    // Error message
    val ErrorMsg = Value("errorMsg")
  }

  /**
    * Message format used for communication in the group channel between the GroupDispatcher and
    * the group members. A GroupMsg contains a JSON node. If the JSON has a key named
    * 'recipient' the message is intended for one particular group member - otherwise it's a
    * broadcast message. If the JSON has a 'action' key it is an group action message.
    *
    * The parameter 'tellWhom' can be used to address the recipient.
    */
  case class GroupMsg(json: JsObject, tellWhom: TellWhom = TellWhom.Unknown)

}

class GroupDispatcher @Inject()(@Assisted dispatcherRegistry: ActorRef,
                                @Assisted actionHandler: GroupActionHandler,
                                @Assisted actionMsgBuilder: GroupActionMsgBuilder,
                                @Assisted groupResultId: Long) extends Actor {

  private val logger: Logger = Logger(this.getClass)

  private val channelRegistry = new ChannelRegistry

  override def postStop(): Unit = dispatcherRegistry ! Unregister(groupResultId)

  def receive: Receive = {
    case groupMsg: GroupMsg =>
      // We got a GroupMsg from a client
      handleGroupMsg(groupMsg)
    case JoinedGroup(studyResultId: Long) =>
      // A member joined
      joined(studyResultId);
    case LeftGroup(studyResultId: Long) =>
      // A member left
      left(studyResultId)
    case RegisterChannel(studyResultId: Long) =>
      // A GroupChannelActor wants to register
      registerChannel(studyResultId)
    case UnregisterChannel(studyResultId: Long) =>
      // A GroupChannelActor wants to unregister
      unregisterChannel(studyResultId)
    case rc: ReassignChannel =>
      // A GroupChannelActor has to be reassigned
      reassignChannel(rc)
    case p: PoisonChannel =>
      // Comes from GroupChannel service: close a group channel
      poisonChannel(p)
    case PoisonEmptyDispatcher =>
      poisonEmptyDispatcher()
  }

  /**
    * Handle a GroupMsg received from a client. What to do with it depends on the JSON inside the
    * GroupMsg. It can be an group action msg, a direct msg (to a particular member) or a
    * broadcast msg to everyone in the group.
    */
  private def handleGroupMsg(msg: GroupMsg): Unit = {
    logger.debug(s".handleGroupMsg: groupResultId $groupResultId, groupMsg " +
        s"${Json.stringify(msg.json)}")

    if (msg.json.keys.contains(GroupActionJsonKey.Action.toString)) {
      // We have a group action message
      val studyResultId = channelRegistry.getStudyResult(sender).get
      val msgList = actionHandler.handleActionMsg(msg, groupResultId, studyResultId)
      tellActionMsg(msgList)

    } else if (msg.json.keys.contains(GroupActionJsonKey.Recipient.toString)) {
      // We have a message intended for only one recipient (direct msg)
      // Recipient's study result ID comes as a string with quotes and we have to convert to Long
      val recipient = (msg.json \ GroupActionJsonKey.Recipient.toString).as[String]
          .replace("\"", "").toLong
      tellRecipientOnly(msg, recipient)

    } else {
      // We have broadcast msg: Tell everyone except the sender
      tellAllButSender(msg)
    }
  }

  /**
    * Registers the given channel and sends an OPENED action group message to everyone in this
    * group.
    */
  private def registerChannel(studyResultId: Long): Unit = {
    logger.debug(s".registerChannel: groupResultId $groupResultId, studyResultId $studyResultId")
    channelRegistry.register(studyResultId, sender)
    val msg1 = actionMsgBuilder.build(groupResultId, studyResultId,
      channelRegistry, includeSessionData = true, GroupAction.Opened, TellWhom.SenderOnly)
    val msg2 = actionMsgBuilder.build(groupResultId, studyResultId,
      channelRegistry, includeSessionData = false, GroupAction.Opened, TellWhom.AllButSender)
    tellActionMsg(List(msg1, msg2))
  }

  /**
    * Unregisters the given channel and sends an CLOSED action group message to everyone in this
    * group. Then if the group is now empty it sends a PoisonPill to this GroupDispatcher itself.
    */
  private def unregisterChannel(studyResultId: Long): Unit = {
    logger.debug(s".unregisterChannel: groupResultId $groupResultId, studyResultId $studyResultId")

    // Only unregister GroupChannelActor if it's the one from the sender (there
    // might be a new GroupChannelActor for the same StudyResult after a reload)
    if (channelRegistry.containsStudyResult(studyResultId)
        && channelRegistry.getChannel(studyResultId).get == sender) {
      channelRegistry.unregister(studyResultId)
      val msg = actionMsgBuilder.build(groupResultId, studyResultId,
        channelRegistry, includeSessionData = false, GroupAction.Closed, TellWhom.AllButSender)
      tellActionMsg(List(msg))
    }
  }

  /**
    * Forwards this ReassignChannel message to the right group channel.
    */
  private def reassignChannel(reassignChannel: ReassignChannel): Unit = {
    logger.debug(s".reassignChannel: groupResultId $groupResultId, studyResultId " +
        s"${reassignChannel.studyResultId}")
    val groupChannelOption = channelRegistry.getChannel(reassignChannel.studyResultId)
    if (groupChannelOption.nonEmpty)
      groupChannelOption.get forward reassignChannel
    else {
      val errorMsg = s"StudyResult with ID ${reassignChannel.studyResultId} not handled by " +
          s"GroupDispatcher for GroupResult with ID $groupResultId."
      val groupMsg = actionMsgBuilder.buildError(groupResultId, errorMsg, TellWhom.SenderOnly)
      tellSenderOnly(groupMsg)
    }
  }

  /**
    * Tells the GroupChannelActor to close itself. The GroupChannelActor then sends a
    * ChannelClosed back to this GroupDispatcher during postStop and then we can remove the
    * channel from the group registry and tell all other members about it. Also send false back
    * to the sender (GroupChannel service) if the GroupChannelActor wasn't handled by this
    * GroupDispatcher.
    */
  private def poisonChannel(poison: PoisonChannel): Unit = {
    logger.debug(s".poisonGroupChannel: groupResultId $groupResultId, studyResultId " +
        s"${poison.studyResultId}")
    val groupChannelOption = channelRegistry.getChannel(poison.studyResultId)
    if (groupChannelOption.nonEmpty) {
      groupChannelOption.get ! GroupMsg(Json.obj(GroupActionJsonKey.Action.toString -> GroupAction.Closed))
      groupChannelOption.get ! poison
      tellSenderOnly(true)
    }
    else tellSenderOnly(false)
  }

  private def poisonEmptyDispatcher(): Unit = {
    // Tell this dispatcher to kill itself if it has no more members
    if (channelRegistry.isEmpty) self ! PoisonPill
  }

  /**
    * Send the JOINED group action message to all group members. Who's joined the group is
    * specified in the given JoinedGroup object.
    */
  private def joined(studyResultId: Long): Unit = {
    logger.debug(s".joined: groupResultId $groupResultId studyResultId $studyResultId")
    val msg = actionMsgBuilder.build(groupResultId, studyResultId,
      channelRegistry, includeSessionData = false, GroupAction.Joined, TellWhom.AllButSender)
    tellAllButSender(msg)
  }

  /**
    * Send the LEFT group action message to all group members. Who's left is specified by the
    * StudyResult.
    */
  private def left(studyResultId: Long): Unit = {
    logger.debug(s".left: groupResultId $groupResultId, studyResultId $studyResultId")
    val msg = actionMsgBuilder.build(groupResultId, studyResultId, channelRegistry, includeSessionData = false,
      GroupAction.Left, TellWhom.AllButSender)
    tellAllButSender(msg)
  }

  /**
    * Sends the message only to the recipient specified by the given study result ID.
    */
  private def tellRecipientOnly(msg: GroupMsg, recipientStudyResultId: Long): Unit = {
    logger.debug(s".tellRecipientOnly: groupResultId $groupResultId, recipientStudyResultId " +
        s"$recipientStudyResultId, msg ${Json.stringify(msg.json)}")
    val groupChannel = channelRegistry.getChannel(recipientStudyResultId)
    if (groupChannel.isDefined)
      groupChannel.get ! msg
    else {
      val errorMsg = s"Recipient $recipientStudyResultId isn't member of this group."
      logger.debug(s".tellRecipientOnly: groupResultId $groupResultId, errorMsg $errorMsg")
      val groupMsg = actionMsgBuilder.buildError(groupResultId, errorMsg, TellWhom.SenderOnly)
      tellActionMsg(List(groupMsg))
    }
  }

  private def tellActionMsg(msgList: List[GroupMsg]): Unit = {
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
  private def tellAll(msg: GroupMsg): Unit = {
    logger.debug(s".tellAll: groupResultId $groupResultId, msg ${Json.stringify(msg.json)}")
    for (actorRef <- channelRegistry.getAllChannels)
      actorRef ! msg
  }

  /**
    * Sends the message to everyone in the group registry except the sender of this message.
    */
  private def tellAllButSender(msg: GroupMsg): Unit = {
    logger.debug(s".tellAllButSender: groupResultId $groupResultId, " +
        s"msg ${Json.stringify(msg.json)}")
    for (actorRef <- channelRegistry.getAllChannels)
      if (actorRef != sender) actorRef ! msg
  }

  /**
    * Sends the message only to the sender.
    */
  private def tellSenderOnly(msg: GroupMsg): Unit = {
    logger.debug(s".tellSenderOnly: groupResultId $groupResultId, msg ${Json.stringify(msg.json)}")
    sender ! msg
  }

  /**
    * Sends the message only to the sender.
    */
  private def tellSenderOnly(msg: Any): Unit = {
    logger.debug(s".tellSenderOnly: groupResultId $groupResultId, msg $msg")
    sender ! msg
  }

}
