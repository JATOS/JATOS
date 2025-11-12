package batch

import akka.actor.{ActorRef, ActorSystem}
import batch.BatchDispatcher.TellWhom.TellWhom
import batch.BatchDispatcher._
import com.google.inject.assistedinject.Assisted
import play.api.Logger
import play.api.libs.json.{JsObject, Json}

import javax.inject.Inject

/**
 * A BatchDispatcher is responsible for distributing messages (BatchMsg) within a batch.
 *
 * A BatchDispatcher handles and distributes messages between currently active members of a batch. These messages are
 * essentially JSON Patches after RFC 6902 and used to describe changes in the batch session data. The session data are
 * stored and persisted with the Batch.
 *
 * A BatchChannelActor is always opened during initialization of jatos.js (where a GroupChannelActor is opened only
 * after the group was joined). A BatchChannelActor registers and unregisters itself in a BatchDispatcher.
 *
 * A new BatchDispatcher is created by the BatchDispatcherRegistry. If a BatchDispatcher has no more members, it closes
 * itself.
 *
 * @author Kristian Lange
 */
object BatchDispatcher {

  trait Factory {
    def create(batchId: Long): BatchDispatcher
  }

  object TellWhom extends Enumeration {
    type TellWhom = Value
    val All, SenderOnly, Unknown = Value
  }

  /**
   * Strings used as keys in the batch action JSON
   */
  //noinspection TypeAnnotation
  object BatchActionJsonKey extends Enumeration {
    // Action (mandatory for an BatchMsg)
    val Action = Value("action")
    // Session data (must be accompanied by a session version)
    val SessionData = Value("data")
    // Session patches (must be accompanied by a session version)
    val SessionPatches = Value("patches")
    // Identifier of a session action (mandatory)
    val SessionActionId = Value("id")
    // Batch session version (mandatory for session data or patches)
    val SessionVersion = Value("version")
    // Defines if we check the version before applying the patch
    val SessionVersioning = Value("versioning")
    // Error message
    val ErrorMsg = Value("errorMsg")
  }

  /**
   * All possible batch actions a batch action message can have. They are
   * used as values in the JSON message's action field.
   */
  //noinspection TypeAnnotation
  object BatchAction extends Enumeration {
    type BatchAction = Value
    val Opened = Value("OPENED") // Signals the opening of a batch channel
    val Closed = Value("CLOSED") // Signals the closing of a batch channel
    val Session = Value("SESSION") // Signals this message contains a batch session update
    val SessionAck = Value("SESSION_ACK") // Signals that the session update was successful
    val SessionFail = Value("SESSION_FAIL") // Signals that the session update failed
    val Error = Value("ERROR") // Used to send an error back to the sender
  }

  /**
   * Message used for an action message. It has a JSON string, and the JSON
   * contains an 'action' field. Additionally, it can be addressed with TellWhom.
   */
  case class BatchMsg(json: JsObject, tellWhom: TellWhom = TellWhom.Unknown)

}

class BatchDispatcher @Inject()(actorSystem: ActorSystem,
                                dispatcherRegistry: BatchDispatcherRegistry,
                                actionHandler: BatchActionHandler,
                                actionMsgBuilder: BatchActionMsgBuilder,
                                @Assisted batchId: Long) {

  private val logger: Logger = Logger(this.getClass)

  private val channelRegistry = new BatchChannelRegistry

  /**
   * Handles batch actions originating from a client
   */
  def handleActionMsg(actionMsg: BatchMsg, studyResultId: Long, sender: ActorRef): Unit = {
    logger.debug(s".handleActionMsg: batchId $batchId, " +
      s"studyResultId $studyResultId, " +
      s"actionMsg ${Json.stringify(actionMsg.json)}")
    val msgList = actionHandler.handleActionMsg(actionMsg, batchId)
    tellActionMsg(msgList, sender)
  }

  /**
   * Registers the given channel in the channelRegistry and sends an 'Opened' msg back to the channel
   */
  def registerChannel(studyResultId: Long, channel: ActorRef): Unit = {
    logger.debug(s".registerChannel: batchId $batchId, studyResultId $studyResultId")
    channelRegistry.register(studyResultId, channel)
    tellActionMsg(List(actionMsgBuilder.buildSessionData(batchId, BatchAction.Opened, TellWhom.SenderOnly)), channel)
  }

  /**
   * Unregisters the given channel. Then, if the batch is now empty, it unregisters this BatchDispatcher itself.
   */
  def unregisterChannel(studyResultId: Long): Unit = {
    logger.debug(s".unregisterChannel: batchId $batchId, studyResultId $studyResultId")

    if (channelRegistry.containsChannel(studyResultId)) {
      channelRegistry.unregister(studyResultId)
    }  else {
      logger.debug(s".unregisterChannel: study result $studyResultId is not handled by the BatchDispatcher $batchId.")
    }

    if (channelRegistry.isEmpty) dispatcherRegistry.unregister(batchId)
  }

  /**
   * Stops the BatchChannelActor and unregisters the channel. It sends a 'Closed' msg to the channel before it stops.
   */
  def poisonChannel(studyResultId: Long): Unit = {
    logger.debug(s".poisonChannel: batchId $batchId, studyResultId $studyResultId")
    val channelOption = channelRegistry.getChannel(studyResultId)
    if (channelOption.isDefined) {
      channelOption.get ! BatchMsg(Json.obj(BatchActionJsonKey.Action.toString -> BatchAction.Closed))
      actorSystem.stop(channelOption.get)
      unregisterChannel(studyResultId)
      logger.debug(s".poisonChannel: batchId $batchId, studyResultId $studyResultId, " + "stopped and unregistered channel")
    }  else {
      logger.debug(s".poisonChannel: study result $studyResultId is not handled by the BatchDispatcher $batchId.")
    }
  }

  private def tellActionMsg(msgList: List[BatchMsg], sender: ActorRef): Unit = {
    msgList.foreach(msg =>
      msg.tellWhom match {
        case TellWhom.All => tellAll(msg)
        case TellWhom.SenderOnly => tellSenderOnly(msg, sender)
        case _ => logger.warn(s".tellActionMsg: no TellWhom specified")
      }
    )
  }

  /**
   * Sends the message to everyone in batch channelRegistry.
   */
  private def tellAll(msg: BatchMsg): Unit = {
    logger.debug(s".tellAll: batchId $batchId, msg ${Json.stringify(msg.json)}")
    for (recipient <- channelRegistry.getAllChannels) {
      recipient ! msg
    }
  }

  /**
   * Sends the message only to the sender.
   */
  private def tellSenderOnly(msg: BatchMsg, sender: ActorRef): Unit = {
    logger.debug(s".tellSenderOnly: batchId $batchId, msg ${Json.stringify(msg.json)}")
    sender ! msg
  }

}
