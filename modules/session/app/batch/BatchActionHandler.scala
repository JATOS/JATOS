package batch

import javax.inject.{Inject, Singleton}

import batch.BatchDispatcher.{BatchAction, BatchActionJsonKey, BatchMsg, TellWhom}
import com.google.common.base.Strings
import daos.common.BatchDao
import gnieh.diffson.playJson._
import models.common.Batch
import play.api.Logger
import play.api.libs.json.{JsObject, JsValue, Json}
import play.db.jpa.JPAApi

import scala.compat.java8.FunctionConverters.asJavaSupplier

/**
  * Handles batch action messages received by a BatchDispatcher from a client via a batch channel.
  *
  * @author Kristian Lange (2017)
  */
@Singleton
class BatchActionHandler @Inject()(jpa: JPAApi,
                                   batchDao: BatchDao,
                                   msgBuilder: BatchActionMsgBuilder) {

  private val logger: Logger = Logger(this.getClass)

  /**
    * Handles batch action messages originating from a client: Gets a BatchMsg that contains a field
    * 'action' in their JSON. The only action handled here is the a patch for the batch session.
    * The function returns BatchMsges that will be send out to the batch members.
    */
  def handleActionMsg(actionMsg: BatchMsg, batchId: Long): List[BatchMsg] = {
    val actionValue = (actionMsg.json \ BatchActionJsonKey.Action.toString).as[String]
    val action = BatchAction.withName(actionValue)
    action match {
      case BatchAction.Session => handlePatch(actionMsg.json, batchId)
      case _ =>
        List(msgBuilder.buildError(s"Unknown action $action", TellWhom.SenderOnly))
    }
  }

  /**
    * Applies JSON Patch for the batch session and tells everyone in the batch
    */
  private def handlePatch(json: JsObject, batchId: Long): List[BatchMsg] = {
    jpa.withTransaction(asJavaSupplier(() => {
      val batch = batchDao.findById(batchId)
      if (batch == null) {
        val errorMsg = s"Couldn't find batch with ID $batchId in database."
        List(msgBuilder.buildError(errorMsg, TellWhom.SenderOnly))
      }

      try {
        val clientsVersion = (json \ BatchActionJsonKey.SessionVersion.toString).as[Long]
        val patch = (json \ BatchActionJsonKey.SessionPatches.toString).get
        val patchedSessionData = patchSessionData(patch, batch)
        logger.debug(s".handlePatch: batchId $batchId, " +
          s"clientsVersion $clientsVersion, batchSessionPatch ${Json.stringify(patch)}, " +
          s"updatedSessionData ${Json.stringify(patchedSessionData)}")

        val success = checkVersionAndPersistSessionData(patchedSessionData, batch, clientsVersion)
        if (success) {
          val msg1 = msgBuilder.buildSessionPatch(batch, patch, TellWhom.All)
          val msg2 = msgBuilder.buildSimple(batch, BatchAction.SessionAck, TellWhom.SenderOnly)
          List(msg1, msg2)
        } else
          List(msgBuilder.buildSimple(batch, BatchAction.SessionFail, TellWhom.SenderOnly))

      } catch {
        case e: Exception =>
          logger.warn(s".handlePatch: batchId $batchId, json ${Json.stringify(json)}, " +
            s"${e.getClass.getName}: ${e.getMessage}")
          List(msgBuilder.buildSimple(batch, BatchAction.SessionFail, TellWhom.SenderOnly))
      }
    }))
  }

  private def patchSessionData(patch: JsValue, batch: Batch): JsValue = {
    val currentSessionData =
      if (!Strings.isNullOrEmpty(batch.getBatchSessionData))
        Json.parse(batch.getBatchSessionData)
      else Json.obj()
    JsonPatch.apply(patch)(currentSessionData)
  }

  /**
    * Persists the given sessionData in the Batch and increases
    * the batchSessionVersion by 1 - but only if the stored version
    * is equal to the received one. Returns true if this was successful -
    * otherwise false.
    */
  private def checkVersionAndPersistSessionData(sessionData: JsValue, batch: Batch,
                                                version: Long): Boolean = {
    if (batch != null && sessionData != null && batch.getBatchSessionVersion == version) {
      batch.setBatchSessionData(sessionData.toString)
      batch.setBatchSessionVersion(batch.getBatchSessionVersion + 1l)
      batchDao.update(batch)
      return true
    }
    false
  }

}
