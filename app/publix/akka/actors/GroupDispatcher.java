package publix.akka.actors;

import java.util.HashMap;
import java.util.Map;

import models.GroupResult.GroupState;
import publix.akka.messages.Dropout;
import publix.akka.messages.GroupMsg;
import publix.akka.messages.IsMember;
import publix.akka.messages.JoinGroup;
import publix.akka.messages.PoisonSomeone;
import publix.akka.messages.Unregister;
import publix.services.GroupService;
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

	public static final String ACTOR_NAME = "GroupDispatcher";

	private static final String ERROR = "error";
	private static final String JOINED = "joined";
	private static final String GROUP_RESULT_ID = "groupId";
	private static final String GROUP_MEMBERS = "groupMembers";
	private static final String GROUP_STATE = "groupState";
	private static final String DROPPED = "dropped";
	private static final String RECIPIENT = "recipient";
	/**
	 * Contains the members of this group. Maps StudyResult's IDs to ActorRefs.
	 */
	private final Map<Long, ActorRef> groupChannelMap = new HashMap<>();
	private final ActorRef groupDispatcherRegistry;
	private final GroupService groupService;
	private long groupResultId;

	public static Props props(ActorRef groupDispatcherRegistry,
			GroupService groupService, long groupResultId) {
		return Props.create(GroupDispatcher.class, groupDispatcherRegistry,
				groupService, groupResultId);
	}

	public GroupDispatcher(ActorRef groupDispatcherRegistry,
			GroupService groupService, long groupResultId) {
		this.groupDispatcherRegistry = groupDispatcherRegistry;
		this.groupService = groupService;
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
			joinGroupDispatcher(msg);
		} else if (msg instanceof Dropout) {
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

	// Comes from ChannelService (no persisting)
	private void closeAGroupChannel(Object msg) {
		PoisonSomeone poison = (PoisonSomeone) msg;
		long studyResultId = poison.idOfTheOneToPoison;
		ActorRef groupChannel = groupChannelMap.get(studyResultId);
		if (groupChannel != null) {
			// Remove from this GroupDispatcher
			groupChannelMap.remove(studyResultId);
			// Tell GroupChannel to close itself
			groupChannel.forward(msg, getContext());
			tellDropoutToEveryone(studyResultId);
			sender().tell(true, self());
		} else {
			sender().tell(false, self());
		}
	}

	// Tell all group members "dropped" and the current group members
	private void tellDropoutToEveryone(long studyResultId) {
		ObjectNode objectNode = JsonUtils.OBJECTMAPPER.createObjectNode();
		objectNode.put(DROPPED, studyResultId);
		objectNode.put(GROUP_RESULT_ID, groupResultId);
		objectNode.put(GROUP_MEMBERS, groupChannelMap.keySet().toString());
		GroupState groupState = groupService.getGroupState(groupResultId);
		objectNode.put(GROUP_STATE, groupState.toString());
		tellAll(new GroupMsg(objectNode));
	}

	// Comes from GroupChannel (has to be persisted)
	private void dropout(Object msg) {
		Dropout droppout = (Dropout) msg;
		long studyResultId = droppout.studyResultId;
		// Only remove GroupChannel if it's the one from the sender
		if (groupChannelMap.get(studyResultId).equals(sender())) {
			groupChannelMap.remove(droppout.studyResultId);
			groupService.dropGroupResult(droppout.studyResultId);
			tellDropoutToEveryone(droppout.studyResultId);
		}
		if (groupChannelMap.isEmpty()) {
			// Tell this dispatcher to kill itself if it has no more members
			self().tell(PoisonPill.getInstance(), self());
		}
	}

	private void joinGroupDispatcher(Object msg) {
		JoinGroup joinGroup = (JoinGroup) msg;
		long studyResultId = joinGroup.studyResultId;
		groupChannelMap.put(studyResultId, sender());
		ObjectNode objectNode = JsonUtils.OBJECTMAPPER.createObjectNode();
		objectNode.put(JOINED, studyResultId);
		objectNode.put(GROUP_RESULT_ID, groupResultId);
		objectNode.put(GROUP_MEMBERS, groupChannelMap.keySet().toString());
		GroupState groupState = groupService.getGroupState(groupResultId);
		objectNode.put(GROUP_STATE, groupState.toString());
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