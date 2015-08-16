package publix.controllers.actors.actors;

import java.util.HashMap;
import java.util.Map;

import publix.controllers.actors.messages.DropMsg;
import publix.controllers.actors.messages.GroupMessage;
import publix.controllers.actors.messages.JoinGroupMsg;
import publix.controllers.actors.messages.PoisonMsg;
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
public class GroupDispatcher extends UntypedActor {

	/**
	 * Contains the members of this group. Maps StudyResult's IDs to ActorRefs.
	 */
	private final Map<Long, ActorRef> groupChannelMap = new HashMap<>();
	private final Map<Long, ActorRef> systemChannelMap = new HashMap<>();

	public static Props props() {
		return Props.create(GroupDispatcher.class);
	}

	@Override
	public void onReceive(Object msg) throws Exception {
		if (msg instanceof GroupMessage) {
			tellAllButSender(msg);
		} else if (msg instanceof JoinGroupMsg) {
			JoinGroupMsg joinGroup = (JoinGroupMsg) msg;
			groupChannelMap.put(joinGroup.studyResultId, sender());
			systemChannelMap.put(joinGroup.studyResultId,
					joinGroup.systemChannel);
		} else if (msg instanceof DropMsg) {
			DropMsg dropMsg = (DropMsg) msg;
			groupChannelMap.remove(dropMsg.studyResultId);
			systemChannelMap.remove(dropMsg.studyResultId);
		} else if (msg instanceof PoisonMsg) {
			PoisonMsg poisonMsg = (PoisonMsg) msg;
			ActorRef groupChannel = groupChannelMap
					.get(poisonMsg.studyResultId);
			if (groupChannel != null) {
				groupChannel.forward(msg, getContext());
			}
		}
	}

	private void tellAllButSender(Object msg) {
		for (ActorRef actorRef : groupChannelMap.values()) {
			if (actorRef != sender()) {
				actorRef.tell(msg, self());
			}
		}
	}
}