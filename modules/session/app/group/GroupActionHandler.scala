package group

import com.google.common.base.Strings
import daos.common.GroupResultDao
import diffson.jsonpatch._
import diffson.playJson.DiffsonProtocol._
import group.GroupDispatcher.{GroupAction, GroupActionJsonKey, GroupMsg, TellWhom}
import models.common.GroupResult
import models.common.GroupResult.GroupState
import play.api.Logger
import play.api.libs.json.{JsObject, JsValue, Json}
import play.db.jpa.JPAApi

import javax.inject.{Inject, Singleton}
import scala.compat.java8.FunctionConverters.asJavaSupplier
import scala.util.Try

/**
  * Handles group action messages. Those messages are of type GroupMsg with a JSON object that
  * contains an 'action' field. It was received by a GroupDispatcher and comes from a client via
  * a GroupChannelActor.
  *
  * @author Kristian Lange (2017)
  */
//noinspection ScalaDeprecation
@Singleton
class GroupActionHandler @Inject()(jpa: JPAApi,
                                   groupResultDao: GroupResultDao,
                                   msgBuilder: GroupActionMsgBuilder) {

  private val logger: Logger = Logger(this.getClass)

  /**
    * Handles group actions originating from a client: Gets a GroupMsg that contains a field
    * 'action' in their JSON. The only action handled here is 1) the patch for the group
    * session, or 2) the msg to fix the group. The function returns GroupMsges that will be sent
    * out to the group members.
    */
  def handleActionMsg(msg: GroupMsg, groupResultId: Long, studyResultId: Long): List[GroupMsg] = {
    logger.debug(s".handleActionMsg: groupResultId $groupResultId, studyResultId $studyResultId, " +
      s"jsonNode ${Json.stringify(msg.json)}")
    val actionValue = (msg.json \ GroupActionJsonKey.Action.toString).as[String]
    val action = GroupAction.withName(actionValue)
    action match {
      case GroupAction.Session => handlePatch(msg.json, groupResultId, studyResultId)
      case GroupAction.Fixed => handleActionFix(groupResultId);
      case _ =>
        List(msgBuilder.buildError(groupResultId, s"Unknown action $action", TellWhom.SenderOnly))
    }
  }

  /**
    * Applies the patch to the group session
    */
  private def handlePatch(json: JsObject, groupResultId: Long, studyResultId: Long): List[GroupMsg] = {
    jpa.withTransaction(asJavaSupplier(() => {
      val groupResult = groupResultDao.findById(groupResultId)
      if (groupResult == null) {
        val errorMsg = s"Couldn't find group result with ID $groupResultId in database."
        List(msgBuilder.buildError(groupResultId, errorMsg, TellWhom.SenderOnly))
      }

      val sessionActionId = (json \ GroupActionJsonKey.SessionActionId.toString).as[Long]
      val clientsVersion = (json \ GroupActionJsonKey.SessionVersion.toString).as[Long]
      val versioning = (json \ GroupActionJsonKey.SessionVersioning.toString).as[Boolean]
      try {
        val patches = (json \ GroupActionJsonKey.SessionPatches.toString).get
        val patchedSessionData = patchSessionData(patches, groupResult)
        logger.debug(s".handlePatch: groupResultId $groupResultId, " +
          s"clientsVersion $clientsVersion, versioning $versioning, groupSessionPatch ${Json.stringify(patches)}, " +
          s"updatedSessionData ${Json.stringify(patchedSessionData)}")

        val success = checkVersionAndPersistSessionData(patchedSessionData, groupResult, clientsVersion, versioning)
        if (success) {
          val msg1 = msgBuilder.buildSessionPatch(groupResult, studyResultId, patches, TellWhom.All)
          val msg2 = msgBuilder.buildSimple(groupResult, GroupAction.SessionAck, Some(sessionActionId), TellWhom.SenderOnly)
          List(msg1, msg2)
        } else {
          List(msgBuilder.buildSimple(groupResult, GroupAction.SessionFail, Some(sessionActionId), TellWhom.SenderOnly))
        }
      } catch {
        case e: Exception =>
          logger.warn(s".handlePatch: groupResultId $groupResultId, json ${Json.stringify(json)}," +
            s" ${e.getClass.getName}: ${e.getMessage}")
          List(msgBuilder.buildSimple(groupResult, GroupAction.SessionFail, Some(sessionActionId), TellWhom.SenderOnly))
      }
    }))
  }

  private def patchSessionData(patches: JsValue, groupResult: GroupResult): JsValue = {
    val currentSessionData =
      if (!Strings.isNullOrEmpty(groupResult.getGroupSessionData))
        Json.parse(groupResult.getGroupSessionData)
      else Json.obj()

    // Fix for gnieh.diffson JsonPatch for "remove" and "/" - clear session data
    // Assumes the 'remove' operation is in the first JSON patch
    if ((patches \ 0 \ "op").as[String] == "remove" && (patches \ 0 \ "path").as[String] == "/") {
      return Json.obj()
    }

    val patch = Json.parse(patches.toString()).as[JsonPatch[JsValue]]
    patch[Try](currentSessionData).get
  }

  /**
    * Persists the given sessionData in the GroupResult and increases the groupSessionVersion by 1 - but only if the
    * stored version is equal to the received one or versioning is turned off. Returns true if this was successful -
    * otherwise false.
    */
  private def checkVersionAndPersistSessionData(sessionData: JsValue, groupResult: GroupResult,
                                                version: Long,
                                                versioning: Boolean): Boolean = {
    if (groupResult != null && sessionData != null && (!versioning || groupResult.getGroupSessionVersion == version)) {
      groupResult.setGroupSessionData(sessionData.toString)
      groupResult.setGroupSessionVersion(groupResult.getGroupSessionVersion + 1L)
      groupResultDao.update(groupResult)
      return true
    }
    false
  }

  /**
    * Changes the state of GroupResult to FIXED and sends an update to all group
    * members
    */
  private def handleActionFix(groupResultId: Long) = {
    jpa.withTransaction(asJavaSupplier(() => {
      val groupResult = groupResultDao.findById(groupResultId)
      if (groupResult != null) {
        groupResult.setGroupState(GroupState.FIXED)
        groupResultDao.update(groupResult)
        List(msgBuilder.buildSimple(groupResult, GroupAction.Fixed, None, TellWhom.SenderOnly))
      } else {
        val errorMsg = s"Couldn't find group result with ID $groupResultId in database."
        List(msgBuilder.buildError(groupResultId, errorMsg, TellWhom.SenderOnly))
      }
    }))
  }

}
