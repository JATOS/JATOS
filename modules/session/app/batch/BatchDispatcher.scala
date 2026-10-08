package batch

import batch.BatchProtocol._
import cluster.{BatchClusterMessage, ChannelMessagePublisher, NodeIdentity}
import org.apache.pekko.actor.{ActorRef, PoisonPill}
import play.api.Logger
import play.api.libs.json.{JsObject, Json}

import javax.inject.{Inject, Singleton}
import scala.collection.mutable

/**
 * The node-local BatchDispatcher distributes BatchMsgs for all batches handled by this JATOS node.
 *
 * A BatchDispatcher handles and distributes messages between currently active members of a batch. These messages are
 * essentially JSON Patches after RFC 6902 and used to describe changes in the batch session data. The session data are
 * stored and persisted with the Batch.
 *
 * A BatchChannelActor is always opened during initialization of jatos.js (where a GroupChannelActor is opened only
 * after the group was joined). A BatchChannelActor registers and unregisters itself in a BatchDispatcher.
 *
 * Channels are grouped by batch ID. Empty batch entries are removed automatically.
 */
@Singleton
class BatchDispatcher @Inject()(actionHandler: BatchActionHandler,
                                actionMsgBuilder: BatchActionMsgBuilder,
                                messagePublisher: ChannelMessagePublisher,
                                nodeIdentity: NodeIdentity) {

  private val logger: Logger = Logger(this.getClass)

  private val channelsByBatch = mutable.HashMap.empty[Long, mutable.HashMap[Long, ActorRef]]

  def buildPong(batchId: Long): JsObject = actionMsgBuilder.buildPong(batchId)

  /**
   * Handles batch actions originating from a client
   */
  def handleActionMsg(actionMsg: BatchMsg, batchId: Long, studyResultId: Long, sender: ActorRef): Unit = {
    logger.debug(s".handleActionMsg: batchId $batchId, " +
      s"studyResultId $studyResultId, " +
      s"actionMsg ${Json.stringify(actionMsg.json)}")
    val msgList = actionHandler.handleActionMsg(actionMsg, batchId)
    tellActionMsg(msgList, batchId, sender)
  }

  /**
   * Delivers a message received from another JATOS node to the local batch channels.
   *
   * The message is not processed or published again.
   */
  def deliverFromRemote(batchId: Long, json: JsObject): Unit = {
    logger.debug(s".deliverFromRemote: batchId $batchId, msg ${Json.stringify(json)}")
    tellAllLocal(BatchMsg(json, TellWhom.All), batchId)
  }

  /**
   * Registers the given channel and sends an 'Opened' message back to it.
   */
  def registerChannel(batchId: Long, studyResultId: Long, channel: ActorRef): Unit = {
    logger.debug(s".registerChannel: batchId $batchId, studyResultId $studyResultId")
    synchronized {
      channelsByBatch.getOrElseUpdate(batchId, mutable.HashMap.empty).put(studyResultId, channel)
    }
    tellActionMsg(List(actionMsgBuilder.buildSessionData(batchId, BatchAction.Opened, TellWhom.SenderOnly)),
      batchId, channel)
  }

  /**
   * Unregisters the given channel and removes the batch entry if it is now empty.
   */
  def unregisterChannel(batchId: Long, studyResultId: Long): Unit = {
    logger.debug(s".unregisterChannel: batchId $batchId, studyResultId $studyResultId")

    val removed = synchronized {
      channelsByBatch.get(batchId).flatMap { channels =>
        val channel = channels.remove(studyResultId)
        if (channels.isEmpty) channelsByBatch.remove(batchId)
        channel
      }
    }
    if (removed.isEmpty) {
      logger.debug(s".unregisterChannel: study result $studyResultId is not handled by the BatchDispatcher $batchId.")
    }
  }

  /**
   * Stops the BatchChannelActor and unregisters the channel. It sends a 'Closed' msg to the channel before it stops.
   */
  def poisonChannel(batchId: Long, studyResultId: Long): Unit = {
    logger.debug(s".poisonChannel: batchId $batchId, studyResultId $studyResultId")
    val channelOption = channel(batchId, studyResultId)
    if (channelOption.isDefined) {
      val channel = channelOption.get
      channel ! BatchMsg(Json.obj(BatchActionJsonKey.Action.toString -> BatchAction.Closed))
      channel ! PoisonPill
      unregisterChannel(batchId, studyResultId)
      logger.debug(s".poisonChannel: batchId $batchId, studyResultId $studyResultId, " + "stopped and unregistered channel")
    }  else {
      logger.debug(s".poisonChannel: study result $studyResultId is not handled by the BatchDispatcher $batchId.")
    }
  }

  private def tellActionMsg(msgList: List[BatchMsg],
                            batchId: Long,
                            sender: ActorRef): Unit = {
    msgList.foreach(msg =>
      msg.tellWhom match {
        case TellWhom.All =>
          tellAllLocal(msg, batchId)
          publishToCluster(msg, batchId)
        case TellWhom.SenderOnly => tellSenderOnly(msg, batchId, sender)
        case _ => logger.warn(s".tellActionMsg: no TellWhom specified")
      }
    )
  }

  /**
   * Sends the message to every local channel in the batch.
   */
  private def tellAllLocal(msg: BatchMsg, batchId: Long): Unit = {
    logger.debug(s".tellAllLocal: batchId $batchId, msg ${Json.stringify(msg.json)}")
    for (recipient <- channels(batchId)) {
      recipient ! msg
    }
  }

  private def publishToCluster(msg: BatchMsg, batchId: Long): Unit = {
    if (!messagePublisher.isDistributed) return
    logger.debug(s".publishToCluster: batchId $batchId, msg ${Json.stringify(msg.json)}")
    messagePublisher.publishBatchMsgToCluster(BatchClusterMessage(
      originNodeId = nodeIdentity.id,
      batchId = batchId,
      json = Json.stringify(msg.json)))
  }

  private def channel(batchId: Long, studyResultId: Long): Option[ActorRef] = synchronized {
    channelsByBatch.get(batchId).flatMap(_.get(studyResultId))
  }

  private def channels(batchId: Long): List[ActorRef] = synchronized {
    channelsByBatch.get(batchId).fold(List.empty[ActorRef])(_.values.toList)
  }

  /**
   * Sends the message only to the sender.
   */
  private def tellSenderOnly(msg: BatchMsg, batchId: Long, sender: ActorRef): Unit = {
    logger.debug(s".tellSenderOnly: batchId $batchId, msg ${Json.stringify(msg.json)}")
    sender ! msg
  }

}
