package session.batch.akka.actors;

import com.fasterxml.jackson.databind.node.ObjectNode;

import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.UntypedActor;
import play.Logger;
import play.Logger.ALogger;
import play.libs.Json;
import session.DispatcherRegistryProtocol.Unregister;
import session.Registry;
import session.batch.akka.actors.BatchDispatcherProtocol.BatchActionMsg;
import session.batch.akka.actors.BatchDispatcherProtocol.BatchActionMsg.BatchAction;
import session.batch.akka.actors.BatchDispatcherProtocol.BatchActionMsg.TellWhom;
import session.batch.akka.actors.BatchDispatcherProtocol.BatchMsg;
import session.batch.akka.actors.BatchDispatcherProtocol.PoisonChannel;
import session.batch.akka.actors.BatchDispatcherProtocol.RegisterChannel;
import session.batch.akka.actors.BatchDispatcherProtocol.UnregisterChannel;

/**
 * @author Kristian Lange (2017)
 */
public class BatchDispatcher extends UntypedActor {

	private static final ALogger LOGGER = Logger.of(BatchDispatcher.class);

	public static final String ACTOR_NAME = "BatchDispatcher";

	private Registry batchRegistry = new Registry();
	private final ActorRef batchDispatcherRegistry;
	private final BatchActionHandler batchActionHandler;
	private final BatchActionMsgBuilder batchActionMsgBuilder;
	private long batchId;

	/**
	 * Akka method to get this Actor started. Changes in props must be done in
	 * the constructor too.
	 */
	public static Props props(ActorRef batchDispatcherRegistry,
			BatchActionHandler batchActionHandler,
			BatchActionMsgBuilder batchActionBuilder, long batchId) {
		return Props.create(BatchDispatcher.class, batchDispatcherRegistry,
				batchActionHandler, batchActionBuilder, batchId);
	}

	public BatchDispatcher(ActorRef batchDispatcherRegistry,
			BatchActionHandler batchActionHandler,
			BatchActionMsgBuilder batchActionMsgBuilder, long batchId) {
		this.batchDispatcherRegistry = batchDispatcherRegistry;
		this.batchActionHandler = batchActionHandler;
		this.batchActionMsgBuilder = batchActionMsgBuilder;
		this.batchId = batchId;
	}

	@Override
	public void postStop() {
		batchDispatcherRegistry.tell(new Unregister(batchId), self());
	}

	@Override
	public void onReceive(Object msg) throws Exception {
		if (msg instanceof BatchMsg) {
			// We got a BatchMsg from a client
			handleBatchMsg((BatchMsg) msg);
		} else if (msg instanceof RegisterChannel) {
			// A BatchChannel wants to register
			registerChannel((RegisterChannel) msg);
		} else if (msg instanceof UnregisterChannel) {
			// A BatchChannel wants to unregister
			unregisterChannel((UnregisterChannel) msg);
		} else if (msg instanceof PoisonChannel) {
			// Comes from BatchChannelService: close a batch channel
			poisonABatchChannel((PoisonChannel) msg);
		} else {
			unhandled(msg);
		}
	}

	/**
	 * Handle a BatchMsg received from a client. What to do with it depends on
	 * the JSON inside the BatchMsg.
	 * 
	 * @see BatchDispatcherProtocol.BatchMsg
	 * @see BatchDispatcherProtocol.BatchActionMsg
	 */
	private void handleBatchMsg(BatchMsg batchMsg) {
		LOGGER.debug(".handleBatchMsg: batchId {}, batchMsg {}", batchId,
				Json.stringify(batchMsg.jsonNode));
		ObjectNode jsonNode = batchMsg.jsonNode;
		if (jsonNode.has(BatchActionMsg.ACTION)) {
			// We have a batch action message
			handleBatchActionMsg(jsonNode);
		} else {
			// We have broadcast message: Just tell everyone except the sender
			tellAllButSender(batchMsg);
		}
	}

	/**
	 * Handles batch actions originating from a client
	 */
	private void handleBatchActionMsg(ObjectNode jsonNode) {
		long studyResultId = batchRegistry.getStudyResult(sender());
		BatchActionMsgBundle msgBundle = batchActionHandler
				.handleBatchActionMsg(batchId, studyResultId, batchRegistry,
						jsonNode);
		for (BatchActionMsg msg : msgBundle.getAll()) {
			tellBatchActionMsg(msg);
		}
	}

	/**
	 * Registers the given channel and sends an OPENED action batch message to
	 * everyone in this batch.
	 */
	private void registerChannel(RegisterChannel registerChannel) {
		LOGGER.debug(".registerChannel: batchId {}, studyResultId {}",
				batchId, registerChannel.studyResultId);
		long studyResultId = registerChannel.studyResultId;
		batchRegistry.register(studyResultId, sender());
		BatchActionMsg msg1 = batchActionMsgBuilder.buildWithSession(batchId,
				BatchAction.OPENED, TellWhom.SENDER_ONLY);
		tellBatchActionMsg(msg1);
	}

	/**
	 * Unregisters the given channel and sends an CLOSED action batch message to
	 * everyone in this batch. Then if the batch is now empty it sends a
	 * PoisonPill to this BatchDispatcher itself.
	 */
	private void unregisterChannel(UnregisterChannel unregisterChannel) {
		LOGGER.debug(".unregisterChannel: batchId {}, studyResultId {}",
				batchId, unregisterChannel.studyResultId);
		long studyResultId = unregisterChannel.studyResultId;
		// Only unregister BatchChannel if it's the one from the sender (there
		// might be a new BatchChannel for the same StudyResult after a reload)
		if (batchRegistry.containsStudyResult(studyResultId)
				&& batchRegistry.getChannel(studyResultId).equals(sender())) {
			batchRegistry.unregister(unregisterChannel.studyResultId);
		}

		// Tell this dispatcher to kill itself if it has no more members
		if (batchRegistry.isEmpty()) {
			self().tell(PoisonPill.getInstance(), self());
		}
	}

	/**
	 * Tells the BatchChannel to close itself. The BatchChannel then sends a
	 * ChannelClosed back to this BatchDispatcher during postStop and then we
	 * can remove the channel from the batch registry and tell all other members
	 * about it. Also send false back to the sender (BatchChannelService) if the
	 * BatchChannel wasn't handled by this BatchDispatcher.
	 */
	private void poisonABatchChannel(PoisonChannel poison) {
		LOGGER.debug(".poisonABatchChannel: batchId {}, studyResultId {}",
				batchId, poison.studyResultIdOfTheOneToPoison);
		long studyResultId = poison.studyResultIdOfTheOneToPoison;
		ActorRef batchChannel = batchRegistry.getChannel(studyResultId);
		if (batchChannel != null) {
			batchChannel.forward(poison, getContext());
			tellSenderOnly(true);
		} else {
			tellSenderOnly(false);
		}
	}

	private void tellBatchActionMsg(BatchActionMsg msg) {
		if (msg == null) {
			return;
		}
		switch (msg.tellWhom) {
		case ALL:
			tellAll(msg);
			break;
		case ALL_BUT_SENDER:
			tellAllButSender(msg);
			break;
		case SENDER_ONLY:
			tellSenderOnly(msg);
			break;
		}
	}

	/**
	 * Sends the message to everyone in the batch registry except the sender of
	 * this message.
	 */
	private void tellAllButSender(BatchMsg msg) {
		LOGGER.debug(".tellAllButSender: batchId {}, msg {}", batchId,
				Json.stringify(msg.jsonNode));
		for (ActorRef actorRef : batchRegistry.getAllChannels()) {
			if (actorRef != sender()) {
				actorRef.tell(msg, self());
			}
		}
	}

	/**
	 * Sends the message to everyone in batch registry.
	 */
	private void tellAll(BatchMsg msg) {
		LOGGER.debug(".tellAll: batchId {}, msg {}", batchId,
				Json.stringify(msg.jsonNode));
		for (ActorRef actorRef : batchRegistry.getAllChannels()) {
			actorRef.tell(msg, self());
		}
	}

	/**
	 * Sends the message only to the sender.
	 */
	private void tellSenderOnly(Object msg) {
		LOGGER.debug(".tellSenderOnly: batchId {}, msg {}", batchId, msg);
		sender().tell(msg, self());
	}

	/**
	 * Sends the message only to the sender.
	 */
	private void tellSenderOnly(BatchMsg msg) {
		LOGGER.debug(".tellSenderOnly: batchId {}, msg {}", batchId,
				Json.stringify(msg.jsonNode));
		sender().tell(msg, self());
	}

}
