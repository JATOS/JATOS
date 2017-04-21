package session.batch.akka.actors.services;

import java.io.IOException;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.JsonPatchException;
import com.google.common.base.Strings;

import daos.common.BatchDao;
import models.common.Batch;
import play.Logger;
import play.Logger.ALogger;
import play.db.jpa.JPAApi;
import play.libs.Json;
import session.Registry;
import session.batch.akka.protocol.BatchDispatcherProtocol.BatchActionMsg;
import session.batch.akka.protocol.BatchDispatcherProtocol.BatchActionMsg.BatchAction;
import session.batch.akka.protocol.BatchDispatcherProtocol.BatchActionMsg.TellWhom;
import session.batch.akka.protocol.BatchDispatcherProtocol.BatchMsg;

/**
 * Handles batch action messages (BatchActionMsg) received by an BatchDispatcher
 * from a client via a batch channel.
 * 
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
	 * Handles batch action messages originating from a client: Gets a batch
	 * actions message and returns a BatchActionMsgBundle. The batch action
	 * messages in the BatchActionMsgBundle will be send by the BatchDispatcher
	 * to their receivers.
	 */
	public BatchActionMsgBundle handleBatchActionMsg(BatchMsg batchActionMsg,
			long batchId, long studyResultId, Registry batchRegistry) {
		ObjectNode batchActionMsgJson = batchActionMsg.jsonNode;
		LOGGER.debug(
				".handleBatchActionMsg:"
						+ " batchId {}, studyResultId {}, jsonNode {}",
				batchId, studyResultId, Json.stringify(batchActionMsgJson));
		String action = batchActionMsgJson.get(BatchActionMsg.ACTION).asText();
		switch (BatchAction.valueOf(action)) {
		case SESSION:
			return handleBatchActionSessionPatch(batchActionMsgJson, batchId,
					studyResultId, batchRegistry);
		default:
			String errorMsg = "Unknown action " + action;
			BatchActionMsg msg = batchActionMsgBuilder.buildError(errorMsg,
					TellWhom.SENDER_ONLY);
			return BatchActionMsgBundle.build(msg);
		}
	}

	/**
	 * Persists batch session and tells everyone
	 */
	private BatchActionMsgBundle handleBatchActionSessionPatch(
			ObjectNode batchActionMsgJson, long batchId, long studyResultId,
			Registry batchRegistry) {
		return jpa.withTransaction(() -> {

			Batch batch = batchDao.findById(batchId);
			if (batch == null) {
				String errorMsg = "Couldn't find batch with ID " + batchId
						+ " in database.";
				BatchActionMsg msg = batchActionMsgBuilder.buildError(errorMsg,
						TellWhom.SENDER_ONLY);
				return BatchActionMsgBundle.build(msg);
			}

			Long clientsVersion;
			JsonNode batchSessionPatchNode;
			JsonNode patchedSessionData;
			try {
				clientsVersion = Long.valueOf(batchActionMsgJson
						.get(BatchActionMsg.BATCH_SESSION_VERSION).asText());
				batchSessionPatchNode = batchActionMsgJson
						.get(BatchActionMsg.BATCH_SESSION_PATCHES);
				patchedSessionData = patchBatchSessionData(
						batchSessionPatchNode, batch);
				LOGGER.debug(
						".handleBatchActionSessionPatch:"
								+ " batchId {}, clientsVersion {},"
								+ " batchSessionPatch {}, updatedSessionData {}",
						batchId, clientsVersion,
						Json.stringify(batchSessionPatchNode),
						Json.stringify(patchedSessionData));
			} catch (Exception e) {
				LOGGER.warn(
						".handleBatchActionSessionPatch:"
								+ " batchId {}, jsonNode {}, {}: {}",
						batchId, Json.stringify(batchActionMsgJson),
						e.getClass().getName(), e.getMessage());
				BatchActionMsg msg = batchActionMsgBuilder.buildSimple(batch,
						BatchAction.SESSION_FAIL, TellWhom.SENDER_ONLY);
				return BatchActionMsgBundle.build(msg);
			}

			boolean success = checkVersionAndPersistBatchSessionData(
					patchedSessionData, batch, clientsVersion);
			if (success) {
				BatchActionMsg msg1 = batchActionMsgBuilder.buildSessionPatch(
						batch, batchSessionPatchNode, TellWhom.ALL);
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

	private JsonNode patchBatchSessionData(JsonNode batchSessionPatchNode,
			Batch batch) throws IOException, JsonPatchException {
		JsonPatch batchSessionPatch = JsonPatch.fromJson(batchSessionPatchNode);
		JsonNode currentBatchSessionData;
		if (Strings.isNullOrEmpty(batch.getBatchSessionData())) {
			currentBatchSessionData = Json.mapper().createObjectNode();
		} else {
			currentBatchSessionData = Json.mapper()
					.readTree(batch.getBatchSessionData());
		}
		return batchSessionPatch.apply(currentBatchSessionData);
	}

	/**
	 * Persists the given sessionData in the Batch and does the versioning: and
	 * increases the batchSessionVersion by 1 - but only if the stored version
	 * is equal to the received one. Returns true if this was successful -
	 * otherwise false.
	 */
	private boolean checkVersionAndPersistBatchSessionData(JsonNode sessionData,
			Batch batch, Long version) {
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
