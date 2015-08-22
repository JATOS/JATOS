package publix.akka.actors;

import java.util.HashMap;
import java.util.Map;

import publix.akka.messages.ItsThisOne;
import publix.akka.messages.PoisonSomeone;
import publix.akka.messages.Register;
import publix.akka.messages.Unregister;
import publix.akka.messages.Get;
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
		if (msg instanceof Get) {
			Get whichIs = (Get) msg;
			ActorRef systemChannel = systemChannelMap
					.get(whichIs.id);
			ItsThisOne answer = new ItsThisOne(systemChannel);
			sender().tell(answer, self());
		} else if (msg instanceof Register) {
			Register register = (Register) msg;
			systemChannelMap.put(register.id, sender());
		} else if (msg instanceof Unregister) {
			Unregister unregister = (Unregister) msg;
			systemChannelMap.remove(unregister.id);
		} else if (msg instanceof PoisonSomeone) {
			PoisonSomeone poison = (PoisonSomeone) msg;
			ActorRef actorRef = systemChannelMap.get(poison.idOfTheOneToPoison);
			if (actorRef != null) {
				actorRef.forward(msg, getContext());
			}
		}
	}
}