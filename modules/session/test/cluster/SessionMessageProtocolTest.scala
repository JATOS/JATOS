package cluster

import org.junit.Assert.{assertEquals, assertNotEquals}
import org.junit.Test

import java.util.UUID

class SessionMessageProtocolTest {

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
    val message = GroupReassignmentClusterMessage("node-1", 30L, 20L, 21L)

    assertEquals("node-1", message.originNodeId)
    assertEquals(30L, message.studyResultId)
    assertEquals(20L, message.currentGroupResultId)
    assertEquals(21L, message.differentGroupResultId)
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
