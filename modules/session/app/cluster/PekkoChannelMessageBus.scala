package cluster

import org.apache.pekko.actor.ActorSystem

import javax.inject.Inject

/**
 * Publishes and receives distributed channel messages through Pekko Distributed PubSub.
 */
class PekkoChannelMessageBus @Inject()(actorSystem: ActorSystem,
                                       nodeIdentity: NodeIdentity) extends ChannelMessageBus {

  import ChannelPubSubGateway._

  private val gateway = actorSystem.actorOf(
    ChannelPubSubGateway.props(nodeIdentity),
    "channelPubSubGateway")

  override val isDistributed: Boolean = true

  override def publishBatchMsgToCluster(message: BatchClusterMessage): Unit = gateway ! PublishBatchMsgToCluster(message)

  override def publishGroupMsgToCluster(message: GroupClusterMessage): Unit = gateway ! PublishGroupMsgToCluster(message)

  override def publishGroupDirectMsgToCluster(message: GroupDirectMsgDeliveryRequest): Unit =
    gateway ! PublishGroupDirectMsgToCluster(message)

  override def publishGroupChannelPresenceRequestToCluster(message: GroupChannelPresenceRequest): Unit =
    gateway ! PublishGroupChannelPresenceRequestToCluster(message)

  override def publishGroupOpenChannelsRequestToCluster(message: GroupOpenChannelsRequest): Unit =
    gateway ! PublishGroupOpenChannelsRequestToCluster(message)

  override def publishGroupReassignmentRequestToCluster(message: GroupReassignmentRequest): Unit =
    gateway ! PublishGroupReassignmentRequestToCluster(message)

  override def publishGroupChannelCloseRequestToCluster(message: GroupChannelCloseRequest): Unit =
    gateway ! PublishGroupChannelCloseRequestToCluster(message)

  override def registerLocalReceiver(receiver: ChannelMessageReceiver): Unit = gateway ! RegisterLocalReceiver(receiver)
}
