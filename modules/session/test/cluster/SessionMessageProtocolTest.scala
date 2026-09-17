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
    val deliveries = Seq(
      GroupDelivery.All(),
      GroupDelivery.AllButSender(),
      GroupDelivery.Recipient(30L))

    deliveries.foreach { delivery =>
      val message = GroupClusterMessage("node-1", 10L, 20L, "{}", delivery)
      assertEquals(delivery, message.delivery)
    }
  }

  @Test
  def randomNodeIdentity_isStableAndUniquePerInstance(): Unit = {
    val first = new RandomNodeIdentity
    val second = new RandomNodeIdentity

    assertEquals(first.id, first.id)
    UUID.fromString(first.id)
    UUID.fromString(second.id)
    assertNotEquals(first.id, second.id)
  }
}
