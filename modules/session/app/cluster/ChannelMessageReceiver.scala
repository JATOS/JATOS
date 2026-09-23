package cluster

import batch.BatchDispatcher
import group.GroupDispatcher
import play.api.Logger
import play.api.libs.json.{JsObject, Json}

import javax.inject.{Inject, Singleton}
import scala.util.{Failure, Success, Try}

/**
 * Receives batch and group channel messages published by other JATOS nodes.
 */
trait ChannelMessageReceiver {

  def receiveBatch(message: BatchClusterMessage): Unit

  def receiveGroup(message: GroupClusterMessage): Unit

  def receiveGroupDirectMsg(message: GroupDirectMsgDeliveryRequest): Boolean

  def receiveGroupDirectMsgDeliveryAck(message: GroupDirectMsgDeliveryAck): Unit

  def receiveGroupChannelPresenceRequest(message: GroupChannelPresenceRequest): Boolean

  def receiveGroupChannelPresenceAck(message: GroupChannelPresenceAck): Unit

  def receiveGroupOpenChannelsRequest(message: GroupOpenChannelsRequest): Set[String] = Set.empty

  def receiveGroupOpenChannelsResponse(message: GroupOpenChannelsResponse): Unit = ()

  def receiveGroupReassignmentRequest(message: GroupReassignmentRequest): Unit

  def receiveGroupChannelCloseRequest(message: GroupChannelCloseRequest): Unit = ()

  def ready(): Unit
}

/**
 * Routes messages received from other JATOS nodes to the node-local dispatchers.
 */
@Singleton
class DispatcherChannelMessageReceiver @Inject()(batchDispatcher: BatchDispatcher,
                                                  groupDispatcher: GroupDispatcher) extends ChannelMessageReceiver {

  private val logger = Logger(this.getClass)

    override def receiveBatch(message: BatchClusterMessage): Unit = {
    Try(Json.parse(message.json).as[JsObject]) match {
      case Success(json) => batchDispatcher.deliverFromRemote(message.batchId, json)
      case Failure(e) => logger.warn(
        s".receiveBatch: invalid JSON for batch ${message.batchId} from node ${message.originNodeId}: ${e.getMessage}")
    }
  }

  override def receiveGroup(message: GroupClusterMessage): Unit = {
    Try(Json.parse(message.json).as[JsObject]) match {
      case Success(json) => groupDispatcher.deliverFromRemote(
        message.groupResultId, message.senderStudyResultId, json, message.recipients)
      case Failure(e) => logger.warn(
        s".receiveGroup: invalid JSON for group ${message.groupResultId} from node ${message.originNodeId}: ${e.getMessage}")
    }
  }

  override def receiveGroupDirectMsg(message: GroupDirectMsgDeliveryRequest): Boolean = {
    Try(Json.parse(message.json).as[JsObject]) match {
      case Success(json) => groupDispatcher.deliverDirectMsgFromRemote(
        message.groupResultId, message.recipientStudyResultId, json)
      case Failure(e) =>
        logger.warn(s".receiveGroupDirectMsg: invalid JSON for group ${message.groupResultId} " +
          s"from node ${message.originNodeId}: ${e.getMessage}")
        false
    }
  }

  override def receiveGroupDirectMsgDeliveryAck(message: GroupDirectMsgDeliveryAck): Unit = {
    groupDispatcher.acknowledgeDirectDelivery(message.deliveryId)
  }

  override def receiveGroupChannelPresenceRequest(message: GroupChannelPresenceRequest): Boolean = {
    groupDispatcher.hasChannel(message.studyResultId)
  }

  override def receiveGroupChannelPresenceAck(message: GroupChannelPresenceAck): Unit = {
    groupDispatcher.acknowledgeChannelPresence(message.requestId)
  }

  override def receiveGroupOpenChannelsRequest(message: GroupOpenChannelsRequest): Set[String] = {
    groupDispatcher.localStudyResultIds(message.groupResultId).map(_.toString).toSet
  }

  override def receiveGroupOpenChannelsResponse(message: GroupOpenChannelsResponse): Unit = {
    groupDispatcher.receiveOpenChannelsResponse(message)
  }

  override def receiveGroupReassignmentRequest(message: GroupReassignmentRequest): Unit = {
    groupDispatcher.reassignFromRemote(
      message.studyResultId, message.currentGroupResultId, message.differentGroupResultId)
  }

  override def receiveGroupChannelCloseRequest(message: GroupChannelCloseRequest): Unit = {
    groupDispatcher.poisonChannelFromRemote(message.groupResultId, message.studyResultId)
  }

  override def ready(): Unit = ()
}

/**
 * Registers the receiver after both the receiver and transport have been fully constructed.
 */
@Singleton
class ChannelMessageReceiverRegistration @Inject()(receiver: ChannelMessageReceiver,
                                                     registrar: ChannelMessageReceiverRegistrar) {
  registrar.registerLocalReceiver(receiver)
}
