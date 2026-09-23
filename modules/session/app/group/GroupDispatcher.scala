package group

import daos.common.StudyDao
import group.GroupDispatcher.TellWhom.TellWhom
import group.GroupDispatcher._
import cluster.{GroupChannelCloseRequest, GroupChannelPresenceRequest, GroupOpenChannelsRequest, GroupOpenChannelsResponse, GroupClusterMessage, GroupDirectMsgDeliveryRequest, GroupReassignmentRequest, GroupRecipients, NodeIdentity, ChannelMessagePublisher}
import general.common.Common
import models.common.Study.GroupSessionWriteScope
import org.apache.pekko.actor.{ActorRef, ActorSystem, Cancellable, PoisonPill}
import play.api.Logger
import play.api.libs.json.Reads._
import play.api.libs.json.{JsObject, Json}

import javax.inject.{Inject, Singleton}
import java.util.UUID
import scala.collection.mutable
import scala.concurrent.{Future, Promise}
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
    val Opened = Value("OPENED") // Signals to the sender that its group channel opened
    val Closed = Value("CLOSED") // Signals to the recipient that its group channel was closed by JATOS
    val ChannelOpened = Value("CHANNEL_OPENED") // Signals that another member's group channel opened
    val ChannelClosed = Value("CHANNEL_CLOSED") // Signals that another member's group channel closed
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
                                messagePublisher: ChannelMessagePublisher,
                                nodeIdentity: NodeIdentity,
                                _common: Common, // Needed, because Guice doesn't guarantee Common's creation before this class.
                                actorSystem: ActorSystem) {

  private val logger: Logger = Logger(this.getClass)

  private case class LocalGroup(groupSessionWriteScope: GroupSessionWriteScope,
                                channels: mutable.HashMap[Long, GroupChannelActor])

  private case class PendingDirectDelivery(groupResultId: Long,
                                           senderStudyResultId: Long,
                                           recipientStudyResultId: Long,
                                           sender: ActorRef,
                                           timeout: Cancellable)

  private case class PendingChannelPresence(studyResultId: Long,
                                            result: Promise[Boolean],
                                            timeout: Cancellable)

  private case class PendingOpenChannels(groupResultId: Long,
                                         studyResultId: Long,
                                         sender: ActorRef,
                                         responses: mutable.HashMap[String, Set[Long]],
                                         channelChanges: mutable.LinkedHashMap[Long, Boolean],
                                         timeout: Cancellable)

  private val groups = mutable.HashMap.empty[Long, LocalGroup]
  private val pendingDirectDeliveries = mutable.HashMap.empty[String, PendingDirectDelivery]
  private val pendingChannelPresences = mutable.HashMap.empty[String, PendingChannelPresence]
  private val pendingOpenChannels = mutable.HashMap.empty[String, PendingOpenChannels]
  private val pendingJoinedMembers = mutable.HashSet.empty[(Long, Long)]
  private val messageAckTimeout = Common.getGroupMessageAckTimeout.toScala

  // Channel presence

  def hasChannel(studyResultId: Long): Boolean = synchronized {
    groups.values.exists(_.channels.contains(studyResultId))
  }

  /**
   * Reports whether this or another cluster node owns a channel for the given study result.
   */
  def hasChannelInCluster(studyResultId: Long): Future[Boolean] = {
    if (hasChannel(studyResultId)) return Future.successful(true)
    if (!messagePublisher.isDistributed) return Future.successful(false)

    val requestId = UUID.randomUUID().toString
    val result = Promise[Boolean]()
    val timeout = actorSystem.scheduler.scheduleOnce(messageAckTimeout) {
      completeChannelPresence(requestId, present = false)
    }(actorSystem.dispatcher)
    synchronized {
      pendingChannelPresences.put(requestId, PendingChannelPresence(studyResultId, result, timeout))
    }
    messagePublisher.publishGroupChannelPresenceRequestToCluster(GroupChannelPresenceRequest(
      originNodeId = nodeIdentity.id,
      requestId = requestId,
      studyResultId = studyResultId))
    result.future
  }

  /**
   * Completes a pending cluster presence check after another node found the channel.
   */
  def acknowledgeChannelPresence(requestId: String): Unit = completeChannelPresence(requestId, present = true)

  /**
   * Completes a pending cluster-wide channel-presence check.
   */
  private def completeChannelPresence(requestId: String, present: Boolean): Unit = {
    val pending = synchronized { pendingChannelPresences.remove(requestId) }
    pending.foreach { presence =>
      if (!present) {
        logger.debug(s".hasChannelInCluster: presence request $requestId for studyResultId " +
          s"${presence.studyResultId} timed out after $messageAckTimeout")
      }
      presence.timeout.cancel()
      presence.result.trySuccess(present)
    }
  }

  // Message entry points

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
    recordChannelChangeFromAction(groupResultId, json)
    val msg = GroupMsg(json)
    recipients match {
      case GroupRecipients.All() => tellAllLocal(msg, groupResultId)
      case GroupRecipients.AllButSender() => tellAllButSenderLocal(msg, groupResultId, senderStudyResultId)
      case GroupRecipients.Recipient(studyResultId) => channel(groupResultId, studyResultId).foreach(_.self ! msg)
    }
  }

  // Channel lifecycle

  /**
   * Registers the given channel, sends CHANNEL_OPENED to the other members, and initializes the sender with OPENED.
   */
  def registerChannel(groupResultId: Long, studyResultId: Long, channel: GroupChannelActor): Unit = {
    logger.debug(s".registerChannel: groupResultId $groupResultId, studyResultId $studyResultId")
    synchronized { group(groupResultId).channels.put(studyResultId, channel) }
    recordChannelChange(groupResultId, studyResultId, opened = true)
    val openedForOthers = actionMsgBuilder.build(groupResultId, studyResultId, None,
      includeSessionData = false, GroupAction.ChannelOpened, TellWhom.AllButSender)
    tellActionMsg(List(openedForOthers), groupResultId, studyResultId, channel.self)

    if (messagePublisher.isDistributed) requestOpenChannels(groupResultId, studyResultId, channel.self)
    else sendOpenedToNewChannel(groupResultId, studyResultId, studyResultIds(groupResultId), channel.self)
  }

  /**
   * Unregisters the given channel, sends CHANNEL_CLOSED to the other members, and removes an empty group entry.
   */
  def unregisterChannel(groupResultId: Long, studyResultId: Long): Unit = {
    logger.debug(s".unregisterChannel: groupResultId $groupResultId, studyResultId $studyResultId")

    val channelOption = channel(groupResultId, studyResultId)
    if (channelOption.isDefined) {
      removeChannel(groupResultId, studyResultId)
      recordChannelChange(groupResultId, studyResultId, opened = false)
      val msg = actionMsgBuilder.build(groupResultId, studyResultId, None,
        includeSessionData = false, GroupAction.ChannelClosed, TellWhom.AllButSender)
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
    if (!poisonLocalChannel(groupResultId, studyResultId) && messagePublisher.isDistributed) {
      logger.debug(s".poisonChannel: publishing channel close to cluster for study result $studyResultId")
      messagePublisher.publishGroupChannelCloseRequestToCluster(GroupChannelCloseRequest(
        originNodeId = nodeIdentity.id,
        groupResultId = groupResultId,
        studyResultId = studyResultId))
    }
  }

  /**
   * Applies a channel-close request received from another JATOS node without publishing it again.
   */
  def poisonChannelFromRemote(groupResultId: Long, studyResultId: Long): Unit = {
    logger.debug(s".poisonChannelFromRemote: groupResultId $groupResultId, studyResultId $studyResultId")
    poisonLocalChannel(groupResultId, studyResultId)
  }

  /**
   * Closes and unregisters a channel if it is owned by this node.
   */
  private def poisonLocalChannel(groupResultId: Long, studyResultId: Long): Boolean = {
    logger.debug(s".poisonChannel: groupResultId $groupResultId, studyResultId $studyResultId")
    val channelOption = channel(groupResultId, studyResultId)
    if (channelOption.isDefined) {
      val channel = channelOption.get
      channel.self ! GroupMsg(Json.obj(GroupActionJsonKey.Action.toString -> GroupAction.Closed))
      channel.self ! PoisonPill
      unregisterChannel(groupResultId, studyResultId)
      logger.debug(s".poisonChannel: groupResultId $groupResultId, studyResultId $studyResultId, " + "stopped and unregistered channel")
      true
    } else {
      logger.debug(s".poisonChannel: study result $studyResultId is not handled by the GroupDispatcher $groupResultId.")
      false
    }
  }

  // Channel reassignment

  /**
   * Moves the given channel from one group entry to another and updates the channel's group result ID.
   */
  def reassignChannel(studyResultId: Long, groupResultId: Long, differentGroupResultId: Long): Unit = {
    logger.debug(s".reassignChannel: groupResultId $groupResultId, differentGroupResultId $differentGroupResultId, studyResultId $studyResultId")
    if (!reassignLocalChannel(studyResultId, groupResultId, differentGroupResultId) && messagePublisher.isDistributed) {
      logger.debug(s".reassignChannel: publishing reassignment to cluster for study result $studyResultId")
      messagePublisher.publishGroupReassignmentRequestToCluster(GroupReassignmentRequest(
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

  /**
   * Moves a locally owned channel from its current group to another group.
   */
  private def reassignLocalChannel(studyResultId: Long,
                                   groupResultId: Long,
                                   differentGroupResultId: Long): Boolean = {
    val channelOption = channel(groupResultId, studyResultId)
    if (channelOption.isDefined) {
      left(groupResultId, studyResultId)
      unregisterChannel(groupResultId, studyResultId)
      channelOption.get.setGroupResultId(differentGroupResultId)
      joined(differentGroupResultId, studyResultId)
      registerChannel(differentGroupResultId, studyResultId, channelOption.get)
      true
    } else {
      logger.debug(s".reassignLocalChannel: study result $studyResultId is not handled by the GroupDispatcher $groupResultId.")
      false
    }
  }

  // Group membership lifecycle

  /**
   * Sends JOINED to all current group members. If the joining member's channel is not open yet, its notification is
   * retained until registration while the existing members are notified immediately.
   */
  def joined(groupResultId: Long, studyResultId: Long): Unit = {
    logger.debug(s".joined: groupResultId $groupResultId studyResultId $studyResultId")
    val channelOption = channel(groupResultId, studyResultId)
    val tellWhom = if (channelOption.isDefined) TellWhom.All else TellWhom.AllButSender
    if (channelOption.isEmpty) synchronized { pendingJoinedMembers.add(groupResultId -> studyResultId) }
    val msg = actionMsgBuilder.build(groupResultId, studyResultId, None,
      includeSessionData = false, GroupAction.Joined, tellWhom)
    tellActionMsg(List(msg), groupResultId, studyResultId,
      channelOption.map(_.self).getOrElse(ActorRef.noSender))
  }

  /**
   * Sends LEFT to every group member, including the member that left.
   */
  def left(groupResultId: Long, studyResultId: Long): Unit = {
    logger.debug(s".left: groupResultId $groupResultId, studyResultId $studyResultId")
    val msg = actionMsgBuilder.build(groupResultId, studyResultId, None, includeSessionData = false,
      GroupAction.Left, TellWhom.All)
    tellActionMsg(List(msg), groupResultId, studyResultId, ActorRef.noSender)
  }

  // Direct message delivery

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

  /**
   * Publishes a direct message to the cluster and tracks its delivery acknowledgement.
   */
  private def publishDirectMsgToCluster(msg: GroupMsg,
                                        groupResultId: Long,
                                        senderStudyResultId: Long,
                                        recipientStudyResultId: Long,
                                        sender: ActorRef): Unit = {
    val deliveryId = UUID.randomUUID().toString
    val timeout = actorSystem.scheduler.scheduleOnce(messageAckTimeout) {
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

  /**
   * Fails a pending direct delivery when no node acknowledged it in time.
   */
  private def directDeliveryTimedOut(deliveryId: String): Unit = {
    val pending = synchronized { pendingDirectDeliveries.remove(deliveryId) }
    pending.foreach { delivery =>
      logger.warn(s".directDeliveryTimedOut: deliveryId $deliveryId, groupResultId " +
        s"${delivery.groupResultId}, recipientStudyResultId ${delivery.recipientStudyResultId}, " +
        s"timeout $messageAckTimeout")
      val errorMsg = s"Recipient ${delivery.recipientStudyResultId} is not connected or the message could not be delivered."
      sendDirectDeliveryError(delivery.groupResultId, delivery.senderStudyResultId,
        delivery.recipientStudyResultId, delivery.sender, errorMsg)
    }
  }

  /**
   * Sends a direct-message delivery error to the original sender.
   */
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

  // Message routing

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

  /**
   * Sends the message only to the sender.
   */
  private def tellSenderOnly(msg: GroupMsg, groupResultId: Long, sender: ActorRef): Unit = {
    logger.debug(s".tellSenderOnly: groupResultId $groupResultId, msg ${Json.stringify(msg.json)}")
    sender ! msg
  }

  /**
   * Publishes a group message to the other nodes in a distributed installation.
   */
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

  // Cluster-wide open-channel discovery

  /**
   * Returns the channels for a group that are owned by this node.
   */
  def localStudyResultIds(groupResultId: Long): List[Long] = studyResultIds(groupResultId)

  /**
   * Adds one node's response to a pending cluster-wide open-channel request.
   */
  def receiveOpenChannelsResponse(response: GroupOpenChannelsResponse): Unit = {
    val shouldComplete = synchronized {
      pendingOpenChannels.get(response.requestId).exists { pending =>
        pending.responses.put(response.originNodeId, response.channelStudyResultIds.flatMap(_.toLongOption))
        pending.responses.size >= response.clusterMemberCount
      }
    }
    if (shouldComplete) completeOpenChannels(response.requestId, timedOut = false)
  }

  /**
   * Requests the currently open group channels from every cluster node.
   */
  private def requestOpenChannels(groupResultId: Long,
                                  studyResultId: Long,
                                  sender: ActorRef): Unit = {
    val requestId = UUID.randomUUID().toString
    val timeout = actorSystem.scheduler.scheduleOnce(messageAckTimeout) {
      completeOpenChannels(requestId, timedOut = true)
    }(actorSystem.dispatcher)
    synchronized {
      pendingOpenChannels.put(requestId, PendingOpenChannels(
        groupResultId,
        studyResultId,
        sender,
        mutable.HashMap.empty,
        mutable.LinkedHashMap.empty,
        timeout))
    }
    messagePublisher.publishGroupOpenChannelsRequestToCluster(GroupOpenChannelsRequest(
      originNodeId = nodeIdentity.id,
      requestId = requestId,
      groupResultId = groupResultId))
  }

  /**
   * Completes an open-channel request and initializes the newly opened channel.
   */
  private def completeOpenChannels(requestId: String, timedOut: Boolean): Unit = {
    val pendingOption = synchronized { pendingOpenChannels.remove(requestId) }
    pendingOption.foreach { pending =>
      pending.timeout.cancel()
      val openChannelIds = pending.responses.values.flatten.to(mutable.Set)
      pending.channelChanges.foreach { case (studyResultId, opened) =>
        if (opened) openChannelIds += studyResultId else openChannelIds -= studyResultId
      }
      // The registering channel must be present even if the local PubSub response was delayed or lost.
      openChannelIds += pending.studyResultId
      if (timedOut) {
        logger.warn(s".completeOpenChannels: request $requestId for groupResultId " +
          s"${pending.groupResultId} timed out after $messageAckTimeout; using ${pending.responses.size} response(s)")
      }
      sendOpenedToNewChannel(
        pending.groupResultId, pending.studyResultId, openChannelIds.toList, pending.sender)
    }
  }

  /**
   * Sends the initial OPENED message and any pending JOINED message to a new channel.
   */
  private def sendOpenedToNewChannel(groupResultId: Long,
                                     studyResultId: Long,
                                     channelIds: Iterable[Long],
                                     sender: ActorRef): Unit = {
    val opened = actionMsgBuilder.build(groupResultId, studyResultId, Some(channelIds),
      includeSessionData = true, GroupAction.Opened, TellWhom.SenderOnly)
    tellSenderOnly(opened, groupResultId, sender)
    val joinedIsPending = synchronized { pendingJoinedMembers.remove(groupResultId -> studyResultId) }
    if (joinedIsPending) {
      val joined = actionMsgBuilder.build(groupResultId, studyResultId, None,
        includeSessionData = false, GroupAction.Joined, TellWhom.SenderOnly)
      tellSenderOnly(joined, groupResultId, sender)
    }
  }

  /**
   * Extracts an open or close event and applies it to pending open-channel requests.
   */
  private def recordChannelChangeFromAction(groupResultId: Long, json: JsObject): Unit = {
    val action = (json \ GroupActionJsonKey.Action.toString).asOpt[String]
    val studyResultId = (json \ GroupActionJsonKey.MemberId.toString).asOpt[String].flatMap(_.toLongOption)
    (action, studyResultId) match {
      case (Some("CHANNEL_OPENED"), Some(id)) => recordChannelChange(groupResultId, id, opened = true)
      case (Some("CHANNEL_CLOSED"), Some(id)) => recordChannelChange(groupResultId, id, opened = false)
      case _ =>
    }
  }

  /**
   * Records a channel delta that occurred while an open-channel request was pending.
   */
  private def recordChannelChange(groupResultId: Long, studyResultId: Long, opened: Boolean): Unit = synchronized {
    pendingOpenChannels.values
      .filter(_.groupResultId == groupResultId)
      .foreach(_.channelChanges.put(studyResultId, opened))
  }

  // Local group state

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
