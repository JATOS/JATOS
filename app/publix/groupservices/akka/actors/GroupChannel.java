package publix.groupservices.akka.actors;

import publix.groupservices.akka.messages.Dropout;
import publix.groupservices.akka.messages.GroupMsg;
import publix.groupservices.akka.messages.JoinGroup;
import publix.groupservices.akka.messages.PoisonSomeone;
import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.UntypedActor;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * GroupChannel is an Akka Actor that represents the group channel's WebSocket.
 * A group channel is a WebSocket connecting a client who's running a study and
 * the JATOS server. A GroupChannel belongs to a group, which is managed by a
 * GroupDispatcher. Group data are persisted in a GroupResult. A GroupChannel
 * joins its group by sending the JoinMessage to it's GroupDispatcher and drops
 * out of one by sending a Dropout message.
 * 
 * @author Kristian Lange
 */
public class GroupChannel extends UntypedActor {

	/**
	 * Output channel of the WebSocket: JATOS -> client
	 */
	private final ActorRef out;
	private final long studyResultId;
	private final ActorRef groupDispatcher;

	public static Props props(ActorRef out, long studyResultId,
			ActorRef groupDispatcher) {
		return Props.create(GroupChannel.class, out, studyResultId,
				groupDispatcher);
	}

	public GroupChannel(ActorRef out, long studyResultId,
			ActorRef groupDispatcher) {
		this.out = out;
		this.studyResultId = studyResultId;
		this.groupDispatcher = groupDispatcher;
	}

	@Override
	public void preStart() {
		groupDispatcher.tell(new JoinGroup(studyResultId), self());
	}

	@Override
	public void postStop() {
		groupDispatcher.tell(new Dropout(studyResultId), self());
	}

	@Override
	// WebSocket's input channel: client -> JATOS
	public void onReceive(Object msg) throws Exception {
		if (msg instanceof ObjectNode) {
			// If we receive a JsonNode (only from the client) wrap it in a
			// GroupMsg and forward it to the GroupDispatcher
			ObjectNode jsonNode = (ObjectNode) msg;
			groupDispatcher.tell(new GroupMsg(jsonNode), self());
		} else if (msg instanceof GroupMsg) {
			// If we receive a GroupMessage (only from the GroupDispatcher) send
			// the wrapped JsonNode to the client
			GroupMsg groupMsg = (GroupMsg) msg;
			out.tell(groupMsg.jsonNode, self());
		} else if (msg instanceof PoisonSomeone) {
			// Kill this group channel
			self().tell(PoisonPill.getInstance(), self());
		} else {
			unhandled(msg);
		}
	}

}
