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
import play.db.jpa.JPAApi

import javax.inject.{Inject, Singleton}
import scala.jdk.javaapi.FunctionConverters.asJavaFunction
import scala.jdk.javaapi.FunctionConverters.asJavaSupplier
import scala.util.Try

/**
 * Handles batch action messages received by a BatchDispatcher from a client via a batch channel.
 */
@Singleton
class BatchActionHandler @Inject()(jpa: JPAApi,
                                   batchDao: BatchDao,
                                   msgBuilder: BatchActionMsgBuilder) {

  private val logger: Logger = Logger(this.getClass)

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

  /**
   * Applies JSON Patch for the batch session and tells everyone in the batch
   */
  private def handlePatch(json: JsObject, batchId: Long): List[BatchMsg] = {
    batchDao.withTransaction(asJavaFunction(_ => {
      val batch = batchDao.findById(batchId)
      if (batch == null) {
        val errorMsg = s"Couldn't find batch with ID $batchId in database."
        return List(msgBuilder.buildError(errorMsg, TellWhom.SenderOnly))
      }

      val sessionActionId = (json \ BatchActionJsonKey.SessionActionId.toString).as[Long]
      val clientsVersion = (json \ BatchActionJsonKey.SessionVersion.toString).as[Long]
      val versioning = (json \ BatchActionJsonKey.SessionVersioning.toString).as[Boolean]
      try {
        val patches = (json \ BatchActionJsonKey.SessionPatches.toString).get
        val patchedSessionData = patchSessionData(patches, batch)
        logger.debug(s".handlePatch: batchId $batchId, " +
          s"clientsVersion $clientsVersion, versioning $versioning, batchSessionPatch ${Json.stringify(patches)}, " +
          s"updatedSessionData ${Json.stringify(patchedSessionData)}")

        val success = checkVersionAndPersistSessionData(patchedSessionData, batch, clientsVersion, versioning)
        if (success) {
          val msg1 = msgBuilder.buildSessionPatch(batch, patches, TellWhom.All)
          val msg2 = msgBuilder.buildSimple(batch, BatchAction.SessionAck, sessionActionId, None, TellWhom.SenderOnly)
          List(msg1, msg2)
        } else {
          val errorMsg = s"Version mismatch or concurrent update conflict (client version: $clientsVersion, current: ${batch.getBatchSessionVersion})."
          List(msgBuilder.buildSimple(batch, BatchAction.SessionFail, sessionActionId, Some(errorMsg), TellWhom.SenderOnly))
        }

      } catch {
        case e: Exception =>
          logger.debug(s".handlePatch: batchId $batchId, json ${Json.stringify(json)}, " +
            s"${e.getClass.getName}: ${e.getMessage}")
          val errorMsg = s"Failed to apply patch: ${e.getMessage}"
          List(msgBuilder.buildSimple(batch, BatchAction.SessionFail, sessionActionId, Some(errorMsg), TellWhom.SenderOnly))
      }
    }))
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

  /**
   * Persists the given sessionData in the Batch and increases the batchSessionVersion by 1 - but only if the stored
   * version is equal to the received one or versioning is turned off. Returns true if this was successful -
   * otherwise false.
   */
  private def checkVersionAndPersistSessionData(sessionData: JsValue, batch: Batch,
                                                version: Long,
                                                versioning: Boolean): Boolean = {
    if (batch != null && sessionData != null && (!versioning || batch.getBatchSessionVersion == version)) {
      batch.setBatchSessionData(sessionData.toString)
      batch.setBatchSessionVersion(batch.getBatchSessionVersion + 1L)
      batchDao.merge(batch)
      return true
    }
    false
  }

}
