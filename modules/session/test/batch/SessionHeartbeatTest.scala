package batch

import daos.common.{BatchDao, GroupResultDao}
import general.common.JpaTransactions
import group.GroupActionMsgBuilder
import org.junit.Assert._
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito._
import play.api.libs.json.Json

import java.util.Optional

class SessionHeartbeatTest {

  private val transactions = Mockito.mock(classOf[JpaTransactions])

  @Test
  def batchPongReadsCurrentVersionAndFallsBackWithoutBreakingHeartbeat(): Unit = {
    val dao = mock(classOf[BatchDao])
    val builder = new BatchActionMsgBuilder(transactions, dao)
    when(dao.findSessionVersion(1L)).thenReturn(Optional.of(7L), Optional.of(8L), Optional.empty())
      .thenThrow(new RuntimeException("Database unavailable"))
    assertEquals(Json.obj("heartbeat" -> "pong", "version" -> 7L), builder.buildPong(1L))
    assertEquals(Json.obj("heartbeat" -> "pong", "version" -> 8L), builder.buildPong(1L))
    assertEquals(Json.obj("heartbeat" -> "pong"), builder.buildPong(1L))
    assertEquals(Json.obj("heartbeat" -> "pong"), builder.buildPong(1L))
    verify(dao, times(4)).findSessionVersion(1L)
    verifyNoMoreInteractions(dao)
  }

  @Test
  def groupPongReadsCurrentVersionAndFallsBackWithoutBreakingHeartbeat(): Unit = {
    val dao = mock(classOf[GroupResultDao])
    val builder = new GroupActionMsgBuilder(transactions, dao)
    when(dao.findSessionVersion(2L)).thenReturn(Optional.of(7L), Optional.of(8L), Optional.empty())
      .thenThrow(new RuntimeException("Database unavailable"))
    assertEquals(Json.obj("heartbeat" -> "pong", "sessionVersion" -> 7L), builder.buildPong(2L))
    assertEquals(Json.obj("heartbeat" -> "pong", "sessionVersion" -> 8L), builder.buildPong(2L))
    assertEquals(Json.obj("heartbeat" -> "pong"), builder.buildPong(2L))
    assertEquals(Json.obj("heartbeat" -> "pong"), builder.buildPong(2L))
    verify(dao, times(4)).findSessionVersion(2L)
    verifyNoMoreInteractions(dao)
  }

}
