package general

import org.junit.Assert.assertThrows
import org.junit.Test

class OnStartStopTest {

  @Test
  def validateMultiNodeConfiguration_acceptsSingleNodeConfiguration(): Unit = {
    OnStartStop.validateMultiNodeConfiguration(multiNode = false, actorProvider = "local")
  }

  @Test
  def validateMultiNodeConfiguration_acceptsMultiNodeConfiguration(): Unit = {
    OnStartStop.validateMultiNodeConfiguration(multiNode = true, actorProvider = "cluster")
  }

  @Test
  def validateMultiNodeConfiguration_rejectsMultiNodeWithLocalProvider(): Unit = {
    assertThrows(classOf[IllegalStateException], () =>
      OnStartStop.validateMultiNodeConfiguration(multiNode = true, actorProvider = "local"))
  }

  @Test
  def validateMultiNodeConfiguration_rejectsClusterProviderInSingleNodeMode(): Unit = {
    assertThrows(classOf[IllegalStateException], () =>
      OnStartStop.validateMultiNodeConfiguration(multiNode = false, actorProvider = "cluster"))
  }
}
