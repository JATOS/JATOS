package session.batch.akka.actors;

import java.io.IOException;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.JsonPatchException;

import daos.common.BatchDao;
import models.common.Batch;
import play.Logger;
import play.Logger.ALogger;
import play.db.jpa.JPAApi;
import play.libs.Json;
import session.Registry;
import session.batch.akka.actors.BatchDispatcherProtocol.BatchActionMsg;
import session.batch.akka.actors.BatchDispatcherProtocol.BatchActionMsg.BatchAction;
import session.batch.akka.actors.BatchDispatcherProtocol.BatchActionMsg.TellWhom;

/**
 * @author Kristian Lange (2017)
 */
@Singleton
public class BatchActionHandler {

	private static final ALogger LOGGER = Logger.of(BatchActionHandler.class);

	private final JPAApi jpa;

	private final BatchDao batchDao;

	private final BatchActionMsgBuilder batchActionMsgBuilder;

	@Inject
	public BatchActionHandler(JPAApi jpa, BatchDao batchDao,
			BatchActionMsgBuilder batchActionMsgBuilder) {
		this.jpa = jpa;
		this.batchDao = batchDao;
		this.batchActionMsgBuilder = batchActionMsgBuilder;
	}

	/**
	 * Handles batch actions originating from a client
	 */
	public BatchActionMsgBundle handleBatchActionMsg(long batchId,
			long studyResultId, Registry batchRegistry, ObjectNode jsonNode) {
		LOGGER.debug(
				".handleBatchActionMsg:"
						+ " batchId {}, studyResultId {}, jsonNode {}",
				batchId, studyResultId, Json.stringify(jsonNode));
		String action = jsonNode.get(BatchActionMsg.ACTION).asText();
		switch (BatchAction.valueOf(action)) {
		case SESSION:
			return handleBatchActionSessionPatch(batchId, studyResultId,
					batchRegistry, jsonNode);
		default:
			String errorMsg = "Unknown action " + action;
			BatchActionMsg msg = batchActionMsgBuilder.buildError(batchId,
					errorMsg, TellWhom.SENDER_ONLY);
			return BatchActionMsgBundle.build(msg);
		}
	}

	/**
	 * Persists batch session and tells everyone
	 */
	private BatchActionMsgBundle handleBatchActionSessionPatch(long batchId,
			long studyResultId, Registry batchRegistry, ObjectNode jsonNode) {
		return jpa.withTransaction(() -> {
			Long clientsVersion = Long.valueOf(jsonNode
					.get(BatchActionMsg.BATCH_SESSION_VERSION).asText());
			JsonNode batchSessionPatchNode = jsonNode
					.get(BatchActionMsg.BATCH_SESSION_PATCH);

			// Get batch from DB
			Batch batch = batchDao.findById(batchId);
			if (batch == null) {
				String errorMsg = "Couldn't find batch with ID " + batchId
						+ " in database.";
				BatchActionMsg msg = batchActionMsgBuilder.buildError(batchId,
						errorMsg, TellWhom.SENDER_ONLY);
				return BatchActionMsgBundle.build(msg);
			}

			// Apply patch
			JsonNode patchedSessionData;
			try {
				patchedSessionData = patchBatchSessionData(batch,
						batchSessionPatchNode);
			} catch (IOException | JsonPatchException e) {
				LOGGER.debug(".handleBatchActionSessionPatch: ", e);
				BatchActionMsg msg = batchActionMsgBuilder.buildSimple(batch,
						BatchAction.SESSION_FAIL, TellWhom.SENDER_ONLY);
				return BatchActionMsgBundle.build(msg);
			}

			LOGGER.debug(
					".handleBatchActionSessionPatch:"
							+ " batchId {}, clientsVersion {},"
							+ " batchSessionPatch {}, updatedSessionData {}",
					batchId, clientsVersion,
					Json.stringify(batchSessionPatchNode),
					Json.stringify(patchedSessionData));

			boolean success = checkVersionAndPersistBatchSessionData(batch,
					clientsVersion, patchedSessionData);
			if (success) {
				BatchActionMsg msg1 = batchActionMsgBuilder.buildSessionPatch(
						batch, batchSessionPatchNode,
						TellWhom.ALL_BUT_SENDER);
				BatchActionMsg msg2 = batchActionMsgBuilder.buildSimple(batch,
						BatchAction.SESSION_ACK, TellWhom.SENDER_ONLY);
				return BatchActionMsgBundle.build(msg1, msg2);
			} else {
				BatchActionMsg msg = batchActionMsgBuilder.buildSimple(batch,
						BatchAction.SESSION_FAIL, TellWhom.SENDER_ONLY);
				return BatchActionMsgBundle.build(msg);
			}
		});
	}

	private JsonNode patchBatchSessionData(Batch batch,
			JsonNode batchSessionPatchNode)
			throws IOException, JsonPatchException {
		JsonPatch batchSessionPatch = JsonPatch.fromJson(batchSessionPatchNode);
		JsonNode currentBatchSessionData = Json.mapper()
				.readTree(batch.getBatchSessionData());
		return batchSessionPatch.apply(currentBatchSessionData);
	}

	/**
	 * Persists the given sessionData in the Batch and does the versioning: and
	 * increases the batchSessionVersion by 1 - but only if the stored version
	 * is equal to the received one. Returns true if this was successful -
	 * otherwise false.
	 */
	private boolean checkVersionAndPersistBatchSessionData(Batch batch,
			Long version, JsonNode sessionData) {
		if (batch != null && version != null && sessionData != null
				&& batch.getBatchSessionVersion().equals(version)) {
			batch.setBatchSessionData(sessionData.toString());
			long newVersion = batch.getBatchSessionVersion() + 1l;
			batch.setBatchSessionVersion(newVersion);
			updateBatch(batch);
			return true;
		}
		return false;
	}

	/**
	 * Persists the changes in the batch.
	 */
	private void updateBatch(Batch batch) {
		jpa.withTransaction(() -> {
			batchDao.update(batch);
		});
	}

}