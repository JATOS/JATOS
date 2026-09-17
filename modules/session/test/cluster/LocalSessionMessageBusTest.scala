package cluster

import org.junit.Test

class LocalSessionMessageBusTest {

  @Test
  def publish_isNoOpForSingleNodeMode(): Unit = {
    val bus = new LocalSessionMessageBus

    bus.publishBatch(BatchClusterMessage("node-1", 1L, "{}"))
    bus.publishGroup(GroupClusterMessage("node-1", 3L, 4L, "{}", GroupDelivery.All()))
  }
}
