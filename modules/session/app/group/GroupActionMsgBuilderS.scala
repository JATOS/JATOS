package group

import javax.inject.Inject

import daos.common.GroupResultDao
import general.ChannelRegistry
import group.GroupDispatcherS.GroupAction.GroupAction
import group.GroupDispatcherS.TellWhom.TellWhom
import group.GroupDispatcherS._
import models.common.GroupResult
import play.api.Logger
import play.api.libs.json.{JsArray, JsNumber, JsString, Json}
import play.db.jpa.JPAApi

import scala.collection.JavaConverters._
import scala.compat.java8.FunctionConverters.asJavaSupplier

class GroupActionMsgBuilderS @Inject()(jpa: JPAApi,
                                       groupResultDao: GroupResultDao) {

  private val logger: Logger = Logger(this.getClass)

  /**
    * Creates a simple BatchActionMsg with an error message
    */
  def buildError(groupResultId: Long, errorMsg: String, tellWhom: TellWhom) = {
    val json = Json.obj(
      GroupActionJsonKey.Action.toString -> GroupAction.Error.toString,
      GroupActionJsonKey.ErrorMsg.toString -> errorMsg,
      GroupActionJsonKey.GroupResultId.toString -> groupResultId)
    GroupMsg(json, tellWhom)
  }

  /**
    * Builds a GroupActionMsg with or without session data but always with
    * session version
    */
  def build(groupResultId: Long, studyResultId: Long, registry: ChannelRegistry,
            includeSessionData: Boolean, action: GroupAction,
            tellWhom: TellWhom): GroupMsg = {
    // The current group data are persisted in a GroupResult entity. The
    // GroupResult determines who is member of the group - and not
    // the group registry.
    jpa.withTransaction(asJavaSupplier(() => {
      logger.debug(s".build: groupResultId $groupResultId, studyResultId $studyResultId, action $action , tellWhom ${tellWhom.toString}")
      val groupResult = groupResultDao.findById(studyResultId)
      if (groupResult != null) buildAction(groupResult, studyResultId, registry, includeSessionData, action, tellWhom)
      else buildError(groupResultId, s"Couldn't find group result with ID $groupResultId in database.", TellWhom.SenderOnly);
    }))

  }

  private def buildAction(groupResult: GroupResult, studyResultId: Long, registry: ChannelRegistry,
                          includeSessionData: Boolean, action: GroupAction, tellWhom: TellWhom) = {
    val members = JsArray(groupResult.getActiveMemberList.asScala.map(sr => JsNumber(BigDecimal(sr.getId))).toSeq)
    val channels = JsArray(registry.getAllStudyResultIds.map(id => JsNumber(BigDecimal(id))).toSeq)
    val json = Json.obj(
      GroupActionJsonKey.Action.toString -> action.toString,
      GroupActionJsonKey.MemberId.toString -> studyResultId,
      GroupActionJsonKey.GroupResultId.toString -> JsNumber(BigDecimal(groupResult.getId)),
      GroupActionJsonKey.GroupState.toString -> groupResult.getGroupState.name,
      GroupActionJsonKey.Members.toString -> members,
      GroupActionJsonKey.Channels.toString -> channels,
      GroupActionJsonKey.SessionVersion.toString -> JsNumber(BigDecimal(groupResult.getGroupSessionVersion)))
    if (includeSessionData) json + (GroupActionJsonKey.SessionData.toString -> JsString(groupResult.getGroupSessionData))
    GroupMsg(json, tellWhom)
  }


}
