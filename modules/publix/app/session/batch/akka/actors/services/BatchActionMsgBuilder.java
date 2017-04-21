package session.batch.akka.actors.services;

import java.io.IOException;

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
import session.batch.akka.protocol.BatchDispatcherProtocol.BatchActionMsg;
import session.batch.akka.protocol.BatchDispatcherProtocol.BatchActionMsg.BatchAction;
import session.batch.akka.protocol.BatchDispatcherProtocol.BatchActionMsg.TellWhom;

/**
 * Utility class that builds BatchActionMsgs. So it mostly handles the JSON node
 * creation.
 * 
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

	/**
	 * Builds a simple BatchActionMsg with the action and the session version
	 */
	public BatchActionMsg buildSimple(Batch batch, BatchAction action,
			TellWhom tellWhom) {
		LOGGER.debug(".buildSimple: batchId {}", batch.getId());
		ObjectNode objectNode = Json.mapper().createObjectNode();
		objectNode.put(BatchActionMsg.ACTION, action.toString());
		objectNode.put(BatchActionMsg.BATCH_SESSION_VERSION,
				batch.getBatchSessionVersion());
		return new BatchActionMsg(objectNode, tellWhom);
	}

	/**
	 * Creates a simple BatchActionMsg with an error message
	 */
	public BatchActionMsg buildError(String errorMsg, TellWhom tellWhom) {
		ObjectNode objectNode = Json.mapper().createObjectNode();
		objectNode.put(BatchActionMsg.ACTION, BatchAction.ERROR.toString());
		objectNode.put(BatchActionMsg.ERROR_MSG, errorMsg);
		return new BatchActionMsg(objectNode, tellWhom);
	}

	/**
	 * Builds a BatchActionMessage with the batch session patch and version
	 */
	public BatchActionMsg buildSessionPatch(Batch batch,
			JsonNode batchSessionPatchNode, TellWhom tellWhom) {
		LOGGER.debug(".buildSessionPatch: batchId {}", batch.getId());
		ObjectNode objectNode = Json.mapper().createObjectNode();
		objectNode.put(BatchActionMsg.ACTION, BatchAction.SESSION.toString());
		objectNode.set(BatchActionMsg.BATCH_SESSION_PATCHES,
				batchSessionPatchNode);
		objectNode.put(BatchActionMsg.BATCH_SESSION_VERSION,
				batch.getBatchSessionVersion());
		return new BatchActionMsg(objectNode, tellWhom);
	}

	/**
	 * Builds a BatchActionMsg with the current batch session data and version
	 */
	public BatchActionMsg buildSessionData(long batchId, BatchAction action,
			TellWhom tellWhom) {
		LOGGER.debug(".buildSessionData: batchId {}, action {}, tellWhom {}",
				batchId, action, tellWhom.name());
		return jpa.withTransaction(() -> {
			Batch batch = batchDao.findById(batchId);
			if (batch != null) {
				return buildSessionActionMsg(batch, action, tellWhom);
			} else {
				String errorMsg = "Couldn't find batch with ID " + batchId
						+ " in database.";
				return buildError(errorMsg, TellWhom.SENDER_ONLY);
			}
		});
	}

	private BatchActionMsg buildSessionActionMsg(Batch batch,
			BatchAction action, TellWhom tellWhom) {
		ObjectNode objectNode = Json.mapper().createObjectNode();
		objectNode.put(BatchActionMsg.ACTION, action.toString());
		try {
			if (Strings.isNullOrEmpty(batch.getBatchSessionData())) {
				objectNode.set(BatchActionMsg.BATCH_SESSION_DATA,
						Json.mapper().createObjectNode());
			} else {
				objectNode.set(BatchActionMsg.BATCH_SESSION_DATA,
						Json.mapper().readTree(batch.getBatchSessionData()));
			}
		} catch (IOException e) {
			LOGGER.error(
					".buildSessionActionMsg: invalid session data in DB -"
							+ " batchId {}, batchSessionVersion {},"
							+ " batchSessionData {}, error: {}",
					batch.getId(), batch.getBatchSessionVersion(),
					batch.getBatchSessionData(), e.getMessage());
			objectNode.set(BatchActionMsg.BATCH_SESSION_DATA,
					Json.mapper().createObjectNode());
		}
		objectNode.put(BatchActionMsg.BATCH_SESSION_VERSION,
				batch.getBatchSessionVersion());
		return new BatchActionMsg(objectNode, tellWhom);
	}

}
