package cluster

import batch.BatchDispatcher
import group.GroupDispatcher
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.{mock, verify, verifyNoInteractions}
import org.mockito.Mockito.when
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
  def receiveGroupDirect_reportsLocalDeliveryAndRoutesAck(): Unit = {
    val groupDispatcher = mock(classOf[GroupDispatcher])
    val receiver = new DispatcherSessionMessageReceiver(mock(classOf[BatchDispatcher]), groupDispatcher)
    val json = Json.obj("recipient" -> "30", "text" -> "hello")
    val request = GroupDirectMsgDeliveryRequest("node-1", "delivery-1", 10L, 20L, 30L, Json.stringify(json))
    when(groupDispatcher.deliverDirectMsgFromRemote(10L, 30L, json)).thenReturn(true)

    assertEquals(true, receiver.receiveGroupDirectMsg(request))
    receiver.receiveGroupDirectMsgDeliveryAck(GroupDirectMsgDeliveryAck("node-2", "node-1", "delivery-1"))

    verify(groupDispatcher).deliverDirectMsgFromRemote(10L, 30L, json)
    verify(groupDispatcher).acknowledgeDirectDelivery("delivery-1")
  }

  @Test
  def receiveGroupChannelPresence_checksLocalDispatcherAndRoutesAck(): Unit = {
    val groupDispatcher = mock(classOf[GroupDispatcher])
    val receiver = new DispatcherSessionMessageReceiver(mock(classOf[BatchDispatcher]), groupDispatcher)
    val request = GroupChannelPresenceRequest("node-1", "presence-1", 30L)
    when(groupDispatcher.hasChannel(30L)).thenReturn(true)

    assertEquals(true, receiver.receiveGroupChannelPresence(request))
    receiver.receiveGroupChannelPresenceAck(GroupChannelPresenceAck("node-2", "node-1", "presence-1"))

    verify(groupDispatcher).hasChannel(30L)
    verify(groupDispatcher).acknowledgeChannelPresence("presence-1")
  }

  @Test
  def receiveGroupReassignment_routesToLocalDispatcher(): Unit = {
    val groupDispatcher = mock(classOf[GroupDispatcher])
    val receiver = new DispatcherSessionMessageReceiver(mock(classOf[BatchDispatcher]), groupDispatcher)

    receiver.receiveGroupReassignment(GroupReassignmentClusterMessage("node-1", 30L, 20L, 21L))

    verify(groupDispatcher).reassignFromRemote(30L, 20L, 21L)
  }
}
