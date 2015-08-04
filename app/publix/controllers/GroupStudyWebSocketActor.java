package publix.controllers;

import java.util.HashSet;
import java.util.Set;

import akka.actor.*;

public class GroupStudyWebSocketActor extends UntypedActor {

	private static Set<ActorRef> groupMember = new HashSet<>();

	public static Props props(ActorRef out) {
		groupMember.add(out);
		return Props.create(GroupStudyWebSocketActor.class, out);
	}

	private final ActorRef out;

	public GroupStudyWebSocketActor(ActorRef out) {
		this.out = out;
	}

	public void onReceive(Object message) throws Exception {
		if (message instanceof String) {
			for (ActorRef out : groupMember) {
				if (out != this.out) {
					out.tell("I received your message: " + message, self());
				}
			}
		}
	}

	public void postStop() throws Exception {
		groupMember.remove(this.out);
	}
}
