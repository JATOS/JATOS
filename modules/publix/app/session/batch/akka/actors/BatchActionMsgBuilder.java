package session.batch.akka.actors;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Strings;

import daos.common.BatchDao;
import models.common.Batch;
import play.Logger;
import play.Logger.ALogger;
import play.db.jpa.JPAApi;
import play.libs.Json;
import session.batch.akka.actors.BatchDispatcherProtocol.BatchActionMsg;
import session.batch.akka.actors.BatchDispatcherProtocol.BatchActionMsg.BatchAction;
import session.batch.akka.actors.BatchDispatcherProtocol.BatchActionMsg.TellWhom;

/**
 * @author Kristian Lange (2017)
 */
@Singleton
public class BatchActionMsgBuilder {

	private static final ALogger LOGGER = Logger
			.of(BatchActionMsgBuilder.class);

	private final JPAApi jpa;

	private final BatchDao batchDao;

	@Inject
	public BatchActionMsgBuilder(JPAApi jpa, BatchDao batchDao) {
		this.jpa = jpa;
		this.batchDao = batchDao;
	}

	public BatchActionMsg buildSimple(Batch batch, BatchAction action,
			TellWhom tellWhom) {
		ObjectNode objectNode = Json.mapper().createObjectNode();
		objectNode.put(BatchActionMsg.ACTION, action.toString());
		objectNode.put(BatchActionMsg.BATCH_SESSION_VERSION,
				batch.getBatchSessionVersion());
		return new BatchActionMsg(objectNode, tellWhom);
	}

	/**
	 * Sends a batch action message with the current batch session data and
	 * version to all members.
	 */
	public BatchActionMsg buildSessionPatch(Batch batch,
			JsonNode batchSessionPatchNode, TellWhom tellWhom) {
		LOGGER.debug(".buildSessionPatch: batchId {}, studyResultId {}",
				batch.getId());
		ObjectNode objectNode = Json.mapper().createObjectNode();
		objectNode.put(BatchActionMsg.ACTION, BatchAction.SESSION.toString());
		objectNode.set(BatchActionMsg.BATCH_SESSION_PATCHES,
				batchSessionPatchNode);
		objectNode.put(BatchActionMsg.BATCH_SESSION_VERSION,
				batch.getBatchSessionVersion());
		return new BatchActionMsg(objectNode, tellWhom);
	}

	/**
	 * Sends a TODO
	 */
	public BatchActionMsg buildWithSession(long batchId, BatchAction action,
			TellWhom tellWhom) {
		LOGGER.debug(".buildWithSession: batchId {}, action {}, tellWhom {}",
				batchId, action, tellWhom.name());
		return jpa.withTransaction(() -> {
			Batch batch = batchDao.findById(batchId);
			if (batch != null) {
				return buildSessionActionMsg(batch, action, tellWhom);
			} else {
				String errorMsg = "Couldn't find batch with ID " + batchId
						+ " in database.";
				return buildErrorActionMsg(errorMsg, TellWhom.SENDER_ONLY);
			}
		});
	}

	/**
	 * Creates a BatchActionMsg. The BatchActionMsg includes a whole bunch of
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
	private BatchActionMsg buildSessionActionMsg(Batch batch,
			BatchAction action, TellWhom tellWhom) {
		ObjectNode objectNode = Json.mapper().createObjectNode();
		objectNode.put(BatchActionMsg.ACTION, action.toString());
		if (Strings.isNullOrEmpty(batch.getBatchSessionData())) {
			objectNode.put(BatchActionMsg.BATCH_SESSION_DATA, "{}");
		} else {
			objectNode.put(BatchActionMsg.BATCH_SESSION_DATA,
					batch.getBatchSessionData());
		}
		objectNode.put(BatchActionMsg.BATCH_SESSION_VERSION,
				batch.getBatchSessionVersion());
		return new BatchActionMsg(objectNode, tellWhom);
	}

	public BatchActionMsg buildError(long batchId, String errorMsg,
			TellWhom tellWhom) {
		return buildErrorActionMsg(errorMsg, tellWhom);
	}

	/**
	 * Creates an ERROR group action message.
	 */
	private BatchActionMsg buildErrorActionMsg(String errorMsg,
			TellWhom tellWhom) {
		ObjectNode objectNode = Json.mapper().createObjectNode();
		objectNode.put(BatchActionMsg.ACTION, BatchAction.ERROR.toString());
		objectNode.put(BatchActionMsg.ERROR_MSG, errorMsg);
		return new BatchActionMsg(objectNode, tellWhom);
	}

}
