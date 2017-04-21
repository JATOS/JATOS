package session.batch.akka.actors;

import com.fasterxml.jackson.databind.node.ObjectNode;

import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.UntypedActor;
import session.batch.akka.protocol.BatchDispatcherProtocol.BatchMsg;
import session.batch.akka.protocol.BatchDispatcherProtocol.PoisonChannel;
import session.batch.akka.protocol.BatchDispatcherProtocol.RegisterChannel;
import session.batch.akka.protocol.BatchDispatcherProtocol.UnregisterChannel;

/**
 * BatchChannel is an Akka Actor that represents the batch channel's WebSocket.
 * A batch channel is a WebSocket connecting a client who's running a study with
 * the JATOS server.
 * 
 * A BatchChannel is always opened during initialization of jatos.js.
 * 
 * A BatchChannel belongs to a BatchDispatcher. A BatchChannel is created by the
 * BatchChannelService and registers itself by sending a RegisterChannel message
 * to its BatchDispatcher. It closes down after receiving a PoisonChannel
 * message or if the WebSocket is closed. While closing down it unregisters from
 * the BatchDispatcher by sending a UnregisterChannel message. A BatchChannel
 * can, if it's told to, reassign itself to a different BatchDispatcher.
 * 
 * @author Kristian Lange (2017)
 */
public class BatchChannel extends UntypedActor {

	/**
	 * Output of the WebSocket: JATOS -> client
	 */
	private final ActorRef out;
	private final long studyResultId;
	private ActorRef batchDispatcher;

	/**
	 * Akka method to get this Actor started. Changes in props must be done in
	 * the constructor too.
	 */
	public static Props props(ActorRef out, long studyResultId,
			ActorRef batchDispatcher) {
		return Props.create(BatchChannel.class, out, studyResultId,
				batchDispatcher);
	}

	public BatchChannel(ActorRef out, long studyResultId,
			ActorRef batchDispatcher) {
		this.out = out;
		this.studyResultId = studyResultId;
		this.batchDispatcher = batchDispatcher;
	}

	@Override
	public void preStart() {
		batchDispatcher.tell(new RegisterChannel(studyResultId), self());
	}

	@Override
	public void postStop() {
		batchDispatcher.tell(new UnregisterChannel(studyResultId), self());
	}

	@Override
	// WebSocket's input channel: client -> JATOS
	public void onReceive(Object msg) throws Exception {
		if (msg instanceof ObjectNode) {
			// If we receive a JsonNode (only from the client) wrap it in a
			// BatchMsg and forward it to the BatchDispatcher
			ObjectNode jsonNode = (ObjectNode) msg;
			batchDispatcher.tell(new BatchMsg(jsonNode), self());
		} else if (msg instanceof BatchMsg) {
			// If we receive a BatchMsg (only from the BatchDispatcher) send
			// the wrapped JsonNode to the client
			BatchMsg batchMsg = (BatchMsg) msg;
			out.tell(batchMsg.jsonNode, self());
		} else if (msg instanceof PoisonChannel) {
			// Kill this batch channel
			self().tell(PoisonPill.getInstance(), self());
		} else {
			unhandled(msg);
		}
	}

}
