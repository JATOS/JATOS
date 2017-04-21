package session.group.akka.actors;

import com.fasterxml.jackson.databind.node.ObjectNode;

import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.UntypedActor;
import play.Logger;
import play.Logger.ALogger;
import play.libs.Json;
import session.DispatcherRegistryProtocol.Unregister;
import session.Registry;
import session.group.akka.actors.services.GroupActionHandler;
import session.group.akka.actors.services.GroupActionMsgBuilder;
import session.group.akka.actors.services.GroupActionMsgBundle;
import session.group.akka.protocol.GroupDispatcherProtocol;
import session.group.akka.protocol.GroupDispatcherProtocol.GroupActionMsg;
import session.group.akka.protocol.GroupDispatcherProtocol.GroupActionMsg.GroupAction;
import session.group.akka.protocol.GroupDispatcherProtocol.GroupActionMsg.TellWhom;
import session.group.akka.protocol.GroupDispatcherProtocol.GroupMsg;
import session.group.akka.protocol.GroupDispatcherProtocol.Joined;
import session.group.akka.protocol.GroupDispatcherProtocol.Left;
import session.group.akka.protocol.GroupDispatcherProtocol.PoisonChannel;
import session.group.akka.protocol.GroupDispatcherProtocol.ReassignChannel;
import session.group.akka.protocol.GroupDispatcherProtocol.RegisterChannel;
import session.group.akka.protocol.GroupDispatcherProtocol.UnregisterChannel;

/**
 * A GroupDispatcher is an Akka Actor responsible for distributing messages
 * (GroupMsg) within a group. Thus it is the central class handling a group.
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
 * A GroupChannel registers in a GroupDispatcher by sending the RegisterChannel
 * message and unregisters by sending a UnregisterChannel message.
 * 
 * A new GroupDispatcher is created by the GroupDispatcherRegistry. If a
 * GroupDispatcher has no more members it closes itself.
 * 
 * A GroupDispatcher handles all messages specified in the
 * GroupDispatcherProtocol. There are fundamentally three different message
 * types: 1) group session patches, 2) broadcast messages, and 3) direct
 * messages intended for a certain group member.
 * 
 * The group session patches are JSON Patches after RFC 6902 and used to
 * describe changes in the group session data. The session data are stored in
 * the GroupResult.
 * 
 * @author Kristian Lange (2015)
 */
public class GroupDispatcher extends UntypedActor {

	private static final ALogger LOGGER = Logger.of(GroupDispatcher.class);

	public static final String ACTOR_NAME = "GroupDispatcher";

	private Registry groupRegistry = new Registry();
	private final ActorRef groupDispatcherRegistry;
	private final GroupActionHandler groupActionHandler;
	private final GroupActionMsgBuilder groupActionMsgBuilder;
	private long groupResultId;

	/**
	 * Akka method to get this Actor started. Changes in props must be done in
	 * the constructor too.
	 */
	public static Props props(ActorRef groupDispatcherRegistry,
			GroupActionHandler groupActionHandler,
			GroupActionMsgBuilder groupActionMsgBuilder, long groupResultId) {
		return Props.create(GroupDispatcher.class, groupDispatcherRegistry,
				groupActionHandler, groupActionMsgBuilder, groupResultId);
	}

	public GroupDispatcher(ActorRef groupDispatcherRegistry,
			GroupActionHandler groupActionHandler,
			GroupActionMsgBuilder groupActionMsgBuilder, long groupResultId) {
		this.groupDispatcherRegistry = groupDispatcherRegistry;
		this.groupActionHandler = groupActionHandler;
		this.groupActionMsgBuilder = groupActionMsgBuilder;
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
		LOGGER.debug(".handleGroupMsg: groupResultId {}, groupMsg {}",
				groupResultId, Json.stringify(groupMsg.jsonNode));
		ObjectNode jsonNode = groupMsg.jsonNode;
		if (jsonNode.has(GroupActionMsg.ACTION)) {
			// We have a group action message
			handleGroupActionMsg(groupMsg);
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
	private void handleGroupActionMsg(GroupMsg groupMsg) {
		long studyResultId = groupRegistry.getStudyResult(sender());
		GroupActionMsgBundle msgBundle = groupActionHandler
				.handleGroupActionMsg(groupMsg, groupResultId, studyResultId,
						groupRegistry);
		for (GroupActionMsg msg : msgBundle.getAll()) {
			tellGroupActionMsg(msg);
		}
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
			tellErrorSenderOnly(errorMsg);
		}
		return recipientStudyResultId;
	}

	/**
	 * Registers the given channel and sends an OPENED action group message to
	 * everyone in this group.
	 */
	private void registerChannel(RegisterChannel registerChannel) {
		LOGGER.debug(".registerChannel: groupResultId {}, studyResultId {}",
				groupResultId, registerChannel.studyResultId);
		long studyResultId = registerChannel.studyResultId;
		groupRegistry.register(studyResultId, sender());
		GroupActionMsg msg1 = groupActionMsgBuilder.buildSessionData(
				groupResultId, studyResultId, groupRegistry, GroupAction.OPENED,
				TellWhom.SENDER_ONLY);
		tellGroupActionMsg(msg1);
		GroupActionMsg msg2 = groupActionMsgBuilder.build(groupResultId,
				studyResultId, groupRegistry, GroupAction.OPENED,
				TellWhom.ALL_BUT_SENDER);
		tellGroupActionMsg(msg2);
	}

	/**
	 * Unregisters the given channel and sends an CLOSED action group message to
	 * everyone in this group. Then if the group is now empty it sends a
	 * PoisonPill to this GroupDispatcher itself.
	 */
	private void unregisterChannel(UnregisterChannel unregisterChannel) {
		LOGGER.debug(".unregisterChannel: groupResultId {}, studyResultId {}",
				groupResultId, unregisterChannel.studyResultId);
		long studyResultId = unregisterChannel.studyResultId;
		// Only unregister GroupChannel if it's the one from the sender (there
		// might be a new GroupChannel for the same StudyResult after a reload)
		if (groupRegistry.containsStudyResult(studyResultId)
				&& groupRegistry.getChannel(studyResultId).equals(sender())) {
			groupRegistry.unregister(unregisterChannel.studyResultId);
			GroupActionMsg msg = groupActionMsgBuilder.build(groupResultId,
					studyResultId, groupRegistry, GroupAction.CLOSED,
					TellWhom.ALL_BUT_SENDER);
			tellGroupActionMsg(msg);
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
		LOGGER.debug(".reassignChannel: groupResultId {}, studyResultId {}",
				groupResultId, reassignChannel.studyResultId);
		long studyResultId = reassignChannel.studyResultId;
		if (groupRegistry.containsStudyResult(studyResultId)) {
			ActorRef groupChannel = groupRegistry.getChannel(studyResultId);
			groupChannel.forward(reassignChannel, getContext());
		} else {
			String errorMsg = "StudyResult with ID " + studyResultId
					+ " not handled by GroupDispatcher for GroupResult with ID "
					+ groupResultId + ".";
			tellErrorSenderOnly(errorMsg);
		}
	}

	/**
	 * Send the JOINED group action message to all group members. Who's joined
	 * the group is specified in the given Joined object.
	 */
	private void joined(Joined joined) {
		LOGGER.debug(".joined: groupResultId {}, studyResultId {}",
				groupResultId, joined.studyResultId);
		GroupActionMsg msg = groupActionMsgBuilder.build(groupResultId,
				joined.studyResultId, groupRegistry, GroupAction.JOINED,
				TellWhom.ALL_BUT_SENDER);
		tellGroupActionMsg(msg);
	}

	/**
	 * Send the LEFT group action message to all group members. Who's left the
	 * group is specified in the given Left object.
	 */
	private void left(Left left) {
		LOGGER.debug(".left: groupResultId {}, studyResultId {}", groupResultId,
				left.studyResultId);
		GroupActionMsg msg = groupActionMsgBuilder.build(groupResultId,
				left.studyResultId, groupRegistry, GroupAction.LEFT,
				TellWhom.ALL_BUT_SENDER);
		tellGroupActionMsg(msg);
	}

	/**
	 * Tells the GroupChannel to close itself. The GroupChannel then sends a
	 * ChannelClosed back to this GroupDispatcher during postStop and then we
	 * can remove the channel from the group registry and tell all other members
	 * about it. Also send false back to the sender (GroupChannelService) if the
	 * GroupChannel wasn't handled by this GroupDispatcher.
	 */
	private void poisonAGroupChannel(PoisonChannel poison) {
		LOGGER.debug(".poisonAGroupChannel: groupResultId {}, studyResultId {}",
				groupResultId, poison.studyResultIdOfTheOneToPoison);
		long studyResultId = poison.studyResultIdOfTheOneToPoison;
		ActorRef groupChannel = groupRegistry.getChannel(studyResultId);
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
		LOGGER.debug(
				".tellRecipientOnly: groupResultId {}, studyResultId {}, msg {}",
				groupResultId, recipientStudyResultId,
				Json.stringify(msg.jsonNode));
		if (recipientStudyResultId == null) {
			String errorMsg = "No recipient specified.";
			tellErrorSenderOnly(errorMsg);
			return;
		}
		ActorRef groupChannel = groupRegistry
				.getChannel(recipientStudyResultId);
		if (groupChannel != null) {
			groupChannel.tell(msg, self());
		} else {
			String errorMsg = "Recipient "
					+ String.valueOf(recipientStudyResultId)
					+ " isn't member of this group.";
			tellErrorSenderOnly(errorMsg);
		}
	}

	private void tellGroupActionMsg(GroupActionMsg msg) {
		if (msg == null) {
			return;
		}
		switch (msg.tellWhom) {
		case ALL:
			tellAll(msg);
			break;
		case ALL_BUT_SENDER:
			tellAllButSender(msg);
			break;
		case SENDER_ONLY:
			tellSenderOnly(msg);
			break;
		}
	}

	private void tellErrorSenderOnly(String errorMsg) {
		LOGGER.debug(".tellErrorSenderOnly: groupResultId {}, errorMsg {}",
				groupResultId, errorMsg);
		GroupActionMsg groupActionMsg = groupActionMsgBuilder
				.buildError(groupResultId, errorMsg, TellWhom.SENDER_ONLY);
		tellGroupActionMsg(groupActionMsg);
	}

	/**
	 * Sends the message to everyone in the group registry except the sender of
	 * this message.
	 */
	private void tellAllButSender(GroupMsg msg) {
		LOGGER.debug(".tellAllButSender: groupResultId {}, msg {}",
				groupResultId, Json.stringify(msg.jsonNode));
		for (ActorRef actorRef : groupRegistry.getAllChannels()) {
			if (actorRef != sender()) {
				actorRef.tell(msg, self());
			}
		}
	}

	/**
	 * Sends the message to everyone in group registry.
	 */
	private void tellAll(GroupMsg msg) {
		LOGGER.debug(".tellAll: groupResultId {}, msg {}", groupResultId,
				Json.stringify(msg.jsonNode));
		for (ActorRef actorRef : groupRegistry.getAllChannels()) {
			actorRef.tell(msg, self());
		}
	}

	/**
	 * Sends the message only to the sender.
	 */
	private void tellSenderOnly(Object msg) {
		LOGGER.debug(".tellSenderOnly: groupResultId {}, msg {}", groupResultId,
				msg);
		sender().tell(msg, self());
	}

	/**
	 * Sends the message only to the sender.
	 */
	private void tellSenderOnly(GroupMsg msg) {
		LOGGER.debug(".tellSenderOnly: groupResultId {}, msg {}", groupResultId,
				Json.stringify(msg.jsonNode));
		sender().tell(msg, self());
	}

}
