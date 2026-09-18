package cluster

import batch.BatchDispatcher
import group.GroupDispatcher
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.{mock, verify, verifyNoInteractions}
import play.api.libs.json.Json

class DispatcherSessionMessageReceiverTest {

  @Test
  def receiveBatch_deliversParsedJsonToLocalDispatcher(): Unit = {
    val dispatcher = mock(classOf[BatchDispatcher])
    val receiver = new DispatcherSessionMessageReceiver(dispatcher, mock(classOf[GroupDispatcher]))
    val json = Json.obj("action" -> "SESSION", "version" -> 2)

    receiver.receiveBatch(BatchClusterMessage("node-1", 10L, Json.stringify(json)))

    verify(dispatcher).deliverFromRemote(10L, json)
  }

  @Test
  def receiveBatch_ignoresInvalidJson(): Unit = {
    val dispatcher = mock(classOf[BatchDispatcher])
    val receiver = new DispatcherSessionMessageReceiver(dispatcher, mock(classOf[GroupDispatcher]))

    receiver.receiveBatch(BatchClusterMessage("node-1", 10L, "not-json"))

    verifyNoInteractions(dispatcher)
  }

  @Test
  def receiveGroup_deliversParsedJsonToLocalDispatcher(): Unit = {
    val batchDispatcher = mock(classOf[BatchDispatcher])
    val groupDispatcher = mock(classOf[GroupDispatcher])
    val receiver = new DispatcherSessionMessageReceiver(batchDispatcher, groupDispatcher)
    val json = Json.obj("text" -> "hello")
    val recipients = GroupRecipients.AllButSender()

    receiver.receiveGroup(GroupClusterMessage(
      "node-1", 10L, 20L, Json.stringify(json), recipients))

    verify(groupDispatcher).deliverFromRemote(10L, 20L, json, recipients)
    verifyNoInteractions(batchDispatcher)
    assertEquals((), receiver.ready())
  }

  @Test
  def receiveGroup_ignoresInvalidJson(): Unit = {
    val groupDispatcher = mock(classOf[GroupDispatcher])
    val receiver = new DispatcherSessionMessageReceiver(mock(classOf[BatchDispatcher]), groupDispatcher)

    receiver.receiveGroup(GroupClusterMessage(
      "node-1", 10L, 20L, "not-json", GroupRecipients.All()))

    verifyNoInteractions(groupDispatcher)
  }

  @Test
  def receiveGroupReassignment_routesToLocalDispatcher(): Unit = {
    val groupDispatcher = mock(classOf[GroupDispatcher])
    val receiver = new DispatcherSessionMessageReceiver(mock(classOf[BatchDispatcher]), groupDispatcher)

    receiver.receiveGroupReassignment(GroupReassignmentClusterMessage("node-1", 30L, 20L, 21L))

    verify(groupDispatcher).reassignFromRemote(30L, 20L, 21L)
  }
}
