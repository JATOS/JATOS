package group

import akka.actor.{ActorRef, ActorSystem}
import com.google.inject.assistedinject.Assisted
import group.GroupDispatcher.TellWhom.TellWhom
import group.GroupDispatcher._
import play.api.Logger
import play.api.libs.json.Reads._
import play.api.libs.json.{JsObject, Json}

import javax.inject.Inject

/**
 * A GroupDispatcher is responsible for distributing messages (GroupMsg) within a group. Thus, it is the central class
 * handling a group.
 *
 * A GroupDispatcher only handles the GroupChannelActors but is not responsible for the actual joining of a study run to
 * a group (a StudyResult to a GroupResult). This is done before creating a GroupDispatcher by the GroupAdministration,
 * which persists all data in a GroupResult. Who is a member in a group is stored in a GroupResult.
 *
 * A GroupChannelActor is only opened after a StudyResult joined a GroupResult, which is done in the
 * GroupAdministration. Group data (e.g., who is member) are persisted in a GroupResult entity. A GroupChannelActor is
 * closed after the StudyResult left the group.
 *
 * A GroupChannelActor registers and unregisters itself in a GroupDispatcher.
 *
 * A new GroupDispatcher is created by the GroupDispatcherRegistry. If a GroupDispatcher has no more members, it closes
 * itself.
 *
 * A GroupDispatcher handles all messages specified in the GroupDispatcherProtocol. There are fundamentally three
 * different message types: 1) group session patches, 2) broadcast messages, and 3) direct messages for a particular
 * group member.
 *
 * The group session patches are JSON Patches after RFC 6902 and used to describe changes in the group session data. The
 * session data are stored in the GroupResult.
 *
 * @author Kristian Lange
 */
object GroupDispatcher {

  trait Factory {
    def create(groupResultId: Long): GroupDispatcher
  }

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
   * the group members. A GroupMsg contains a JSON node. If the JSON has an 'action' key, it is a group action
   * message. With 'tellWhom' the recipient can be specified.
   */
  case class GroupMsg(json: JsObject, tellWhom: TellWhom = TellWhom.Unknown)

}

class GroupDispatcher @Inject()(actorSystem: ActorSystem,
                                dispatcherRegistry: GroupDispatcherRegistry,
                                actionHandler: GroupActionHandler,
                                actionMsgBuilder: GroupActionMsgBuilder,
                                @Assisted groupResultId: Long) {

  private val logger: Logger = Logger(this.getClass)

  private val channelRegistry = new GroupChannelRegistry

  def hasChannel(studyResultId: Long): Boolean = channelRegistry.containsStudyResult(studyResultId)

  /**
   * Handle a GroupMsg received from a client. What to do with it depends on the JSON inside the GroupMsg. It can be
   * a group action msg, a direct msg (to a particular member) or a broadcast msg to everyone in the group.
   */
  def handleGroupMsg(msg: GroupMsg, studyResultId: Long, sender: ActorRef): Unit = {
    logger.debug(s".handleGroupMsg: groupResultId $groupResultId, groupMsg ${Json.stringify(msg.json)}")

    if (msg.json.keys.contains(GroupActionJsonKey.Action.toString)) {
      // We have a group action message
      val msgList = actionHandler.handleActionMsg(msg, groupResultId, studyResultId)
      tellActionMsg(msgList, sender)

    } else if (msg.json.keys.contains(GroupActionJsonKey.Recipient.toString)) {
      // We have a message intended for only one recipient (direct msg)
      // Recipient's study result ID comes as a string with quotes, and we have to convert to Long
      val recipient = (msg.json \ GroupActionJsonKey.Recipient.toString).as[String].replace("\"", "").toLong
      tellRecipientOnly(msg, recipient, sender)

    } else {
      // We have broadcast msg: Tell everyone except the sender
      tellAllButSender(msg, sender)
    }
  }

  /**
   * Registers the given channel and sends an OPENED action group message to everyone in this group.
   */
  def registerChannel(studyResultId: Long, channel: GroupChannelActor): Unit = {
    logger.debug(s".registerChannel: groupResultId $groupResultId, studyResultId $studyResultId")
    channelRegistry.register(studyResultId, channel)
    val msg1 = actionMsgBuilder.build(groupResultId, studyResultId,
      channelRegistry, includeSessionData = true, GroupAction.Opened, TellWhom.SenderOnly)
    val msg2 = actionMsgBuilder.build(groupResultId, studyResultId,
      channelRegistry, includeSessionData = false, GroupAction.Opened, TellWhom.AllButSender)
    tellActionMsg(List(msg1, msg2), channel.self)
  }

  /**
   * Unregisters the given channel and sends an CLOSED action group message to everyone in this group. Then, if the
   * group is now empty, it unregisters this GroupDispatcher itself.
   */
  def unregisterChannel(studyResultId: Long): Unit = {
    logger.debug(s".unregisterChannel: groupResultId $groupResultId, studyResultId $studyResultId")

    val channelOption = channelRegistry.getChannelActor(studyResultId)
    if (channelOption.isDefined) {
      channelRegistry.unregister(studyResultId)
      val msg = actionMsgBuilder.build(groupResultId, studyResultId,
        channelRegistry, includeSessionData = false, GroupAction.Closed, TellWhom.AllButSender)
      tellActionMsg(List(msg), channelOption.get)
    } else {
      logger.debug(s".unregisterChannel: study result $studyResultId is not handled by the GroupDispatcher $groupResultId.")
    }

    if (channelRegistry.isEmpty) dispatcherRegistry.unregister(groupResultId)
  }

  /**
   * Stops and unregisters the GroupChannelActor belonging to the given study result ID. Before it sends a 'Closed'
   * message to the GroupChannelActor.
   */
  def poisonChannel(studyResultId: Long): Unit = {
    logger.debug(s".poisonChannel: groupResultId $groupResultId, studyResultId $studyResultId")
    val channelOption = channelRegistry.getChannelActor(studyResultId)
    if (channelOption.isDefined) {
      channelOption.get ! GroupMsg(Json.obj(GroupActionJsonKey.Action.toString -> GroupAction.Closed))
      actorSystem.stop(channelOption.get)
      unregisterChannel(studyResultId)
      logger.debug(s".poisonChannel: groupResultId $groupResultId, studyResultId $studyResultId, " + "stopped and unregistered channel")
    } else {
      logger.debug(s".poisonChannel: study result $studyResultId is not handled by the GroupDispatcher $groupResultId.")
    }
  }

  /**
   * Reassigns the given channel to a different GroupDispatcher. It unregisters the channel from this GroupDispatcher
   * registers it with the different GroupDispatcher. It sets the different GroupDispatcher in the channel.
   */
  def reassignChannel(studyResultId: Long, differentDispatcher: GroupDispatcher): Unit = {
    logger.debug(s".reassignChannel: groupResultId $groupResultId, studyResultId $studyResultId")
    val channelOption = channelRegistry.getChannel(studyResultId)
    if (channelOption.isDefined) {
      unregisterChannel(studyResultId)
      left(studyResultId)
      channelOption.get.setGroupDispatcher(differentDispatcher)
      differentDispatcher.registerChannel(studyResultId, channelOption.get)
      differentDispatcher.joined(studyResultId)
    } else {
      logger.debug(s".reassignChannel: study result $studyResultId is not handled by the GroupDispatcher $groupResultId.")
    }
  }

  /**
   * Send the 'Joined' group action message to all group members.
   */
  def joined(studyResultId: Long): Unit = {
    logger.debug(s".joined: groupResultId $groupResultId studyResultId $studyResultId")
    val channel = channelRegistry.getChannelActor(studyResultId)
    if (channel.isDefined) {
      val msg = actionMsgBuilder.build(groupResultId, studyResultId,
        channelRegistry, includeSessionData = false, GroupAction.Joined, TellWhom.AllButSender)
      tellAllButSender(msg, channel.get)
    } else {
      logger.debug(s".joined: study result $studyResultId is not handled by the GroupDispatcher $groupResultId.")
    }
  }

  /**
   * Send the 'Left' group action message to all group members.
   */
  def left(studyResultId: Long): Unit = {
    logger.debug(s".left: groupResultId $groupResultId, studyResultId $studyResultId")
    val channel = channelRegistry.getChannelActor(studyResultId)
    if (channel.isDefined) {
      val msg = actionMsgBuilder.build(groupResultId, studyResultId, channelRegistry, includeSessionData = false,
        GroupAction.Left, TellWhom.AllButSender)
      tellAllButSender(msg, channel.get)
    } else {
      logger.debug(s".left: study result $studyResultId is not handled by the GroupDispatcher $groupResultId.")
    }
  }

  /**
   * Sends the message only to the recipient specified by the given study result ID.
   */
  private def tellRecipientOnly(msg: GroupMsg, recipientStudyResultId: Long, sender: ActorRef): Unit = {
    logger.debug(s".tellRecipientOnly: groupResultId $groupResultId, recipientStudyResultId " +
      s"$recipientStudyResultId, msg ${Json.stringify(msg.json)}")
    val channel = channelRegistry.getChannelActor(recipientStudyResultId)
    if (channel.isDefined)
      channel.get ! msg
    else {
      val errorMsg = s"Recipient $recipientStudyResultId isn't member of this group."
      logger.debug(s".tellRecipientOnly: groupResultId $groupResultId, errorMsg $errorMsg")
      val groupMsg = actionMsgBuilder.buildError(groupResultId, errorMsg, TellWhom.SenderOnly)
      tellActionMsg(List(groupMsg), sender)
    }
  }

  private def tellActionMsg(msgList: List[GroupMsg], sender: ActorRef): Unit = {
    msgList.foreach(msg =>
      msg.tellWhom match {
        case TellWhom.All => tellAll(msg)
        case TellWhom.SenderOnly => tellSenderOnly(msg, sender)
        case TellWhom.AllButSender => tellAllButSender(msg, sender)
        case _ => logger.warn(s".tellActionMsg: no TellWhom specified")
      }
    )
  }

  /**
   * Sends the message to everyone in channel registry.
   */
  private def tellAll(msg: GroupMsg): Unit = {
    logger.debug(s".tellAll: groupResultId $groupResultId, msg ${Json.stringify(msg.json)}")
    for (recipient <- channelRegistry.getAllChannels)
      recipient.self ! msg
  }

  /**
   * Sends the message to everyone in the group channel registry except to the sender.
   */
  private def tellAllButSender(msg: GroupMsg, sender: ActorRef): Unit = {
    logger.debug(s".tellAllButSender: groupResultId $groupResultId, " +
      s"msg ${Json.stringify(msg.json)}")
    for (recipient <- channelRegistry.getAllChannels)
      if (recipient.self != sender) recipient.self ! msg
  }

  /**
   * Sends the message only to the sender.
   */
  private def tellSenderOnly(msg: GroupMsg, sender: ActorRef): Unit = {
    logger.debug(s".tellSenderOnly: groupResultId $groupResultId, msg ${Json.stringify(msg.json)}")
    sender ! msg
  }

}
