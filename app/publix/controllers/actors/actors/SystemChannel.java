package publix.controllers.actors.actors;

import publix.controllers.actors.messages.DropMsg;
import publix.controllers.actors.messages.JoinMsg;
import publix.controllers.actors.messages.PoisonMsg;
import publix.controllers.actors.messages.SystemMessage;
import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.UntypedActor;

public class SystemChannel extends UntypedActor {

	private final ActorRef out;
	private final ActorRef systemChannelAllocator;
	private long studyResultId;

	public static Props props(ActorRef out, ActorRef systemChannelAllocator,
			long studyResultId) {
		return Props.create(SystemChannel.class, out,
				systemChannelAllocator, studyResultId);
	}

	public SystemChannel(ActorRef out, ActorRef systemChannelAllocator,
			long studyResultId) {
		this.out = out;
		this.systemChannelAllocator = systemChannelAllocator;
		this.studyResultId = studyResultId;
	}

	@Override
	public void preStart() {
		systemChannelAllocator.tell(new JoinMsg(studyResultId), self());
	}

	@Override
	public void postStop() {
		systemChannelAllocator.tell(new DropMsg(studyResultId), self());
	}

	@Override
	// WebSocket's input channel: client -> JATOS
	public void onReceive(Object msg) throws Exception {
		if (msg instanceof String) {
			// Msg from client
		} else if (msg instanceof SystemMessage) {
			out.tell(msg.toString(), self());
		} else if (msg instanceof PoisonMsg) {
			self().tell(PoisonPill.getInstance(), self());
		}
	}
}
