package publix.groupservices.akka.actors;

import java.util.HashMap;
import java.util.Map;

import models.GroupModel;
import persistance.GroupDao;
import play.Logger;
import play.db.jpa.JPA;
import publix.groupservices.akka.messages.GroupDispatcherProtocol.GroupMsg;
import publix.groupservices.akka.messages.GroupDispatcherProtocol.Joined;
import publix.groupservices.akka.messages.GroupDispatcherProtocol.Left;
import publix.groupservices.akka.messages.GroupDispatcherProtocol.PoisonChannel;
import publix.groupservices.akka.messages.GroupDispatcherProtocol.RegisterChannel;
import publix.groupservices.akka.messages.GroupDispatcherProtocol.UnregisterChannel;
import publix.groupservices.akka.messages.GroupDispatcherRegistryProtocol.Unregister;
import utils.JsonUtils;
import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.UntypedActor;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * A GroupDispatcher is an Akka Actor responsible for distributing messages
 * (GroupMsg) within a group.
 * 
 * A GroupDispatcher only handles the GroupChannels but is not responsible for
 * the actual joining of a group. This is done prior to creating a
 * GroupDispatcher by the GroupService which persists all data in a GroupModel.
 * 
 * A GroupChannel is only be opened after a StudyResult joined a group, which is
 * done in the GroupService. Group data (e.g. who's member) are persisted in a
 * GroupModel. A GroupChannel is closed after the StudyResult left the group.
 * 
 * A GroupChannel registers in a GroupDispatcher by sending the RegisterChannel
 * message and unregisters by sending a UnregisterChannel message.
 * 
 * A new GroupDispatcher is created by the GroupDispatcherRegistry. If a
 * GroupDispatcher has no more members it closes itself.
 * 
 * @author Kristian Lange (2015)
 */
public class GroupDispatcher extends UntypedActor {

	public static final String ACTOR_NAME = "GroupDispatcher";

	private static final String CLASS_NAME = GroupDispatcher.class
			.getSimpleName();

	/**
	 * Contains the members that are handled by this GroupDispatcher. Maps
	 * StudyResult's IDs to ActorRefs.
	 */
	private final Map<Long, ActorRef> groupChannelMap = new HashMap<>();
	private final ActorRef groupDispatcherRegistry;
	private final GroupDao groupDao;
	private long groupId;

	/**
	 * Akka method to get this Actor started. Changes in props must be done in
	 * the constructor too.
	 */
	public static Props props(ActorRef groupDispatcherRegistry,
			GroupDao groupDao, long groupId) {
		return Props.create(GroupDispatcher.class, groupDispatcherRegistry,
				groupDao, groupId);
	}

	public GroupDispatcher(ActorRef groupDispatcherRegistry, GroupDao groupDao,
			long groupId) {
		this.groupDispatcherRegistry = groupDispatcherRegistry;
		this.groupDao = groupDao;
		this.groupId = groupId;
	}

	@Override
	public void postStop() {
		groupDispatcherRegistry.tell(new Unregister(groupId), self());
	}

	@Override
	public void onReceive(Object msg) throws Exception {
		if (msg instanceof GroupMsg) {
			// We got a GroupMsg from a client
			dispatchGroupMsg(msg);
		} else if (msg instanceof Joined) {
			// A member joined
			joined(msg);
		} else if (msg instanceof Left) {
			// A member left
			left(msg);
		} else if (msg instanceof RegisterChannel) {
			// A GroupChannel wants to register
			registerChannel(msg);
		} else if (msg instanceof UnregisterChannel) {
			// A GroupChannel wants to unregister
			unregisterChannel(msg);
		} else if (msg instanceof PoisonChannel) {
			// Comes from ChannelService: close a group channel
			poisonAGroupChannel(msg);
		} else {
			unhandled(msg);
		}
	}

	private void dispatchGroupMsg(Object msg) {
		ObjectNode jsonNode = ((GroupMsg) msg).jsonNode;
		if (jsonNode.has(GroupMsg.RECIPIENT)) {
			tellRecipientOnly(msg, jsonNode);
		} else {
			tellAllButSender(msg);
		}
	}

	private void tellRecipientOnly(Object msg, ObjectNode jsonNode) {
		Long studyResultId = null;
		try {
			studyResultId = Long.valueOf(jsonNode.get(GroupMsg.RECIPIENT)
					.asText());
		} catch (NumberFormatException e) {
			String errorMsg = "Recipient "
					+ jsonNode.get(GroupMsg.RECIPIENT).asText()
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

	private void registerChannel(Object msg) {
		RegisterChannel registerChannel = (RegisterChannel) msg;
		long studyResultId = registerChannel.studyResultId;
		groupChannelMap.put(studyResultId, sender());
		tellGroupAction(studyResultId, GroupMsg.OPENED);
	}

	private void unregisterChannel(Object msg) {
		UnregisterChannel channelClosed = (UnregisterChannel) msg;
		long studyResultId = channelClosed.studyResultId;
		// Only remove GroupChannel if it's the one from the sender (there might
		// be a new GroupChannel for the same StudyResult after a reload)
		if (groupChannelMap.containsKey(studyResultId)
				&& groupChannelMap.get(studyResultId).equals(sender())) {
			groupChannelMap.remove(channelClosed.studyResultId);
			tellGroupAction(studyResultId, GroupMsg.CLOSED);
		}

		// Tell this dispatcher to kill itself if it has no more members
		if (groupChannelMap.isEmpty()) {
			self().tell(PoisonPill.getInstance(), self());
		}
	}

	private void joined(Object msg) {
		Joined joined = (Joined) msg;
		tellGroupAction(joined.studyResultId, GroupMsg.JOINED);
	}

	private void left(Object msg) {
		Left left = (Left) msg;
		tellGroupAction(left.studyResultId, GroupMsg.LEFT);
	}

	private void tellGroupAction(long studyResultId, String action) {
		// The current group data are persisted in a GroupModel. The GroupModel
		// determines who is member of the group - and not the groupChannelMap.
		GroupModel group = getGroup(groupId);
		if (group == null) {
			return;
		}
		ObjectNode objectNode = JsonUtils.OBJECTMAPPER.createObjectNode();
		objectNode.put(GroupMsg.ACTION, action);
		objectNode.put(GroupMsg.GROUP_ID, groupId);
		objectNode.put(GroupMsg.MEMBER_ID, studyResultId);
		objectNode.put(GroupMsg.MEMBERS,
				String.valueOf(group.getStudyResultList()));
		objectNode.put(GroupMsg.CHANNELS,
				String.valueOf(groupChannelMap.keySet()));
		tellAll(new GroupMsg(objectNode));
	}

	private void poisonAGroupChannel(Object msg) {
		PoisonChannel poison = (PoisonChannel) msg;
		long studyResultId = poison.studyResultIdOfTheOneToPoison;
		ActorRef groupChannel = groupChannelMap.get(studyResultId);
		if (groupChannel != null) {
			// Tell GroupChannel to close itself. The GroupChannel sends a
			// ChannelClosed to this GroupDispatcher during postStop and then we
			// can remove it from the groupChannelMap and tell all other members
			// about it
			groupChannel.forward(msg, getContext());
			sender().tell(true, self());
		} else {
			sender().tell(false, self());
		}
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
		jsonNode.put(GroupMsg.ERROR, errorMsg);
		sender().tell(new GroupMsg(jsonNode), self());
	}

	private GroupModel getGroup(long groupId) {
		try {
			return JPA.withTransaction(() -> {
				return groupDao.findById(groupId);
			});
		} catch (Throwable e) {
			Logger.error(CLASS_NAME + ".getGroup: ", e);
		}
		return null;
	}

}