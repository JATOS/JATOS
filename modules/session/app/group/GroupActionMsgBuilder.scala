package group

import daos.common.GroupResultDao
import group.GroupProtocol.GroupAction.GroupAction
import group.GroupProtocol.TellWhom.TellWhom
import group.GroupProtocol._
import jakarta.persistence.EntityManager
import models.common.GroupResult
import play.api.Logger
import play.api.libs.json._

import javax.inject.{Inject, Singleton}
import scala.jdk.CollectionConverters._

/**
 * Utility class that builds GroupMsgs. So it mostly handles the JSON creation.
 */
@Singleton
class GroupActionMsgBuilder @Inject()(groupResultDao: GroupResultDao) {

  private val logger: Logger = Logger(this.getClass)

  /** A database version detects lost cluster broadcasts even while the WebSocket stays healthy. */
  def buildPong(groupResultId: Long): JsObject = {
    val pong = Json.obj("heartbeat" -> "pong")
    try {
      val version = groupResultDao.findSessionVersion(groupResultId)
      if (version.isPresent) pong ++ Json.obj("sessionVersion" -> version.get().longValue())
      else pong
    } catch {
      case scala.util.control.NonFatal(error) =>
        // A failed version lookup must not turn a healthy connection into a failed heartbeat.
        logger.warn(s"Could not read session version for groupResultId $groupResultId", error)
        pong
    }
  }

  /**
   * Creates a simple GroupMsg with an error message
   */
  def buildError(groupResultId: Long, errorMsg: String, errorCode: String, tellWhom: TellWhom): GroupMsg = {
    val json = Json.obj(
      GroupActionJsonKey.Action.toString -> GroupAction.Error.toString,
      GroupActionJsonKey.ErrorCode.toString -> errorCode,
      GroupActionJsonKey.ErrorMsg.toString -> errorMsg,
      GroupActionJsonKey.GroupResultId.toString -> groupResultId.toString)
    GroupMsg(json, tellWhom)
  }

  /**
   * Builds a simple GroupMsg with the action, group result ID, and the session version
   */
  def buildSimple(groupResult: GroupResult,
                  action: GroupAction,
                  sessionActionId: Option[Long],
                  errorMsg: Option[String] = None,
                  tellWhom: TellWhom): GroupMsg = {
    logger.debug(s".buildSimple: groupResult ${groupResult.getId}")
    var json = Json.obj(
      GroupActionJsonKey.Action.toString -> action.toString,
      GroupActionJsonKey.GroupResultId.toString -> groupResult.getId.toString,
      GroupActionJsonKey.GroupState.toString -> groupResult.getGroupState.name,
      GroupActionJsonKey.SessionVersion.toString -> JsNumber(BigDecimal(groupResult.getGroupSessionVersion)))
    if (sessionActionId.isDefined) {
      json = json + (GroupActionJsonKey.SessionActionId.toString -> JsNumber(BigDecimal(sessionActionId.get)))
    }
    if (errorMsg.isDefined) {
      json = json + (GroupActionJsonKey.ErrorMsg.toString -> JsString(errorMsg.get))
    }
    GroupMsg(json, tellWhom)
  }

  /**
   * Builds a failed session-update response with a machine-readable error code.
   */
  def buildSessionFailure(groupResult: GroupResult,
                          sessionActionId: Long,
                          errorMsg: String,
                          errorCode: String,
                          tellWhom: TellWhom): GroupMsg = {
    val message = buildSimple(groupResult, GroupAction.SessionFail, Some(sessionActionId), Some(errorMsg), tellWhom)
    message.copy(json = message.json ++ Json.obj(
      GroupActionJsonKey.ErrorCode.toString -> errorCode))
  }

  /**
   * Builds a GroupMsg with or without session data but always with the session version
   */
  def build(groupResultId: Long, studyResultId: Long, channelStudyResultIds: Option[Iterable[Long]],
            includeSessionData: Boolean, action: GroupAction, tellWhom: TellWhom): GroupMsg = {
    // The current group data are persisted in a GroupResult entity.
    // The GroupResult determines who is a member of the group - and not the group registry.
    groupResultDao.withReadOnlyTransaction((_: EntityManager) => {
      logger.debug(s".build: groupResultId $groupResultId, studyResultId $studyResultId, action " +
        s"$action , tellWhom ${tellWhom.toString}")
      val groupResult = groupResultDao.findById(groupResultId)
      if (groupResult != null)
        buildAction(groupResult, studyResultId, channelStudyResultIds, includeSessionData, action, tellWhom)
      else
        buildError(groupResultId, s"Couldn't find group result with ID $groupResultId in database.", GroupErrorCode.GroupNotFound, TellWhom.SenderOnly)
    })
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

  private def buildAction(groupResult: GroupResult, studyResultId: Long,
                          channelStudyResultIds: Option[Iterable[Long]],
                          includeSessionData: Boolean, action: GroupAction, tellWhom: TellWhom): GroupMsg = {
    val members = JsArray(
      groupResult.getActiveMemberList.asScala.map(sr => JsString(sr.getId.toString)).toSeq
    )
    var json = Json.obj(
      GroupActionJsonKey.Action.toString -> action.toString,
      GroupActionJsonKey.MemberId.toString -> studyResultId.toString,
      GroupActionJsonKey.GroupResultId.toString -> groupResult.getId.toString,
      GroupActionJsonKey.GroupState.toString -> groupResult.getGroupState.name,
      GroupActionJsonKey.Members.toString -> members,
      GroupActionJsonKey.SessionVersion.toString -> JsNumber(BigDecimal(groupResult.getGroupSessionVersion)))
    channelStudyResultIds.foreach { ids =>
      json = json + (GroupActionJsonKey.Channels.toString -> JsArray(ids.map(id => JsString(id.toString)).toSeq))
    }
    if (includeSessionData)
      json = json + (GroupActionJsonKey.SessionData.toString -> Json.parse(groupResult.getGroupSessionData))
    GroupMsg(json, tellWhom)
  }

}
