package cluster

import org.apache.pekko.actor.ActorSystem

import javax.inject.Inject

/**
 * Publishes and receives distributed session messages through Pekko Distributed PubSub.
 */
class PekkoSessionMessageBus @Inject()(actorSystem: ActorSystem,
                                       nodeIdentity: NodeIdentity) extends SessionMessageBus {

  import SessionPubSubGateway._

  private val gateway = actorSystem.actorOf(
    SessionPubSubGateway.props(nodeIdentity),
    "sessionPubSubGateway")

  override val isDistributed: Boolean = true

  override def publishBatchToCluster(message: BatchClusterMessage): Unit = gateway ! PublishBatchToCluster(message)

  override def publishGroupToCluster(message: GroupClusterMessage): Unit = gateway ! PublishGroupToCluster(message)

  override def publishGroupReassignmentToCluster(message: GroupReassignmentClusterMessage): Unit =
    gateway ! PublishGroupReassignmentToCluster(message)

  override def registerLocalReceiver(receiver: SessionMessageReceiver): Unit = gateway ! RegisterLocalReceiver(receiver)
}
