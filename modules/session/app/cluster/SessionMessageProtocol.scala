package cluster

import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}

/**
 * Marks messages that can be serialized and sent between JATOS nodes.
 */
trait ClusterSerializable

/**
 * Defines the common origin information for distributed session messages.
 */
sealed trait SessionClusterMessage extends ClusterSerializable {
  def originNodeId: String
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(Array(
  new JsonSubTypes.Type(value = classOf[GroupRecipients.All], name = "all"),
  new JsonSubTypes.Type(value = classOf[GroupRecipients.AllButSender], name = "all-but-sender"),
  new JsonSubTypes.Type(value = classOf[GroupRecipients.Recipient], name = "recipient")
))
/**
 * Defines which group channels should receive a group message.
 */
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
                                     json: String) extends SessionClusterMessage

/**
 * Carries a group message between JATOS nodes.
 */
final case class GroupClusterMessage(originNodeId: String,
                                     groupResultId: Long,
                                     senderStudyResultId: Long,
                                     json: String,
                                     recipients: GroupRecipients) extends SessionClusterMessage

/**
 * Requests delivery of a direct group message by the node owning the recipient's channel.
 */
final case class GroupDirectMsgDeliveryRequest(originNodeId: String,
                                               deliveryId: String,
                                               groupResultId: Long,
                                               senderStudyResultId: Long,
                                               recipientStudyResultId: Long,
                                               json: String) extends SessionClusterMessage

/**
 * Confirms that a direct group message was delivered to its recipient's local channel.
 */
final case class GroupDirectMsgDeliveryAck(originNodeId: String,
                                           targetNodeId: String,
                                           deliveryId: String) extends SessionClusterMessage

/**
 * Requests that the node owning a group channel move it to another group.
 */
final case class GroupReassignmentClusterMessage(originNodeId: String,
                                                 studyResultId: Long,
                                                 currentGroupResultId: Long,
                                                 differentGroupResultId: Long) extends SessionClusterMessage
