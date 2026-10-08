package batch

import daos.common.{BatchDao, GroupResultDao}
import group.{GroupActionHandler, GroupActionMsgBuilder, GroupProtocol}
import models.common.{Batch, GroupResult}
import models.common.Study.GroupSessionWriteScope
import org.junit.Assert._
import org.junit.Test
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import play.api.libs.json.{JsObject, Json}
import testutils.session.JPAMocker

class SessionErrorCodeTest {
  private def assertFailure(json: JsObject, action: String, code: String): Unit = {
    assertEquals(action, (json \ "action").as[String])
    assertEquals(code, (json \ "errorCode").as[String])
    assertTrue((json \ "errorMsg").as[String].nonEmpty)
    assertFalse(json.keys.contains("data"))
    assertFalse(json.keys.contains("sessionData"))
    if (action == "SESSION_FAIL") {
      val idKey = if (json.keys.contains("id")) "id" else "sessionActionId"
      assertEquals(19L, (json \ idKey).as[Long])
    }
  }

  @Test
  def batchFailuresHaveSpecificCodes(): Unit = {
    val dao = mock(classOf[BatchDao])
    JPAMocker.mockDaoTransactions(null, dao)
    val builder = new BatchActionMsgBuilder(dao)
    val handler = new BatchActionHandler(dao, builder)
    def send(json: JsObject): JsObject = {
      val result = handler.handleActionMsg(BatchProtocol.BatchMsg(json), 1L).head
      assertEquals(BatchProtocol.TellWhom.SenderOnly, result.tellWhom)
      result.json
    }
    val request = Json.obj("action" -> "SESSION", "id" -> 19L, "version" -> 7L,
      "versioning" -> true, "patches" -> Json.arr(Json.obj("op" -> "add", "path" -> "/score", "value" -> 2)))
    assertFailure(send(Json.obj("action" -> "UNKNOWN")), "ERROR", "UNKNOWN_ACTION")
    assertFailure(send(request), "ERROR", "BATCH_NOT_FOUND")
    assertFailure(send(Json.obj("action" -> "SESSION_GET")), "ERROR", "BATCH_NOT_FOUND")
    val batch = new Batch()
    batch.setBatchSessionVersion(7L)
    batch.setBatchSessionData("""{"list":[]}""")
    when(dao.findById(1L)).thenReturn(batch)
    assertFailure(send(request ++ Json.obj("patches" -> Json.arr(
      Json.obj("op" -> "replace", "path" -> "/list/5", "value" -> 99)))), "SESSION_FAIL", "SESSION_PATCH_FAILED")
    when(dao.updateBatchSession(any[java.lang.Long](), any[java.lang.Long](), anyString())).thenReturn(null)
    assertFailure(send(request ++ Json.obj("versioning" -> false)), "SESSION_FAIL", "SESSION_UPDATE_RETRIES_EXHAUSTED")
    verify(dao, times(5)).updateBatchSession(any[java.lang.Long](), any[java.lang.Long](), anyString())
  }

  @Test
  def groupFailuresHaveSpecificCodes(): Unit = {
    val dao = mock(classOf[GroupResultDao])
    JPAMocker.mockDaoTransactions(null, dao)
    val builder = new GroupActionMsgBuilder(dao)
    val handler = new GroupActionHandler(dao, builder)
    def send(json: JsObject, scope: GroupSessionWriteScope = GroupSessionWriteScope.SHARED): JsObject = {
      val result = handler.handleActionMsg(GroupProtocol.GroupMsg(json), 1L, 100L, scope).head
      assertEquals(GroupProtocol.TellWhom.SenderOnly, result.tellWhom)
      result.json
    }
    val request = Json.obj("action" -> "SESSION", "sessionActionId" -> 19L, "sessionVersion" -> 7L,
      "sessionVersioning" -> true, "sessionPatches" -> Json.arr(Json.obj("op" -> "add", "path" -> "/score", "value" -> 2)))
    assertFailure(send(Json.obj("action" -> "UNKNOWN")), "ERROR", "UNKNOWN_ACTION")
    assertFailure(send(request), "ERROR", "GROUP_NOT_FOUND")
    assertFailure(send(Json.obj("action" -> "SESSION_GET")), "ERROR", "GROUP_NOT_FOUND")
    assertFailure(send(Json.obj("action" -> "FIXED")), "ERROR", "GROUP_NOT_FOUND")
    val group = new GroupResult()
    group.setId(1L)
    group.setGroupState(GroupResult.GroupState.STARTED)
    group.setGroupSessionVersion(7L)
    group.setGroupSessionData("""{"list":[]}""")
    when(dao.findById(1L)).thenReturn(group)
    assertFailure(send(request, GroupSessionWriteScope.MEMBER), "SESSION_FAIL", "SESSION_WRITE_FORBIDDEN")
    assertFailure(send(request ++ Json.obj("sessionPatches" -> Json.arr(
      Json.obj("op" -> "replace", "path" -> "/list/5", "value" -> 99)))), "SESSION_FAIL", "SESSION_PATCH_FAILED")
    when(dao.updateGroupSession(any[java.lang.Long](), any[java.lang.Long](), anyString())).thenReturn(null)
    assertFailure(send(request ++ Json.obj("sessionVersioning" -> false)), "SESSION_FAIL", "SESSION_UPDATE_RETRIES_EXHAUSTED")
    verify(dao, times(5)).updateGroupSession(any[java.lang.Long](), any[java.lang.Long](), anyString())
    assertFailure(builder.buildError(1L, "Delivery not confirmed", GroupProtocol.GroupErrorCode.MessageDeliveryFailed,
      GroupProtocol.TellWhom.SenderOnly).json, "ERROR", "MESSAGE_DELIVERY_FAILED")
  }
}
