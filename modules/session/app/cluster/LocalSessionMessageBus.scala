package cluster

import javax.inject.Singleton

/**
 * Provides a no-op transport for single-node installations.
 *
 * Local dispatchers already deliver messages to all local channels.
 */
@Singleton
class LocalSessionMessageBus extends SessionMessageBus {

  override val isDistributed: Boolean = false

  override def publishBatchMsgToCluster(message: BatchClusterMessage): Unit = ()

  override def publishGroupMsgToCluster(message: GroupClusterMessage): Unit = ()

  override def publishGroupDirectMsgToCluster(message: GroupDirectMsgDeliveryRequest): Unit = ()

  override def publishGroupReassignmentToCluster(message: GroupReassignmentClusterMessage): Unit = ()

  override def registerLocalReceiver(receiver: SessionMessageReceiver): Unit = receiver.ready()
}
