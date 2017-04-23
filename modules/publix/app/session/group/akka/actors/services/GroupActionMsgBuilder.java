package session.group.akka.actors.services;

import java.io.IOException;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Strings;

import daos.common.GroupResultDao;
import models.common.GroupResult;
import models.common.StudyResult;
import play.Logger;
import play.Logger.ALogger;
import play.db.jpa.JPAApi;
import play.libs.Json;
import session.Registry;
import session.group.akka.protocol.GroupDispatcherProtocol.GroupActionMsg;
import session.group.akka.protocol.GroupDispatcherProtocol.GroupActionMsg.GroupAction;
import session.group.akka.protocol.GroupDispatcherProtocol.GroupActionMsg.TellWhom;

/**
 * Utility class that builds GroupActionMsgs. So it mostly handles the JSON node
 * creation.
 * 
 * @author Kristian Lange (2017)
 */
@Singleton
public class GroupActionMsgBuilder {

	private static final ALogger LOGGER = Logger
			.of(GroupActionMsgBuilder.class);

	private final JPAApi jpa;

	private final GroupResultDao groupResultDao;

	@Inject
	public GroupActionMsgBuilder(JPAApi jpa, GroupResultDao groupResultDao) {
		this.jpa = jpa;
		this.groupResultDao = groupResultDao;
	}

	/**
	 * Builds a simple GroupActionMsg with the action, group result ID, and the
	 * session version
	 */
	public GroupActionMsg buildSimple(GroupResult groupResult,
			GroupAction action, TellWhom tellWhom) {
		LOGGER.debug(".buildSimple: groupResult {}", groupResult.getId());
		ObjectNode objectNode = Json.mapper().createObjectNode();
		objectNode.put(GroupActionMsg.ACTION, action.toString());
		objectNode.put(GroupActionMsg.GROUP_RESULT_ID, groupResult.getId());
		objectNode.put(GroupActionMsg.GROUP_STATE,
				groupResult.getGroupState().name());
		objectNode.put(GroupActionMsg.GROUP_SESSION_VERSION,
				groupResult.getGroupSessionVersion());
		return new GroupActionMsg(objectNode, tellWhom);
	}

	/**
	 * Creates a simple GroupActionMsg with an error message
	 */
	public GroupActionMsg buildError(long groupResultId, String errorMsg,
			TellWhom tellWhom) {
		ObjectNode objectNode = Json.mapper().createObjectNode();
		objectNode.put(GroupActionMsg.ACTION, GroupAction.ERROR.toString());
		objectNode.put(GroupActionMsg.ERROR_MSG, errorMsg);
		objectNode.put(GroupActionMsg.GROUP_RESULT_ID, groupResultId);
		return new GroupActionMsg(objectNode, tellWhom);
	}

	/**
	 * Builds a GroupActionMsg with the group session patch and version
	 */
	public GroupActionMsg buildSessionPatch(GroupResult groupResult,
			long studyResultId, JsonNode groupSessionPatchNode,
			TellWhom tellWhom) {
		LOGGER.debug(".buildSessionPatch: groupResultId {}, studyResultId {}",
				groupResult.getId(), studyResultId);
		ObjectNode objectNode = Json.mapper().createObjectNode();
		objectNode.put(GroupActionMsg.ACTION, GroupAction.SESSION.toString());
		objectNode.set(GroupActionMsg.GROUP_SESSION_PATCHES,
				groupSessionPatchNode);
		objectNode.put(GroupActionMsg.GROUP_SESSION_VERSION,
				groupResult.getGroupSessionVersion());
		return new GroupActionMsg(objectNode, tellWhom);
	}

	/**
	 * Builds a GroupActionMsg with the current group session data and version
	 */
	public GroupActionMsg buildSessionData(long groupResultId,
			long studyResultId, Registry groupRegistry, GroupAction action,
			TellWhom tellWhom) {
		LOGGER.debug(
				".buildSessionData: groupResultId {}, studyResultId {}, action {}, tellWhom {}",
				groupResultId, studyResultId, action, tellWhom.name());
		return jpa.withTransaction(() -> {
			GroupResult groupResult = groupResultDao.findById(groupResultId);
			if (groupResult != null) {
				return buildActionMsg(groupResult, studyResultId, groupRegistry,
						true, action, tellWhom);
			} else {
				String errorMsg = "Couldn't find group result with ID "
						+ groupResultId + " in database.";
				return buildError(groupResultId, errorMsg,
						TellWhom.SENDER_ONLY);
			}
		});
	}

	/**
	 * Builds a GroupActionMsg without session data or patches but the session
	 * version
	 */
	public GroupActionMsg build(long groupResultId, long studyResultId,
			Registry groupRegistry, GroupAction action, TellWhom tellWhom) {
		// The current group data are persisted in a GroupResult entity. The
		// GroupResult determines who is member of the group - and not
		// the group registry.
		LOGGER.debug(
				".build: groupResultId {}, studyResultId {}, action {}, tellWhom {}",
				groupResultId, studyResultId, action, tellWhom.name());
		return jpa.withTransaction(() -> {
			GroupResult groupResult = groupResultDao.findById(groupResultId);
			if (groupResult != null) {
				return buildActionMsg(groupResult, studyResultId, groupRegistry,
						false, action, tellWhom);
			} else {
				String errorMsg = "Couldn't find group result with ID "
						+ groupResultId + " in database.";
				return buildError(groupResultId, errorMsg,
						TellWhom.SENDER_ONLY);
			}
		});
	}

	private GroupActionMsg buildActionMsg(GroupResult groupResult,
			long studyResultId, Registry groupRegistry,
			boolean includeSessionData, GroupAction action, TellWhom tellWhom) {
		ObjectNode objectNode = Json.mapper().createObjectNode();
		objectNode.put(GroupActionMsg.ACTION, action.toString());
		objectNode.put(GroupActionMsg.MEMBER_ID, studyResultId);
		objectNode.put(GroupActionMsg.GROUP_RESULT_ID, groupResult.getId());
		objectNode.put(GroupActionMsg.GROUP_STATE,
				groupResult.getGroupState().name());
		ArrayNode members = Json.mapper().createArrayNode();
		for (StudyResult studyResult : groupResult.getActiveMemberList()) {
			members.add(String.valueOf(studyResult.getId()));
		}
		objectNode.set(GroupActionMsg.MEMBERS, members);
		ArrayNode channels = Json.mapper().createArrayNode();
		for (Long id : groupRegistry.getAllStudyResultIds()) {
			channels.add(String.valueOf(id));
		}
		objectNode.set(GroupActionMsg.CHANNELS, channels);
		if (includeSessionData) {
			objectNode.set(GroupActionMsg.GROUP_SESSION_DATA,
					getSessionData(groupResult));
		}
		objectNode.put(GroupActionMsg.GROUP_SESSION_VERSION,
				groupResult.getGroupSessionVersion());
		return new GroupActionMsg(objectNode, tellWhom);
	}

	private JsonNode getSessionData(GroupResult groupResult) {
		try {
			if (Strings.isNullOrEmpty(groupResult.getGroupSessionData())) {
				return Json.mapper().createObjectNode();
			} else {
				return Json.mapper()
						.readTree(groupResult.getGroupSessionData());
			}
		} catch (IOException e) {
			LOGGER.error(
					".getSessionData: invalid session data in DB -"
							+ " groupResultId {}, groupSessionVersion {},"
							+ " groupSessionData {}, error: {}",
					groupResult.getId(), groupResult.getGroupSessionVersion(),
					groupResult.getGroupSessionData(), e.getMessage());
			return Json.mapper().createObjectNode();
		}
	}

}
