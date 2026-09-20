package cluster

/**
 * Publishes session messages to batch and group channels on other JATOS nodes.
 */
trait SessionMessagePublisher {

  def isDistributed: Boolean

  def publishBatchMsgToCluster(message: BatchClusterMessage): Unit

  def publishGroupMsgToCluster(message: GroupClusterMessage): Unit

  def publishGroupDirectMsgToCluster(message: GroupDirectMsgDeliveryRequest): Unit

  def publishGroupChannelPresenceToCluster(message: GroupChannelPresenceRequest): Unit

  def publishGroupReassignmentToCluster(message: GroupReassignmentClusterMessage): Unit
}

/**
 * Registers the node-local receiver for messages published by other JATOS nodes.
 */
trait SessionMessageReceiverRegistrar {

  def registerLocalReceiver(receiver: SessionMessageReceiver): Unit
}

/**
 * Provides both outbound publishing and inbound receiver registration for session messages.
 */
trait SessionMessageBus extends SessionMessagePublisher with SessionMessageReceiverRegistrar
