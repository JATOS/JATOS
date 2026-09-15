package batch

import batch.BatchDispatcher.{BatchAction, BatchMsg, TellWhom}
import daos.common.BatchDao
import models.common.Batch
import org.junit.Assert._
import org.junit.{Before, Test}
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers._
import org.mockito.invocation.InvocationOnMock
import org.mockito.Mockito._
import play.api.libs.json._
import testutils.session.JPAMocker

import java.lang

class BatchActionHandlerTest {

  private var batchDao: BatchDao = _
  private var msgBuilder: BatchActionMsgBuilder = _
  private var batchActionHandler: BatchActionHandler = _

  private val batchId = 1L

  @Before
  def setUp(): Unit = {
    batchDao = mock(classOf[BatchDao])
    JPAMocker.mockDaoTransactions(null, batchDao)
    // Unless a test overrides this, simulate a successful compare-and-set update.
    when(batchDao.updateBatchSession(
      ArgumentMatchers.eq(lang.Long.valueOf(batchId)),
      any[lang.Long](),
      anyString()))
      .thenAnswer((invocation: InvocationOnMock) => {
        val expectedVersion = invocation.getArgument[lang.Long](1)
        lang.Long.valueOf(expectedVersion + 1L)
      })
    msgBuilder = mock(classOf[BatchActionMsgBuilder])
    batchActionHandler = new BatchActionHandler(batchDao, msgBuilder)
  }

  private def sessionMsg(patches: JsValue, version: Long = 1L, versioning: Boolean = true, sessionActionId: Long = 10L): BatchMsg = {
    BatchMsg(Json.obj(
      "action" -> "SESSION",
      "id" -> sessionActionId,
      "version" -> version,
      "versioning" -> versioning,
      "patches" -> patches
    ))
  }

  // =========================================================================
  // Tests for handleActionMsg actions
  // =========================================================================

  @Test
  def handleActionMsg_unknownAction_returnsErrorMsg(): Unit = {
    val unknownMsg = BatchMsg(Json.obj("action" -> "UNKNOWN_ACTION"))
    val expectedError = BatchMsg(Json.obj("action" -> "ERROR", "errorMsg" -> "Unknown action UNKNOWN_ACTION"))
    when(msgBuilder.buildError("Unknown action UNKNOWN_ACTION", TellWhom.SenderOnly))
      .thenReturn(expectedError)

    val result = batchActionHandler.handleActionMsg(unknownMsg, batchId)
    assertEquals(List(expectedError), result)
  }

  @Test
  def handleActionMsg_batchNotFound_returnsError(): Unit = {
    when(batchDao.findById(batchId)).thenReturn(null)
    val expectedError = BatchMsg(Json.obj("action" -> "ERROR"))
    when(msgBuilder.buildError(s"Couldn't find batch with ID $batchId in database.", TellWhom.SenderOnly))
      .thenReturn(expectedError)

    val result = batchActionHandler.handleActionMsg(sessionMsg(Json.arr()), batchId)
    assertEquals(List(expectedError), result)
  }

  // =========================================================================
  // Tests for Versioning and Concurrency
  // =========================================================================

  @Test
  def handleActionMsg_versionMismatch_failsPatch(): Unit = {
    val batch = new Batch()
    batch.setId(batchId)
    batch.setBatchSessionData("""{"a":1}""")
    batch.setBatchSessionVersion(2L) // Stored version is 2
    when(batchDao.findById(batchId)).thenReturn(batch)

    val failMsg = BatchMsg(Json.obj("action" -> "SESSION_FAIL"))
    when(msgBuilder.buildSimple(
      ArgumentMatchers.eq(batch),
      ArgumentMatchers.eq(BatchAction.SessionFail),
      ArgumentMatchers.eq(10L),
      any(),
      ArgumentMatchers.eq(TellWhom.SenderOnly)))
      .thenReturn(failMsg)

    // Send patch with client version 1 (mismatch) and versioning = true
    val patches = Json.arr(Json.obj("op" -> "add", "path" -> "/b", "value" -> 2))
    //noinspection RedundantDefaultArgument
    val msg = sessionMsg(patches, version = 1L, versioning = true, sessionActionId = 10L)

    val result = batchActionHandler.handleActionMsg(msg, batchId)

    assertEquals(List(failMsg), result)
    verify(batchDao, never()).updateBatchSession(anyLong(), anyLong(), anyString())
  }

  @Test
  def handleActionMsg_versionMismatchWithVersioningDisabled_succeeds(): Unit = {
    val batch = new Batch()
    batch.setId(batchId)
    batch.setBatchSessionData("""{"a":1}""")
    batch.setBatchSessionVersion(2L)
    when(batchDao.findById(batchId)).thenReturn(batch)

    val ackMsg = BatchMsg(Json.obj("action" -> "SESSION_ACK"))
    val patchBroadcast = BatchMsg(Json.obj("action" -> "SESSION"))
    when(msgBuilder.buildSimple(batch, BatchAction.SessionAck, 10L, None, TellWhom.SenderOnly)).thenReturn(ackMsg)
    when(msgBuilder.buildSessionPatch(any[Batch](), any[JsValue](), same(TellWhom.All))).thenReturn(patchBroadcast)

    // Versioning turned off
    val patches = Json.arr(Json.obj("op" -> "add", "path" -> "/b", "value" -> 2))
    //noinspection RedundantDefaultArgument
    val msg = sessionMsg(patches, version = 99L, versioning = false, sessionActionId = 10L)

    val result = batchActionHandler.handleActionMsg(msg, batchId)

    assertEquals(List(patchBroadcast, ackMsg), result)
    assertEquals(3L, batch.getBatchSessionVersion) // Version incremented
    verify(batchDao).updateBatchSession(batchId, 2L, """{"a":1,"b":2}""")
  }

  @Test
  def handleActionMsg_compareAndSetConflictWithVersioningEnabled_failsWithoutRetry(): Unit = {
    val originalBatch = new Batch()
    originalBatch.setId(batchId)
    originalBatch.setBatchSessionData("""{"a":1}""")
    originalBatch.setBatchSessionVersion(1L)

    val concurrentlyUpdatedBatch = new Batch()
    concurrentlyUpdatedBatch.setId(batchId)
    concurrentlyUpdatedBatch.setBatchSessionData("""{"a":1,"other":true}""")
    concurrentlyUpdatedBatch.setBatchSessionVersion(2L)

    when(batchDao.findById(batchId)).thenReturn(originalBatch, concurrentlyUpdatedBatch)
    when(batchDao.updateBatchSession(
      ArgumentMatchers.eq(lang.Long.valueOf(batchId)),
      ArgumentMatchers.eq(lang.Long.valueOf(1L)),
      anyString())).thenReturn(null)

    val failMsg = BatchMsg(Json.obj("action" -> "SESSION_FAIL"))
    when(msgBuilder.buildSimple(
      ArgumentMatchers.eq(concurrentlyUpdatedBatch),
      ArgumentMatchers.eq(BatchAction.SessionFail),
      ArgumentMatchers.eq(10L),
      any(),
      ArgumentMatchers.eq(TellWhom.SenderOnly)))
      .thenReturn(failMsg)

    val patches = Json.arr(Json.obj("op" -> "add", "path" -> "/b", "value" -> 2))
    //noinspection RedundantDefaultArgument
    val result = batchActionHandler.handleActionMsg(
      sessionMsg(patches, version = 1L, versioning = true), batchId)

    assertEquals(List(failMsg), result)
    verify(batchDao, times(1)).updateBatchSession(batchId, 1L, """{"a":1,"b":2}""")
    verify(batchDao, times(2)).findById(batchId)
  }

  @Test
  def handleActionMsg_compareAndSetConflictWithVersioningDisabled_reloadsReappliesAndRetries(): Unit = {
    val originalBatch = new Batch()
    originalBatch.setId(batchId)
    originalBatch.setBatchSessionData("""{"a":1}""")
    originalBatch.setBatchSessionVersion(1L)

    val concurrentlyUpdatedBatch = new Batch()
    concurrentlyUpdatedBatch.setId(batchId)
    concurrentlyUpdatedBatch.setBatchSessionData("""{"a":1,"other":true}""")
    concurrentlyUpdatedBatch.setBatchSessionVersion(2L)

    when(batchDao.findById(batchId)).thenReturn(originalBatch, concurrentlyUpdatedBatch)
    when(batchDao.updateBatchSession(
      ArgumentMatchers.eq(lang.Long.valueOf(batchId)),
      ArgumentMatchers.eq(lang.Long.valueOf(1L)),
      anyString())).thenReturn(null)
    when(batchDao.updateBatchSession(
      ArgumentMatchers.eq(lang.Long.valueOf(batchId)),
      ArgumentMatchers.eq(lang.Long.valueOf(2L)),
      anyString())).thenReturn(lang.Long.valueOf(3L))

    val patches = Json.arr(Json.obj("op" -> "add", "path" -> "/b", "value" -> 2))
    val ackMsg = BatchMsg(Json.obj("action" -> "SESSION_ACK"))
    val patchBroadcast = BatchMsg(Json.obj("action" -> "SESSION"))
    when(msgBuilder.buildSimple(
      ArgumentMatchers.eq(concurrentlyUpdatedBatch),
      ArgumentMatchers.eq(BatchAction.SessionAck),
      ArgumentMatchers.eq(10L),
      ArgumentMatchers.eq(None),
      ArgumentMatchers.eq(TellWhom.SenderOnly)))
      .thenReturn(ackMsg)
    when(msgBuilder.buildSessionPatch(
      ArgumentMatchers.eq(concurrentlyUpdatedBatch),
      ArgumentMatchers.eq(patches),
      ArgumentMatchers.eq(TellWhom.All)))
      .thenReturn(patchBroadcast)

    val result = batchActionHandler.handleActionMsg(
      sessionMsg(patches, version = 99L, versioning = false), batchId)

    assertEquals(List(patchBroadcast, ackMsg), result)
    verify(batchDao).updateBatchSession(batchId, 1L, """{"a":1,"b":2}""")
    verify(batchDao).updateBatchSession(batchId, 2L, """{"a":1,"other":true,"b":2}""")
    assertEquals(3L, concurrentlyUpdatedBatch.getBatchSessionVersion)
    assertEquals(Json.obj("a" -> 1, "other" -> true, "b" -> 2),
      Json.parse(concurrentlyUpdatedBatch.getBatchSessionData))
  }

  // =========================================================================
  // Tests for patchSessionData: General object operations
  // =========================================================================

  @Test
  def patchSessionData_nullOrEmptyInitialSession_initializesAsEmptyObject(): Unit = {
    val batch = new Batch()
    batch.setBatchSessionData(null) // Initially null
    batch.setBatchSessionVersion(1L)
    when(batchDao.findById(batchId)).thenReturn(batch)

    when(msgBuilder.buildSimple(any(), any(), anyLong(), any(), any())).thenReturn(BatchMsg(Json.obj()))
    when(msgBuilder.buildSessionPatch(any(), any(), any())).thenReturn(BatchMsg(Json.obj()))

    val addPatch = Json.arr(Json.obj("op" -> "add", "path" -> "/foo", "value" -> "bar"))
    batchActionHandler.handleActionMsg(sessionMsg(addPatch), batchId)

    assertEquals("""{"foo":"bar"}""", batch.getBatchSessionData)
  }

  @Test
  def patchSessionData_clearRootWithReplaceEmpty_clearsData(): Unit = {
    val batch = new Batch()
    batch.setBatchSessionData("""{"a": 1, "b": "foo", "c": [1, 2, 3]}""")
    batch.setBatchSessionVersion(1L)
    when(batchDao.findById(batchId)).thenReturn(batch)

    when(msgBuilder.buildSimple(any(), any(), anyLong(), any(), any())).thenReturn(BatchMsg(Json.obj()))
    when(msgBuilder.buildSessionPatch(any(), any(), any())).thenReturn(BatchMsg(Json.obj()))

    val clearPatch = Json.arr(Json.obj("op" -> "replace", "path" -> "", "value" -> Json.obj()))
    batchActionHandler.handleActionMsg(sessionMsg(clearPatch), batchId)

    assertEquals("""{}""", batch.getBatchSessionData)
  }

  // =========================================================================
  // Tests for patchSessionData: Array handling
  // =========================================================================

  @Test
  def patchSessionData_addArrayElements_appendsAndInserts(): Unit = {
    val batch = new Batch()
    batch.setBatchSessionData("""{"list": [1, 2, 3]}""")
    batch.setBatchSessionVersion(1L)
    when(batchDao.findById(batchId)).thenReturn(batch)

    when(msgBuilder.buildSimple(any(), any(), anyLong(), any(), any())).thenReturn(BatchMsg(Json.obj()))
    when(msgBuilder.buildSessionPatch(any(), any(), any())).thenReturn(BatchMsg(Json.obj()))

    // Insert at index 1 and append at end using '-'
    val patches = Json.arr(
      Json.obj("op" -> "add", "path" -> "/list/1", "value" -> 99),
      Json.obj("op" -> "add", "path" -> "/list/-", "value" -> 100)
    )
    batchActionHandler.handleActionMsg(sessionMsg(patches), batchId)

    val updatedJson = Json.parse(batch.getBatchSessionData)
    assertEquals(Json.arr(1, 99, 2, 3, 100), (updatedJson \ "list").get)
  }

  @Test
  def patchSessionData_replaceAndRemoveInArray(): Unit = {
    val batch = new Batch()
    batch.setBatchSessionData("""{"list": ["a", "b", "c", "d"]}""")
    batch.setBatchSessionVersion(1L)
    when(batchDao.findById(batchId)).thenReturn(batch)

    when(msgBuilder.buildSimple(any(), any(), anyLong(), any(), any())).thenReturn(BatchMsg(Json.obj()))
    when(msgBuilder.buildSessionPatch(any(), any(), any())).thenReturn(BatchMsg(Json.obj()))

    // Replace index 0 with "z", remove index 2 ("c")
    val patches = Json.arr(
      Json.obj("op" -> "replace", "path" -> "/list/0", "value" -> "z"),
      Json.obj("op" -> "remove", "path" -> "/list/2")
    )
    batchActionHandler.handleActionMsg(sessionMsg(patches), batchId)

    val updatedJson = Json.parse(batch.getBatchSessionData)
    assertEquals(Json.arr("z", "b", "d"), (updatedJson \ "list").get)
  }

  @Test
  def patchSessionData_moveAndCopyWithinArrays(): Unit = {
    val batch = new Batch()
    batch.setBatchSessionData("""{"src": [10, 20], "dest": [1, 2]}""")
    batch.setBatchSessionVersion(1L)
    when(batchDao.findById(batchId)).thenReturn(batch)

    when(msgBuilder.buildSimple(any(), any(), anyLong(), any(), any())).thenReturn(BatchMsg(Json.obj()))
    when(msgBuilder.buildSessionPatch(any(), any(), any())).thenReturn(BatchMsg(Json.obj()))

    // Copy src[0] to dest append, and move src[1] to dest[0]
    val patches = Json.arr(
      Json.obj("op" -> "copy", "from" -> "/src/0", "path" -> "/dest/-"),
      Json.obj("op" -> "move", "from" -> "/src/1", "path" -> "/dest/0")
    )
    batchActionHandler.handleActionMsg(sessionMsg(patches), batchId)

    val updatedJson = Json.parse(batch.getBatchSessionData)
    assertEquals(Json.arr(10), (updatedJson \ "src").get)
    assertEquals(Json.arr(20, 1, 2, 10), (updatedJson \ "dest").get)
  }

  @Test
  def patchSessionData_nestedObjectInArray(): Unit = {
    val batch = new Batch()
    batch.setBatchSessionData("""{"users": [{"id": 1, "name": "Alice"}, {"id": 2, "name": "Bob"}]}""")
    batch.setBatchSessionVersion(1L)
    when(batchDao.findById(batchId)).thenReturn(batch)

    when(msgBuilder.buildSimple(any(), any(), anyLong(), any(), any())).thenReturn(BatchMsg(Json.obj()))
    when(msgBuilder.buildSessionPatch(any(), any(), any())).thenReturn(BatchMsg(Json.obj()))

    // Modify a property of an object inside an array
    val patches = Json.arr(
      Json.obj("op" -> "replace", "path" -> "/users/1/name", "value" -> "Robert"),
      Json.obj("op" -> "add", "path" -> "/users/0/role", "value" -> "admin")
    )
    batchActionHandler.handleActionMsg(sessionMsg(patches), batchId)

    val updatedJson = Json.parse(batch.getBatchSessionData)
    assertEquals("Robert", (updatedJson \ "users" \ 1 \ "name").as[String])
    assertEquals("admin", (updatedJson \ "users" \ 0 \ "role").as[String])
  }

  @Test
  def patchSessionData_arrayOutOfBounds_returnsSessionFail(): Unit = {
    val batch = new Batch()
    batch.setBatchSessionData("""{"list": [1, 2]}""")
    batch.setBatchSessionVersion(1L)
    when(batchDao.findById(batchId)).thenReturn(batch)

    val failMsg = BatchMsg(Json.obj("action" -> "SESSION_FAIL"))
    when(msgBuilder.buildSimple(
      ArgumentMatchers.eq(batch),
      ArgumentMatchers.eq(BatchAction.SessionFail),
      ArgumentMatchers.eq(10L),
      any(),
      ArgumentMatchers.eq(TellWhom.SenderOnly)))
      .thenReturn(failMsg)

    // Index 5 is out of bounds for array of length 2
    val patches = Json.arr(Json.obj("op" -> "replace", "path" -> "/list/5", "value" -> 99))
    val result = batchActionHandler.handleActionMsg(sessionMsg(patches), batchId)

    assertEquals(List(failMsg), result)
    verify(batchDao, never()).updateBatchSession(anyLong(), anyLong(), anyString())
  }

  // =========================================================================
  // Tests for numeric object keys handling in JSON Patch
  // =========================================================================

  @Test
  def patchSessionData_copyFromNumericObjectKey_succeeds(): Unit = {
    val batch = new Batch()
    batch.setBatchSessionData("""{"100": {"a": 123}, "shared": {}}""")
    batch.setBatchSessionVersion(1L)
    when(batchDao.findById(batchId)).thenReturn(batch)

    when(msgBuilder.buildSimple(any(), any(), anyLong(), any(), any())).thenReturn(BatchMsg(Json.obj()))
    when(msgBuilder.buildSessionPatch(any(), any(), any())).thenReturn(BatchMsg(Json.obj()))

    // Copying from "/100/a" to "/shared/a"
    val patches = Json.arr(Json.obj("op" -> "copy", "from" -> "/100/a", "path" -> "/shared/a"))
    //noinspection RedundantDefaultArgument
    batchActionHandler.handleActionMsg(sessionMsg(patches, sessionActionId = 10L), batchId)

    val updatedJson = Json.parse(batch.getBatchSessionData)
    assertEquals(123, (updatedJson \ "shared" \ "a").as[Int])
    assertEquals(123, (updatedJson \ "100" \ "a").as[Int])
  }

  @Test
  def patchSessionData_copyToNumericObjectKey_succeeds(): Unit = {
    val batch = new Batch()
    batch.setBatchSessionData("""{"shared": {"a": 123}, "100": {}}""")
    batch.setBatchSessionVersion(1L)
    when(batchDao.findById(batchId)).thenReturn(batch)

    when(msgBuilder.buildSimple(any(), any(), anyLong(), any(), any())).thenReturn(BatchMsg(Json.obj()))
    when(msgBuilder.buildSessionPatch(any(), any(), any())).thenReturn(BatchMsg(Json.obj()))

    // Copying from "/shared/a" to "/100/b"
    val patches = Json.arr(Json.obj("op" -> "copy", "from" -> "/shared/a", "path" -> "/100/b"))
    //noinspection RedundantDefaultArgument
    batchActionHandler.handleActionMsg(sessionMsg(patches, sessionActionId = 10L), batchId)

    val updatedJson = Json.parse(batch.getBatchSessionData)
    assertEquals(123, (updatedJson \ "100" \ "b").as[Int])
  }
}
