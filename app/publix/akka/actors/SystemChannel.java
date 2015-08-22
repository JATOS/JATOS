package publix.akka.actors;

import publix.akka.messages.PoisonSomeone;
import publix.akka.messages.Register;
import publix.akka.messages.SystemMsg;
import publix.akka.messages.Unregister;
import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.UntypedActor;

public class SystemChannel extends UntypedActor {

	private final ActorRef out;
	private long studyResultId;
	private final ActorRef systemChannelRegistry;

	public static Props props(ActorRef out, ActorRef systemChannelRegistry,
			long studyResultId) {
		return Props.create(SystemChannel.class, out, systemChannelRegistry,
				studyResultId);
	}

	public SystemChannel(ActorRef out, ActorRef systemChannelRegistry,
			long studyResultId) {
		this.out = out;
		this.systemChannelRegistry = systemChannelRegistry;
		this.studyResultId = studyResultId;
	}

	@Override
	public void preStart() {
		systemChannelRegistry.tell(new Register(studyResultId), self());
	}

	@Override
	public void postStop() {
		systemChannelRegistry.tell(new Unregister(studyResultId), self());
	}

	@Override
	// WebSocket's input channel: client -> JATOS
	public void onReceive(Object msg) throws Exception {
		if (msg instanceof String) {
			// Msg from client
		} else if (msg instanceof SystemMsg) {
			out.tell(msg.toString(), self());
		} else if (msg instanceof PoisonSomeone) {
			self().tell(PoisonPill.getInstance(), self());
		}
	}
}
