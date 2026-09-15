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
import scala.annotation.tailrec

/**
 * Handles group action messages. Those messages are of type GroupMsg with a JSON object that
 * contains an 'action' field. It was received by a GroupDispatcher and comes from a client via
 * a GroupChannelActor.
 */
@Singleton
class GroupActionHandler @Inject()(groupResultDao: GroupResultDao,
                                   msgBuilder: GroupActionMsgBuilder) {

  private val logger: Logger = Logger(this.getClass)

  private val maxUpdateAttempts = 5

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

  private def handlePatch(json: JsObject, groupResultId: Long, studyResultId: Long,
                          scope: GroupSessionWriteScope): List[GroupMsg] = {
    val sessionActionId = (json \ GroupActionJsonKey.SessionActionId.toString).as[Long]
    val clientsVersion = (json \ GroupActionJsonKey.SessionVersion.toString).as[Long]
    val versioning = (json \ GroupActionJsonKey.SessionVersioning.toString).as[Boolean]
    val patches = (json \ GroupActionJsonKey.SessionPatches.toString).get

    tryUpdate(groupResultId, studyResultId, scope, sessionActionId, clientsVersion, versioning, patches, attempt = 1)
  }

  /**
   * Attempts to update the group session data for a specified group result. The update process verifies
   * version compatibility, validates patches based on the provided scope, and applies the patches
   * if possible. In case of update conflicts, retry logic is implemented based on the number of attempts.
   *
   * @return a list of `GroupMsg` instances representing the results of the update process
   */
  @tailrec
  private def tryUpdate(groupResultId: Long, studyResultId: Long, scope: GroupSessionWriteScope,
                        sessionActionId: Long, clientsVersion: Long, versioning: Boolean,
                        patches: JsValue, attempt: Int): List[GroupMsg] = {
    val groupResult = groupResultDao.findById(groupResultId)
    if (groupResult == null) {
      return List(msgBuilder.buildError(groupResultId,
        s"Couldn't find group result with ID $groupResultId in database.", TellWhom.SenderOnly))
    }

    val currentVersion = groupResult.getGroupSessionVersion
    if (versioning && clientsVersion != currentVersion) {
      val errorMsg = s"Version mismatch (client version: $clientsVersion, current: $currentVersion)."
      return List(msgBuilder.buildSimple(groupResult, GroupAction.SessionFail,
        Some(sessionActionId), Some(errorMsg), TellWhom.SenderOnly))
    }

    if (scope == GroupSessionWriteScope.MEMBER && !isPatchWithinMemberScope(patches, studyResultId)) {
      logger.warn(s".tryUpdate: rejected out-of-scope group session patch from studyResultId " +
        s"$studyResultId in groupResultId $groupResultId")
      val errorMsg = s"Patch rejected: member $studyResultId is only allowed to modify '/$studyResultId' or '/shared'."
      return List(msgBuilder.buildSimple(groupResult, GroupAction.SessionFail,
        Some(sessionActionId), Some(errorMsg), TellWhom.SenderOnly))
    }

    val patchedSessionData =
      try {
        patchSessionData(patches, groupResult)
      } catch {
        case e: Exception =>
          logger.debug(s".tryUpdate: groupResultId $groupResultId, patches ${Json.stringify(patches)}, " +
            s"${e.getClass.getName}: ${e.getMessage}")
          val errorMsg = s"Failed to apply patch: ${e.getMessage}"
          return List(msgBuilder.buildSimple(groupResult, GroupAction.SessionFail,
            Some(sessionActionId), Some(errorMsg), TellWhom.SenderOnly))
      }

    logger.debug(s".tryUpdate: groupResultId $groupResultId, clientsVersion $clientsVersion, " +
      s"versioning $versioning, groupSessionPatch ${Json.stringify(patches)}, " +
      s"updatedSessionData ${Json.stringify(patchedSessionData)}")

    val newVersion = groupResultDao.updateGroupSession(groupResultId, currentVersion, Json.stringify(patchedSessionData))

    if (newVersion != null) {
      groupResult.setGroupSessionData(Json.stringify(patchedSessionData))
      groupResult.setGroupSessionVersion(newVersion)
      val updateMsg = msgBuilder.buildSessionPatch(groupResult, studyResultId, patches, TellWhom.All)
      val acknowledgement = msgBuilder.buildSimple(groupResult, GroupAction.SessionAck,
        Some(sessionActionId), None, TellWhom.SenderOnly)
      List(updateMsg, acknowledgement)

    } else if (!versioning && attempt < maxUpdateAttempts) {
      tryUpdate(groupResultId, studyResultId, scope, sessionActionId, clientsVersion, versioning, patches, attempt + 1)

    } else {
      val currentGroupResult = groupResultDao.findById(groupResultId)
      val actualVersion = Option(currentGroupResult).map(_.getGroupSessionVersion).getOrElse(currentVersion)
      val errorMsg =
        if (versioning)
          s"Concurrent update conflict (client version: $clientsVersion, current: $actualVersion)."
        else
          s"Couldn't update group session after $maxUpdateAttempts attempts because of concurrent updates."
      val messageGroupResult = Option(currentGroupResult).getOrElse(groupResult)
      List(msgBuilder.buildSimple(messageGroupResult, GroupAction.SessionFail,
        Some(sessionActionId), Some(errorMsg), TellWhom.SenderOnly))
    }
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
