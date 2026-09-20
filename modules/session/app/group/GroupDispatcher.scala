package group

import daos.common.StudyDao
import group.GroupDispatcher.TellWhom.TellWhom
import group.GroupDispatcher._
import cluster.{GroupClusterMessage, GroupDirectMsgDeliveryRequest, GroupReassignmentClusterMessage, GroupRecipients, NodeIdentity, SessionMessagePublisher}
import general.common.Common
import models.common.Study.GroupSessionWriteScope
import org.apache.pekko.actor.{ActorRef, ActorSystem, Cancellable, PoisonPill}
import play.api.Logger
import play.api.libs.json.Reads._
import play.api.libs.json.{JsObject, Json}

import javax.inject.{Inject, Singleton}
import java.util.UUID
import scala.collection.mutable
import scala.jdk.DurationConverters._

/**
 * The node-local GroupDispatcher distributes GroupMsgs for all groups handled by this JATOS node.
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
 * Channels are grouped by group result ID. Empty group entries are removed automatically.
 *
 * A GroupDispatcher handles all messages specified in the GroupDispatcherProtocol. There are fundamentally three
 * different message types: 1) group session patches, 2) broadcast messages, and 3) direct messages for a particular
 * group member.
 *
 * The group session patches are JSON Patches after RFC 6902 and used to describe changes in the group session data. The
 * session data are stored in the GroupResult.
 */
object GroupDispatcher {

  object TellWhom extends Enumeration {
    type TellWhom = Value
    val All, AllButSender, SenderOnly, Unknown = Value
  }

  //noinspection TypeAnnotation
  object GroupAction extends Enumeration {
    type GroupAction = Value
    val Ready = Value("READY") // jatos.js signals that the group channel is ready (comes before OPENED)
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

@Singleton
class GroupDispatcher @Inject()(actionHandler: GroupActionHandler,
                                actionMsgBuilder: GroupActionMsgBuilder,
                                studyDao: StudyDao,
                                messagePublisher: SessionMessagePublisher,
                                nodeIdentity: NodeIdentity,
                                actorSystem: ActorSystem) {

  private val logger: Logger = Logger(this.getClass)

  private case class LocalGroup(groupSessionWriteScope: GroupSessionWriteScope,
                                channels: mutable.HashMap[Long, GroupChannelActor])

  private case class PendingDirectDelivery(groupResultId: Long,
                                           senderStudyResultId: Long,
                                           recipientStudyResultId: Long,
                                           sender: ActorRef,
                                           timeout: Cancellable)

  private val groups = mutable.HashMap.empty[Long, LocalGroup]
  private val pendingDirectDeliveries = mutable.HashMap.empty[String, PendingDirectDelivery]
  private val directMessageAckTimeout = Common.getGroupDirectMessageAckTimeout.toScala

  def hasChannel(studyResultId: Long): Boolean = synchronized {
    groups.values.exists(_.channels.contains(studyResultId))
  }

  /**
   * Handle a GroupMsg received from a client. What to do with it depends on the JSON inside the GroupMsg. It can be
   * a group action msg, a direct msg (to a particular member) or a broadcast msg to everyone in the group.
   */
  def handleGroupMsg(msg: GroupMsg, groupResultId: Long, studyResultId: Long, sender: ActorRef): Unit = {
    logger.debug(s".handleGroupMsg: groupResultId $groupResultId, groupMsg ${Json.stringify(msg.json)}")

    if (msg.json.keys.contains(GroupActionJsonKey.Action.toString)) {
      // We have a group action message
      val msgList = actionHandler.handleActionMsg(msg, groupResultId, studyResultId,
        group(groupResultId).groupSessionWriteScope)
      tellActionMsg(msgList, groupResultId, studyResultId, sender)

    } else if (msg.json.keys.contains(GroupActionJsonKey.Recipient.toString)) {
      // We have a message intended for only one recipient (direct msg)
      // Recipient's study result ID comes as a string with quotes, and we have to convert to Long
      val recipient = (msg.json \ GroupActionJsonKey.Recipient.toString).as[String].replace("\"", "").toLong
      tellRecipientOnly(msg, groupResultId, studyResultId, recipient, sender)

    } else {
      // We have broadcast msg: Tell everyone except the sender
      tellAllButSenderLocal(msg, groupResultId, sender)
      publishToCluster(msg, groupResultId, studyResultId, GroupRecipients.AllButSender())
    }
  }

  /**
   * Delivers a message received from another JATOS node to matching local group channels.
   *
   * The message is not processed or published again.
   */
  def deliverFromRemote(groupResultId: Long,
                        senderStudyResultId: Long,
                        json: JsObject,
                        recipients: GroupRecipients): Unit = {
    logger.debug(s".deliverFromRemote: groupResultId $groupResultId, senderStudyResultId $senderStudyResultId, " +
      s"recipients $recipients, msg ${Json.stringify(json)}")
    val msg = GroupMsg(json)
    recipients match {
      case GroupRecipients.All() => tellAllLocal(msg, groupResultId)
      case GroupRecipients.AllButSender() => tellAllButSenderLocal(msg, groupResultId, senderStudyResultId)
      case GroupRecipients.Recipient(studyResultId) => channel(groupResultId, studyResultId).foreach(_.self ! msg)
    }
  }

  /**
   * Delivers a direct message received from another JATOS node and reports whether its channel exists locally.
   */
  def deliverDirectMsgFromRemote(groupResultId: Long,
                                 recipientStudyResultId: Long,
                                 json: JsObject): Boolean = {
    logger.debug(s".deliverDirectMsgFromRemote: groupResultId $groupResultId, " +
      s"recipientStudyResultId $recipientStudyResultId, msg ${Json.stringify(json)}")
    channel(groupResultId, recipientStudyResultId) match {
      case Some(recipient) =>
        recipient.self ! GroupMsg(json)
        true
      case None => false
    }
  }

  /**
   * Completes a pending direct delivery after its owning node confirmed delivery.
   */
  def acknowledgeDirectDelivery(deliveryId: String): Unit = synchronized {
    pendingDirectDeliveries.remove(deliveryId).foreach(_.timeout.cancel())
  }

  /**
   * Registers the given channel and sends an OPENED action group message to everyone in this group.
   */
  def registerChannel(groupResultId: Long, studyResultId: Long, channel: GroupChannelActor): Unit = {
    logger.debug(s".registerChannel: groupResultId $groupResultId, studyResultId $studyResultId")
    synchronized { group(groupResultId).channels.put(studyResultId, channel) }
    val channelIds = studyResultIds(groupResultId)
    val msg1 = actionMsgBuilder.build(groupResultId, studyResultId,
      channelIds, includeSessionData = true, GroupAction.Opened, TellWhom.SenderOnly)
    val msg2 = actionMsgBuilder.build(groupResultId, studyResultId,
      channelIds, includeSessionData = false, GroupAction.Opened, TellWhom.AllButSender)
    tellActionMsg(List(msg1, msg2), groupResultId, studyResultId, channel.self)
  }

  /**
   * Unregisters the given channel, sends a CLOSED action message, and removes an empty group entry.
   */
  def unregisterChannel(groupResultId: Long, studyResultId: Long): Unit = {
    logger.debug(s".unregisterChannel: groupResultId $groupResultId, studyResultId $studyResultId")

    val channelOption = channel(groupResultId, studyResultId)
    if (channelOption.isDefined) {
      removeChannel(groupResultId, studyResultId)
      val msg = actionMsgBuilder.build(groupResultId, studyResultId,
        studyResultIds(groupResultId), includeSessionData = false, GroupAction.Closed, TellWhom.AllButSender)
      tellActionMsg(List(msg), groupResultId, studyResultId, channelOption.get.self)
    } else {
      logger.debug(s".unregisterChannel: study result $studyResultId is not handled by the GroupDispatcher $groupResultId.")
    }

  }

  /**
   * Stops and unregisters the GroupChannelActor belonging to the given study result ID. Before it sends a 'Closed'
   * message to the GroupChannelActor.
   */
  def poisonChannel(groupResultId: Long, studyResultId: Long): Unit = {
    logger.debug(s".poisonChannel: groupResultId $groupResultId, studyResultId $studyResultId")
    val channelOption = channel(groupResultId, studyResultId)
    if (channelOption.isDefined) {
      val channel = channelOption.get
      channel.self ! GroupMsg(Json.obj(GroupActionJsonKey.Action.toString -> GroupAction.Closed))
      channel.self ! PoisonPill
      unregisterChannel(groupResultId, studyResultId)
      logger.debug(s".poisonChannel: groupResultId $groupResultId, studyResultId $studyResultId, " + "stopped and unregistered channel")
    } else {
      logger.debug(s".poisonChannel: study result $studyResultId is not handled by the GroupDispatcher $groupResultId.")
    }
  }

  /**
   * Moves the given channel from one group entry to another and updates the channel's group result ID.
   */
  def reassignChannel(studyResultId: Long, groupResultId: Long, differentGroupResultId: Long): Unit = {
    logger.debug(s".reassignChannel: groupResultId $groupResultId, differentGroupResultId $differentGroupResultId, studyResultId $studyResultId")
    if (!reassignLocalChannel(studyResultId, groupResultId, differentGroupResultId) && messagePublisher.isDistributed) {
      logger.debug(s".reassignChannel: publishing reassignment to cluster for study result $studyResultId")
      messagePublisher.publishGroupReassignmentToCluster(GroupReassignmentClusterMessage(
        originNodeId = nodeIdentity.id,
        studyResultId = studyResultId,
        currentGroupResultId = groupResultId,
        differentGroupResultId = differentGroupResultId))
    }
  }

  /**
   * Applies a reassignment received from another JATOS node without publishing it again.
   */
  def reassignFromRemote(studyResultId: Long,
                         groupResultId: Long,
                         differentGroupResultId: Long): Unit = {
    logger.debug(s".reassignFromRemote: groupResultId $groupResultId, " +
      s"differentGroupResultId $differentGroupResultId, studyResultId $studyResultId")
    reassignLocalChannel(studyResultId, groupResultId, differentGroupResultId)
  }

  private def reassignLocalChannel(studyResultId: Long,
                                   groupResultId: Long,
                                   differentGroupResultId: Long): Boolean = {
    val channelOption = channel(groupResultId, studyResultId)
    if (channelOption.isDefined) {
      unregisterChannel(groupResultId, studyResultId)
      left(groupResultId, studyResultId)
      channelOption.get.setGroupResultId(differentGroupResultId)
      registerChannel(differentGroupResultId, studyResultId, channelOption.get)
      joined(differentGroupResultId, studyResultId)
      true
    } else {
      logger.debug(s".reassignLocalChannel: study result $studyResultId is not handled by the GroupDispatcher $groupResultId.")
      false
    }
  }

  /**
   * Send the 'Joined' group action message to all group members.
   */
  def joined(groupResultId: Long, studyResultId: Long): Unit = {
    logger.debug(s".joined: groupResultId $groupResultId studyResultId $studyResultId")
    val channelOption = channel(groupResultId, studyResultId)
    if (channelOption.isDefined) {
      val msg = actionMsgBuilder.build(groupResultId, studyResultId,
        studyResultIds(groupResultId), includeSessionData = false, GroupAction.Joined, TellWhom.AllButSender)
      tellActionMsg(List(msg), groupResultId, studyResultId, channelOption.get.self)
    } else {
      logger.debug(s".joined: study result $studyResultId is not handled by the GroupDispatcher $groupResultId.")
    }
  }

  /**
   * Send the 'Left' group action message to all group members. It sends the message to all group members except the
   * sender, and even if the study result is not handled by the GroupDispatcher (or never was).
   */
  def left(groupResultId: Long, studyResultId: Long): Unit = {
    logger.debug(s".left: groupResultId $groupResultId, studyResultId $studyResultId")
    val channelOption = channel(groupResultId, studyResultId)
    val tellWhom = if (channelOption.isDefined) TellWhom.AllButSender else TellWhom.All
    val senderRef = channelOption.map(_.self).getOrElse(ActorRef.noSender)
    val msg = actionMsgBuilder.build(groupResultId, studyResultId, studyResultIds(groupResultId), includeSessionData = false,
      GroupAction.Left, tellWhom)
    tellActionMsg(List(msg), groupResultId, studyResultId, senderRef)
  }

  /**
   * Sends the message only to the recipient specified by the given study result ID.
   */
  private def tellRecipientOnly(msg: GroupMsg,
                                groupResultId: Long,
                                senderStudyResultId: Long,
                                recipientStudyResultId: Long,
                                sender: ActorRef): Unit = {
    logger.debug(s".tellRecipientOnly: groupResultId $groupResultId, recipientStudyResultId " +
      s"$recipientStudyResultId, msg ${Json.stringify(msg.json)}")
    val channelOption = channel(groupResultId, recipientStudyResultId)
    channelOption match {
      case Some(recipient) => recipient.self ! msg
      case None if messagePublisher.isDistributed => publishDirectMsgToCluster(
        msg, groupResultId, senderStudyResultId, recipientStudyResultId, sender)
      case None => sendDirectDeliveryError(
        groupResultId, senderStudyResultId, recipientStudyResultId, sender,
        s"Recipient $recipientStudyResultId isn't member of this group.")
    }
  }

  private def publishDirectMsgToCluster(msg: GroupMsg,
                                        groupResultId: Long,
                                        senderStudyResultId: Long,
                                        recipientStudyResultId: Long,
                                        sender: ActorRef): Unit = {
    val deliveryId = UUID.randomUUID().toString
    val timeout = actorSystem.scheduler.scheduleOnce(directMessageAckTimeout) {
      directDeliveryTimedOut(deliveryId)
    }(actorSystem.dispatcher)
    synchronized {
      pendingDirectDeliveries.put(deliveryId, PendingDirectDelivery(
        groupResultId, senderStudyResultId, recipientStudyResultId, sender, timeout))
    }
    messagePublisher.publishGroupDirectMsgToCluster(GroupDirectMsgDeliveryRequest(
      originNodeId = nodeIdentity.id,
      deliveryId = deliveryId,
      groupResultId = groupResultId,
      senderStudyResultId = senderStudyResultId,
      recipientStudyResultId = recipientStudyResultId,
      json = Json.stringify(msg.json)))
  }

  private def directDeliveryTimedOut(deliveryId: String): Unit = {
    val pending = synchronized { pendingDirectDeliveries.remove(deliveryId) }
    pending.foreach { delivery =>
      val errorMsg = s"Recipient ${delivery.recipientStudyResultId} is not connected or the message could not be delivered."
      sendDirectDeliveryError(delivery.groupResultId, delivery.senderStudyResultId,
        delivery.recipientStudyResultId, delivery.sender, errorMsg)
    }
  }

  private def sendDirectDeliveryError(groupResultId: Long,
                                      senderStudyResultId: Long,
                                      recipientStudyResultId: Long,
                                      sender: ActorRef,
                                      errorMsg: String): Unit = {
    logger.debug(s".sendDirectDeliveryError: groupResultId $groupResultId, " +
      s"recipientStudyResultId $recipientStudyResultId, errorMsg $errorMsg")
    val groupMsg = actionMsgBuilder.buildError(groupResultId, errorMsg, TellWhom.SenderOnly)
    tellActionMsg(List(groupMsg), groupResultId, senderStudyResultId, sender)
  }

  private def tellActionMsg(msgList: List[GroupMsg],
                            groupResultId: Long,
                            senderStudyResultId: Long,
                            sender: ActorRef): Unit = {
    msgList.foreach(msg =>
      msg.tellWhom match {
        case TellWhom.All =>
          tellAllLocal(msg, groupResultId)
          publishToCluster(msg, groupResultId, senderStudyResultId, GroupRecipients.All())
        case TellWhom.SenderOnly => tellSenderOnly(msg, groupResultId, sender)
        case TellWhom.AllButSender =>
          tellAllButSenderLocal(msg, groupResultId, sender)
          publishToCluster(msg, groupResultId, senderStudyResultId, GroupRecipients.AllButSender())
        case _ => logger.warn(s".tellActionMsg: no TellWhom specified")
      }
    )
  }

  /**
   * Sends the message to every local channel in the group.
   */
  private def tellAllLocal(msg: GroupMsg, groupResultId: Long): Unit = {
    logger.debug(s".tellAllLocal: groupResultId $groupResultId, msg ${Json.stringify(msg.json)}")
    for (recipient <- channels(groupResultId))
      recipient.self ! msg
  }

  /**
   * Sends the message to every local channel in the group except the sender.
   */
  private def tellAllButSenderLocal(msg: GroupMsg, groupResultId: Long, sender: ActorRef): Unit = {
    logger.debug(s".tellAllButSenderLocal: groupResultId $groupResultId, " +
      s"msg ${Json.stringify(msg.json)}")
    for (recipient <- channels(groupResultId))
      if (recipient.self != sender) recipient.self ! msg
  }

  private def tellAllButSenderLocal(msg: GroupMsg,
                                    groupResultId: Long,
                                    senderStudyResultId: Long): Unit = {
    logger.debug(s".tellAllButSenderLocal: groupResultId $groupResultId, senderStudyResultId $senderStudyResultId, " +
      s"msg ${Json.stringify(msg.json)}")
    channelsByStudyResultId(groupResultId).foreach { case (studyResultId, recipient) =>
      if (studyResultId != senderStudyResultId) recipient.self ! msg
    }
  }

  private def publishToCluster(msg: GroupMsg,
                               groupResultId: Long,
                               senderStudyResultId: Long,
                               recipients: GroupRecipients): Unit = {
    if (!messagePublisher.isDistributed) return
    logger.debug(s".publishToCluster: groupResultId $groupResultId, senderStudyResultId $senderStudyResultId, " +
      s"recipients $recipients, msg ${Json.stringify(msg.json)}")
    messagePublisher.publishGroupMsgToCluster(GroupClusterMessage(
      originNodeId = nodeIdentity.id,
      groupResultId = groupResultId,
      senderStudyResultId = senderStudyResultId,
      json = Json.stringify(msg.json),
      recipients = recipients))
  }

  /**
   * Sends the message only to the sender.
   */
  private def tellSenderOnly(msg: GroupMsg, groupResultId: Long, sender: ActorRef): Unit = {
    logger.debug(s".tellSenderOnly: groupResultId $groupResultId, msg ${Json.stringify(msg.json)}")
    sender ! msg
  }

  private def group(groupResultId: Long): LocalGroup = synchronized {
    groups.getOrElseUpdate(groupResultId, {
      val writeScope = studyDao.findGroupSessionWriteScope(groupResultId)
      LocalGroup(writeScope, mutable.HashMap.empty)
    })
  }

  private def channel(groupResultId: Long, studyResultId: Long): Option[GroupChannelActor] = synchronized {
    groups.get(groupResultId).flatMap(_.channels.get(studyResultId))
  }

  private def channels(groupResultId: Long): List[GroupChannelActor] = synchronized {
    groups.get(groupResultId).fold(List.empty[GroupChannelActor])(_.channels.values.toList)
  }

  private def channelsByStudyResultId(groupResultId: Long): List[(Long, GroupChannelActor)] = synchronized {
    groups.get(groupResultId).fold(List.empty[(Long, GroupChannelActor)])(_.channels.toList)
  }

  private def studyResultIds(groupResultId: Long): List[Long] = synchronized {
    groups.get(groupResultId).fold(List.empty[Long])(_.channels.keys.toList)
  }

  private def removeChannel(groupResultId: Long, studyResultId: Long): Unit = synchronized {
    groups.get(groupResultId).foreach { localGroup =>
      localGroup.channels.remove(studyResultId)
      if (localGroup.channels.isEmpty) groups.remove(groupResultId)
    }
  }

}
