package publix.akka.actors;

import java.util.HashMap;
import java.util.Map;

import publix.akka.messages.DropGroup;
import publix.akka.messages.GroupMsg;
import publix.akka.messages.IsMember;
import publix.akka.messages.JoinGroup;
import publix.akka.messages.PoisonSomeone;
import publix.akka.messages.SystemMsg;
import publix.akka.messages.Unregister;
import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.UntypedActor;

import com.fasterxml.jackson.databind.JsonNode;

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

	private static final String RECIPIENT = "recipient";
	/**
	 * Contains the members of this group. Maps StudyResult's IDs to ActorRefs.
	 */
	private final Map<Long, ActorRef> groupChannelMap = new HashMap<>();
	private final Map<Long, ActorRef> systemChannelMap = new HashMap<>();
	private final ActorRef groupDispatcherRegistry;
	private long groupResultId;

	public static Props props(ActorRef groupDispatcherRegistry,
			long groupResultId) {
		return Props.create(GroupDispatcher.class, groupDispatcherRegistry,
				groupResultId);
	}

	public GroupDispatcher(ActorRef groupDispatcherRegistry, long groupResultId) {
		this.groupDispatcherRegistry = groupDispatcherRegistry;
		this.groupResultId = groupResultId;
	}

	@Override
	public void postStop() {
		groupDispatcherRegistry.tell(new Unregister(groupResultId), self());
	}

	@Override
	public void onReceive(Object msg) throws Exception {
		if (msg instanceof GroupMsg) {
			JsonNode jsonNode = ((GroupMsg) msg).jsonNode;
			if (jsonNode.has(RECIPIENT)) {
				tellRecipient(msg, jsonNode);
			} else {
				tellAllButSender(msg);
			}
		} else if (msg instanceof IsMember) {
			IsMember isMember = (IsMember) msg;
			ActorRef groupChannel = groupChannelMap.get(isMember.id);
			sender().tell((groupChannel != null), self());
		} else if (msg instanceof JoinGroup) {
			JoinGroup joinGroup = (JoinGroup) msg;
			groupChannelMap.put(joinGroup.studyResultId, sender());
			systemChannelMap.put(joinGroup.studyResultId,
					joinGroup.systemChannel);
			tellSystemMsg(new SystemMsg(joinGroup.studyResultId + " joined"));
		} else if (msg instanceof DropGroup) {
			DropGroup dropGroup = (DropGroup) msg;
			groupChannelMap.remove(dropGroup.studyResultId);
			systemChannelMap.remove(dropGroup.studyResultId);
			if (groupChannelMap.isEmpty()) {
				// Tell this dispatcher to commit suicide if it has no more
				// members
				self().tell(PoisonPill.getInstance(), self());
			} else {
				tellSystemMsg(new SystemMsg(dropGroup.studyResultId
						+ " dropped"));
			}
		} else if (msg instanceof PoisonSomeone) {
			PoisonSomeone poison = (PoisonSomeone) msg;
			ActorRef groupChannel = groupChannelMap
					.get(poison.idOfTheOneToPoison);
			if (groupChannel != null) {
				groupChannel.forward(msg, getContext());
			}
		}
	}

	private void tellRecipient(Object msg, JsonNode jsonNode) {
		Long studyResultId = null;
		try {
			studyResultId = Long.valueOf(jsonNode.get(RECIPIENT).asText());
		} catch (NumberFormatException e) {
			String errorMsg = "Recipient " + jsonNode.get(RECIPIENT).asText()
					+ " isn't a study result ID.";
			sender().tell(new SystemMsg(errorMsg), self());
		}

		ActorRef actorRef = groupChannelMap.get(studyResultId);
		if (actorRef != null) {
			actorRef.tell(msg, self());
		} else {
			String errorMsg = "Recipient " + studyResultId.toString()
					+ " isn't member of this group.";
			sender().tell(new SystemMsg(errorMsg), self());
		}
	}

	private void tellAllButSender(Object msg) {
		for (ActorRef actorRef : groupChannelMap.values()) {
			if (actorRef != sender()) {
				actorRef.tell(msg, self());
			}
		}
	}

	private void tellSystemMsg(Object msg) {
		for (ActorRef actorRef : systemChannelMap.values()) {
			actorRef.tell(msg, self());
		}
	}

}