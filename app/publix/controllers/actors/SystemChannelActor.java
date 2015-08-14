package publix.controllers.actors;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;

public class SystemChannelActor extends UntypedActor {

	public static Props props(ActorRef out) {
		return Props.create(SystemChannelActor.class, out);
	}

	private final ActorRef out;

	public SystemChannelActor(ActorRef out) {
		this.out = out;
	}

	@Override
	public void onReceive(Object message) throws Exception {
		if (message instanceof String) {
			out.tell("I received your message: " + message, self());
		}
	}

	@Override
	public void postStop() throws Exception {
		// TODO
	}
}
