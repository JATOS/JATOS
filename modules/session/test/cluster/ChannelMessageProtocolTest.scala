package cluster

import org.junit.Assert.{assertEquals, assertNotEquals}
import org.junit.Test

import java.util.UUID

class ChannelMessageProtocolTest {

  @Test
  def batchMessage_keepsRoutingData(): Unit = {
    val message = BatchClusterMessage(
      originNodeId = "node-1",
      batchId = 10L,
      json = """{"action":"SESSION"}""")

    assertEquals("node-1", message.originNodeId)
    assertEquals(10L, message.batchId)
    assertEquals("""{"action":"SESSION"}""", message.json)
  }

  @Test
  def groupMessage_supportsAllDeliveryModes(): Unit = {
    val recipients = Seq(
      GroupRecipients.All(),
      GroupRecipients.AllButSender(),
      GroupRecipients.Recipient(30L))

    recipients.foreach { groupRecipients =>
      val message = GroupClusterMessage("node-1", 10L, 20L, "{}", groupRecipients)
      assertEquals(groupRecipients, message.recipients)
    }
  }

  @Test
  def groupReassignmentMessage_containsSourceAndDestinationGroups(): Unit = {
    val message = GroupReassignmentRequest("node-1", 30L, 20L, 21L)

    assertEquals("node-1", message.originNodeId)
    assertEquals(30L, message.studyResultId)
    assertEquals(20L, message.currentGroupResultId)
    assertEquals(21L, message.differentGroupResultId)
  }

  @Test
  def groupChannelCloseMessage_identifiesChannel(): Unit = {
    val message = GroupChannelCloseRequest("node-1", 20L, 30L)

    assertEquals("node-1", message.originNodeId)
    assertEquals(20L, message.groupResultId)
    assertEquals(30L, message.studyResultId)
  }

  @Test
  def groupDirectMessages_keepDeliveryRoutingData(): Unit = {
    val request = GroupDirectMsgDeliveryRequest("node-1", "delivery-1", 10L, 20L, 30L, "{}")
    val ack = GroupDirectMsgDeliveryAck("node-2", "node-1", "delivery-1")

    assertEquals("delivery-1", request.deliveryId)
    assertEquals(30L, request.recipientStudyResultId)
    assertEquals("node-1", ack.targetNodeId)
    assertEquals(request.deliveryId, ack.deliveryId)
  }

  @Test
  def groupChannelPresenceMessages_keepRequestRoutingData(): Unit = {
    val request = GroupChannelPresenceRequest("node-1", "presence-1", 30L)
    val ack = GroupChannelPresenceAck("node-2", "node-1", "presence-1")

    assertEquals(30L, request.studyResultId)
    assertEquals("node-1", ack.targetNodeId)
    assertEquals(request.requestId, ack.requestId)
  }

  @Test
  def groupOpenChannelsMessages_keepChannelsAndRoutingData(): Unit = {
    val request = GroupOpenChannelsRequest("node-1", "open-channels-1", 20L)
    val response = GroupOpenChannelsResponse(
      "node-2", "node-1", "open-channels-1", 20L, Set("30", "40"), 2)

    assertEquals(20L, request.groupResultId)
    assertEquals("node-1", response.targetNodeId)
    assertEquals(request.requestId, response.requestId)
    assertEquals(Set("30", "40"), response.channelStudyResultIds)
    assertEquals(2, response.clusterMemberCount)
  }

  @Test
  def nodeIdentity_isStableAndUniquePerInstance(): Unit = {
    val first = new NodeIdentity
    val second = new NodeIdentity

    assertEquals(first.id, first.id)
    UUID.fromString(first.id)
    UUID.fromString(second.id)
    assertNotEquals(first.id, second.id)
  }
}
