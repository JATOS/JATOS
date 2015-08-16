package publix.controllers.actors.actors;

import java.util.HashMap;
import java.util.Map;

import publix.controllers.actors.messages.DropMsg;
import publix.controllers.actors.messages.JoinMsg;
import publix.controllers.actors.messages.PoisonMsg;
import publix.controllers.actors.messages.WhichIsMsg;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;

/**
 * A GroupActor is an Akka Actor responsible for distributing messages within a
 * group. If one member of the group sends a messages, all other members should
 * receive it. Members of the group are GroupChannelActors, which in turn
 * represent a WebSocket. Each WebSocket is connected to a group member. On the
 * group member's client runs a study. A GroupChannelActor can join a group by
 * sending the JoinMessage to it's GroupActor.
 * 
 * @author Kristian Lange
 */
public class SystemChannelRegistry extends UntypedActor {

	/**
	 * Contains all system channels. Maps StudyResult's IDs to ActorRefs. Each
	 * running study (represented by it's StudyResult can have only one system
	 * channel).
	 */
	private final Map<Long, ActorRef> systemChannelMap = new HashMap<>();

	public static Props props() {
		return Props.create(SystemChannelRegistry.class);
	}

	@Override
	public void onReceive(Object msg) throws Exception {
		if (msg instanceof WhichIsMsg) {
			WhichIsMsg whichIsMsg = (WhichIsMsg) msg;
			ActorRef systemChannel = systemChannelMap
					.get(whichIsMsg.studyResultId);
			sender().tell(systemChannel, self());
		} else if (msg instanceof JoinMsg) {
			JoinMsg joinMsg = (JoinMsg) msg;
			systemChannelMap.put(joinMsg.studyResultId, sender());
		} else if (msg instanceof DropMsg) {
			DropMsg dropMsg = (DropMsg) msg;
			systemChannelMap.remove(dropMsg.studyResultId);
		} else if (msg instanceof PoisonMsg) {
			PoisonMsg poisonMsg = (PoisonMsg) msg;
			ActorRef actorRef = systemChannelMap.get(poisonMsg.studyResultId);
			if (actorRef != null) {
				actorRef.forward(msg, getContext());
			}
		}
	}
}