package services.publix.group.akka.actors;

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
import play.Logger.ALogger;
import play.db.jpa.JPAApi;
import services.publix.group.akka.messages.GroupDispatcherProtocol;
import services.publix.group.akka.messages.GroupDispatcherProtocol.GroupActionMsg;
import services.publix.group.akka.messages.GroupDispatcherProtocol.GroupActionMsg.GroupAction;
import services.publix.group.akka.messages.GroupDispatcherProtocol.GroupMsg;
import services.publix.group.akka.messages.GroupDispatcherProtocol.Joined;
import services.publix.group.akka.messages.GroupDispatcherProtocol.Left;
import services.publix.group.akka.messages.GroupDispatcherProtocol.PoisonChannel;
import services.publix.group.akka.messages.GroupDispatcherProtocol.ReassignChannel;
import services.publix.group.akka.messages.GroupDispatcherProtocol.RegisterChannel;
import services.publix.group.akka.messages.GroupDispatcherProtocol.UnregisterChannel;
import services.publix.group.akka.messages.GroupDispatcherRegistryProtocol.Unregister;

/**
 * A GroupDispatcher is an Akka Actor responsible for distributing messages
 * (GroupMsg) within a group.
 * 
 * A GroupDispatcher only handles the GroupChannels but is not responsible for
 * the actual joining of a GroupResult. This is done prior to creating a
 * GroupDispatcher by the GroupAdministration which persists all data in a
 * GroupResult. Who's member in a group is defined by the GroupResult.
 * 
 * A GroupChannel is only opened after a StudyResult joined a GroupResult, which
 * is done in the GroupAdministration. Group data (e.g. who's member) are
 * persisted in a GroupResult entity. A GroupChannel is closed after the
 * StudyResult left the group.
 * 
 * A GroupDispatcher handles all messages specified in the
 * GroupDispatcherProtocol.
 * 
 * For the group session the GroupDispatcher is a message broker in a simple pub
 * sub system with receipt messages to assure delivery or its absence.
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

	private static final ALogger LOGGER = Logger.of(GroupDispatcher.class);

	public static final String ACTOR_NAME = "GroupDispatcher";

	private GroupRegistry groupRegistry = new GroupRegistry();
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
		} else if (msg instanceof ReassignChannel) {
			// A GroupChannel has to be reassigned
			reassignChannel((ReassignChannel) msg);
		} else if (msg instanceof PoisonChannel) {
			// Comes from GroupChannelService: close a group channel
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
			tellRecipientOnly(groupMsg, recipient);
		} else {
			// We have broadcast message: Just tell everyone except the sender
			tellAllButSender(groupMsg);
		}
	}

	/**
	 * Handles group actions originating from a client
	 */
	private void handleGroupActionMsg(ObjectNode jsonNode) {
		String action = jsonNode.get(GroupActionMsg.ACTION).asText();
		GroupAction.SESSION.name();
		switch (GroupAction.valueOf(action)) {
		case SESSION:
			handleActionGroupSession(jsonNode);
			break;
		case FIXED:
			handleActionFix(jsonNode);
			break;
		default:
			String errorMsg = "Unknown action " + action;
			sendErrorBackToSender(errorMsg);
			break;
		}
	}

	/**
	 * Persists GroupSession and tells everyone
	 */
	private void handleActionGroupSession(ObjectNode jsonNode) {
		jpa.withTransaction(() -> {
			Long clientsVersion = Long.valueOf(jsonNode
					.get(GroupActionMsg.GROUP_SESSION_VERSION).asText());
			JsonNode updatedSessionData = jsonNode
					.get(GroupActionMsg.GROUP_SESSION_DATA);
			GroupResult groupResult = groupResultDao.findById(groupResultId);
			boolean success = persistGroupSessionData(groupResult,
					clientsVersion, updatedSessionData);
			if (success) {
				Long studyResultId = groupRegistry.getStudyResult(sender());
				tellAllSessionGroupAction(studyResultId, groupResult);
				tellSenderOnlySimpleGroupAction(GroupAction.SESSION_ACK);
			} else {
				tellSenderOnlySimpleGroupAction(GroupAction.SESSION_FAIL);
			}
		});
	}

	/**
	 * Changes state of GroupResult to FIXED and sends an update to all group
	 * members
	 */
	private void handleActionFix(ObjectNode jsonNode) {
		jpa.withTransaction(() -> {
			GroupResult groupResult = groupResultDao.findById(groupResultId);
			if (groupResult != null) {
				groupResult.setGroupState(GroupState.FIXED);
				updateGroupResult(groupResult);
				tellAllFullGroupAction(GroupAction.FIXED, groupResult);
			} else {
				String errorMsg = "Couldn't fix the group result.";
				sendErrorBackToSender(errorMsg);
			}
		});
	}

	/**
	 * Persists the given sessionData in the GroupResult and increases the
	 * groupSessionVersion by 1 - but only if the stored version is equal to the
	 * received one. Returns true if this was successful - otherwise false.
	 */
	private boolean persistGroupSessionData(GroupResult groupResult,
			Long version, JsonNode sessionData) {
		if (groupResult != null && version != null && sessionData != null
				&& groupResult.getGroupSessionVersion().equals(version)) {
			groupResult.setGroupSessionData(sessionData.toString());
			long newVersion = groupResult.getGroupSessionVersion() + 1l;
			groupResult.setGroupSessionVersion(newVersion);
			updateGroupResult(groupResult);
			return true;
		}
		return false;
	}

	/**
	 * Retrieves the recipient's study result ID from the given jsonNode. If
	 * it's malformed it sends and error back to sender and returns a null.
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
			sendErrorBackToSender(errorMsg);
		}
		return recipientStudyResultId;
	}

	/**
	 * Registers the given channel and sends an OPENED action group message to
	 * everyone in this group.
	 */
	private void registerChannel(RegisterChannel registerChannel) {
		long studyResultId = registerChannel.studyResultId;
		groupRegistry.register(studyResultId, sender());
		tellAllFullGroupAction(studyResultId, GroupAction.OPENED);
	}

	/**
	 * Unregisters the given channel and sends an CLOSED action group message to
	 * everyone in this group. Then if the group is now empty it sends a
	 * PoisonPill to this GroupDispatcher itself.
	 */
	private void unregisterChannel(UnregisterChannel unregisterChannel) {
		long studyResultId = unregisterChannel.studyResultId;
		// Only unregister GroupChannel if it's the one from the sender (there
		// might be a new GroupChannel for the same StudyResult after a reload)
		if (groupRegistry.containsStudyResult(studyResultId) && groupRegistry
				.getGroupChannel(studyResultId).equals(sender())) {
			groupRegistry.unregister(unregisterChannel.studyResultId);
			tellAllFullGroupAction(studyResultId, GroupAction.CLOSED);
		}

		// Tell this dispatcher to kill itself if it has no more members
		if (groupRegistry.isEmpty()) {
			self().tell(PoisonPill.getInstance(), self());
		}
	}

	/**
	 * Forwards this ReassignChannel message to the right group channel.
	 */
	private void reassignChannel(ReassignChannel reassignChannel) {
		long studyResultId = reassignChannel.studyResultId;
		if (groupRegistry.containsStudyResult(studyResultId)) {
			ActorRef groupChannel = groupRegistry
					.getGroupChannel(studyResultId);
			groupChannel.forward(reassignChannel, getContext());
		} else {
			String errorMsg = "StudyResult with ID " + studyResultId
					+ " not handled by GroupDispatcher for GroupResult with ID "
					+ groupResultId + ".";
			sendErrorBackToSender(errorMsg);
		}
	}

	/**
	 * Send the JOINED group action message to all group members. Who's joined
	 * the group is specified in the given Joined object.
	 */
	private void joined(Joined joined) {
		tellAllFullGroupAction(joined.studyResultId, GroupAction.JOINED);
	}

	/**
	 * Send the LEFT group action message to all group members. Who's left the
	 * group is specified in the given Left object.
	 */
	private void left(Left left) {
		tellAllFullGroupAction(left.studyResultId, GroupAction.LEFT);
	}

	/**
	 * Wrapper around {@link #tellAllFullGroupAction(Long, String, GroupResult)
	 * tellGroupAction} but retrieves the GroupResult from the database before
	 * calling it.
	 */
	private void tellAllFullGroupAction(long studyResultId,
			GroupAction action) {
		// The current group data are persisted in a GroupResult entity. The
		// GroupResult determines who is member of the group - and not
		// the group registry.
		jpa.withTransaction(() -> {
			GroupResult groupResult = groupResultDao.findById(groupResultId);
			if (groupResult == null) {
				String errorMsg = "Couldn't find group result with ID "
						+ groupResultId + " in database.";
				sendErrorBackToSender(errorMsg);
				return;
			}
			tellAllFullGroupAction(studyResultId, action, groupResult);
		});
	}

	/**
	 * Wrapper around {@link #tellAllFullGroupAction(Long, String, GroupResult)
	 * but for an action that originates in JATOS itself and not in a client.
	 */
	private void tellAllFullGroupAction(GroupAction action,
			GroupResult groupResult) {
		tellAllFullGroupAction(null, action, groupResult);
	}

	/**
	 * Sends a full group action message it to all group members. The message
	 * includes a whole bunch of data including the action, all currently open
	 * channels, the group session data and the group session version.
	 * 
	 * @param studyResultId
	 *            Which group member initiated this action
	 * @param action
	 *            The action of the GroupActionMsg
	 * @param GroupResult
	 *            The GroupResult of this group
	 */
	private void tellAllFullGroupAction(Long studyResultId, GroupAction action,
			GroupResult groupResult) {
		LOGGER.debug(".tellAllFullGroupAction: studyResultId " + studyResultId
				+ ", action " + action + ", groupResultId "
				+ groupResult.getId());
		GroupActionMsg msg = GroupActionMsgUtils.buildFullActionMsg(
				studyResultId, action, groupResult,
				groupRegistry.getAllStudyResultIds());
		tellAll(msg);
	}

	/**
	 * Sends a group action message with the current group session data and
	 * version to all members.
	 */
	private void tellAllSessionGroupAction(Long studyResultId,
			GroupResult groupResult) {
		LOGGER.debug(".tellAllFullGroupAction: studyResultId " + studyResultId
				+ ", action " + GroupAction.SESSION + ", groupResultId "
				+ groupResult.getId());
		GroupActionMsg msg = GroupActionMsgUtils
				.buildSessionActionMsg(studyResultId, groupResult);
		tellAll(msg);
	}

	/**
	 * Sends a simple group action message to the sender only.
	 */
	private void tellSenderOnlySimpleGroupAction(GroupAction action) {
		LOGGER.debug(".tellSenderOnlySimpleGroupAction: action " + action
				+ ", groupResultId " + groupResultId);
		GroupActionMsg msg = GroupActionMsgUtils.buildSimpleActionMsg(action,
				groupResultId);
		tellSenderOnly(msg);
	}

	/**
	 * Sends an error group action message back to the sender.
	 */
	private void sendErrorBackToSender(String errorMsg) {
		LOGGER.debug(".sendErrorBackToSender: groupResultId " + groupResultId
				+ " - " + errorMsg);
		GroupActionMsg msg = GroupActionMsgUtils.buildErrorActionMsg(errorMsg,
				groupResultId);
		tellSenderOnly(msg);
	}

	/**
	 * Tells the GroupChannel to close itself. The GroupChannel then sends a
	 * ChannelClosed back to this GroupDispatcher during postStop and then we
	 * can remove the channel from the group registry and tell all other members
	 * about it. Also send false back to the sender (GroupChannelService) if the
	 * GroupChannel wasn't handled by this GroupDispatcher.
	 */
	private void poisonAGroupChannel(PoisonChannel poison) {
		long studyResultId = poison.studyResultIdOfTheOneToPoison;
		ActorRef groupChannel = groupRegistry.getGroupChannel(studyResultId);
		if (groupChannel != null) {
			groupChannel.forward(poison, getContext());
			tellSenderOnly(true);
		} else {
			tellSenderOnly(false);
		}
	}

	/**
	 * Sends the message only to the recipient specified by the given study
	 * result ID.
	 */
	private void tellRecipientOnly(GroupMsg msg, Long recipientStudyResultId) {
		if (recipientStudyResultId == null) {
			String errorMsg = "No recipient specified.";
			sendErrorBackToSender(errorMsg);
			return;
		}
		ActorRef groupChannel = groupRegistry
				.getGroupChannel(recipientStudyResultId);
		if (groupChannel != null) {
			groupChannel.tell(msg, self());
		} else {
			String errorMsg = "Recipient "
					+ String.valueOf(recipientStudyResultId)
					+ " isn't member of this group.";
			sendErrorBackToSender(errorMsg);
		}
	}

	/**
	 * Sends the message to everyone in the group registry except the sender of
	 * this message.
	 */
	private void tellAllButSender(Object msg) {
		for (ActorRef actorRef : groupRegistry.getAllGroupChannels()) {
			if (actorRef != sender()) {
				actorRef.tell(msg, self());
			}
		}
	}

	/**
	 * Sends the message to everyone in group registry.
	 */
	private void tellAll(Object msg) {
		for (ActorRef actorRef : groupRegistry.getAllGroupChannels()) {
			actorRef.tell(msg, self());
		}
	}

	/**
	 * Sends the message only to the sender.
	 */
	private void tellSenderOnly(Object msg) {
		sender().tell(msg, self());
	}

	/**
	 * Persists the changes in the GroupResult.
	 */
	private void updateGroupResult(GroupResult groupResult) {
		jpa.withTransaction(() -> {
			groupResultDao.update(groupResult);
		});
	}

}
