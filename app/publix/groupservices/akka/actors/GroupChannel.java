package publix.groupservices.akka.actors;

import publix.groupservices.akka.messages.GroupDispatcherProtocol.UnregisterChannel;
import publix.groupservices.akka.messages.GroupDispatcherProtocol.GroupMsg;
import publix.groupservices.akka.messages.GroupDispatcherProtocol.RegisterChannel;
import publix.groupservices.akka.messages.GroupDispatcherProtocol.PoisonChannel;
import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.UntypedActor;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * GroupChannel is an Akka Actor that represents the group channel's WebSocket.
 * A group channel is a WebSocket connecting a client who's running a study with
 * the JATOS server.
 * 
 * A GroupChannel is only be opened after a StudyResult joined a group, which is
 * done in the GroupService. Group data (e.g. who's member) are persisted in a
 * GroupModel. A GroupChannel is closed after the StudyResult left the group.
 * 
 * A GroupChannel belongs to a GroupDispatcher. A GroupChannel is created by the
 * ChannelService and registers itself by sending a RegisterChannel message to
 * its GroupDispatcher. A closes down after receiving a PoisonChannel message or
 * if the WebSocket is closed. While closing down it unregisters from the
 * GroupDispatcher by sending a UnregisterChannel message.
 * 
 * @author Kristian Lange (2015)
 */
public class GroupChannel extends UntypedActor {

	/**
	 * Output of the WebSocket: JATOS -> client
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
		groupDispatcher.tell(new RegisterChannel(studyResultId), self());
	}

	@Override
	public void postStop() {
		groupDispatcher.tell(new UnregisterChannel(studyResultId), self());
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
		} else if (msg instanceof PoisonChannel) {
			// Kill this group channel
			self().tell(PoisonPill.getInstance(), self());
		} else {
			unhandled(msg);
		}
	}

}
