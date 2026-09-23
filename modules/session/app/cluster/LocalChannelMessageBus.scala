package cluster

import javax.inject.Singleton

/**
 * Provides a no-op transport for single-node installations.
 *
 * Local dispatchers already deliver messages to all local channels.
 */
@Singleton
class LocalChannelMessageBus extends ChannelMessageBus {

  override val isDistributed: Boolean = false

  override def publishBatchMsgToCluster(message: BatchClusterMessage): Unit = ()

  override def publishGroupMsgToCluster(message: GroupClusterMessage): Unit = ()

  override def publishGroupDirectMsgToCluster(message: GroupDirectMsgDeliveryRequest): Unit = ()

  override def publishGroupChannelPresenceRequestToCluster(message: GroupChannelPresenceRequest): Unit = ()

  override def publishGroupOpenChannelsRequestToCluster(message: GroupOpenChannelsRequest): Unit = ()

  override def publishGroupReassignmentRequestToCluster(message: GroupReassignmentRequest): Unit = ()

  override def registerLocalReceiver(receiver: ChannelMessageReceiver): Unit = receiver.ready()
}
