package group

import daos.common.GroupResultDao
import group.GroupDispatcher.GroupAction.GroupAction
import group.GroupDispatcher.TellWhom.TellWhom
import group.GroupDispatcher._
import models.common.GroupResult
import play.api.Logger
import play.api.libs.json._
import play.db.jpa.JPAApi

import javax.inject.{Inject, Singleton}
import scala.compat.java8.FunctionConverters.asJavaSupplier
import scala.jdk.CollectionConverters._


/**
 * Utility class that builds GroupMsgs. So it mostly handles the JSON creation.
 *
 * @author Kristian Lange
 */
@Singleton
class GroupActionMsgBuilder @Inject()(jpa: JPAApi, groupResultDao: GroupResultDao) {

  private val logger: Logger = Logger(this.getClass)

  /**
   * Creates a simple GroupMsg with an error message
   */
  def buildError(groupResultId: Long, errorMsg: String, tellWhom: TellWhom): GroupMsg = {
    val json = Json.obj(
      GroupActionJsonKey.Action.toString -> GroupAction.Error.toString,
      GroupActionJsonKey.ErrorMsg.toString -> errorMsg,
      GroupActionJsonKey.GroupResultId.toString -> groupResultId.toString)
    GroupMsg(json, tellWhom)
  }

  /**
   * Builds a simple GroupMsg with the action, group result ID, and the session version
   */
  def buildSimple(groupResult: GroupResult, action: GroupAction, sessionActionId: Option[Long], tellWhom: TellWhom): GroupMsg = {
    logger.debug(s".buildSimple: groupResult ${groupResult.getId}")
    var json = Json.obj(
      GroupActionJsonKey.Action.toString -> action.toString,
      GroupActionJsonKey.GroupResultId.toString -> groupResult.getId.toString,
      GroupActionJsonKey.GroupState.toString -> groupResult.getGroupState.name,
      GroupActionJsonKey.SessionVersion.toString -> JsNumber(BigDecimal(groupResult.getGroupSessionVersion)))
    if (sessionActionId.isDefined) {
      json = json + (GroupActionJsonKey.SessionActionId.toString -> JsNumber(BigDecimal(sessionActionId.get)))
    }
    GroupMsg(json, tellWhom)
  }

  /**
   * Builds a GroupMsg with or without session data but always with the session version
   */
  def build(groupResultId: Long, studyResultId: Long, registry: GroupChannelRegistry,
            includeSessionData: Boolean, action: GroupAction, tellWhom: TellWhom): GroupMsg = {
    // The current group data are persisted in a GroupResult entity.
    // The GroupResult determines who is a member of the group - and not the group registry.
    jpa.withTransaction(asJavaSupplier(() => {
      logger.debug(s".build: groupResultId $groupResultId, studyResultId $studyResultId, action " +
        s"$action , tellWhom ${tellWhom.toString}")
      val groupResult = groupResultDao.findById(groupResultId)
      if (groupResult != null)
        buildAction(groupResult, studyResultId, registry, includeSessionData, action, tellWhom)
      else
        buildError(groupResultId, s"Couldn't find group result with ID $groupResultId in database" +
          s".", TellWhom.SenderOnly)
    }))
  }

  /**
   * Builds a GroupMsg with the group session patch and version
   */
  def buildSessionPatch(groupResult: GroupResult, studyResultId: Long, patches: JsValue, tellWhom: TellWhom): GroupMsg = {
    logger.debug(s".buildSessionPatch: groupResultId ${groupResult.getId}, studyResultId $studyResultId")
    val json = Json.obj(
      GroupActionJsonKey.Action.toString -> GroupAction.Session.toString,
      GroupActionJsonKey.SessionPatches.toString -> patches,
      GroupActionJsonKey.SessionVersion.toString -> JsNumber(BigDecimal(groupResult.getGroupSessionVersion)))
    GroupMsg(json, tellWhom)
  }

  private def buildAction(groupResult: GroupResult, studyResultId: Long, registry: GroupChannelRegistry,
                          includeSessionData: Boolean, action: GroupAction, tellWhom: TellWhom): GroupMsg = {
    val members = JsArray(
      groupResult.getActiveMemberList.asScala.map(sr => JsString(sr.getId.toString)).toSeq
    )
    val channels = JsArray(registry.getAllStudyResultIds.map(id => JsString(id.toString)).toSeq)
    var json = Json.obj(
      GroupActionJsonKey.Action.toString -> action.toString,
      GroupActionJsonKey.MemberId.toString -> studyResultId.toString,
      GroupActionJsonKey.GroupResultId.toString -> groupResult.getId.toString,
      GroupActionJsonKey.GroupState.toString -> groupResult.getGroupState.name,
      GroupActionJsonKey.Members.toString -> members,
      GroupActionJsonKey.Channels.toString -> channels,
      GroupActionJsonKey.SessionVersion.toString -> JsNumber(BigDecimal(groupResult.getGroupSessionVersion)))
    if (includeSessionData)
      json = json + (GroupActionJsonKey.SessionData.toString -> Json.parse(groupResult.getGroupSessionData))
    GroupMsg(json, tellWhom)
  }

}
