package services.publix.akka.actors;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.UntypedActor;
import daos.common.GroupResultDao;
import models.common.GroupResult;
import models.common.GroupResult.GroupState;
import play.Logger;
import play.db.jpa.JPAApi;
import services.publix.akka.messages.GroupDispatcherProtocol;
import services.publix.akka.messages.GroupDispatcherProtocol.GroupActionMsg;
import services.publix.akka.messages.GroupDispatcherProtocol.GroupActionMsg.GroupAction;
import services.publix.akka.messages.GroupDispatcherProtocol.GroupErrorMsg;
import services.publix.akka.messages.GroupDispatcherProtocol.GroupMsg;
import services.publix.akka.messages.GroupDispatcherProtocol.Joined;
import services.publix.akka.messages.GroupDispatcherProtocol.Left;
import services.publix.akka.messages.GroupDispatcherProtocol.PoisonChannel;
import services.publix.akka.messages.GroupDispatcherProtocol.RegisterChannel;
import services.publix.akka.messages.GroupDispatcherProtocol.UnregisterChannel;
import services.publix.akka.messages.GroupDispatcherRegistryProtocol.Unregister;
import utils.common.JsonUtils;

/**
 * A GroupDispatcher is an Akka Actor responsible for distributing messages
 * (GroupMsg) within a group.
 * 
 * A GroupDispatcher only handles the GroupChannels but is not responsible for
 * the actual joining of a GroupResult. This is done prior to creating a
 * GroupDispatcher by the GroupService which persists all data in a GroupResult.
 * 
 * A GroupChannel is only opened after a StudyResult joined a GroupResult, which
 * is done in the GroupService. Group data (e.g. who's member) are persisted in
 * a GroupResult entity. A GroupChannel is closed after the StudyResult left the
 * group.
 * 
 * A GroupDispatcher handles all messages specified in the
 * GroupDispatcherProtocol.
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
	 * StudyResult's IDs to ActorRefs. It's a one-to-one relationship.
	 */
	private final Map<Long, ActorRef> groupChannelMap = new HashMap<>();
	private final JPAApi jpa;
	private final ActorRef groupDispatcherRegistry;
	private final GroupResultDao groupResultDao;
	private long groupResultId;

	/**
	 * Akka method to get this Actor started. Changes in props must be done in
	 * the constructor too.
	 */
	public static Props props(JPAApi jpa, ActorRef groupDispatcherRegistry,
			GroupResultDao groupResultDao, long groupResultId) {
		return Props.create(GroupDispatcher.class, jpa, groupDispatcherRegistry,
				groupResultDao, groupResultId);
	}

	public GroupDispatcher(JPAApi jpa, ActorRef groupDispatcherRegistry,
			GroupResultDao groupResultDao, long groupResultId) {
		this.jpa = jpa;
		this.groupDispatcherRegistry = groupDispatcherRegistry;
		this.groupResultDao = groupResultDao;
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
			handleGroupMsg((GroupMsg) msg);
		} else if (msg instanceof Joined) {
			// A member joined
			joined((Joined) msg);
		} else if (msg instanceof Left) {
			// A member left
			left((Left) msg);
		} else if (msg instanceof RegisterChannel) {
			// A GroupChannel wants to register
			registerChannel((RegisterChannel) msg);
		} else if (msg instanceof UnregisterChannel) {
			// A GroupChannel wants to unregister
			unregisterChannel((UnregisterChannel) msg);
		} else if (msg instanceof PoisonChannel) {
			// Comes from ChannelService: close a group channel
			poisonAGroupChannel((PoisonChannel) msg);
		} else {
			unhandled(msg);
		}
	}

	/**
	 * Handle a GroupMsg received from a client. What to do with it depends on
	 * the JSON inside the GroupMsg.
	 * 
	 * @see GroupDispatcherProtocol.GroupMsg
	 * @see GroupDispatcherProtocol.GroupActionMsg
	 */
	private void handleGroupMsg(GroupMsg groupMsg) {
		ObjectNode jsonNode = groupMsg.jsonNode;
		if (jsonNode.has(GroupActionMsg.ACTION)) {
			// We have a group action message
			handleGroupActionMsg(jsonNode);
		} else if (jsonNode.has(GroupMsg.RECIPIENT)) {
			// We have a message intended for only one recipient
			Long recipient = retrieveRecipient(jsonNode);
			if (recipient != null) {
				tellRecipientOnly(groupMsg, recipient);
			}
		} else {
			// We have broadcast message: Just tell everyone except the sender
			tellAllButSender(groupMsg);
		}
	}

	/**
	 * Handles group actions originating from the client
	 */
	private void handleGroupActionMsg(ObjectNode jsonNode) {
		String action = jsonNode.get(GroupActionMsg.ACTION).asText();
		GroupAction.GROUP_SESSION.name();
		switch (GroupAction.valueOf(action)) {
		case GROUP_SESSION:
			handleActionGroupSession(jsonNode);
			break;
		case FIXED:
			handleActionFix(jsonNode);
			break;
		default:
			String errorMsg = "Unknown action " + action;
			sendErrorBackToSender(jsonNode, errorMsg);
			break;
		}
	}

	/**
	 * Persists GroupSession and tells everyone
	 */
	private void handleActionGroupSession(ObjectNode jsonNode) {
		Long clientsVersion = Long.valueOf(
				jsonNode.get(GroupActionMsg.GROUP_SESSION_VERSION).asText());
		JsonNode newSessionData = jsonNode
				.get(GroupActionMsg.GROUP_SESSION_DATA);
		GroupResult groupResult = persistGroupSessionData(clientsVersion,
				newSessionData);
		tellGroupSessionData(groupResult);
	}

	/**
	 * Changes state of GroupResult to FIXED
	 */
	private void handleActionFix(ObjectNode jsonNode) {
		GroupResult groupResult = getGroupResult(groupResultId);
		if (groupResult != null) {
			groupResult.setGroupState(GroupState.FIXED);
			updateGroupResult(groupResult);
		} else {
			String errorMsg = "Couldn't fix group result.";
			sendErrorBackToSender(errorMsg);
		}
	}

	/**
	 * Persists the given sessionData in the GroupResult and increases the
	 * groupSessionVersion by 1 - but only if the stored version is equal to the
	 * received one. Returns the GroupResult object if this was successful -
	 * otherwise null.
	 */
	private GroupResult persistGroupSessionData(Long version,
			JsonNode sessionData) {
		GroupResult groupResult = getGroupResult(groupResultId);
		if (groupResult != null && version != null && sessionData != null
				&& groupResult.getGroupSessionVersion().equals(version)) {
			groupResult.setGroupSessionData(sessionData.toString());
			long newVersion = groupResult.getGroupSessionVersion() + 1l;
			groupResult.setGroupSessionVersion(newVersion);
			boolean success = updateGroupResult(groupResult);
			return success ? groupResult : null;
		}
		return null;
	}

	/**
	 * Retrieves the recipient's study result ID from the given jsonNode. If
	 * it's malformed it returns a null.
	 */
	private Long retrieveRecipient(ObjectNode jsonNode) {
		Long recipientStudyResultId = null;
		try {
			recipientStudyResultId = Long
					.valueOf(jsonNode.get(GroupMsg.RECIPIENT).asText());
		} catch (NumberFormatException e) {
			String errorMsg = "Recipient "
					+ jsonNode.get(GroupMsg.RECIPIENT).asText()
					+ " isn't a study result ID.";
			sendErrorBackToSender(jsonNode, errorMsg);
		}
		return recipientStudyResultId;
	}

	/**
	 * Sends a GROUP_SESSION action message to everyone in the group - or if the
	 * GroupResult doesn't exist sends an error message back to the sender.
	 */
	private void tellGroupSessionData(GroupResult groupResult) {
		if (groupResult != null) {
			Long studyResultId = getStudyResultByActorRef(sender());
			tellGroupAction(studyResultId, GroupAction.GROUP_SESSION,
					groupResult);
		} else {
			String errorMsg = "Group session data not stored";
			sendErrorBackToSender(errorMsg);
		}
	}

	/**
	 * Gets the study result ID that maps to the given ActorRef. Not performant.
	 */
	public Long getStudyResultByActorRef(ActorRef actorRef) {
		for (Entry<Long, ActorRef> entry : groupChannelMap.entrySet()) {
			if (Objects.equals(actorRef, entry.getValue())) {
				return entry.getKey();
			}
		}
		return null;
	}

	/**
	 * Registers the given channel and sends an OPENED action group message to
	 * everyone in this group.
	 */
	private void registerChannel(RegisterChannel registerChannel) {
		long studyResultId = registerChannel.studyResultId;
		groupChannelMap.put(studyResultId, sender());
		tellGroupAction(studyResultId, GroupAction.OPENED);
	}

	/**
	 * Unregisters the given channel and sends an CLOSED action group message to
	 * everyone in this group. Then if the group is now empty it sends a
	 * PoisonPill to this GroupDispatcher.
	 */
	private void unregisterChannel(UnregisterChannel unregisterChannel) {
		long studyResultId = unregisterChannel.studyResultId;
		// Only remove GroupChannel if it's the one from the sender (there might
		// be a new GroupChannel for the same StudyResult after a reload)
		if (groupChannelMap.containsKey(studyResultId)
				&& groupChannelMap.get(studyResultId).equals(sender())) {
			groupChannelMap.remove(unregisterChannel.studyResultId);
			tellGroupAction(studyResultId, GroupAction.CLOSED);
		}

		// Tell this dispatcher to kill itself if it has no more members
		if (groupChannelMap.isEmpty()) {
			self().tell(PoisonPill.getInstance(), self());
		}
	}

	/**
	 * Send the JOINED action to group member specified in Joined.
	 */
	private void joined(Joined joined) {
		tellGroupAction(joined.studyResultId, GroupAction.JOINED);
	}

	/**
	 * Send the LEFT action to group member specified in Left.
	 */
	private void left(Left left) {
		tellGroupAction(left.studyResultId, GroupAction.LEFT);
	}

	/**
	 * Wrapper around {@link #tellGroupAction(long, String, GroupResult)
	 * tellGroupAction} but retrieves the GroupResult from the database before
	 * calling it.
	 */
	private void tellGroupAction(long studyResultId, GroupAction action) {
		// The current group data are persisted in a GroupResult entity. The
		// GroupResult determines who is member of the group - and not
		// the groupChannelMap.
		GroupResult groupResult = getGroupResult(groupResultId);
		if (groupResult == null) {
			String errorMsg = "Couldn't find group result with ID "
					+ groupResultId + " in database.";
			sendErrorBackToSender(errorMsg);
			return;
		}
		tellGroupAction(studyResultId, action, groupResult);
	}

	/**
	 * Creates a new GroupMsg and sends it to all group members. The GroupMsg
	 * includes a whole bunch of data including the action, all currently open
	 * channels, the group session data and the group session version.
	 * 
	 * @param studyResultId
	 *            Which group member initiated this action
	 * @param action
	 *            The action of the GroupMsg
	 * @param GroupResult
	 *            The GroupResult of this group
	 */
	private void tellGroupAction(long studyResultId, GroupAction action,
			GroupResult groupResult) {
		ObjectNode objectNode = JsonUtils.OBJECTMAPPER.createObjectNode();
		objectNode.put(GroupActionMsg.ACTION, action.toString());
		objectNode.put(GroupActionMsg.GROUP_RESULT_ID, groupResultId);
		objectNode.put(GroupActionMsg.MEMBER_ID, studyResultId);
		objectNode.put(GroupActionMsg.MEMBERS,
				String.valueOf(groupResult.getStudyResultList()));
		objectNode.put(GroupActionMsg.CHANNELS,
				String.valueOf(groupChannelMap.keySet()));
		objectNode.put(GroupActionMsg.GROUP_SESSION_DATA,
				groupResult.getGroupSessionData());
		objectNode.put(GroupActionMsg.GROUP_SESSION_VERSION,
				groupResult.getGroupSessionVersion());
		tellAll(new GroupActionMsg(objectNode));
	}

	/**
	 * Tell GroupChannel to close itself. The GroupChannel then sends a
	 * ChannelClosed back to this GroupDispatcher during postStop and then we
	 * can remove the channel from the groupChannelMap and tell all other
	 * members about it. Also send false back to the sender if the GroupChannel
	 * wasn't handled by this GroupDispatcher.
	 */
	private void poisonAGroupChannel(PoisonChannel poison) {
		long studyResultId = poison.studyResultIdOfTheOneToPoison;
		ActorRef groupChannel = groupChannelMap.get(studyResultId);
		if (groupChannel != null) {
			groupChannel.forward(poison, getContext());
			sender().tell(true, self());
		} else {
			sender().tell(false, self());
		}
	}

	/**
	 * Send the message to everyone in the groupChannelMap except the sender of
	 * this message.
	 */
	private void tellAllButSender(Object msg) {
		for (ActorRef actorRef : groupChannelMap.values()) {
			if (actorRef != sender()) {
				actorRef.tell(msg, self());
			}
		}
	}

	/**
	 * Send the message to everyone in groupChannelMap.
	 */
	private void tellAll(Object msg) {
		for (ActorRef actorRef : groupChannelMap.values()) {
			actorRef.tell(msg, self());
		}
	}

	/**
	 * Sends the message msg only to the recipient specified with the given
	 * study result ID.
	 */
	private void tellRecipientOnly(GroupMsg msg, Long recipientStudyResultId) {
		ActorRef actorRef = groupChannelMap.get(recipientStudyResultId);
		if (actorRef != null) {
			actorRef.tell(msg, self());
		} else {
			String errorMsg = "Recipient "
					+ String.valueOf(recipientStudyResultId)
					+ " isn't member of this group.";
			sendErrorBackToSender(errorMsg);
		}
	}

	/**
	 * Send an error back to sender.
	 */
	private void sendErrorBackToSender(String errorMsg) {
		ObjectNode jsonNode = JsonUtils.OBJECTMAPPER.createObjectNode();
		jsonNode.put(GroupErrorMsg.ERROR, errorMsg);
		sender().tell(new GroupErrorMsg(jsonNode), self());
	}

	/**
	 * Send an error back to sender. Recycle the JsonNode.
	 */
	private void sendErrorBackToSender(ObjectNode jsonNode, String errorMsg) {
		jsonNode.removeAll();
		jsonNode.put(GroupErrorMsg.ERROR, errorMsg);
		sender().tell(new GroupErrorMsg(jsonNode), self());
	}

	/**
	 * Retrieve the GroupResult from the database.
	 */
	private GroupResult getGroupResult(long groupResultId) {
		try {
			return jpa.withTransaction(() -> {
				return groupResultDao.findById(groupResultId);
			});
		} catch (Throwable e) {
			Logger.error(CLASS_NAME + ".getGroupResult: ", e);
		}
		return null;
	}

	/**
	 * Persist the changes in the GroupResult. Returns true if successful and
	 * false otherwise.
	 */
	private boolean updateGroupResult(GroupResult groupResult) {
		try {
			jpa.withTransaction(() -> {
				groupResultDao.update(groupResult);
			});
			return true;
		} catch (Throwable e) {
			Logger.error(CLASS_NAME + ".updateGroupResult: ", e);
			return false;
		}
	}

}