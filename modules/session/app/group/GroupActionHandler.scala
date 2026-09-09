package group

import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import com.flipkart.zjsonpatch.JsonPatch
import com.google.common.base.Strings
import daos.common.GroupResultDao
import group.GroupDispatcher.{GroupAction, GroupActionJsonKey, GroupMsg, TellWhom}
import models.common.GroupResult
import models.common.GroupResult.GroupState
import models.common.Study.GroupSessionWriteScope
import play.api.Logger
import play.api.libs.json.{JsArray, JsObject, JsValue, Json}

import javax.inject.{Inject, Singleton}

/**
 * Handles group action messages. Those messages are of type GroupMsg with a JSON object that
 * contains an 'action' field. It was received by a GroupDispatcher and comes from a client via
 * a GroupChannelActor.
 */
@Singleton
class GroupActionHandler @Inject()(groupResultDao: GroupResultDao,
                                   msgBuilder: GroupActionMsgBuilder) {

  private val logger: Logger = Logger(this.getClass)

  private val objectMapper = new ObjectMapper()

  /**
   * Handles group actions originating from a client: Gets a GroupMsg that contains a field
   * 'action' in their JSON. The only action handled here is 1) the patch for the group
   * session, or 2) the msg to fix the group. The function returns GroupMsges that will be sent
   * out to the group members.
   */
  def handleActionMsg(msg: GroupMsg,
                      groupResultId: Long,
                      studyResultId: Long,
                      scope: GroupSessionWriteScope): List[GroupMsg] = {
    logger.debug(s".handleActionMsg: groupResultId $groupResultId, studyResultId $studyResultId, " +
      s"jsonNode ${Json.stringify(msg.json)}")
    val actionValue = (msg.json \ GroupActionJsonKey.Action.toString).as[String]
    val actionOpt = GroupAction.values.find(_.toString == actionValue)
    actionOpt match {
      case Some(GroupAction.Session) => handlePatch(msg.json, groupResultId, studyResultId, scope)
      case Some(GroupAction.Fixed) => handleActionFix(groupResultId)
      case _ => List(msgBuilder.buildError(groupResultId, s"Unknown action $actionValue", TellWhom.SenderOnly))
    }
  }

  /**
   * Applies the patch to the group session
   */
  private def handlePatch(json: JsObject,
                          groupResultId: Long,
                          studyResultId: Long,
                          scope: GroupSessionWriteScope): List[GroupMsg] = {
    groupResultDao.withTransaction(_ => {
      val groupResult = groupResultDao.findById(groupResultId)
      if (groupResult == null) {
        val errorMsg = s"Couldn't find group result with ID $groupResultId in database."
        return List(msgBuilder.buildError(groupResultId, errorMsg, TellWhom.SenderOnly))
      }

      val sessionActionId = (json \ GroupActionJsonKey.SessionActionId.toString).as[Long]
      val clientsVersion = (json \ GroupActionJsonKey.SessionVersion.toString).as[Long]
      val versioning = (json \ GroupActionJsonKey.SessionVersioning.toString).as[Boolean]
      try {
        val patches = (json \ GroupActionJsonKey.SessionPatches.toString).get

        if (scope == GroupSessionWriteScope.MEMBER && !isPatchWithinMemberScope(patches, studyResultId)) {
          logger.warn(s".handlePatch: rejected out-of-scope group session patch from studyResultId " +
            s"$studyResultId in groupResultId $groupResultId")
          val errorMsg = s"Patch rejected: member $studyResultId is only allowed to modify '/$studyResultId' or '/shared'."
          return List(msgBuilder.buildSimple(groupResult, GroupAction.SessionFail,
            Some(sessionActionId), Some(errorMsg),TellWhom.SenderOnly))
        }

        val patchedSessionData = patchSessionData(patches, groupResult)
        logger.debug(s".handlePatch: groupResultId $groupResultId, " +
          s"clientsVersion $clientsVersion, versioning $versioning, groupSessionPatch ${Json.stringify(patches)}, " +
          s"updatedSessionData ${Json.stringify(patchedSessionData)}")

        val success = checkVersionAndPersistSessionData(patchedSessionData, groupResult, clientsVersion, versioning)
        if (success) {
          val msg1 = msgBuilder.buildSessionPatch(groupResult, studyResultId, patches, TellWhom.All)
          val msg2 = msgBuilder.buildSimple(groupResult, GroupAction.SessionAck, Some(sessionActionId), None, TellWhom.SenderOnly)
          List(msg1, msg2)
        } else {
          val errorMsg = s"Version mismatch or concurrent update conflict (client version: $clientsVersion, current: ${groupResult.getGroupSessionVersion})."
          List(msgBuilder.buildSimple(groupResult, GroupAction.SessionFail, Some(sessionActionId), Some(errorMsg), TellWhom.SenderOnly))
        }
      } catch {
        case e: Exception =>
          logger.debug(s".handlePatch: groupResultId $groupResultId, json ${Json.stringify(json)}," +
            s" ${e.getClass.getName}: ${e.getMessage}")
          val errorMsg = s"Failed to apply patch: ${e.getMessage}"
          List(msgBuilder.buildSimple(groupResult, GroupAction.SessionFail, Some(sessionActionId), Some(errorMsg), TellWhom.SenderOnly))
      }
    })
  }

  /**
   * Authorization check for a group session writes in member scope. A patch is accepted
   * only if all modifying operations target paths that the sender is allowed to write:
   * the sender's own member subtree - "/<studyResultId>" or below - or the shared
   * subtree "/shared" or below.
   *
   * Read-only 'test' operations and the 'from' (source) of a 'copy' are unrestricted; 'move' also
   * deletes its 'from' path, so that must be in scope too.
   */
  def isPatchWithinMemberScope(patches: JsValue, studyResultId: Long): Boolean = {
    val memberPrefix = "/" + studyResultId
    val sharedPrefix = "/shared"

    // The trailing "/" guards against prefix confusion, e.g., member 12 must not match "/123".
    def inSubtree(path: String, prefix: String): Boolean =
      path == prefix || path.startsWith(prefix + "/")

    def inScope(path: String): Boolean =
      inSubtree(path, memberPrefix) || inSubtree(path, sharedPrefix)

    patches match {
      case JsArray(ops) =>
        ops.forall { op =>
          val path = (op \ "path").asOpt[String]
          (op \ "op").asOpt[String] match {
            case Some("add") | Some("replace") | Some("remove") | Some("copy") => path.exists(inScope)
            case Some("move") =>
              val from = (op \ "from").asOpt[String]
              path.exists(inScope) && from.exists(inScope)
            case Some("test") => true
            case _ => false
          }
        }
      case _ => false
    }
  }

  private def patchSessionData(patches: JsValue, groupResult: GroupResult): JsValue = {
    val currentSessionData =
      if (!Strings.isNullOrEmpty(groupResult.getGroupSessionData))
        Json.parse(groupResult.getGroupSessionData)
      else
        Json.obj()

    val sourceNode: JsonNode = objectMapper.readTree(Json.stringify(currentSessionData))
    val patchNode: JsonNode = objectMapper.readTree(Json.stringify(patches))

    val resultNode = JsonPatch.apply(patchNode, sourceNode)

    Json.parse(resultNode.toString)
  }

  /**
   * Persists the given sessionData in the GroupResult and increases the groupSessionVersion by 1 - but only if the
   * stored version is equal to the received one or versioning is turned off. Returns true if this was successful -
   * otherwise false.
   */
  private def checkVersionAndPersistSessionData(sessionData: JsValue,
                                                groupResult: GroupResult,
                                                version: Long,
                                                versioning: Boolean): Boolean = {
    if (groupResult != null && sessionData != null && (!versioning || groupResult.getGroupSessionVersion == version)) {
      groupResult.setGroupSessionData(sessionData.toString)
      groupResult.setGroupSessionVersion(groupResult.getGroupSessionVersion + 1L)
      groupResultDao.merge(groupResult)
      return true
    }
    false
  }

  /**
   * Changes the state of GroupResult to FIXED and sends an update to all group
   * members
   */
  private def handleActionFix(groupResultId: Long): List[GroupMsg] = {
    groupResultDao.withTransaction(_ => {
      val groupResult = groupResultDao.findById(groupResultId)
      if (groupResult != null) {
        groupResult.setGroupState(GroupState.FIXED)
        groupResultDao.merge(groupResult)
        List(msgBuilder.buildSimple(groupResult, GroupAction.Fixed, None, None, TellWhom.SenderOnly))
      } else {
        val errorMsg = s"Couldn't find group result with ID $groupResultId in database."
        List(msgBuilder.buildError(groupResultId, errorMsg, TellWhom.SenderOnly))
      }
    })
  }

}
