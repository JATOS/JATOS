package services.publix.group.akka.actors.services;

import java.io.IOException;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.JsonPatchException;

import daos.common.GroupResultDao;
import models.common.GroupResult;
import models.common.GroupResult.GroupState;
import play.Logger;
import play.Logger.ALogger;
import play.db.jpa.JPAApi;
import play.libs.Json;
import services.publix.group.akka.messages.GroupDispatcherProtocol.GroupActionMsg;
import services.publix.group.akka.messages.GroupDispatcherProtocol.GroupActionMsg.GroupAction;
import services.publix.group.akka.messages.GroupDispatcherProtocol.GroupActionMsg.TellWhom;

/**
 * @author Kristian Lange (2017)
 */
@Singleton
public class GroupActionHandler {

	private static final ALogger LOGGER = Logger.of(GroupActionHandler.class);

	private final JPAApi jpa;

	private final GroupResultDao groupResultDao;

	private final GroupActionMsgBuilder groupActionMsgBuilder;

	@Inject
	public GroupActionHandler(JPAApi jpa, GroupResultDao groupResultDao,
			GroupActionMsgBuilder groupActionMsgBuilder) {
		this.jpa = jpa;
		this.groupResultDao = groupResultDao;
		this.groupActionMsgBuilder = groupActionMsgBuilder;
	}

	/**
	 * Handles group actions originating from a client
	 */
	public GroupActionMsgBundle handleGroupActionMsg(long groupResultId,
			long studyResultId, GroupRegistry groupRegistry,
			ObjectNode jsonNode) {
		LOGGER.debug(
				".handleGroupActionMsg:"
						+ " groupResultId {}, studyResultId {}, jsonNode {}",
				groupResultId, studyResultId, Json.stringify(jsonNode));
		String action = jsonNode.get(GroupActionMsg.ACTION).asText();
		switch (GroupAction.valueOf(action)) {
		case SESSION:
			return handleGroupActionSessionPatch(groupResultId, studyResultId,
					groupRegistry, jsonNode);
		case FIXED:
			return handleActionFix(groupResultId, studyResultId, groupRegistry,
					jsonNode);
		default:
			String errorMsg = "Unknown action " + action;
			GroupActionMsg msg = groupActionMsgBuilder.buildError(groupResultId,
					errorMsg, TellWhom.SENDER_ONLY);
			return GroupActionMsgBundle.build(msg);
		}
	}

	/**
	 * Persists GroupSession and tells everyone
	 */
	private GroupActionMsgBundle handleGroupActionSessionPatch(
			long groupResultId, long studyResultId, GroupRegistry groupRegistry,
			ObjectNode jsonNode) {
		return jpa.withTransaction(() -> {
			Long clientsVersion = Long.valueOf(jsonNode
					.get(GroupActionMsg.GROUP_SESSION_VERSION).asText());
			JsonNode groupSessionPatchNode = jsonNode
					.get(GroupActionMsg.GROUP_SESSION_PATCH);

			// Get group result from DB
			GroupResult groupResult = groupResultDao.findById(groupResultId);
			if (groupResult == null) {
				String errorMsg = "Couldn't find group result with ID "
						+ groupResultId + " in database.";
				GroupActionMsg msg = GroupActionMsgJsonBuilder
						.buildErrorActionMsg(groupResultId, errorMsg,
								TellWhom.SENDER_ONLY);
				return GroupActionMsgBundle.build(msg);
			}

			// Apply patch
			JsonNode patchedSessionData;
			try {
				patchedSessionData = patchGroupSessionData(groupResult,
						groupSessionPatchNode);
			} catch (IOException | JsonPatchException e) {
				LOGGER.debug(".handleActionGroupSessionPatch: ", e);
				GroupActionMsg msg = groupActionMsgBuilder.buildSimple(
						groupResult, GroupAction.SESSION_FAIL,
						TellWhom.SENDER_ONLY);
				return GroupActionMsgBundle.build(msg);
			}

			LOGGER.debug(
					".handleActionGroupSessionPatch:"
							+ " groupResultId {}, clientsVersion {},"
							+ " groupSessionPatch {}, updatedSessionData {}",
					groupResultId, clientsVersion,
					Json.stringify(groupSessionPatchNode),
					Json.stringify(patchedSessionData));

			boolean success = checkVersionAndPersistGroupSessionData(
					groupResult, clientsVersion, patchedSessionData);
			if (success) {
				GroupActionMsg msg1 = groupActionMsgBuilder.buildSessionPatch(
						groupResult, studyResultId, groupSessionPatchNode,
						TellWhom.ALL_BUT_SENDER);
				GroupActionMsg msg2 = groupActionMsgBuilder.buildSimple(
						groupResult, GroupAction.SESSION_ACK,
						TellWhom.SENDER_ONLY);
				return GroupActionMsgBundle.build(msg1, msg2);
			} else {
				GroupActionMsg msg = groupActionMsgBuilder.buildSimple(
						groupResult, GroupAction.SESSION_FAIL,
						TellWhom.SENDER_ONLY);
				return GroupActionMsgBundle.build(msg);
			}
		});
	}

	private JsonNode patchGroupSessionData(GroupResult groupResult,
			JsonNode groupSessionPatchNode)
			throws IOException, JsonPatchException {
		JsonPatch groupSessionPatch = JsonPatch.fromJson(groupSessionPatchNode);
		JsonNode currentGroupSessionData = Json.mapper()
				.readTree(groupResult.getGroupSessionData());
		return groupSessionPatch.apply(currentGroupSessionData);
	}

	/**
	 * Persists the given sessionData in the GroupResult and increases the
	 * groupSessionVersion by 1 - but only if the stored version is equal to the
	 * received one. Returns true if this was successful - otherwise false.
	 */
	private boolean checkVersionAndPersistGroupSessionData(
			GroupResult groupResult, Long version, JsonNode sessionData) {
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
	 * Persists the changes in the GroupResult.
	 */
	private void updateGroupResult(GroupResult groupResult) {
		jpa.withTransaction(() -> {
			groupResultDao.update(groupResult);
		});
	}

	/**
	 * Changes state of GroupResult to FIXED and sends an update to all group
	 * members
	 */
	private GroupActionMsgBundle handleActionFix(long groupResultId,
			long studyResultId, GroupRegistry groupRegistry,
			ObjectNode jsonNode) {
		return jpa.withTransaction(() -> {
			GroupResult groupResult = groupResultDao.findById(groupResultId);
			if (groupResult != null) {
				groupResult.setGroupState(GroupState.FIXED);
				groupResultDao.update(groupResult);
				GroupActionMsg msg = groupActionMsgBuilder.build(groupResultId,
						studyResultId, groupRegistry, GroupAction.FIXED,
						TellWhom.ALL);
				return GroupActionMsgBundle.build(msg);
			} else {
				String errorMsg = "Couldn't find group result with ID "
						+ groupResultId + " in database.";
				GroupActionMsg msg = groupActionMsgBuilder.buildError(
						groupResultId, errorMsg, TellWhom.SENDER_ONLY);
				return GroupActionMsgBundle.build(msg);
			}
		});
	}

}
