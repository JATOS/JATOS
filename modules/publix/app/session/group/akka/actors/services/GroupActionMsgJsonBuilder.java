package services.publix.group.akka.actors.services;

import java.io.IOException;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Strings;

import models.common.GroupResult;
import models.common.StudyResult;
import play.Logger;
import play.Logger.ALogger;
import play.libs.Json;
import services.publix.group.akka.protocol.GroupDispatcherProtocol.GroupActionMsg;
import services.publix.group.akka.protocol.GroupDispatcherProtocol.GroupActionMsg.BatchAction;
import services.publix.group.akka.protocol.GroupDispatcherProtocol.GroupActionMsg.TellWhom;

/**
 * Helper class with some methods that all create an GroupActionMsg.
 * 
 * @author Kristian Lange (2016)
 */
public class GroupActionMsgJsonBuilder {

	private static final ALogger LOGGER = Logger
			.of(GroupActionMsgJsonBuilder.class);

	/**
	 * Creates a GroupActionMsg. The GroupActionMsg includes a whole bunch of
	 * data including the action, all currently open channels, and the group
	 * session version - but not the group session data.
	 * 
	 * @param studyResultId
	 *            Which group member initiated this action
	 * @param action
	 *            The action of the GroupActionMsg
	 * @param GroupResult
	 *            The GroupResult of this group
	 */
	public static GroupActionMsg buildActionMsg(GroupResult groupResult,
			long studyResultId, Set<Long> studyResultIdSet, BatchAction action,
			TellWhom tellWhom) {
		return buildActionMsg(groupResult, studyResultId, studyResultIdSet,
				false, action, tellWhom);
	}

	/**
	 * Creates a GroupActionMsg. The GroupActionMsg includes a whole bunch of
	 * data including the action, all currently open channels, the group session
	 * version, and the group session data.
	 * 
	 * @param studyResultId
	 *            Which group member initiated this action
	 * @param action
	 *            The action of the GroupActionMsg
	 * @param GroupResult
	 *            The GroupResult of this group
	 */
	public static GroupActionMsg buildActionMsgWithSession(
			GroupResult groupResult, long studyResultId,
			Set<Long> studyResultIdSet, BatchAction action, TellWhom tellWhom) {
		return buildActionMsg(groupResult, studyResultId, studyResultIdSet,
				true, action, tellWhom);
	}

	private static GroupActionMsg buildActionMsg(GroupResult groupResult,
			long studyResultId, Set<Long> studyResultIdSet, boolean sessionData,
			BatchAction action, TellWhom tellWhom) {
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
		for (Long id : studyResultIdSet) {
			channels.add(String.valueOf(id));
		}
		objectNode.set(GroupActionMsg.CHANNELS, channels);
		if (sessionData) {
			objectNode.set(GroupActionMsg.GROUP_SESSION_DATA,
					getSessionData(groupResult));
		}
		objectNode.put(GroupActionMsg.GROUP_SESSION_VERSION,
				groupResult.getGroupSessionVersion());
		return new GroupActionMsg(objectNode, tellWhom);
	}

	private static JsonNode getSessionData(GroupResult groupResult) {
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

	/**
	 * Creates a SESSION group action message filled with the session data and
	 * version of the given GroupResult.
	 * 
	 * @param studyResultId
	 *            Which group member initiated this action
	 * @param GroupResult
	 *            The GroupResult of this group
	 */
	public static GroupActionMsg buildSessionPatchActionMsg(
			GroupResult groupResult, long studyResultId,
			JsonNode groupSessionPatchNode, TellWhom tellWhom) {
		ObjectNode objectNode = Json.mapper().createObjectNode();
		objectNode.put(GroupActionMsg.ACTION, BatchAction.SESSION.toString());
		objectNode.set(GroupActionMsg.GROUP_SESSION_PATCHES,
				groupSessionPatchNode);
		objectNode.put(GroupActionMsg.GROUP_SESSION_VERSION,
				groupResult.getGroupSessionVersion());
		return new GroupActionMsg(objectNode, tellWhom);
	}

	/**
	 * Creates a simple group action message with only the given action and the
	 * group result ID.
	 * 
	 * @param studyResultId
	 *            Which group member initiated this action
	 * @param GroupResult
	 *            The GroupResult of this group
	 */
	public static GroupActionMsg buildSimpleActionMsg(GroupResult groupResult,
			BatchAction action, TellWhom tellWhom) {
		ObjectNode objectNode = Json.mapper().createObjectNode();
		objectNode.put(GroupActionMsg.ACTION, action.toString());
		objectNode.put(GroupActionMsg.GROUP_RESULT_ID, groupResult.getId());
		objectNode.put(GroupActionMsg.GROUP_SESSION_VERSION,
				groupResult.getGroupSessionVersion());
		return new GroupActionMsg(objectNode, tellWhom);
	}

	/**
	 * Creates an ERROR group action message.
	 */
	public static GroupActionMsg buildErrorActionMsg(long groupResultId,
			String errorMsg, TellWhom tellWhom) {
		ObjectNode objectNode = Json.mapper().createObjectNode();
		objectNode.put(GroupActionMsg.ACTION, BatchAction.ERROR.toString());
		objectNode.put(GroupActionMsg.ERROR_MSG, errorMsg);
		objectNode.put(GroupActionMsg.GROUP_RESULT_ID, groupResultId);
		return new GroupActionMsg(objectNode, tellWhom);
	}

}