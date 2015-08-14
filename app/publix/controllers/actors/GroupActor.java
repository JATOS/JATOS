package publix.controllers.actors;

import java.util.HashSet;
import java.util.Set;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Terminated;
import akka.actor.UntypedActor;

public class GroupActor extends UntypedActor {

	private final Set<ActorRef> groupMemberSet = new HashSet<>();

	public static Props props() {
		return Props.create(GroupActor.class);
	}

	@Override
	public void onReceive(Object msg) throws Exception {
		if (msg instanceof GroupMessage) {
			for (ActorRef actorRef : groupMemberSet) {
				if (actorRef != sender()) {
					actorRef.tell(msg, self());
				}
			}
		} else if (msg instanceof JoinMessage) {
			getContext().watch(sender());
			groupMemberSet.add(sender());
		} else if (msg instanceof Terminated) {
			groupMemberSet.remove(((Terminated) msg).actor());
		}
	}

}
