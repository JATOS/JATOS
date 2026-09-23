package cluster

import batch.BatchDispatcher.{BatchAction, BatchMsg, TellWhom}
import batch.{BatchActionHandler, BatchActionMsgBuilder, BatchDispatcher}
import group.GroupDispatcher
import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.{Actor, ActorRef, ActorSystem, Props}
import org.apache.pekko.cluster.{Cluster, MemberStatus}
import org.junit.Assert.{assertEquals, assertNull}
import org.junit.{After, Test}
import org.mockito.Mockito.mock
import play.api.libs.json.{JsObject, Json}

import java.util.concurrent.{CountDownLatch, LinkedBlockingQueue, TimeUnit}
import scala.collection.mutable.ListBuffer
import scala.concurrent.Await
import scala.concurrent.duration._

class DistributedBatchMessagingTest {

  import DistributedBatchMessagingTest.FixedNodeIdentity

  private val actorSystems = ListBuffer.empty[ActorSystem]

  @After
  def tearDown(): Unit = {
    actorSystems.foreach(system => Await.ready(system.terminate(), 10.seconds))
  }

  @Test
  def sessionPatchCrossesNodesWhileAckStaysOnOriginNode(): Unit = {
    val system1 = createActorSystem()
    val system2 = createActorSystem()
    val cluster1 = Cluster(system1)
    val cluster2 = Cluster(system2)
    cluster1.join(cluster1.selfAddress)
    cluster2.join(cluster1.selfAddress)
    awaitClusterUp(cluster1, expectedMembers = 2)
    awaitClusterUp(cluster2, expectedMembers = 2)

    val patch = BatchMsg(Json.obj("action" -> "SESSION", "version" -> 2), TellWhom.All)
    val ack = BatchMsg(Json.obj("action" -> "SESSION_ACK", "id" -> 10), TellWhom.SenderOnly)
    val opened = BatchMsg(Json.obj("action" -> "OPENED"), TellWhom.SenderOnly)
    val handler1 = new StubHandler(List(patch, ack))
    val handler2 = new StubHandler(Nil)
    val identity1 = FixedNodeIdentity("node-1")
    val identity2 = FixedNodeIdentity("node-2")
    val bus1 = new PekkoChannelMessageBus(system1, identity1)
    val bus2 = new PekkoChannelMessageBus(system2, identity2)
    val dispatcher1 = new BatchDispatcher(handler1, new StubBuilder(opened), bus1, identity1)
    val dispatcher2 = new BatchDispatcher(handler2, new StubBuilder(opened), bus2, identity2)
    val receiver1 = new ReadyDispatcherReceiver(dispatcher1)
    val receiver2 = new ReadyDispatcherReceiver(dispatcher2)
    bus1.registerLocalReceiver(receiver1)
    bus2.registerLocalReceiver(receiver2)
    receiver1.awaitReady()
    receiver2.awaitReady()

    val senderMessages = new LinkedBlockingQueue[BatchMsg]()
    val remoteMessages = new LinkedBlockingQueue[BatchMsg]()
    val unrelatedMessages = new LinkedBlockingQueue[BatchMsg]()
    val sender = system1.actorOf(Props(new QueueActor(senderMessages)))
    val remote = system2.actorOf(Props(new QueueActor(remoteMessages)))
    val unrelated = system2.actorOf(Props(new QueueActor(unrelatedMessages)))
    dispatcher1.registerChannel(10L, 101L, sender)
    dispatcher2.registerChannel(10L, 102L, remote)
    dispatcher2.registerChannel(11L, 103L, unrelated)
    assertEquals(opened, senderMessages.poll(2, TimeUnit.SECONDS))
    assertEquals(opened, remoteMessages.poll(2, TimeUnit.SECONDS))
    assertEquals(opened, unrelatedMessages.poll(2, TimeUnit.SECONDS))

    // Subscription acknowledgements are local; allow the mediator registrations to gossip to the other node.
    Thread.sleep(500)
    dispatcher1.handleActionMsg(BatchMsg(Json.obj("action" -> "SESSION")), 10L, 101L, sender)

    assertEquals(patch, senderMessages.poll(2, TimeUnit.SECONDS))
    assertEquals(ack, senderMessages.poll(2, TimeUnit.SECONDS))
    assertEquals(patch, remoteMessages.poll(10, TimeUnit.SECONDS))
    assertNull("The remote channel must not receive the ACK",
      remoteMessages.poll(300, TimeUnit.MILLISECONDS))
    assertNull("A different batch must not receive the patch",
      unrelatedMessages.poll(300, TimeUnit.MILLISECONDS))
    assertNull("The origin node must not receive its own publication again",
      senderMessages.poll(500, TimeUnit.MILLISECONDS))
    assertEquals(1, handler1.callCount)
    assertEquals(0, handler2.callCount)
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
    val system = ActorSystem("distributed-batch-test", config)
    actorSystems += system
    system
  }

  private def awaitClusterUp(cluster: Cluster, expectedMembers: Int): Unit = {
    val deadline = 10.seconds.fromNow
    while (cluster.state.members.count(_.status == MemberStatus.Up) != expectedMembers && deadline.hasTimeLeft()) {
      Thread.sleep(25)
    }
    assertEquals(expectedMembers, cluster.state.members.count(_.status == MemberStatus.Up))
  }

  private class StubHandler(messages: List[BatchMsg]) extends BatchActionHandler(null, null) {
    @volatile var callCount = 0
    override def handleActionMsg(actionMsg: BatchMsg, batchId: Long): List[BatchMsg] = {
      callCount += 1
      messages
    }
  }

  private class StubBuilder(opened: BatchMsg) extends BatchActionMsgBuilder(null) {
    override def buildSessionData(batchId: Long,
                                  action: BatchAction.Value,
                                  tellWhom: TellWhom.Value): BatchMsg = opened
  }

  private class ReadyDispatcherReceiver(dispatcher: BatchDispatcher)
    extends DispatcherChannelMessageReceiver(dispatcher, mock(classOf[GroupDispatcher])) {
    private val readyLatch = new CountDownLatch(1)
    override def ready(): Unit = readyLatch.countDown()
    def awaitReady(): Unit = assert(readyLatch.await(10, TimeUnit.SECONDS), "Gateway did not subscribe in time")
  }

  private class QueueActor(queue: LinkedBlockingQueue[BatchMsg]) extends Actor {
    override def receive: Receive = {
      case message: BatchMsg => queue.offer(message)
    }
  }
}

object DistributedBatchMessagingTest {
  private final case class FixedNodeIdentity(override val id: String) extends NodeIdentity
}
