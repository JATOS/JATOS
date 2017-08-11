package batch

import java.io.IOException
import javax.inject.{Inject, Singleton}

import com.google.common.base.Strings
import daos.common.BatchDao
import models.common.Batch
import play.api.Logger
import play.api.libs.json.{JsNumber, JsValue, Json}
import play.db.jpa.JPAApi
import batch.BatchDispatcher.BatchAction.BatchAction
import batch.BatchDispatcher.TellWhom.TellWhom
import batch.BatchDispatcher.{BatchAction, BatchActionJsonKey, BatchActionMsg, TellWhom}

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
    val json = Json.obj(
      BatchActionJsonKey.Action.toString -> BatchAction.Error.toString,
      BatchActionJsonKey.ErrorMsg.toString -> errorMsg)
    BatchActionMsg(json, tellWhom)
  }

  /**
    * Builds a simple BatchActionMsg with the action and the session version
    */
  def buildSimple(batch: Batch, action: BatchAction, tellWhom: TellWhom) = {
    logger.debug(s".buildSimple: batchId ${batch.getId}")
    val json = Json.obj(
      BatchActionJsonKey.Action.toString -> action.toString,
      BatchActionJsonKey.SessionVersion.toString -> JsNumber(BigDecimal(batch.getBatchSessionVersion)))
    BatchActionMsg(json, tellWhom)
  }

  /**
    * Builds a BatchActionMessage with the batch session patch and version
    */
  def buildSessionPatch(batch: Batch, patch: JsValue, tellWhom: TellWhom) = {
    logger.debug(s".buildSessionPatch: batchId ${batch.getId}")
    val json = Json.obj(
      BatchActionJsonKey.Action.toString -> BatchAction.Session.toString,
      BatchActionJsonKey.SessionPatches.toString -> patch,
      BatchActionJsonKey.SessionVersion.toString -> JsNumber(BigDecimal(batch.getBatchSessionVersion)))
    BatchActionMsg(json, tellWhom)
  }

  /**
    * Builds a BatchActionMsg with the current batch session data and version
    */
  def buildSessionData(batchId: Long, action: BatchAction, tellWhom: TellWhom) = {
    jpa.withTransaction(asJavaSupplier(() => {
      logger.debug(s".buildSessionData: batchId $batchId, action $action, tellWhom ${tellWhom.toString}")
      val batch = batchDao.findById(batchId)
      if (batch != null) buildSessionAction(batch, action, tellWhom)
      else buildError(s"Couldn't find batch with ID $batchId in database.", TellWhom.SenderOnly)
    }))
  }

  private def buildSessionAction(batch: Batch, action: BatchAction, tellWhom: TellWhom) = {
    val sessionData =
      try
          if (Strings.isNullOrEmpty(batch.getBatchSessionData))
            Json.obj()
          else
            Json.parse(batch.getBatchSessionData)
      catch {
        case e: IOException =>
          logger.error(s".buildSessionActionMsg: invalid session data in DB - batchId ${batch.getId}, batchSessionVersion ${batch.getBatchSessionVersion}, batchSessionData ${batch.getBatchSessionData}, error: ${e.getMessage}")
          Json.obj()
      }
    val json = Json.obj(
      BatchActionJsonKey.Action.toString -> action.toString,
      BatchActionJsonKey.SessionData.toString -> sessionData,
      BatchActionJsonKey.SessionVersion.toString -> JsNumber(BigDecimal(batch.getBatchSessionVersion)))
    BatchActionMsg(json, tellWhom)
  }

}
