package cluster

import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.cluster.{Cluster, MemberStatus}
import org.apache.pekko.serialization.SerializationExtension
import org.junit.Assert.{assertEquals, assertNull}
import org.junit.{After, Test}

import java.util.concurrent.{CountDownLatch, LinkedBlockingQueue, TimeUnit}
import scala.collection.mutable.ListBuffer
import scala.concurrent.Await
import scala.concurrent.duration._

class PekkoChannelMessageBusTest {

  import PekkoChannelMessageBusTest._

  private val actorSystems = ListBuffer.empty[ActorSystem]

  @After
  def tearDown(): Unit = {
    actorSystems.foreach(system => Await.ready(system.terminate(), 10.seconds))
  }

  @Test
  def messagesAreSerializedAndPublishedToTheOtherNodeOnly(): Unit = {
    val system1 = createActorSystem()
    val system2 = createActorSystem()
    val cluster1 = Cluster(system1)
    val cluster2 = Cluster(system2)

    cluster1.join(cluster1.selfAddress)
    cluster2.join(cluster1.selfAddress)
    awaitClusterUp(cluster1, expectedMembers = 2)
    awaitClusterUp(cluster2, expectedMembers = 2)

    val receiver1 = new RecordingReceiver(deliverDirectMessages = false)
    val receiver2 = new RecordingReceiver(deliverDirectMessages = true)
    val bus1 = new PekkoChannelMessageBus(system1, FixedNodeIdentity("node-1"))
    val bus2 = new PekkoChannelMessageBus(system2, FixedNodeIdentity("node-2"))
    bus1.registerLocalReceiver(receiver1)
    bus2.registerLocalReceiver(receiver2)

    receiver1.awaitReady()
    receiver2.awaitReady()

    val batchMessage = BatchClusterMessage("node-1", 10L, """{"action":"SESSION"}""")
    publishUntilReceived(() => bus1.publishBatchMsgToCluster(batchMessage), receiver2.batchMessages, batchMessage)
    assertNull("The origin node must ignore its own batch publication",
      receiver1.batchMessages.poll(300, TimeUnit.MILLISECONDS))

    val groupMessage = GroupClusterMessage(
      "node-2", 20L, 30L, """{"msg":"hello"}""", GroupRecipients.Recipient(40L))
    publishUntilReceived(() => bus2.publishGroupMsgToCluster(groupMessage), receiver1.groupMessages, groupMessage)
    assertNull("The origin node must ignore its own group publication",
      receiver2.groupMessages.poll(300, TimeUnit.MILLISECONDS))

    val directMessage = GroupDirectMsgDeliveryRequest(
      "node-1", "delivery-1", 20L, 30L, 40L, """{"msg":"direct"}""")
    val expectedAck = GroupDirectMsgDeliveryAck("node-2", "node-1", "delivery-1")
    publishUntilReceived(
      () => bus1.publishGroupDirectMsgToCluster(directMessage), receiver1.directDeliveryAcks, expectedAck)
    assertEquals(directMessage, receiver2.directMessages.poll(2, TimeUnit.SECONDS))
    assertNull("A repeated delivery request must not deliver the message twice",
      receiver2.directMessages.poll(300, TimeUnit.MILLISECONDS))
  }

  @Test
  def clusterMessagesRoundTripThroughJacksonCbor(): Unit = {
    val system = createActorSystem()
    val serialization = SerializationExtension(system)
    val messages: Seq[ChannelClusterMessage] = Seq(
      BatchClusterMessage("node-1", 10L, "{}"),
      GroupClusterMessage("node-1", 20L, 30L, "{}", GroupRecipients.All()),
      GroupClusterMessage("node-1", 20L, 30L, "{}", GroupRecipients.AllButSender()),
      GroupClusterMessage("node-1", 20L, 30L, "{}", GroupRecipients.Recipient(40L)),
      GroupDirectMsgDeliveryRequest("node-1", "delivery-1", 20L, 30L, 40L, "{}"),
      GroupDirectMsgDeliveryAck("node-2", "node-1", "delivery-1"),
      GroupChannelPresenceRequest("node-1", "presence-1", 40L),
      GroupChannelPresenceAck("node-2", "node-1", "presence-1"),
      GroupOpenChannelsRequest("node-1", "open-channels-1", 20L),
      GroupOpenChannelsResponse("node-2", "node-1", "open-channels-1", 20L, Set("30", "40"), 2),
      GroupReassignmentRequest("node-1", 30L, 20L, 21L),
      GroupChannelCloseRequest("node-1", 20L, 30L))

    messages.foreach { message =>
      val serializer = serialization.findSerializerFor(message)
      assertEquals("org.apache.pekko.serialization.jackson.JacksonCborSerializer",
        serializer.getClass.getName)
      val bytes = serialization.serialize(message).get
      val deserialized = serialization.deserialize(bytes, message.getClass).get
      assertEquals(message, deserialized)
    }
  }

  private def createActorSystem(): ActorSystem = {
    val config = ConfigFactory.parseString(
      s"""
         |pekko {
         |  actor {
         |    provider = cluster
         |    serialization-bindings {
         |      "cluster.ClusterSerializable" = jackson-cbor
         |    }
         |  }
         |  remote.artery.canonical {
         |    hostname = "127.0.0.1"
         |    port = 0
         |  }
         |  cluster {
         |    jmx.multi-mbeans-in-same-jvm = on
         |    pub-sub.gossip-interval = 100ms
         |  }
         |  loglevel = WARNING
         |  log-dead-letters = off
         |}
         |""".stripMargin)
      .withFallback(ConfigFactory.defaultReference())

    val system = ActorSystem("channel-pubsub-test", config)
    actorSystems += system
    system
  }

  //noinspection SameParameterValue
  private def awaitClusterUp(cluster: Cluster, expectedMembers: Int): Unit = {
    awaitCondition(10.seconds) {
      cluster.state.members.count(_.status == MemberStatus.Up) == expectedMembers
    }
  }

  private def publishUntilReceived[T](publish: () => Unit,
                                      queue: LinkedBlockingQueue[T],
                                      expected: T): Unit = {
    val deadline = 10.seconds.fromNow
    var received: T = null.asInstanceOf[T]
    while (received == null && deadline.hasTimeLeft()) {
      publish()
      received = queue.poll(200, TimeUnit.MILLISECONDS)
    }
    assertEquals(expected, received)
  }

  private def awaitCondition(timeout: FiniteDuration)(condition: => Boolean): Unit = {
    val deadline = timeout.fromNow
    while (!condition && deadline.hasTimeLeft()) {
      Thread.sleep(25)
    }
    assert(condition)
  }

  private class RecordingReceiver(deliverDirectMessages: Boolean) extends ChannelMessageReceiver {
    val batchMessages = new LinkedBlockingQueue[BatchClusterMessage]()
    val groupMessages = new LinkedBlockingQueue[GroupClusterMessage]()
    val directMessages = new LinkedBlockingQueue[GroupDirectMsgDeliveryRequest]()
    val directDeliveryAcks = new LinkedBlockingQueue[GroupDirectMsgDeliveryAck]()
    private val readyLatch = new CountDownLatch(1)

    override def receiveBatch(message: BatchClusterMessage): Unit = batchMessages.offer(message)

    override def receiveGroup(message: GroupClusterMessage): Unit = groupMessages.offer(message)

    override def receiveGroupDirectMsg(message: GroupDirectMsgDeliveryRequest): Boolean = {
      directMessages.offer(message)
      deliverDirectMessages
    }

    override def receiveGroupDirectMsgDeliveryAck(message: GroupDirectMsgDeliveryAck): Unit =
      directDeliveryAcks.offer(message)

    override def receiveGroupChannelPresenceRequest(message: GroupChannelPresenceRequest): Boolean = false

    override def receiveGroupChannelPresenceAck(message: GroupChannelPresenceAck): Unit = ()

    override def receiveGroupReassignmentRequest(message: GroupReassignmentRequest): Unit = ()

    override def ready(): Unit = readyLatch.countDown()

    def awaitReady(): Unit = {
      assert(readyLatch.await(10, TimeUnit.SECONDS), "PubSub gateway did not subscribe in time")
    }
  }
}

object PekkoChannelMessageBusTest {
  private final case class FixedNodeIdentity(override val id: String) extends NodeIdentity
}
