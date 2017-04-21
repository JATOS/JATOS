package services.publix.group.akka.actors.services;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.fasterxml.jackson.databind.JsonNode;

import daos.common.GroupResultDao;
import models.common.GroupResult;
import play.Logger;
import play.Logger.ALogger;
import play.db.jpa.JPAApi;
import services.publix.group.akka.protocol.GroupDispatcherProtocol.GroupActionMsg;
import services.publix.group.akka.protocol.GroupDispatcherProtocol.GroupActionMsg.BatchAction;
import services.publix.group.akka.protocol.GroupDispatcherProtocol.GroupActionMsg.TellWhom;
import session.Registry;

/**
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

	public GroupActionMsg buildSimple(GroupResult groupResult,
			BatchAction action, TellWhom tellWhom) {
		GroupActionMsg msg = GroupActionMsgJsonBuilder
				.buildSimpleActionMsg(groupResult, action, tellWhom);
		return msg;
	}

	public GroupActionMsg buildError(long groupResultId, String errorMsg,
			TellWhom tellWhom) {
		return GroupActionMsgJsonBuilder.buildErrorActionMsg(groupResultId,
				errorMsg, tellWhom);
	}

	/**
	 * Sends a group action message with the current group session data and
	 * version to all members.
	 */
	public GroupActionMsg buildSessionPatch(GroupResult groupResult,
			long studyResultId, JsonNode groupSessionPatchNode,
			TellWhom tellWhom) {
		LOGGER.debug(".buildSessionPatch: groupResultId {}, studyResultId {}",
				groupResult.getId(), studyResultId);
		return GroupActionMsgJsonBuilder.buildSessionPatchActionMsg(groupResult,
				studyResultId, groupSessionPatchNode, tellWhom);
	}

	/**
	 * TODO
	 */
	public GroupActionMsg build(long groupResultId, long studyResultId,
			Registry groupRegistry, BatchAction action, TellWhom tellWhom) {
		// The current group data are persisted in a GroupResult entity. The
		// GroupResult determines who is member of the group - and not
		// the group registry.
		LOGGER.debug(
				".build: groupResultId {}, studyResultId {}, action {}, tellWhom {}",
				groupResultId, studyResultId, action, tellWhom.name());
		return jpa.withTransaction(() -> {
			GroupResult groupResult = groupResultDao.findById(groupResultId);
			if (groupResult != null) {
				return GroupActionMsgJsonBuilder.buildActionMsg(groupResult,
						studyResultId, groupRegistry.getAllStudyResultIds(),
						action, tellWhom);
			} else {
				String errorMsg = "Couldn't find group result with ID "
						+ groupResultId + " in database.";
				return GroupActionMsgJsonBuilder.buildErrorActionMsg(
						groupResultId, errorMsg, TellWhom.SENDER_ONLY);
			}
		});
	}

	/**
	 * Sends a TODO
	 */
	public GroupActionMsg buildWithSession(long groupResultId,
			long studyResultId, Registry groupRegistry, BatchAction action,
			TellWhom tellWhom) {
		LOGGER.debug(
				".buildWithSession: groupResultId {}, studyResultId {}, action {}, tellWhom {}",
				groupResultId, studyResultId, action, tellWhom.name());
		return jpa.withTransaction(() -> {
			GroupResult groupResult = groupResultDao.findById(groupResultId);
			if (groupResult != null) {
				return GroupActionMsgJsonBuilder.buildActionMsgWithSession(
						groupResult, studyResultId,
						groupRegistry.getAllStudyResultIds(), action, tellWhom);
			} else {
				String errorMsg = "Couldn't find group result with ID "
						+ groupResultId + " in database.";
				return GroupActionMsgJsonBuilder.buildErrorActionMsg(
						groupResultId, errorMsg, TellWhom.SENDER_ONLY);
			}
		});
	}

}
