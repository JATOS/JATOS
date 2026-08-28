package group

import daos.common.GroupResultDao
import group.GroupDispatcher.{GroupAction, GroupMsg, TellWhom}
import models.common.GroupResult
import models.common.GroupResult.GroupState
import models.common.Study.GroupSessionWriteScope
import org.junit.Assert._
import org.junit.{Before, Test}
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import play.api.libs.json._
import play.db.jpa.JPAApi

import java.util.function.Supplier

class GroupActionHandlerTest {

  private var jpa: JPAApi = _
  private var groupResultDao: GroupResultDao = _
  private var msgBuilder: GroupActionMsgBuilder = _
  private var groupActionHandler: GroupActionHandler = _

  private val groupResultId = 1L
  private val studyResultId = 100L

  @Before
  def setUp(): Unit = {
    jpa = mock(classOf[JPAApi])
    // Make jpa.withTransaction execute the supplier directly
    when(jpa.withTransaction(any[Supplier[List[GroupMsg]]]())).thenAnswer(invocation => {
      val supplier = invocation.getArgument[Supplier[List[GroupMsg]]](0)
      supplier.get()
    })

    groupResultDao = mock(classOf[GroupResultDao])
    msgBuilder = mock(classOf[GroupActionMsgBuilder])
    groupActionHandler = new GroupActionHandler(jpa, groupResultDao, msgBuilder)
  }

  // Helper to construct a group session GroupMsg
  private def sessionMsg(patches: JsValue, version: Long = 1L, versioning: Boolean = true, sessionActionId: Long = 10L): GroupMsg = {
    GroupMsg(Json.obj(
      "action" -> "SESSION",
      "sessionActionId" -> sessionActionId,
      "sessionVersion" -> version,
      "sessionVersioning" -> versioning,
      "sessionPatches" -> patches
    ))
  }

  // =========================================================================
  // Tests for handleActionMsg actions
  // =========================================================================

  @Test
  def handleActionMsg_unknownAction_returnsErrorMsg(): Unit = {
    val unknownMsg = GroupMsg(Json.obj("action" -> "UNKNOWN_ACTION"))
    val expectedError = GroupMsg(Json.obj("action" -> "ERROR", "errorMsg" -> "Unknown action UNKNOWN_ACTION"))
    when(msgBuilder.buildError(groupResultId, "Unknown action UNKNOWN_ACTION", TellWhom.SenderOnly))
      .thenReturn(expectedError)

    val result = groupActionHandler.handleActionMsg(unknownMsg, groupResultId, studyResultId, GroupSessionWriteScope.SHARED)
    assertEquals(List(expectedError), result)
  }

  @Test
  def handleActionMsg_fixedAction_setsGroupStateToFixed(): Unit = {
    val groupResult = new GroupResult()
    groupResult.setId(groupResultId)
    groupResult.setGroupState(GroupState.STARTED)
    when(groupResultDao.findById(groupResultId)).thenReturn(groupResult)

    val fixedMsg = GroupMsg(Json.obj("action" -> "FIXED"))
    val expectedResponse = GroupMsg(Json.obj("action" -> "FIXED"))
    when(msgBuilder.buildSimple(groupResult, GroupAction.Fixed, None, None, TellWhom.SenderOnly))
      .thenReturn(expectedResponse)

    val result = groupActionHandler.handleActionMsg(fixedMsg, groupResultId, studyResultId, GroupSessionWriteScope.SHARED)

    assertEquals(GroupState.FIXED, groupResult.getGroupState)
    verify(groupResultDao).update(groupResult)
    assertEquals(List(expectedResponse), result)
  }

  @Test
  def handleActionMsg_groupResultNotFound_returnsError(): Unit = {
    when(groupResultDao.findById(groupResultId)).thenReturn(null)
    val expectedError = GroupMsg(Json.obj("action" -> "ERROR"))
    when(msgBuilder.buildError(groupResultId, s"Couldn't find group result with ID $groupResultId in database.", TellWhom.SenderOnly))
      .thenReturn(expectedError)

    val result = groupActionHandler.handleActionMsg(sessionMsg(Json.arr()), groupResultId, studyResultId, GroupSessionWriteScope.SHARED)
    assertEquals(List(expectedError), result)
  }

  // =========================================================================
  // Tests for Versioning and Concurrency
  // =========================================================================

  @Test
  def handleActionMsg_versionMismatch_failsPatch(): Unit = {
    val groupResult = new GroupResult()
    groupResult.setId(groupResultId)
    groupResult.setGroupSessionData("""{"a":1}""")
    groupResult.setGroupSessionVersion(2L) // Stored version is 2
    when(groupResultDao.findById(groupResultId)).thenReturn(groupResult)

    val failMsg = GroupMsg(Json.obj("action" -> "SESSION_FAIL"))
    when(msgBuilder.buildSimple(groupResult, GroupAction.SessionFail, Some(10L), None, TellWhom.SenderOnly))
      .thenReturn(failMsg)

    // Send patch with client version 1 (mismatch) and versioning = true
    val patches = Json.arr(Json.obj("op" -> "add", "path" -> "/b", "value" -> 2))
    //noinspection RedundantDefaultArgument
    val msg = sessionMsg(patches, version = 1L, versioning = true, sessionActionId = 10L)

    val result = groupActionHandler.handleActionMsg(msg, groupResultId, studyResultId, GroupSessionWriteScope.SHARED)

    assertEquals(List(failMsg), result)
    verify(groupResultDao, never()).update(any[GroupResult]())
  }

  @Test
  def handleActionMsg_versionMismatchWithVersioningDisabled_succeeds(): Unit = {
    val groupResult = new GroupResult()
    groupResult.setId(groupResultId)
    groupResult.setGroupSessionData("""{"a":1}""")
    groupResult.setGroupSessionVersion(2L)
    when(groupResultDao.findById(groupResultId)).thenReturn(groupResult)

    val ackMsg = GroupMsg(Json.obj("action" -> "SESSION_ACK"))
    val patchBroadcast = GroupMsg(Json.obj("action" -> "SESSION"))
    when(msgBuilder.buildSimple(groupResult, GroupAction.SessionAck, Some(10L), None, TellWhom.SenderOnly)).thenReturn(ackMsg)
    when(msgBuilder.buildSessionPatch(any[GroupResult](), anyLong(), any[JsValue](), ArgumentMatchers.same(TellWhom.All))).thenReturn(patchBroadcast)

    // Versioning turned off
    val patches = Json.arr(Json.obj("op" -> "add", "path" -> "/b", "value" -> 2))
    //noinspection RedundantDefaultArgument
    val msg = sessionMsg(patches, version = 99L, versioning = false, sessionActionId = 10L)

    val result = groupActionHandler.handleActionMsg(msg, groupResultId, studyResultId, GroupSessionWriteScope.SHARED)

    assertEquals(List(patchBroadcast, ackMsg), result)
    assertEquals(3L, groupResult.getGroupSessionVersion) // Version incremented
    verify(groupResultDao).update(groupResult)
  }

  // =========================================================================
  // Tests for patchSessionData: General object operations
  // =========================================================================

  @Test
  def patchSessionData_nullOrEmptyInitialSession_initializesAsEmptyObject(): Unit = {
    val groupResult = new GroupResult()
    groupResult.setGroupSessionData(null) // Initially null
    groupResult.setGroupSessionVersion(1L)
    when(groupResultDao.findById(groupResultId)).thenReturn(groupResult)

    when(msgBuilder.buildSimple(any(), any(), any(), any(), any())).thenReturn(GroupMsg(Json.obj()))
    when(msgBuilder.buildSessionPatch(any(), anyLong(), any(), any())).thenReturn(GroupMsg(Json.obj()))

    val addPatch = Json.arr(Json.obj("op" -> "add", "path" -> "/foo", "value" -> "bar"))
    groupActionHandler.handleActionMsg(sessionMsg(addPatch), groupResultId, studyResultId, GroupSessionWriteScope.SHARED)

    assertEquals("""{"foo":"bar"}""", groupResult.getGroupSessionData)
  }

  // =========================================================================
  // Tests for patchSessionData: Array handling
  // =========================================================================

  @Test
  def patchSessionData_addArrayElements_appendsAndInserts(): Unit = {
    val groupResult = new GroupResult()
    groupResult.setGroupSessionData("""{"list": [1, 2, 3]}""")
    groupResult.setGroupSessionVersion(1L)
    when(groupResultDao.findById(groupResultId)).thenReturn(groupResult)

    when(msgBuilder.buildSimple(any(), any(), any(), any(), any())).thenReturn(GroupMsg(Json.obj()))
    when(msgBuilder.buildSessionPatch(any(), anyLong(), any(), any())).thenReturn(GroupMsg(Json.obj()))

    // Insert at index 1 and append at end using '-'
    val patches = Json.arr(
      Json.obj("op" -> "add", "path" -> "/list/1", "value" -> 99),
      Json.obj("op" -> "add", "path" -> "/list/-", "value" -> 100)
    )
    groupActionHandler.handleActionMsg(sessionMsg(patches), groupResultId, studyResultId, GroupSessionWriteScope.SHARED)

    val updatedJson = Json.parse(groupResult.getGroupSessionData)
    assertEquals(Json.arr(1, 99, 2, 3, 100), (updatedJson \ "list").get)
  }

  @Test
  def patchSessionData_replaceAndRemoveInArray(): Unit = {
    val groupResult = new GroupResult()
    groupResult.setGroupSessionData("""{"list": ["a", "b", "c", "d"]}""")
    groupResult.setGroupSessionVersion(1L)
    when(groupResultDao.findById(groupResultId)).thenReturn(groupResult)

    when(msgBuilder.buildSimple(any(), any(), any(), any(), any())).thenReturn(GroupMsg(Json.obj()))
    when(msgBuilder.buildSessionPatch(any(), anyLong(), any(), any())).thenReturn(GroupMsg(Json.obj()))

    // Replace index 0 with "z", remove index 2 ("c")
    val patches = Json.arr(
      Json.obj("op" -> "replace", "path" -> "/list/0", "value" -> "z"),
      Json.obj("op" -> "remove", "path" -> "/list/2")
    )
    groupActionHandler.handleActionMsg(sessionMsg(patches), groupResultId, studyResultId, GroupSessionWriteScope.SHARED)

    val updatedJson = Json.parse(groupResult.getGroupSessionData)
    assertEquals(Json.arr("z", "b", "d"), (updatedJson \ "list").get)
  }

  @Test
  def patchSessionData_clearRootWithReplaceEmpty_clearsData(): Unit = {
    val groupResult = new GroupResult()
    groupResult.setGroupSessionData("""{"a": 1, "b": "foo", "c": [1, 2, 3]}""")
    groupResult.setGroupSessionVersion(1L)
    when(groupResultDao.findById(groupResultId)).thenReturn(groupResult)

    when(msgBuilder.buildSimple(any(), any(), any(), any(), any())).thenReturn(GroupMsg(Json.obj()))
    when(msgBuilder.buildSessionPatch(any(), anyLong(), any(), any())).thenReturn(GroupMsg(Json.obj()))

    val clearPatch = Json.arr(Json.obj("op" -> "replace", "path" -> "", "value" -> Json.obj()))
    groupActionHandler.handleActionMsg(sessionMsg(clearPatch), groupResultId, studyResultId, GroupSessionWriteScope.SHARED)

    assertEquals("""{}""", groupResult.getGroupSessionData)
  }

  @Test
  def patchSessionData_removeSlash_noChange(): Unit = {
    val groupResult = new GroupResult()
    groupResult.setGroupSessionData("""{"a": 1, "b": "foo", "c": [1, 2, 3]}""")
    groupResult.setGroupSessionVersion(1L)
    when(groupResultDao.findById(groupResultId)).thenReturn(groupResult)

    when(msgBuilder.buildSimple(any(), any(), any(), any(), any())).thenReturn(GroupMsg(Json.obj()))
    when(msgBuilder.buildSessionPatch(any(), anyLong(), any(), any())).thenReturn(GroupMsg(Json.obj()))

    val patch = Json.arr(Json.obj("op" -> "remove", "path" -> "/"))
    groupActionHandler.handleActionMsg(sessionMsg(patch), groupResultId, studyResultId, GroupSessionWriteScope.SHARED)

    assertEquals(Json.parse("""{"a": 1, "b": "foo", "c": [1, 2, 3]}"""), Json.parse(groupResult.getGroupSessionData))
  }

  @Test
  def patchSessionData_moveAndCopyWithinArrays(): Unit = {
    val groupResult = new GroupResult()
    groupResult.setGroupSessionData("""{"src": [10, 20], "dest": [1, 2]}""")
    groupResult.setGroupSessionVersion(1L)
    when(groupResultDao.findById(groupResultId)).thenReturn(groupResult)

    when(msgBuilder.buildSimple(any(), any(), any(), any(), any())).thenReturn(GroupMsg(Json.obj()))
    when(msgBuilder.buildSessionPatch(any(), anyLong(), any(), any())).thenReturn(GroupMsg(Json.obj()))

    // Copy src[0] to dest append, and move src[1] to dest[0]
    val patches = Json.arr(
      Json.obj("op" -> "copy", "from" -> "/src/0", "path" -> "/dest/-"),
      Json.obj("op" -> "move", "from" -> "/src/1", "path" -> "/dest/0")
    )
    groupActionHandler.handleActionMsg(sessionMsg(patches), groupResultId, studyResultId, GroupSessionWriteScope.SHARED)

    val updatedJson = Json.parse(groupResult.getGroupSessionData)
    assertEquals(Json.arr(10), (updatedJson \ "src").get)
    assertEquals(Json.arr(20, 1, 2, 10), (updatedJson \ "dest").get)
  }

  @Test
  def patchSessionData_nestedObjectInArray(): Unit = {
    val groupResult = new GroupResult()
    groupResult.setGroupSessionData("""{"users": [{"id": 1, "name": "Alice"}, {"id": 2, "name": "Bob"}]}""")
    groupResult.setGroupSessionVersion(1L)
    when(groupResultDao.findById(groupResultId)).thenReturn(groupResult)

    when(msgBuilder.buildSimple(any(), any(), any(), any(), any())).thenReturn(GroupMsg(Json.obj()))
    when(msgBuilder.buildSessionPatch(any(), anyLong(), any(), any())).thenReturn(GroupMsg(Json.obj()))

    // Modify a property of an object inside an array
    val patches = Json.arr(
      Json.obj("op" -> "replace", "path" -> "/users/1/name", "value" -> "Robert"),
      Json.obj("op" -> "add", "path" -> "/users/0/role", "value" -> "admin")
    )
    groupActionHandler.handleActionMsg(sessionMsg(patches), groupResultId, studyResultId, GroupSessionWriteScope.SHARED)

    val updatedJson = Json.parse(groupResult.getGroupSessionData)
    assertEquals("Robert", (updatedJson \ "users" \ 1 \ "name").as[String])
    assertEquals("admin", (updatedJson \ "users" \ 0 \ "role").as[String])
  }

  @Test
  def patchSessionData_arrayOutOfBounds_returnsSessionFail(): Unit = {
    val groupResult = new GroupResult()
    groupResult.setGroupSessionData("""{"list": [1, 2]}""")
    groupResult.setGroupSessionVersion(1L)
    when(groupResultDao.findById(groupResultId)).thenReturn(groupResult)

    val failMsg = GroupMsg(Json.obj("action" -> "SESSION_FAIL"))
    when(msgBuilder.buildSimple(groupResult, GroupAction.SessionFail, Some(10L), None, TellWhom.SenderOnly))
      .thenReturn(failMsg)

    // Index 5 is out of bounds for array of length 2
    val patches = Json.arr(Json.obj("op" -> "replace", "path" -> "/list/5", "value" -> 99))
    val result = groupActionHandler.handleActionMsg(sessionMsg(patches), groupResultId, studyResultId, GroupSessionWriteScope.SHARED)

    assertEquals(List(failMsg), result)
    verify(groupResultDao, never()).update(any[GroupResult]())
  }

  // =========================================================================
  // Tests for numeric object keys (member IDs) handling in JSON Patch
  // =========================================================================

  @Test
  def patchSessionData_copyFromNumericObjectKey_succeeds(): Unit = {
    val groupResult = new GroupResult()
    groupResult.setGroupSessionData("""{"100": {"a": 123}, "shared": {}}""")
    groupResult.setGroupSessionVersion(1L)
    when(groupResultDao.findById(groupResultId)).thenReturn(groupResult)

    when(msgBuilder.buildSimple(any(), any(), any(), any(), any())).thenReturn(GroupMsg(Json.obj()))
    when(msgBuilder.buildSessionPatch(any(), anyLong(), any(), any())).thenReturn(GroupMsg(Json.obj()))

    // Copying from "/100/a" to "/shared/a"
    val patches = Json.arr(Json.obj("op" -> "copy", "from" -> "/100/a", "path" -> "/shared/a"))
    //noinspection RedundantDefaultArgument
    groupActionHandler.handleActionMsg(sessionMsg(patches, sessionActionId = 10L), groupResultId, studyResultId, GroupSessionWriteScope.SHARED)

    val updatedJson = Json.parse(groupResult.getGroupSessionData)
    assertEquals(123, (updatedJson \ "shared" \ "a").as[Int])
    assertEquals(123, (updatedJson \ "100" \ "a").as[Int])
  }

  @Test
  def patchSessionData_copyToNumericObjectKey_succeeds(): Unit = {
    val groupResult = new GroupResult()
    groupResult.setGroupSessionData("""{"shared": {"a": 123}, "100": {}}""")
    groupResult.setGroupSessionVersion(1L)
    when(groupResultDao.findById(groupResultId)).thenReturn(groupResult)

    when(msgBuilder.buildSimple(any(), any(), any(), any(), any())).thenReturn(GroupMsg(Json.obj()))
    when(msgBuilder.buildSessionPatch(any(), anyLong(), any(), any())).thenReturn(GroupMsg(Json.obj()))

    // Copying from "/shared/a" to "/100/b"
    val patches = Json.arr(Json.obj("op" -> "copy", "from" -> "/shared/a", "path" -> "/100/b"))
    //noinspection RedundantDefaultArgument
    groupActionHandler.handleActionMsg(sessionMsg(patches, sessionActionId = 10L), groupResultId, studyResultId, GroupSessionWriteScope.SHARED)

    val updatedJson = Json.parse(groupResult.getGroupSessionData)
    assertEquals(123, (updatedJson \ "100" \ "b").as[Int])
  }
}