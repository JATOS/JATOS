package cluster

import org.junit.Assert.assertFalse
import org.junit.Test

class LocalSessionMessageBusTest {

  @Test
  def publish_isNoOpForSingleNodeMode(): Unit = {
    val bus = new LocalSessionMessageBus

    assertFalse(bus.isDistributed)
    bus.publishBatchToCluster(BatchClusterMessage("node-1", 1L, "{}"))
    bus.publishGroupToCluster(GroupClusterMessage("node-1", 3L, 4L, "{}", GroupRecipients.All()))
  }
}
