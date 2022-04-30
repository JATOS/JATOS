package batch

import batch.BatchDispatcher.{BatchAction, BatchActionJsonKey, BatchMsg, TellWhom}
import cats.implicits._
import com.google.common.base.Strings
import daos.common.BatchDao
import diffson.jsonpatch._
import diffson.playJson.DiffsonProtocol._

import scala.util.Try
import models.common.Batch
import play.api.Logger
import play.api.libs.json.Reads._
import play.api.libs.json.{JsObject, JsValue, Json}
import play.db.jpa.JPAApi

import javax.inject.{Inject, Singleton}
import scala.compat.java8.FunctionConverters.asJavaSupplier

/**
  * Handles batch action messages received by a BatchDispatcher from a client via a batch channel.
  *
  * @author Kristian Lange
  */
//noinspection ScalaDeprecation
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
          val msg2 = msgBuilder.buildSimple(batch, BatchAction.SessionAck, sessionActionId, TellWhom.SenderOnly)
          List(msg1, msg2)
        } else {
          List(msgBuilder.buildSimple(batch, BatchAction.SessionFail, sessionActionId, TellWhom.SenderOnly))
        }

      } catch {
        case e: Exception =>
          logger.warn(s".handlePatch: batchId $batchId, json ${Json.stringify(json)}, " +
            s"${e.getClass.getName}: ${e.getMessage}")
          List(msgBuilder.buildSimple(batch, BatchAction.SessionFail, sessionActionId, TellWhom.SenderOnly))
      }
    }))
  }

  private def patchSessionData(patches: JsValue, batch: Batch): JsValue = {
    val currentSessionData =
      if (!Strings.isNullOrEmpty(batch.getBatchSessionData))
        Json.parse(batch.getBatchSessionData)
      else Json.obj()

    // Fix for gnieh.diffson JsonPatch for "remove" and "/" - clear all session data
    // Assumes the 'remove' operation is in the first JSON patch
    if ((patches \ 0 \ "op").as[String] == "remove" && (patches \ 0 \ "path").as[String] == "/") {
      return Json.obj()
    }

    val patch = Json.parse(patches.toString()).as[JsonPatch[JsValue]]
    patch[Try](currentSessionData).get
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
      batchDao.update(batch)
      return true
    }
    false
  }

}
