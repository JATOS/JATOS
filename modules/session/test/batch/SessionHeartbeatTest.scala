package batch

import daos.common.{BatchDao, GroupResultDao}
import group.GroupActionMsgBuilder
import jakarta.persistence.{EntityManager, TypedQuery}
import org.junit.Assert._
import org.junit.Test
import org.mockito.Mockito._
import play.api.libs.json.Json
import testutils.session.JPAMocker

import java.util.Optional
import java.util.stream.Stream

class SessionHeartbeatTest {
  @Test
  def batchPongReadsCurrentVersionAndFallsBackWithoutBreakingHeartbeat(): Unit = {
    val dao = mock(classOf[BatchDao])
    val builder = new BatchActionMsgBuilder(dao)
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
    val builder = new GroupActionMsgBuilder(dao)
    when(dao.findSessionVersion(2L)).thenReturn(Optional.of(7L), Optional.of(8L), Optional.empty())
      .thenThrow(new RuntimeException("Database unavailable"))
    assertEquals(Json.obj("heartbeat" -> "pong", "sessionVersion" -> 7L), builder.buildPong(2L))
    assertEquals(Json.obj("heartbeat" -> "pong", "sessionVersion" -> 8L), builder.buildPong(2L))
    assertEquals(Json.obj("heartbeat" -> "pong"), builder.buildPong(2L))
    assertEquals(Json.obj("heartbeat" -> "pong"), builder.buildPong(2L))
    verify(dao, times(4)).findSessionVersion(2L)
    verifyNoMoreInteractions(dao)
  }

  @Test
  def batchVersionQueryDoesNotLoadTheEntity(): Unit = {
    val dao = mock(classOf[BatchDao])
    val em = mock(classOf[EntityManager])
    val query = mock(classOf[TypedQuery[java.lang.Long]])
    JPAMocker.mockDaoTransactions(em, dao)
    when(dao.findSessionVersion(1L)).thenCallRealMethod()
    when(em.createQuery("SELECT s.batchSessionVersion FROM Batch s WHERE s.id = :id", classOf[java.lang.Long]))
      .thenReturn(query)
    when(query.setParameter("id", 1L)).thenReturn(query)
    when(query.getResultStream).thenReturn(Stream.of(java.lang.Long.valueOf(7L)))
    assertEquals(Optional.of(7L), dao.findSessionVersion(1L))
    verify(em).createQuery("SELECT s.batchSessionVersion FROM Batch s WHERE s.id = :id", classOf[java.lang.Long])
    verifyNoMoreInteractions(em)
  }

  @Test
  def groupVersionQueryDoesNotLoadTheEntity(): Unit = {
    val dao = mock(classOf[GroupResultDao])
    val em = mock(classOf[EntityManager])
    val query = mock(classOf[TypedQuery[java.lang.Long]])
    JPAMocker.mockDaoTransactions(em, dao)
    when(dao.findSessionVersion(2L)).thenCallRealMethod()
    when(em.createQuery("SELECT s.groupSessionVersion FROM GroupResult s WHERE s.id = :id", classOf[java.lang.Long]))
      .thenReturn(query)
    when(query.setParameter("id", 2L)).thenReturn(query)
    when(query.getResultStream).thenReturn(Stream.of(java.lang.Long.valueOf(7L)))
    assertEquals(Optional.of(7L), dao.findSessionVersion(2L))
    verify(em).createQuery("SELECT s.groupSessionVersion FROM GroupResult s WHERE s.id = :id", classOf[java.lang.Long])
    verifyNoMoreInteractions(em)
  }
}
