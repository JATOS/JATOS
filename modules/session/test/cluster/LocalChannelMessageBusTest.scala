package cluster

import org.junit.Assert.assertFalse
import org.junit.Test

class LocalChannelMessageBusTest {

  @Test
  def publish_isNoOpForSingleNodeMode(): Unit = {
    val bus = new LocalChannelMessageBus

    assertFalse(bus.isDistributed)
    bus.publishBatchMsgToCluster(BatchClusterMessage("node-1", 1L, "{}"))
    bus.publishGroupMsgToCluster(GroupClusterMessage("node-1", 3L, 4L, "{}", GroupRecipients.All()))
  }
}
