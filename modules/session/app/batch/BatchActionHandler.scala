package batch

import batch.BatchDispatcher.{BatchAction, BatchActionJsonKey, BatchMsg, TellWhom}
import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import com.flipkart.zjsonpatch.JsonPatch
import com.google.common.base.Strings
import daos.common.BatchDao
import models.common.Batch
import play.api.Logger
import play.api.libs.json.Reads._
import play.api.libs.json.{JsObject, JsValue, Json}

import javax.inject.{Inject, Singleton}
import scala.annotation.tailrec

/**
 * Handles batch action messages received by a BatchDispatcher from a client via a batch channel.
 */
@Singleton
class BatchActionHandler @Inject()(batchDao: BatchDao,
                                   msgBuilder: BatchActionMsgBuilder) {

  private val logger: Logger = Logger(this.getClass)

  private val maxUpdateAttempts = 5

  private val objectMapper = new ObjectMapper()

  /**
   * Handles batch action messages originating from a client: Gets a BatchMsg that contains a field
   * 'action' in their JSON. The only action handled here is the patch for the batch session.
   * The function returns BatchMsges that will be sent out to the batch members.
   */
  def handleActionMsg(actionMsg: BatchMsg, batchId: Long): List[BatchMsg] = {
    val actionValue = (actionMsg.json \ BatchActionJsonKey.Action.toString).as[String]
    val actionOpt = BatchAction.values.find(_.toString == actionValue)
    actionOpt match {
      case Some(BatchAction.Session) => handlePatch(actionMsg.json, batchId)
      case _ =>
        List(msgBuilder.buildError(s"Unknown action $actionValue", TellWhom.SenderOnly))
    }
  }

  private def handlePatch(json: JsObject, batchId: Long): List[BatchMsg] = {
    val sessionActionId = (json \ BatchActionJsonKey.SessionActionId.toString).as[Long]
    val clientsVersion = (json \ BatchActionJsonKey.SessionVersion.toString).as[Long]
    val versioning = (json \ BatchActionJsonKey.SessionVersioning.toString).as[Boolean]
    val patches = (json \ BatchActionJsonKey.SessionPatches.toString).get

    tryUpdate(
      batchId = batchId,
      sessionActionId = sessionActionId,
      clientsVersion = clientsVersion,
      versioning = versioning,
      patches = patches,
      attempt = 1
    )
  }

  /**
   * Attempts to update the batch session data for a specified batch. The update process verifies version compatibility
   * and applies the patches if possible. In case of update conflicts, retry logic is implemented based on the number of
   * attempts.
   *
   * @return a list of `BatchMsg` instances representing the results of the update process
   */
  @tailrec
  private def tryUpdate(batchId: Long, sessionActionId: Long, clientsVersion: Long, versioning: Boolean,
                        patches: JsValue, attempt: Int): List[BatchMsg] = {
    val batch = batchDao.findById(batchId)
    if (batch == null) {
      return List(msgBuilder.buildError(s"Couldn't find batch with ID $batchId in database.", TellWhom.SenderOnly))
    }

    val currentVersion = batch.getBatchSessionVersion

    // Reject a stale version before attempting to apply its patch.
    if (versioning && clientsVersion != currentVersion) {
      val errorMsg = s"Version mismatch (client version: $clientsVersion, current: $currentVersion)."
      return List(msgBuilder.buildSimple(batch, BatchAction.SessionFail, sessionActionId, Some(errorMsg), TellWhom.SenderOnly))
    }

    val patchedSessionData =
      try {
        patchSessionData(patches, batch)
      } catch {
        case e: Exception =>
          logger.debug(s".tryUpdate: batchId $batchId, patches ${Json.stringify(patches)}, " +
            s"${e.getClass.getName}: ${e.getMessage}")
          val errorMsg = s"Failed to apply patch: ${e.getMessage}"
          return List(msgBuilder.buildSimple(batch, BatchAction.SessionFail, sessionActionId, Some(errorMsg), TellWhom.SenderOnly))
      }

    logger.debug(s".tryUpdate: batchId $batchId, clientsVersion $clientsVersion, versioning $versioning, " +
      s"batchSessionPatch ${Json.stringify(patches)}, updatedSessionData ${Json.stringify(patchedSessionData)}")

    val newVersion = batchDao.updateBatchSession(batchId, currentVersion, Json.stringify(patchedSessionData))

    if (newVersion != null) {
      // `batch` is detached and only used to build the outgoing messages. Make it reflect what was successfully committed.
      batch.setBatchSessionData(Json.stringify(patchedSessionData))
      batch.setBatchSessionVersion(newVersion)

      val updateMsg = msgBuilder.buildSessionPatch(batch, patches, TellWhom.All)
      val acknowledgement = msgBuilder.buildSimple(batch, BatchAction.SessionAck, sessionActionId, None, TellWhom.SenderOnly)
      List(updateMsg, acknowledgement)

    } else if (!versioning && attempt < maxUpdateAttempts) {
      // Another transaction won. Reload the latest state, reapply the patch to that state, and try another compare-and-set.
      tryUpdate(batchId, sessionActionId, clientsVersion, versioning, patches, attempt + 1)

    } else {
      // Either versioning was enabled, or all retry attempts were exhausted.
      val currentBatch = batchDao.findById(batchId)
      val actualVersion = Option(currentBatch).map(_.getBatchSessionVersion).getOrElse(currentVersion)

      val errorMsg =
        if (versioning)
          s"Concurrent update conflict (client version: $clientsVersion, current: $actualVersion)."
        else
          s"Couldn't update batch session after $maxUpdateAttempts attempts because of concurrent updates."
      val messageBatch = Option(currentBatch).getOrElse(batch)
      List(msgBuilder.buildSimple(messageBatch, BatchAction.SessionFail, sessionActionId, Some(errorMsg), TellWhom.SenderOnly))
    }
  }

  private def patchSessionData(patches: JsValue, batch: Batch): JsValue = {
    val currentSessionData =
      if (!Strings.isNullOrEmpty(batch.getBatchSessionData))
        Json.parse(batch.getBatchSessionData)
      else
        Json.obj()

    val sourceNode: JsonNode = objectMapper.readTree(Json.stringify(currentSessionData))
    val patchNode: JsonNode = objectMapper.readTree(Json.stringify(patches))

    val resultNode = JsonPatch.apply(patchNode, sourceNode)

    Json.parse(resultNode.toString)
  }

}
