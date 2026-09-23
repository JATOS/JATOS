package cluster

import org.apache.pekko.actor.{Actor, ActorLogging, Props, Stash}
import org.apache.pekko.cluster.Cluster
import org.apache.pekko.cluster.ClusterEvent.{CurrentClusterState, InitialStateAsSnapshot, MemberRemoved, MemberUp, ReachableMember, UnreachableMember}
import org.apache.pekko.cluster.pubsub.DistributedPubSub
import org.apache.pekko.cluster.pubsub.DistributedPubSubMediator.{Publish, Subscribe, SubscribeAck}

import scala.collection.mutable

object ChannelPubSubGateway {

  val BatchTopic = "jatos-batch"
  val GroupTopic = "jatos-group"

  // Local commands for sending Batch/GroupClusterMessages through the gateway.
  private[cluster] final case class PublishBatchMsgToCluster(message: BatchClusterMessage)

  private[cluster] final case class PublishGroupMsgToCluster(message: GroupClusterMessage)

  private[cluster] final case class PublishGroupDirectMsgToCluster(message: GroupDirectMsgDeliveryRequest)

  private[cluster] final case class PublishGroupChannelPresenceRequestToCluster(message: GroupChannelPresenceRequest)

  private[cluster] final case class PublishGroupOpenChannelsRequestToCluster(message: GroupOpenChannelsRequest)

  private[cluster] final case class PublishGroupReassignmentRequestToCluster(message: GroupReassignmentRequest)

  private[cluster] final case class PublishGroupChannelCloseRequestToCluster(message: GroupChannelCloseRequest)

  private[cluster] final case class RegisterLocalReceiver(receiver: ChannelMessageReceiver)

  def props(nodeIdentity: NodeIdentity): Props = Props(new ChannelPubSubGateway(nodeIdentity))
}

/**
 * Bridges the local channel-message bus and Pekko Distributed PubSub.
 */
class ChannelPubSubGateway(nodeIdentity: NodeIdentity) extends Actor with ActorLogging with Stash {

  import ChannelPubSubGateway._

  private val mediator = DistributedPubSub(context.system).mediator
  private val cluster = Cluster(context.system)
  private var subscribedTopics = Set.empty[String]
  private var receiverOption = Option.empty[ChannelMessageReceiver]
  private val deliveredDirectMessages = mutable.LinkedHashSet.empty[(String, String)]
  private val maxRememberedDirectMessages = 10000

  override def preStart(): Unit = {
    cluster.subscribe(self, InitialStateAsSnapshot,
      classOf[MemberUp], classOf[UnreachableMember], classOf[ReachableMember], classOf[MemberRemoved])
    mediator ! Subscribe(BatchTopic, self)
    mediator ! Subscribe(GroupTopic, self)
  }

  override def postStop(): Unit = cluster.unsubscribe(self)

  override def receive: Receive = clusterEvents.orElse(starting)

  private def clusterEvents: Receive = {
    case state: CurrentClusterState =>
      log.info("Pekko cluster node [{}] started with [{}] member(s): [{}]",
        cluster.selfAddress, state.members.size, state.members.map(_.address).mkString(", "))
    case MemberUp(member) =>
      log.info("Pekko cluster member joined: [{}]. Current member count: [{}]",
        member.address, cluster.state.members.size)
    case UnreachableMember(member) =>
      log.warning("Pekko cluster member became unreachable: [{}]. Current member count: [{}]",
        member.address, cluster.state.members.size)
    case ReachableMember(member) =>
      log.info("Pekko cluster member became reachable again: [{}]. Current member count: [{}]",
        member.address, cluster.state.members.size)
    case MemberRemoved(member, previousStatus) =>
      log.info("Pekko cluster member removed: [{}], previous status [{}]. Current member count: [{}]",
        member.address, previousStatus, cluster.state.members.size)
  }

  private def starting: Receive = {
    case SubscribeAck(subscribe) =>
      subscribedTopics += subscribe.topic
      activateIfReady()

    case RegisterLocalReceiver(receiver) =>
      receiverOption = Some(receiver)
      activateIfReady()

    case _ => stash()
  }

  private def active(receiver: ChannelMessageReceiver): Receive = {
    case PublishBatchMsgToCluster(message) => mediator ! Publish(BatchTopic, message)
    case PublishGroupMsgToCluster(message) => mediator ! Publish(GroupTopic, message)
    case PublishGroupDirectMsgToCluster(message) => mediator ! Publish(GroupTopic, message)
    case PublishGroupChannelPresenceRequestToCluster(message) => mediator ! Publish(GroupTopic, message)
    case PublishGroupOpenChannelsRequestToCluster(message) => mediator ! Publish(GroupTopic, message)
    case PublishGroupReassignmentRequestToCluster(message) => mediator ! Publish(GroupTopic, message)
    case PublishGroupChannelCloseRequestToCluster(message) => mediator ! Publish(GroupTopic, message)
    case message: BatchClusterMessage => receiveBatch(message, receiver)
    case message: GroupClusterMessage => receiveGroup(message, receiver)
    case message: GroupDirectMsgDeliveryRequest => receiveGroupDirectMsg(message, receiver)
    case message: GroupDirectMsgDeliveryAck => receiveGroupDirectMsgDeliveryAck(message, receiver)
    case message: GroupChannelPresenceRequest => receiveGroupChannelPresenceRequest(message, receiver)
    case message: GroupChannelPresenceAck => receiveGroupChannelPresenceAck(message, receiver)
    case message: GroupOpenChannelsRequest => receiveGroupOpenChannelsRequest(message, receiver)
    case message: GroupOpenChannelsResponse => receiveGroupOpenChannelsResponse(message, receiver)
    case message: GroupReassignmentRequest => receiveGroupReassignmentRequest(message, receiver)
    case message: GroupChannelCloseRequest => receiveGroupChannelCloseRequest(message, receiver)
    case RegisterLocalReceiver(newReceiver) =>
      newReceiver.ready()
      context.become(clusterEvents.orElse(active(newReceiver)))
  }

  private def activateIfReady(): Unit = {
    if (subscribedTopics == Set(BatchTopic, GroupTopic) && receiverOption.isDefined) {
      val receiver = receiverOption.get
      receiver.ready()
      context.become(clusterEvents.orElse(active(receiver)))
      unstashAll()
    }
  }

  private def receiveBatch(message: BatchClusterMessage, receiver: ChannelMessageReceiver): Unit = {
    if (message.originNodeId != nodeIdentity.id) receiver.receiveBatch(message)
  }

  private def receiveGroup(message: GroupClusterMessage, receiver: ChannelMessageReceiver): Unit = {
    if (message.originNodeId != nodeIdentity.id) receiver.receiveGroup(message)
  }

  private def receiveGroupDirectMsg(message: GroupDirectMsgDeliveryRequest,
                                    receiver: ChannelMessageReceiver): Unit = {
    val deliveryKey = message.originNodeId -> message.deliveryId
    val wasDelivered = deliveredDirectMessages.contains(deliveryKey)
    if (message.originNodeId != nodeIdentity.id && (wasDelivered || receiver.receiveGroupDirectMsg(message))) {
      if (!wasDelivered) rememberDirectDelivery(deliveryKey)
      mediator ! Publish(GroupTopic, GroupDirectMsgDeliveryAck(
        originNodeId = nodeIdentity.id,
        targetNodeId = message.originNodeId,
        deliveryId = message.deliveryId))
    }
  }

  private def rememberDirectDelivery(deliveryKey: (String, String)): Unit = {
    deliveredDirectMessages += deliveryKey
    if (deliveredDirectMessages.size > maxRememberedDirectMessages) {
      deliveredDirectMessages.remove(deliveredDirectMessages.head)
    }
  }

  private def receiveGroupDirectMsgDeliveryAck(message: GroupDirectMsgDeliveryAck,
                                               receiver: ChannelMessageReceiver): Unit = {
    if (message.targetNodeId == nodeIdentity.id) receiver.receiveGroupDirectMsgDeliveryAck(message)
  }

  private def receiveGroupChannelPresenceRequest(message: GroupChannelPresenceRequest,
                                                 receiver: ChannelMessageReceiver): Unit = {
    if (message.originNodeId != nodeIdentity.id && receiver.receiveGroupChannelPresenceRequest(message)) {
      mediator ! Publish(GroupTopic, GroupChannelPresenceAck(
        originNodeId = nodeIdentity.id,
        targetNodeId = message.originNodeId,
        requestId = message.requestId))
    }
  }

  private def receiveGroupChannelPresenceAck(message: GroupChannelPresenceAck,
                                             receiver: ChannelMessageReceiver): Unit = {
    if (message.targetNodeId == nodeIdentity.id) receiver.receiveGroupChannelPresenceAck(message)
  }

  private def receiveGroupOpenChannelsRequest(message: GroupOpenChannelsRequest,
                                              receiver: ChannelMessageReceiver): Unit = {
    val channelIds = receiver.receiveGroupOpenChannelsRequest(message)
    mediator ! Publish(GroupTopic, GroupOpenChannelsResponse(
      originNodeId = nodeIdentity.id,
      targetNodeId = message.originNodeId,
      requestId = message.requestId,
      groupResultId = message.groupResultId,
      channelStudyResultIds = channelIds,
      clusterMemberCount = cluster.state.members.size))
  }

  private def receiveGroupOpenChannelsResponse(message: GroupOpenChannelsResponse,
                                               receiver: ChannelMessageReceiver): Unit = {
    if (message.targetNodeId == nodeIdentity.id) receiver.receiveGroupOpenChannelsResponse(message)
  }

  private def receiveGroupReassignmentRequest(message: GroupReassignmentRequest,
                                              receiver: ChannelMessageReceiver): Unit = {
    if (message.originNodeId != nodeIdentity.id) receiver.receiveGroupReassignmentRequest(message)
  }

  private def receiveGroupChannelCloseRequest(message: GroupChannelCloseRequest,
                                              receiver: ChannelMessageReceiver): Unit = {
    if (message.originNodeId != nodeIdentity.id) receiver.receiveGroupChannelCloseRequest(message)
  }
}
