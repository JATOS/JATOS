package services.publix.group.akka.actors;

import java.util.Set;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import models.common.GroupResult;
import models.common.StudyResult;
import services.publix.group.akka.messages.GroupDispatcherProtocol.GroupActionMsg;
import services.publix.group.akka.messages.GroupDispatcherProtocol.GroupActionMsg.GroupAction;
import utils.common.JsonUtils;

/**
 * Helper class with some methods that all create an GroupActionMsg.
 * 
 * @author Kristian Lange (2016)
 */
public class GroupActionMsgUtils {

	/**
	 * Creates a GroupActionMsg. The GroupActionMsg includes a whole bunch of
	 * data including the action, all currently open channels, the group session
	 * data and the group session version.
	 * 
	 * @param studyResultId
	 *            Which group member initiated this action
	 * @param action
	 *            The action of the GroupActionMsg
	 * @param GroupResult
	 *            The GroupResult of this group
	 */
	public static GroupActionMsg buildFullActionMsg(Long studyResultId,
			GroupAction action, GroupResult groupResult,
			Set<Long> studyResultIdSet) {
		ObjectNode objectNode = JsonUtils.OBJECTMAPPER.createObjectNode();
		objectNode.put(GroupActionMsg.ACTION, action.toString());
		if (studyResultId != null) {
			objectNode.put(GroupActionMsg.MEMBER_ID, studyResultId);
		}
		objectNode.put(GroupActionMsg.GROUP_RESULT_ID, groupResult.getId());
		objectNode.put(GroupActionMsg.GROUP_STATE,
				groupResult.getGroupState().name());
		ArrayNode members = JsonUtils.OBJECTMAPPER.createArrayNode();
		for (StudyResult studyResult : groupResult.getActiveMemberList()) {
			members.add(String.valueOf(studyResult.getId()));
		}
		objectNode.set(GroupActionMsg.MEMBERS, members);
		ArrayNode channels = JsonUtils.OBJECTMAPPER.createArrayNode();
		for (Long id : studyResultIdSet) {
			channels.add(String.valueOf(id));
		}
		objectNode.set(GroupActionMsg.CHANNELS, channels);
		objectNode.put(GroupActionMsg.GROUP_SESSION_DATA,
				groupResult.getGroupSessionData());
		objectNode.put(GroupActionMsg.GROUP_SESSION_VERSION,
				groupResult.getGroupSessionVersion());
		return new GroupActionMsg(objectNode);
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
	public static GroupActionMsg buildSessionActionMsg(Long studyResultId,
			GroupResult groupResult) {
		ObjectNode objectNode = JsonUtils.OBJECTMAPPER.createObjectNode();
		objectNode.put(GroupActionMsg.ACTION, GroupAction.SESSION.toString());
		objectNode.put(GroupActionMsg.GROUP_SESSION_DATA,
				groupResult.getGroupSessionData());
		objectNode.put(GroupActionMsg.GROUP_SESSION_VERSION,
				groupResult.getGroupSessionVersion());
		return new GroupActionMsg(objectNode);
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
	public static GroupActionMsg buildSimpleActionMsg(GroupAction action,
			Long groupResultId) {
		ObjectNode objectNode = JsonUtils.OBJECTMAPPER.createObjectNode();
		objectNode.put(GroupActionMsg.ACTION, action.toString());
		objectNode.put(GroupActionMsg.GROUP_RESULT_ID, groupResultId);
		return new GroupActionMsg(objectNode);
	}

	/**
	 * Creates an ERROR group action message.
	 */
	public static GroupActionMsg buildErrorActionMsg(String errorMsg,
			Long groupResultId) {
		ObjectNode objectNode = JsonUtils.OBJECTMAPPER.createObjectNode();
		objectNode.put(GroupActionMsg.ACTION, GroupAction.ERROR.toString());
		objectNode.put(GroupActionMsg.ERROR_MSG, errorMsg);
		objectNode.put(GroupActionMsg.GROUP_RESULT_ID, groupResultId);
		return new GroupActionMsg(objectNode);
	}

}