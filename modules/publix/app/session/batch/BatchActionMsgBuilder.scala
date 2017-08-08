package session.batch

import java.io.IOException
import javax.inject.{Inject, Singleton}

import com.fasterxml.jackson.databind.JsonNode
import com.google.common.base.Strings
import daos.common.BatchDao
import models.common.Batch
import play.api.Logger
import play.db.jpa.JPAApi
import play.libs.Json
import session.batch.BatchDispatcher.BatchAction.BatchAction
import session.batch.BatchDispatcher.TellWhom.TellWhom
import session.batch.BatchDispatcher.{BatchAction, BatchActionJsonKey, BatchActionMsg, TellWhom}

import scala.compat.java8.FunctionConverters.asJavaSupplier

/**
  * Utility class that builds BatchActionMsgs. So it mostly handles the JSON node
  * creation.
  *
  * @author Kristian Lange (2017)
  */
@Singleton
class BatchActionMsgBuilder @Inject()(jpa: JPAApi, batchDao: BatchDao) {

  private val logger: Logger = Logger(this.getClass)

  /**
    * Creates a simple BatchActionMsg with an error message
    */
  def buildError(errorMsg: String, tellWhom: TellWhom) = {
    val objectNode = Json.mapper.createObjectNode
    objectNode.put(BatchActionJsonKey.Action.toString, BatchAction.Error.toString)
    objectNode.put(BatchActionJsonKey.ErrorMsg.toString, errorMsg)
    BatchActionMsg(objectNode, tellWhom)
  }

  /**
    * Builds a simple BatchActionMsg with the action and the session version
    */
  def buildSimple(batch: Batch, action: BatchAction, tellWhom: TellWhom) = {
    logger.debug(s".buildSimple: batchId ${batch.getId}")
    val objectNode = Json.mapper.createObjectNode
    objectNode.put(BatchActionJsonKey.Action.toString, action.toString)
    objectNode.put(BatchActionJsonKey.SessionVersion.toString, batch.getBatchSessionVersion)
    BatchActionMsg(objectNode, tellWhom)
  }

  /**
    * Builds a BatchActionMessage with the batch session patch and version
    */
  def buildSessionPatch(batch: Batch, sessionPatchNode: JsonNode, tellWhom: TellWhom) = {
    logger.debug(s".buildSessionPatch: batchId ${batch.getId}")
    val objectNode = Json.mapper.createObjectNode
    objectNode.put(BatchActionJsonKey.Action.toString, BatchAction.Session.toString)
    objectNode.set(BatchActionJsonKey.SessionPatches.toString, sessionPatchNode)
    objectNode.put(BatchActionJsonKey.SessionVersion.toString, batch.getBatchSessionVersion)
    BatchActionMsg(objectNode, tellWhom)
  }

  /**
    * Builds a BatchActionMsg with the current batch session data and version
    */
  def buildSessionData(batchId: Long, action: BatchAction, tellWhom: TellWhom) = {
    jpa.withTransaction(asJavaSupplier(() => {
      logger.debug(s".buildSessionData: batchId $batchId, action $action, tellWhom ${tellWhom.toString}")
      val batch = batchDao.findById(batchId)
      if (batch != null) buildSessionActionMsg(batch, action, tellWhom)
      else buildError("Couldn't find batch with ID " + batchId + " in database.", TellWhom.SenderOnly)
    }))
  }

  private def buildSessionActionMsg(batch: Batch, action: BatchAction, tellWhom: TellWhom) = {
    val objectNode = Json.mapper.createObjectNode
    objectNode.put(BatchActionJsonKey.Action.toString, action.toString)
    try
        if (Strings.isNullOrEmpty(batch.getBatchSessionData))
          objectNode.set(BatchActionJsonKey.SessionData.toString,
            Json.mapper.createObjectNode)
        else objectNode.set(BatchActionJsonKey.SessionData.toString,
          Json.mapper.readTree(batch.getBatchSessionData))
    catch {
      case e: IOException =>
        logger.error(s".buildSessionActionMsg: invalid session data in DB - batchId ${batch.getId}, batchSessionVersion ${batch.getBatchSessionVersion}, batchSessionData ${batch.getBatchSessionData}, error: ${e.getMessage}")
        objectNode.set(BatchActionJsonKey.SessionData.toString, Json.mapper.createObjectNode)
    }
    objectNode.put(BatchActionJsonKey.SessionVersion.toString, batch.getBatchSessionVersion)
    BatchActionMsg(objectNode, tellWhom)
  }

}
