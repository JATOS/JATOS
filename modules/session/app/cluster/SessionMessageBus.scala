package cluster

/**
 * Publishes session messages to batch and group channels on other JATOS nodes.
 */
trait SessionMessagePublisher {

  def isDistributed: Boolean

  def publishBatchToCluster(message: BatchClusterMessage): Unit

  def publishGroupToCluster(message: GroupClusterMessage): Unit

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
