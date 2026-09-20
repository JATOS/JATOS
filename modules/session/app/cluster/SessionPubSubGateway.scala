package cluster

import org.apache.pekko.actor.{Actor, ActorLogging, Props, Stash}
import org.apache.pekko.cluster.pubsub.DistributedPubSub
import org.apache.pekko.cluster.pubsub.DistributedPubSubMediator.{Publish, Subscribe, SubscribeAck}

import scala.collection.mutable

object SessionPubSubGateway {

  val BatchTopic = "jatos-batch"
  val GroupTopic = "jatos-group"

  // Local commands for sending Batch/GroupClusterMessages through the gateway.
  private[cluster] final case class PublishBatchMsgToCluster(message: BatchClusterMessage)
  private[cluster] final case class PublishGroupMsgToCluster(message: GroupClusterMessage)
  private[cluster] final case class PublishGroupDirectMsgToCluster(message: GroupDirectMsgDeliveryRequest)
  private[cluster] final case class PublishGroupReassignmentToCluster(message: GroupReassignmentClusterMessage)
  private[cluster] final case class RegisterLocalReceiver(receiver: SessionMessageReceiver)

  def props(nodeIdentity: NodeIdentity): Props = Props(new SessionPubSubGateway(nodeIdentity))
}

/**
 * Bridges the local session-message bus and Pekko Distributed PubSub.
 */
class SessionPubSubGateway(nodeIdentity: NodeIdentity) extends Actor with ActorLogging with Stash {

  import SessionPubSubGateway._

  private val mediator = DistributedPubSub(context.system).mediator
  private var subscribedTopics = Set.empty[String]
  private var receiverOption = Option.empty[SessionMessageReceiver]
  private val deliveredDirectMessages = mutable.LinkedHashSet.empty[(String, String)]
  private val maxRememberedDirectMessages = 10000

  override def preStart(): Unit = {
    mediator ! Subscribe(BatchTopic, self)
    mediator ! Subscribe(GroupTopic, self)
  }

  override def receive: Receive = starting

  private def starting: Receive = {
    case SubscribeAck(subscribe) =>
      subscribedTopics += subscribe.topic
      activateIfReady()

    case RegisterLocalReceiver(receiver) =>
      receiverOption = Some(receiver)
      activateIfReady()

    case _ => stash()
  }

  private def active(receiver: SessionMessageReceiver): Receive = {
    case PublishBatchMsgToCluster(message) => mediator ! Publish(BatchTopic, message)
    case PublishGroupMsgToCluster(message) => mediator ! Publish(GroupTopic, message)
    case PublishGroupDirectMsgToCluster(message) => mediator ! Publish(GroupTopic, message)
    case PublishGroupReassignmentToCluster(message) => mediator ! Publish(GroupTopic, message)
    case message: BatchClusterMessage => receiveBatch(message, receiver)
    case message: GroupClusterMessage => receiveGroup(message, receiver)
    case message: GroupDirectMsgDeliveryRequest => receiveGroupDirectMsg(message, receiver)
    case message: GroupDirectMsgDeliveryAck => receiveGroupDirectMsgDeliveryAck(message, receiver)
    case message: GroupReassignmentClusterMessage => receiveGroupReassignment(message, receiver)
    case RegisterLocalReceiver(newReceiver) =>
      newReceiver.ready()
      context.become(active(newReceiver))
  }

  private def activateIfReady(): Unit = {
    if (subscribedTopics == Set(BatchTopic, GroupTopic) && receiverOption.isDefined) {
      val receiver = receiverOption.get
      receiver.ready()
      context.become(active(receiver))
      unstashAll()
    }
  }

  private def receiveBatch(message: BatchClusterMessage, receiver: SessionMessageReceiver): Unit = {
    if (message.originNodeId != nodeIdentity.id) receiver.receiveBatch(message)
  }

  private def receiveGroup(message: GroupClusterMessage, receiver: SessionMessageReceiver): Unit = {
    if (message.originNodeId != nodeIdentity.id) receiver.receiveGroup(message)
  }

  private def receiveGroupDirectMsg(message: GroupDirectMsgDeliveryRequest,
                                    receiver: SessionMessageReceiver): Unit = {
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
                                               receiver: SessionMessageReceiver): Unit = {
    if (message.targetNodeId == nodeIdentity.id) receiver.receiveGroupDirectMsgDeliveryAck(message)
  }

  private def receiveGroupReassignment(message: GroupReassignmentClusterMessage,
                                       receiver: SessionMessageReceiver): Unit = {
    if (message.originNodeId != nodeIdentity.id) receiver.receiveGroupReassignment(message)
  }
}
