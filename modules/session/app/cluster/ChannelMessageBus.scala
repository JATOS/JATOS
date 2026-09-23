package cluster

/**
 * Publishes messages to batch and group channels on other JATOS nodes.
 */
trait ChannelMessagePublisher {

  def isDistributed: Boolean

  def publishBatchMsgToCluster(message: BatchClusterMessage): Unit

  def publishGroupMsgToCluster(message: GroupClusterMessage): Unit

  def publishGroupDirectMsgToCluster(message: GroupDirectMsgDeliveryRequest): Unit

  def publishGroupChannelPresenceRequestToCluster(message: GroupChannelPresenceRequest): Unit

  def publishGroupOpenChannelsRequestToCluster(message: GroupOpenChannelsRequest): Unit = ()

  def publishGroupReassignmentRequestToCluster(message: GroupReassignmentRequest): Unit

  def publishGroupChannelCloseRequestToCluster(message: GroupChannelCloseRequest): Unit = ()
}

/**
 * Registers the node-local receiver for messages published by other JATOS nodes.
 */
trait ChannelMessageReceiverRegistrar {

  def registerLocalReceiver(receiver: ChannelMessageReceiver): Unit
}

/**
 * Provides outbound publishing and inbound receiver registration for batch and group channel messages.
 */
trait ChannelMessageBus extends ChannelMessagePublisher with ChannelMessageReceiverRegistrar
