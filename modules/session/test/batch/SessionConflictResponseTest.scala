package batch

import daos.common.{BatchDao, GroupResultDao}
import group.{GroupActionMsgBuilder, GroupProtocol}
import models.common.{Batch, GroupResult}
import org.junit.Assert._
import org.junit.Test
import org.mockito.Mockito.{mock, when}
import play.api.libs.json.Json
import testutils.session.JPAMocker

class SessionConflictResponseTest {
  @Test
  def batchRefreshContainsFullStateAndVersion(): Unit = {
    val dao = mock(classOf[BatchDao])
    JPAMocker.mockDaoTransactions(null, dao)
    val batch = new Batch()
    batch.setBatchSessionVersion(7L)
    batch.setBatchSessionData("""{"score":2}""")
    when(dao.findById(1L)).thenReturn(batch)
    val response = new BatchActionMsgBuilder(dao).buildSessionData(
      1L, BatchProtocol.BatchAction.Session, BatchProtocol.TellWhom.SenderOnly)
    assertEquals(Json.obj("action" -> "SESSION", "version" -> 7L,
      "data" -> Json.obj("score" -> 2)), response.json)
    assertEquals(BatchProtocol.TellWhom.SenderOnly, response.tellWhom)
  }

  @Test
  def groupRefreshContainsFullStateAndVersion(): Unit = {
    val dao = mock(classOf[GroupResultDao])
    JPAMocker.mockDaoTransactions(null, dao)
    val group = new GroupResult()
    group.setId(8L)
    group.setGroupState(GroupResult.GroupState.STARTED)
    group.setGroupSessionVersion(7L)
    group.setGroupSessionData("""{"score":2}""")
    when(dao.findById(8L)).thenReturn(group)
    val response = new GroupActionMsgBuilder(dao).build(8L, 19L, None,
      includeSessionData = true, GroupProtocol.GroupAction.Session, GroupProtocol.TellWhom.SenderOnly)
    assertEquals("SESSION", (response.json \ "action").as[String])
    assertEquals(7L, (response.json \ "sessionVersion").as[Long])
    assertEquals(Json.obj("score" -> 2), (response.json \ "sessionData").get)
    assertEquals(GroupProtocol.TellWhom.SenderOnly, response.tellWhom)
  }

  @Test
  def batchConflictContainsCorrelationAndVersionButNoFullSession(): Unit = {
    val batch = new Batch()
    batch.setBatchSessionVersion(7L)
    batch.setBatchSessionData("""{"score":2}""")
    val builder = new BatchActionMsgBuilder(mock(classOf[BatchDao]))
    val response = builder.buildSessionFailure(batch, 19L, "Version mismatch",
      "SESSION_VERSION_CONFLICT", BatchProtocol.TellWhom.SenderOnly)
    assertEquals(Json.obj("action" -> "SESSION_FAIL", "id" -> 19L, "version" -> 7L,
      "errorMsg" -> "Version mismatch", "errorCode" -> "SESSION_VERSION_CONFLICT"), response.json)
    assertEquals(BatchProtocol.TellWhom.SenderOnly, response.tellWhom)
  }

  @Test
  def groupConflictContainsCorrelationAndVersionButNoFullSession(): Unit = {
    val group = new GroupResult()
    group.setId(8L)
    group.setGroupState(GroupResult.GroupState.STARTED)
    group.setGroupSessionVersion(7L)
    group.setGroupSessionData("""{"score":2}""")
    val builder = new GroupActionMsgBuilder(mock(classOf[GroupResultDao]))
    val response = builder.buildSessionFailure(group, 19L, "Version mismatch",
      "SESSION_VERSION_CONFLICT", GroupProtocol.TellWhom.SenderOnly)
    assertEquals(Json.obj("action" -> "SESSION_FAIL", "sessionActionId" -> 19L,
      "groupResultId" -> "8", "groupState" -> "STARTED", "sessionVersion" -> 7L,
      "errorMsg" -> "Version mismatch", "errorCode" -> "SESSION_VERSION_CONFLICT"), response.json)
    assertEquals(GroupProtocol.TellWhom.SenderOnly, response.tellWhom)
  }
}
