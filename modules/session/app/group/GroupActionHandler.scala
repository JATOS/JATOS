package group

import javax.inject.{Inject, Singleton}

import com.google.common.base.Strings
import daos.common.GroupResultDao
import general.ChannelRegistry
import gnieh.diffson.playJson._
import group.GroupDispatcher.{GroupAction, GroupActionJsonKey, GroupMsg, TellWhom}
import models.common.GroupResult
import models.common.GroupResult.GroupState
import play.api.Logger
import play.api.libs.json.{JsObject, JsValue, Json}
import play.db.jpa.JPAApi

import scala.compat.java8.FunctionConverters.asJavaSupplier

/**
  * Handles group action messages. Those messages are of type GroupMsg with a JSON object that
  * contains an 'action' field. It was received by an GroupDispatcher and comes from a client via
  * a GroupChannelActor.
  *
  * @author Kristian Lange (2017)
  */
@Singleton
class GroupActionHandler @Inject()(jpa: JPAApi,
                                   groupResultDao: GroupResultDao,
                                   msgBuilder: GroupActionMsgBuilder) {

  private val logger: Logger = Logger(this.getClass)

  /**
    * Handles group actions originating from a client: Gets a GroupMsg that contains a field
    * 'action' in their JSON. The only action handled here are 1) the a patch for the group
    * session, or 2) the msg to fix the group. The function returns GroupMsges that will be send
    * out to the group members.
    */
  def handleActionMsg(msg: GroupMsg, groupResultId: Long, studyResultId: Long,
                      registry: ChannelRegistry): List[GroupMsg] = {
    logger.debug(s".handleActionMsg: groupResultId $groupResultId, studyResultId $studyResultId, " +
      s"jsonNode ${Json.stringify(msg.json)}")
    val actionValue = (msg.json \ GroupActionJsonKey.Action.toString).as[String]
    val action = GroupAction.withName(actionValue)
    action match {
      case GroupAction.Session => handlePatch(msg.json, groupResultId, studyResultId, registry)
      case GroupAction.Fixed => handleActionFix(groupResultId, studyResultId, registry);
      case _ =>
        List(msgBuilder.buildError(groupResultId, s"Unknown action $action", TellWhom.SenderOnly))
    }
  }

  /**
    * Applies the patch to the group session
    */
  private def handlePatch(json: JsObject, groupResultId: Long,
                          studyResultId: Long, registry: ChannelRegistry): List[GroupMsg] = {
    jpa.withTransaction(asJavaSupplier(() => {
      val groupResult = groupResultDao.findById(groupResultId)
      if (groupResult == null) {
        val errorMsg = s"Couldn't find group result with ID $groupResultId in database."
        List(msgBuilder.buildError(groupResultId, errorMsg, TellWhom.SenderOnly))
      }

      try {
        val clientsVersion = (json \ GroupActionJsonKey.SessionVersion.toString).as[Long]
        val patch = (json \ GroupActionJsonKey.SessionPatches.toString).get
        val patchedSessionData = patchSessionData(patch, groupResult)
        logger.debug(s".handlePatch: groupResultId $groupResultId, " +
          s"clientsVersion $clientsVersion, " +
          s"groupSessionPatch ${Json.stringify(patch)}, " +
          s"updatedSessionData ${Json.stringify(patchedSessionData)}")

        val success = checkVersionAndPersistSessionData(patchedSessionData, groupResult,
          clientsVersion)
        if (success) {
          val msg1 = msgBuilder.buildSessionPatch(groupResult, studyResultId, patch, TellWhom.All)
          val msg2 = msgBuilder.buildSimple(groupResult, GroupAction.SessionAck, TellWhom
            .SenderOnly)
          List(msg1, msg2)
        } else {
          List(msgBuilder.buildSimple(groupResult, GroupAction.SessionFail, TellWhom.SenderOnly))
        }
      } catch {
        case e: Exception =>
          logger.warn(s".handlePatch: groupResultId $groupResultId, json ${Json.stringify(json)}," +
            s" ${e.getClass.getName}: ${e.getMessage}")
          List(msgBuilder.buildSimple(groupResult, GroupAction.SessionFail, TellWhom.SenderOnly))
      }
    }))
  }

  private def patchSessionData(patch: JsValue, groupResult: GroupResult): JsValue = {
    val currentSessionData =
      if (!Strings.isNullOrEmpty(groupResult.getGroupSessionData))
        Json.parse(groupResult.getGroupSessionData)
      else Json.obj()
    JsonPatch.apply(patch)(currentSessionData)
  }

  /**
    * Persists the given sessionData in the GroupResult and increases the
    * groupSessionVersion by 1 - but only if the stored version is equal to the
    * received one. Returns true if this was successful - otherwise false.
    */
  private def checkVersionAndPersistSessionData(sessionData: JsValue, groupResult: GroupResult,
                                                version: Long): Boolean = {
    if (groupResult != null && sessionData != null &&
      groupResult.getGroupSessionVersion == version) {
      groupResult.setGroupSessionData(sessionData.toString)
      groupResult.setGroupSessionVersion(groupResult.getGroupSessionVersion + 1l)
      groupResultDao.update(groupResult)
      return true
    }
    false
  }

  /**
    * Changes state of GroupResult to FIXED and sends an update to all group
    * members
    */
  private def handleActionFix(groupResultId: Long, studyResultId: Long,
                              registry: ChannelRegistry) = {
    jpa.withTransaction(asJavaSupplier(() => {
      val groupResult = groupResultDao.findById(groupResultId)
      if (groupResult != null) {
        groupResult.setGroupState(GroupState.FIXED)
        groupResultDao.update(groupResult)
        List(msgBuilder.buildSimple(groupResult, GroupAction.Fixed, TellWhom.All))
      }
      else {
        val errorMsg = s"Couldn't find group result with ID $groupResultId in database."
        List(msgBuilder.buildError(groupResultId, errorMsg, TellWhom.SenderOnly))
      }
    }))
  }

}
