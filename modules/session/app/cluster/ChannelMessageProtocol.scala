package cluster

import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}

/**
 * Marks messages that can be serialized and sent between JATOS nodes.
 */
trait ClusterSerializable

/**
 * Defines the common origin information for messages exchanged between JATOS nodes.
 */
sealed trait ChannelClusterMessage extends ClusterSerializable {
  def originNodeId: String
}

/**
 * Defines which group channels should receive a group message.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(Array(
  new JsonSubTypes.Type(value = classOf[GroupRecipients.All], name = "all"),
  new JsonSubTypes.Type(value = classOf[GroupRecipients.AllButSender], name = "all-but-sender"),
  new JsonSubTypes.Type(value = classOf[GroupRecipients.Recipient], name = "recipient")
))
sealed trait GroupRecipients extends ClusterSerializable

object GroupRecipients {
  final case class All() extends GroupRecipients

  final case class AllButSender() extends GroupRecipients

  final case class Recipient(studyResultId: Long) extends GroupRecipients
}

/**
 * Carries a batch message between JATOS nodes.
 */
final case class BatchClusterMessage(originNodeId: String,
                                     batchId: Long,
                                     json: String) extends ChannelClusterMessage

/**
 * Carries a group message between JATOS nodes.
 */
final case class GroupClusterMessage(originNodeId: String,
                                     groupResultId: Long,
                                     senderStudyResultId: Long,
                                     json: String,
                                     recipients: GroupRecipients) extends ChannelClusterMessage

/**
 * Requests delivery of a direct group message by the node owning the recipient's channel.
 */
final case class GroupDirectMsgDeliveryRequest(originNodeId: String,
                                               deliveryId: String,
                                               groupResultId: Long,
                                               senderStudyResultId: Long,
                                               recipientStudyResultId: Long,
                                               json: String) extends ChannelClusterMessage

/**
 * Confirms that a direct group message was delivered to its recipient's local channel.
 */
final case class GroupDirectMsgDeliveryAck(originNodeId: String,
                                           targetNodeId: String,
                                           deliveryId: String) extends ChannelClusterMessage

/**
 * Asks whether any other node owns a channel for the given study result.
 */
final case class GroupChannelPresenceRequest(originNodeId: String,
                                             requestId: String,
                                             studyResultId: Long) extends ChannelClusterMessage

/**
 * Confirms that a node owns the channel requested by a group-channel presence check.
 */
final case class GroupChannelPresenceAck(originNodeId: String,
                                         targetNodeId: String,
                                         requestId: String) extends ChannelClusterMessage

/**
 * Requests the node-local channel IDs for a group from every cluster node.
 */
final case class GroupOpenChannelsRequest(originNodeId: String,
                                          requestId: String,
                                          groupResultId: Long) extends ChannelClusterMessage

/**
 * Returns one node's open channel IDs for a group to the requesting node.
 */
final case class GroupOpenChannelsResponse(originNodeId: String,
                                           targetNodeId: String,
                                           requestId: String,
                                           groupResultId: Long,
                                           channelStudyResultIds: Set[String],
                                           clusterMemberCount: Int) extends ChannelClusterMessage

/**
 * Requests that the node owning a group channel move it to another group.
 */
final case class GroupReassignmentRequest(originNodeId: String,
                                          studyResultId: Long,
                                          currentGroupResultId: Long,
                                          differentGroupResultId: Long) extends ChannelClusterMessage

/**
 * Requests that the node owning a group channel close it because the member left the group.
 */
final case class GroupChannelCloseRequest(originNodeId: String,
                                          groupResultId: Long,
                                          studyResultId: Long) extends ChannelClusterMessage
