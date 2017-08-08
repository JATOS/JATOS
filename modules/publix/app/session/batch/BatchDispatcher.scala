package session.batch

import javax.inject.Inject

import akka.actor.{Actor, ActorRef, PoisonPill}
import com.fasterxml.jackson.databind.node.ObjectNode
import com.google.inject.assistedinject.Assisted
import play.api.Logger
import play.libs.Json
import session.ChannelRegistry
import session.batch.BatchDispatcher.TellWhom.TellWhom
import session.batch.BatchDispatcher._
import session.batch.BatchDispatcherRegistry.Unregister

/**
  * A BatchDispatcher is an Akka Actor responsible for distributing messages
  * (BatchMsg) within a batch.
  *
  * A BatchChannel is always opened during initialization of jatos.js.
  *
  * A BatchChannel registers in a BatchDispatcher by sending the RegisterChannel
  * message and unregisters by sending a UnregisterChannel message.
  *
  * A new BatchDispatcher is created by the BatchDispatcherRegistry. If a
  * BatchDispatcher has no more members it closes itself.
  *
  * A BatchDispatcher handles all messages specified in the
  * BatchDispatcherProtocol. Fundamentally these are batch session patches.
  *
  * The batch session patches are JSON Patches after RFC 6902 and used to
  * describe changes in the batch session data. The session data are stored in
  * the Batch.
  *
  * @author Kristian Lange (2017)
  */
object BatchDispatcher {

  trait Factory {
    def apply(dispatcherRegistry: ActorRef,
              actionHandler: BatchActionHandler,
              actionMsgBuilder: BatchActionMsgBuilder,
              batchId: Long): Actor
  }

  /**
    * Message a BatchChannel can send to register in a BatchDispatcher.
    */
  case class RegisterChannel(studyResultId: Long)

  /**
    * Message an BatchChannel can send to its BatchDispatcher to indicate it's
    * closure.
    */
  case class UnregisterChannel(studyResultId: Long)

  /**
    * Message that forces a BatchChannel to close itself. Send to a
    * BatchDispatcher it will be forwarded to the right BatchChannel.
    */
  case class PoisonChannel(studyResultId: Long)

  object TellWhom extends Enumeration {
    type TellWhom = Value
    val All, SenderOnly, Unknown = Value
  }

  /**
    * Strings used as keys in the batch action JSON
    */
  object BatchActionJsonKey extends Enumeration {
    type BatchActionKey = Value
    val Action = Value("action") // JSON key name for an action (mandatory for an BatchActionMsg)
    val SessionData = Value("data") // JSON key name for session data (must be accompanied with a session version)
    val SessionPatches = Value("patches") // JSON key name for a session patches (must be accompanied with a session version)
    val SessionVersion = Value("version") // JSON key name for the batch session version (always together with either session data or patches)
    val ErrorMsg = Value("errorMsg") // JSON key name for an error message
  }

  /**
    * All possible batch actions a batch action message can have. They are
    * used as values in JSON message's action field.
    */
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
    * Message used for an action message. It has a JSON string and the JSON
    * contains an 'action' field. Additionally it can be addressed with TellWhom.
    */
  case class BatchActionMsg(jsonNode: ObjectNode, tellWhom: TellWhom = TellWhom.Unknown)

}

class BatchDispatcher @Inject()(@Assisted dispatcherRegistry: ActorRef,
                                @Assisted actionHandler: BatchActionHandler,
                                @Assisted actionMsgBuilder: BatchActionMsgBuilder,
                                @Assisted batchId: Long) extends Actor {

  private val logger: Logger = Logger(this.getClass)

  private val channelRegistry = new ChannelRegistry

  override def postStop() = {
    dispatcherRegistry ! Unregister(batchId)
  }

  def receive = {
    case actionMsg: BatchActionMsg =>
      handleActionMsg(actionMsg)
    case RegisterChannel(studyResultId: Long) =>
      registerChannel(studyResultId)
    case UnregisterChannel(studyResultId: Long) =>
      unregisterChannel(studyResultId)
    case p: PoisonChannel =>
      // Comes from BatchChannelService: close a batch channel
      poisonChannel(p)
  }

  /**
    * Handles batch actions originating from a client
    */
  private def handleActionMsg(actionMsg: BatchActionMsg) = {
    logger.debug(s".handleActionMsg: batchId $batchId, " +
      s"studyResultId ${channelRegistry.getStudyResult(sender).get}, " +
      s"actionMsg ${Json.stringify(actionMsg.jsonNode)}")
    val msgBundle = actionHandler.handleActionMsg(actionMsg, batchId)
    for (msg <- msgBundle.getAll) {
      tellActionMsg(msg)
    }
  }

  /**
    * Registers the given channel in the channelRegistry and send an OPENED msg back to
    * the sender
    */
  private def registerChannel(studyResultId: Long) = {
    logger.debug(s".registerChannel: batchId $batchId, studyResultId $studyResultId")
    channelRegistry.register(studyResultId, sender)
    tellActionMsg(actionMsgBuilder.buildSessionData(
      batchId, BatchAction.Opened, TellWhom.SenderOnly))
  }

  /**
    * Unregisters the given channel and sends an CLOSED action batch message to
    * everyone in this batch. Then if the batch is now empty it sends a
    * PoisonPill to this BatchDispatcher itself.
    */
  private def unregisterChannel(studyResultId: Long) {
    logger.debug(s".unregisterChannel: batchId $batchId, studyResultId $studyResultId")

    // Only unregister BatchChannel if it's the one from the sender (there
    // might be a new BatchChannel for the same StudyResult after a reload)
    val channelOption = channelRegistry.getChannel(studyResultId)
    if (channelOption.isDefined && channelOption.get == sender)
      channelRegistry.unregister(studyResultId)

    // Tell this dispatcher to kill itself if it has no more members
    if (channelRegistry.isEmpty) self ! PoisonPill
  }

  /**
    * Tells the BatchChannel to close itself. The BatchChannel then sends a
    * ChannelClosed back to this BatchDispatcher during postStop and then we
    * can remove the channel from the batch channelRegistry and tell all other members
    * about it. Also send false back to the sender (BatchChannelService) if the
    * BatchChannel wasn't handled by this BatchDispatcher.
    */
  private def poisonChannel(poisonChannel: PoisonChannel) = {
    val studyResultId = poisonChannel.studyResultId
    logger.debug(s".poisonChannel: batchId $batchId, studyResultId $studyResultId")
    val channelOption = channelRegistry.getChannel(studyResultId)
    if (channelOption.nonEmpty) {
      channelOption.get ! poisonChannel
      tellSenderOnly(true)
    }
    else tellSenderOnly(false)
  }

  private def tellActionMsg(msg: BatchActionMsg) = {
    msg.tellWhom match {
      case TellWhom.All => tellAll(msg)
      case TellWhom.SenderOnly => tellSenderOnly(msg)
      case _ => logger.warn(s".tellActionMsg: no TellWhom specified")
    }
  }

  /**
    * Sends the message to everyone in batch channelRegistry.
    */
  private def tellAll(msg: BatchActionMsg) = {
    logger.debug(s".tellAll: batchId $batchId, msg ${Json.stringify(msg.jsonNode)}")
    for (actorRef <- channelRegistry.getAllChannels) {
      actorRef.tell(msg, self)
    }
  }

  /**
    * Sends the message only to the sender.
    */
  private def tellSenderOnly(msg: BatchActionMsg) = {
    logger.debug(s".tellSenderOnly: batchId $batchId, msg ${Json.stringify(msg.jsonNode)}")
    sender ! msg
  }

  /**
    * Sends the message only to the sender.
    */
  private def tellSenderOnly(msg: Any) = {
    logger.debug(s".tellSenderOnly: batchId $batchId, msg $msg")
    sender ! msg
  }

}
