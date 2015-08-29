package publix.akka.actors;

import java.util.HashMap;
import java.util.Map;

import publix.akka.messages.Droppout;
import publix.akka.messages.GroupMsg;
import publix.akka.messages.IsMember;
import publix.akka.messages.JoinGroup;
import publix.akka.messages.PoisonSomeone;
import publix.akka.messages.Unregister;
import utils.JsonUtils;
import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.UntypedActor;

import com.fasterxml.jackson.databind.node.ObjectNode;

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

	private static final String ERROR = "error";
	private static final String JOINED = "joined";
	private static final String GROUP_MEMBERS = "groupMembers";
	private static final String DROPPED = "dropped";
	private static final String RECIPIENT = "recipient";
	/**
	 * Contains the members of this group. Maps StudyResult's IDs to ActorRefs.
	 */
	private final Map<Long, ActorRef> groupChannelMap = new HashMap<>();
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
			// We got a GroupMsg from a client
			dispatchGroupMsg(msg);
		} else if (msg instanceof IsMember) {
			// Someone wants to know if this ID is a member in this group
			returnIsMember(msg);
		} else if (msg instanceof JoinGroup) {
			joinGroup(msg);
		} else if (msg instanceof Droppout) {
			dropout(msg);
		} else if (msg instanceof PoisonSomeone) {
			closeAGroupChannel(msg);
		}
	}

	private void dispatchGroupMsg(Object msg) {
		ObjectNode jsonNode = ((GroupMsg) msg).jsonNode;
		if (jsonNode.has(RECIPIENT)) {
			tellRecipientOnly(msg, jsonNode);
		} else {
			tellAllButSender(msg);
		}
	}

	private void tellRecipientOnly(Object msg, ObjectNode jsonNode) {
		Long studyResultId = null;
		try {
			studyResultId = Long.valueOf(jsonNode.get(RECIPIENT).asText());
		} catch (NumberFormatException e) {
			String errorMsg = "Recipient " + jsonNode.get(RECIPIENT).asText()
					+ " isn't a study result ID.";
			sendErrorBackToSender(jsonNode, errorMsg);
		}

		ActorRef actorRef = groupChannelMap.get(studyResultId);
		if (actorRef != null) {
			actorRef.tell(msg, self());
		} else {
			String errorMsg = "Recipient " + studyResultId.toString()
					+ " isn't member of this group.";
			sendErrorBackToSender(jsonNode, errorMsg);
		}
	}

	private void returnIsMember(Object msg) {
		IsMember isMember = (IsMember) msg;
		ActorRef groupChannel = groupChannelMap.get(isMember.id);
		sender().tell((groupChannel != null), self());
	}

	private void closeAGroupChannel(Object msg) {
		PoisonSomeone poison = (PoisonSomeone) msg;
		ActorRef groupChannel = groupChannelMap.get(poison.idOfTheOneToPoison);
		if (groupChannel != null) {
			groupChannel.forward(msg, getContext());
		}
	}

	private void dropout(Object msg) {
		Droppout droppout = (Droppout) msg;
		groupChannelMap.remove(droppout.studyResultId);
		if (!groupChannelMap.isEmpty()) {
			// Tell all group members "dropped" and the current group members
			ObjectNode objectNode = JsonUtils.OBJECTMAPPER.createObjectNode();
			objectNode.put(DROPPED, droppout.studyResultId);
			objectNode.put(GROUP_MEMBERS, groupChannelMap.keySet().toString());
			tellAll(new GroupMsg(objectNode));
		} else {
			// Tell this dispatcher to kill itself if it has no more members
			self().tell(PoisonPill.getInstance(), self());
		}
	}

	private void joinGroup(Object msg) {
		JoinGroup joinGroup = (JoinGroup) msg;
		groupChannelMap.put(joinGroup.studyResultId, sender());
		ObjectNode objectNode = JsonUtils.OBJECTMAPPER.createObjectNode();
		objectNode.put(JOINED, joinGroup.studyResultId);
		objectNode.put(GROUP_MEMBERS, groupChannelMap.keySet().toString());
		tellAll(new GroupMsg(objectNode));
	}

	private void tellAllButSender(Object msg) {
		for (ActorRef actorRef : groupChannelMap.values()) {
			if (actorRef != sender()) {
				actorRef.tell(msg, self());
			}
		}
	}

	private void tellAll(Object msg) {
		for (ActorRef actorRef : groupChannelMap.values()) {
			actorRef.tell(msg, self());
		}
	}

	/**
	 * Send an error back to sender. Recycle the JsonNode.
	 */
	private void sendErrorBackToSender(ObjectNode jsonNode, String errorMsg) {
		jsonNode.removeAll();
		jsonNode.put(ERROR, errorMsg);
		sender().tell(new GroupMsg(jsonNode), self());
	}

}