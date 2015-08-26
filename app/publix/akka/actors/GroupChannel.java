package publix.akka.actors;

import com.fasterxml.jackson.databind.JsonNode;

import publix.akka.messages.DropGroup;
import publix.akka.messages.GroupMsg;
import publix.akka.messages.JoinGroup;
import publix.akka.messages.PoisonSomeone;
import publix.akka.messages.SystemMsg;
import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.UntypedActor;

/**
 * GroupChannelActor is an Akka Actor that is used by the group channel. A group
 * channel is a WebSocket connecting a client who's running a study and the
 * JATOS server. A GroupChannelActor belongs to a group, which is managed be a
 * GroupActor. A GroupChannelActor joins it's group by sending the JoinMessage
 * to it's GroupActor.
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
	private final ActorRef systemChannel;

	public static Props props(ActorRef out, long studyResultId,
			ActorRef groupDispatcher, ActorRef systemChannel) {
		return Props.create(GroupChannel.class, out, studyResultId,
				groupDispatcher, systemChannel);
	}

	public GroupChannel(ActorRef out, long studyResultId,
			ActorRef groupDispatcher, ActorRef systemChannel) {
		this.out = out;
		this.studyResultId = studyResultId;
		this.groupDispatcher = groupDispatcher;
		this.systemChannel = systemChannel;
	}

	@Override
	public void preStart() {
		groupDispatcher.tell(new JoinGroup(studyResultId, systemChannel),
				self());
	}

	@Override
	public void postStop() {
		groupDispatcher.tell(new DropGroup(studyResultId), self());
	}

	@Override
	// WebSocket's input channel: client -> JATOS
	public void onReceive(Object msg) throws Exception {
		if (msg instanceof JsonNode) {
			JsonNode jsonNode = (JsonNode) msg;
			groupDispatcher.tell(new GroupMsg(jsonNode), self());
		} else if (msg instanceof GroupMsg) {
			JsonNode jsonNode = ((GroupMsg) msg).jsonNode;
//			out.tell("bla", self());
			out.tell(jsonNode.get("msg"), self());
		} else if (msg instanceof SystemMsg) {
			systemChannel.tell(msg, self());
		} else if (msg instanceof PoisonSomeone) {
			self().tell(PoisonPill.getInstance(), self());
		}
	}

}
