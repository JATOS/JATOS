package session.batch

import javax.inject.{Inject, Singleton}

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.{MissingNode, ObjectNode}
import com.github.fge.jsonpatch.JsonPatch
import com.google.common.base.Strings
import daos.common.BatchDao
import models.common.Batch
import play.api.Logger
import play.db.jpa.JPAApi
import play.libs.Json
import session.batch.BatchDispatcher.BatchAction.BatchAction
import session.batch.BatchDispatcher.{BatchAction, BatchActionJsonKey, BatchActionMsg, TellWhom}

import scala.compat.java8.FunctionConverters.asJavaSupplier

/**
  * Handles batch action messages (BatchActionMsg) received by an BatchDispatcher
  * from a client via a batch channel.
  *
  * @author Kristian Lange (2017)
  */
@Singleton
class BatchActionHandler @Inject()(jpa: JPAApi,
                                   batchDao: BatchDao,
                                   msgBuilder: BatchActionMsgBuilder) {

  private val logger: Logger = Logger(this.getClass)

  /**
    * Handles batch action messages originating from a client: Gets a batch
    * actions message and returns a BatchActionMsgBundle. The batch action
    * messages in the BatchActionMsgBundle will be send by the BatchDispatcher
    * to their receivers.
    */
  def handleActionMsg(actionMsg: BatchActionMsg,
                      batchId: Long): BatchActionMsgBundle = {
    val action: BatchAction = BatchAction.withName(
      actionMsg.jsonNode.get(BatchActionJsonKey.Action.toString).asText)
    action match {
      case BatchAction.Session =>
        handlePatch(actionMsg.jsonNode, batchId)
      case _ =>
        new BatchActionMsgBundle(msgBuilder.buildError(
          s"Unknown action $action", TellWhom.SenderOnly))
    }
  }

  /**
    * Persists batch session patch and tells everyone
    */
  def handlePatch(json: ObjectNode,
                  batchId: Long): BatchActionMsgBundle = {
    jpa.withTransaction(asJavaSupplier(() => {
      val batch = batchDao.findById(batchId)
      if (batch == null) {
        val errorMsg = s"Couldn't find batch with ID $batchId in database."
        val msg = msgBuilder.buildError(errorMsg, TellWhom.SenderOnly)
        new BatchActionMsgBundle(msg)
      }

      try {
        val clientsVersion = json.get(BatchActionJsonKey.SessionVersion.toString).asLong
        val patchNode = json.get(BatchActionJsonKey.SessionPatches.toString)
        val patchedSessionData = patchSessionData(patchNode, batch)
        logger.debug(s".handlePatch: batchId $batchId, " +
          s"clientsVersion $clientsVersion, " +
          s"batchSessionPatch ${Json.stringify(patchNode)}, " +
          s"updatedSessionData ${Json.stringify(patchedSessionData)}")

        val success = checkVersionAndPersistSessionData(patchedSessionData,
          batch, clientsVersion)
        if (success) {
          val msg1 = msgBuilder.buildSessionPatch(batch, patchNode,
            TellWhom.All)
          val msg2 = msgBuilder.buildSimple(batch, BatchAction.SessionAck,
            TellWhom.SenderOnly)
          new BatchActionMsgBundle(msg1, msg2)
        } else {
          new BatchActionMsgBundle(msgBuilder.buildSimple(batch,
            BatchAction.SessionFail, TellWhom.SenderOnly))
        }
      } catch {
        case e: Exception =>
          logger.warn(s".handlePatch: batchId $batchId, " +
            s"jsonNode ${Json.stringify(json)}, " +
            s"${e.getClass.getName}: ${e.getMessage}")
          new BatchActionMsgBundle(msgBuilder.buildSimple(batch,
            BatchAction.SessionFail, TellWhom.SenderOnly))
      }
    }))
  }

  private def patchSessionData(sessionPatchNode: JsonNode, batch: Batch) = {
    val sessionPatch = JsonPatch.fromJson(sessionPatchNode)
    val currentSessionData =
      if (!Strings.isNullOrEmpty(batch.getBatchSessionData))
        Json.mapper.readTree(batch.getBatchSessionData)
      else Json.mapper.createObjectNode
    sessionPatch.apply(currentSessionData)
  }

  /**
    * Persists the given sessionData in the Batch and does the versioning: and
    * increases the batchSessionVersion by 1 - but only if the stored version
    * is equal to the received one. Returns true if this was successful -
    * otherwise false.
    */
  private def checkVersionAndPersistSessionData(sessionData: JsonNode,
                                                batch: Batch,
                                                version: Long): Boolean = {
    if (batch != null && sessionData != null
      && batch.getBatchSessionVersion == version) {
      if (!sessionData.isInstanceOf[MissingNode])
        batch.setBatchSessionData(sessionData.toString)
      else batch.setBatchSessionData("{}")
      batch.setBatchSessionVersion(batch.getBatchSessionVersion + 1l)
      batchDao.update(batch)
      return true
    }
    false
  }

}
