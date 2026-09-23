package cluster

import batch.BatchDispatcher
import com.typesafe.config.ConfigFactory
import daos.common.StudyDao
import group.GroupDispatcher.{GroupAction, GroupMsg, TellWhom}
import group.{GroupActionHandler, GroupActionMsgBuilder, GroupChannelActor, GroupDispatcher}
import general.common.Common
import models.common.Study.GroupSessionWriteScope
import org.apache.pekko.actor.{Actor, ActorRef, ActorSystem, Props}
import org.apache.pekko.cluster.{Cluster, MemberStatus}
import org.junit.Assert.{assertEquals, assertNull}
import org.junit.{After, Before, Test}
import org.mockito.{MockedStatic, Mockito}
import org.mockito.Mockito.{mock, when}
import play.api.libs.json.{JsObject, Json}

import java.util.concurrent.{CountDownLatch, LinkedBlockingQueue, TimeUnit}
import java.time.Duration
import scala.collection.mutable.ListBuffer
import scala.concurrent.Await
import scala.concurrent.duration._

class DistributedGroupMessagingTest {

  private val actorSystems = ListBuffer.empty[ActorSystem]
  private var common: MockedStatic[Common] = _

  @Before
  def setUp(): Unit = {
    common = Mockito.mockStatic(classOf[Common])
    common.when(() => Common.getGroupMessageAckTimeout).thenReturn(Duration.ofSeconds(3))
  }

  @After
  def tearDown(): Unit = {
    actorSystems.foreach(system => Await.ready(system.terminate(), 10.seconds))
    common.close()
  }

  @Test
  def groupMessagesAndLifecycleEventsCrossNodes(): Unit = {
    val system1 = createActorSystem()
    val system2 = createActorSystem()
    val cluster1 = Cluster(system1)
    val cluster2 = Cluster(system2)
    cluster1.join(cluster1.selfAddress)
    cluster2.join(cluster1.selfAddress)
    awaitClusterUp(cluster1, expectedMembers = 2)
    awaitClusterUp(cluster2, expectedMembers = 2)

    val identity1 = new FixedNodeIdentity("node-1")
    val identity2 = new FixedNodeIdentity("node-2")
    val bus1 = new PekkoChannelMessageBus(system1, identity1)
    val bus2 = new PekkoChannelMessageBus(system2, identity2)
    val dispatcher1 = newDispatcher(bus1, identity1, system1, sessionHandler = true)
    val dispatcher2 = newDispatcher(bus2, identity2, system2, sessionHandler = false)
    val receiver1 = new ReadyGroupReceiver(dispatcher1)
    val receiver2 = new ReadyGroupReceiver(dispatcher2)
    bus1.registerLocalReceiver(receiver1)
    bus2.registerLocalReceiver(receiver2)
    receiver1.awaitReady()
    receiver2.awaitReady()

    // Subscription acknowledgements are local; allow mediator registrations to gossip between nodes.
    Thread.sleep(500)

    val groupResultId = 100L
    val senderMessages = new LinkedBlockingQueue[GroupMsg]()
    val remoteMessages = new LinkedBlockingQueue[GroupMsg]()
    val senderRef = system1.actorOf(Props(new QueueActor(senderMessages)))
    val remoteRef = system2.actorOf(Props(new QueueActor(remoteMessages)))
    val senderChannel = channel(senderRef)
    val remoteChannel = channel(remoteRef)

    dispatcher1.registerChannel(groupResultId, 1L, senderChannel)
    val senderOpened = senderMessages.poll(2, TimeUnit.SECONDS)
    assertAction("OPENED", senderOpened)
    assertEquals(Set("1"), (senderOpened.json \ "channels").as[Set[String]])
    dispatcher2.registerChannel(groupResultId, 2L, remoteChannel)
    val remoteOpened = remoteMessages.poll(2, TimeUnit.SECONDS)
    assertAction("OPENED", remoteOpened)
    assertEquals(Set("1", "2"), (remoteOpened.json \ "channels").as[Set[String]])
    assertAction("CHANNEL_OPENED", senderMessages.poll(10, TimeUnit.SECONDS))
    senderMessages.clear()
    remoteMessages.clear()

    // The leave request can arrive on a different node from the departing member's channel.
    val departingMessages = new LinkedBlockingQueue[GroupMsg]()
    val departingRef = system2.actorOf(Props(new QueueActor(departingMessages)))
    dispatcher2.registerChannel(groupResultId, 3L, channel(departingRef))
    assertAction("OPENED", departingMessages.poll(2, TimeUnit.SECONDS))
    assertAction("CHANNEL_OPENED", senderMessages.poll(10, TimeUnit.SECONDS))
    assertAction("CHANNEL_OPENED", remoteMessages.poll(2, TimeUnit.SECONDS))
    senderMessages.clear()
    remoteMessages.clear()

    dispatcher1.left(groupResultId, 3L)
    dispatcher1.poisonChannel(groupResultId, 3L)

    assertEquals(Set("LEFT", "CHANNEL_CLOSED"), Set(
      (senderMessages.poll(10, TimeUnit.SECONDS).json \ "action").as[String],
      (senderMessages.poll(10, TimeUnit.SECONDS).json \ "action").as[String]))
    assertEquals(Set("LEFT", "CHANNEL_CLOSED"), Set(
      (remoteMessages.poll(10, TimeUnit.SECONDS).json \ "action").as[String],
      (remoteMessages.poll(10, TimeUnit.SECONDS).json \ "action").as[String]))
    assertEquals(Set("LEFT", "CLOSED"), Set(
      (departingMessages.poll(10, TimeUnit.SECONDS).json \ "action").as[String],
      (departingMessages.poll(10, TimeUnit.SECONDS).json \ "action").as[String]))
    assertEquals(false, dispatcher2.hasChannel(3L))
    senderMessages.clear()
    remoteMessages.clear()

    assertEquals(true, Await.result(dispatcher1.hasChannelInCluster(2L), 5.seconds))
    assertEquals(false, Await.result(dispatcher1.hasChannelInCluster(999L), 5.seconds))

    val sessionInput = GroupMsg(Json.obj("action" -> "SESSION"))
    dispatcher1.handleGroupMsg(sessionInput, groupResultId, 1L, senderRef)
    assertAction("SESSION", senderMessages.poll(2, TimeUnit.SECONDS))
    assertAction("SESSION_ACK", senderMessages.poll(2, TimeUnit.SECONDS))
    assertAction("SESSION", remoteMessages.poll(10, TimeUnit.SECONDS))
    assertNull(remoteMessages.poll(300, TimeUnit.MILLISECONDS))

    val broadcast = Json.obj("text" -> "hello all")
    dispatcher1.handleGroupMsg(GroupMsg(broadcast), groupResultId, 1L, senderRef)
    assertEquals(broadcast, remoteMessages.poll(10, TimeUnit.SECONDS).json)
    assertNull(senderMessages.poll(300, TimeUnit.MILLISECONDS))

    val direct = Json.obj("recipient" -> "2", "text" -> "hello member")
    dispatcher1.handleGroupMsg(GroupMsg(direct), groupResultId, 1L, senderRef)
    assertEquals(direct, remoteMessages.poll(10, TimeUnit.SECONDS).json)
    assertNull(senderMessages.poll(1500, TimeUnit.MILLISECONDS))

    val missingDirect = Json.obj("recipient" -> "999", "text" -> "anyone there?")
    dispatcher1.handleGroupMsg(GroupMsg(missingDirect), groupResultId, 1L, senderRef)
    assertAction("ERROR", senderMessages.poll(5, TimeUnit.SECONDS))

    dispatcher2.joined(groupResultId, 2L)
    assertAction("JOINED", senderMessages.poll(10, TimeUnit.SECONDS))
    assertAction("JOINED", remoteMessages.poll(2, TimeUnit.SECONDS))

    dispatcher2.left(groupResultId, 2L)
    assertAction("LEFT", senderMessages.poll(10, TimeUnit.SECONDS))
    assertAction("LEFT", remoteMessages.poll(2, TimeUnit.SECONDS))

    val differentGroupResultId = 101L
    dispatcher1.reassignChannel(2L, groupResultId, differentGroupResultId)
    assertEquals(Set("LEFT", "CHANNEL_CLOSED"), Set(
      (senderMessages.poll(10, TimeUnit.SECONDS).json \ "action").as[String],
      (senderMessages.poll(10, TimeUnit.SECONDS).json \ "action").as[String]))
    assertAction("LEFT", remoteMessages.poll(10, TimeUnit.SECONDS))
    assertAction("OPENED", remoteMessages.poll(10, TimeUnit.SECONDS))
    assertAction("JOINED", remoteMessages.poll(10, TimeUnit.SECONDS))

    val directAfterReassignment = Json.obj("recipient" -> "2", "text" -> "new group")
    dispatcher1.handleGroupMsg(
      GroupMsg(directAfterReassignment), differentGroupResultId, 1L, senderRef)
    assertEquals(directAfterReassignment, remoteMessages.poll(10, TimeUnit.SECONDS).json)
  }

  private def newDispatcher(messagePublisher: ChannelMessagePublisher,
                            nodeIdentity: NodeIdentity,
                            actorSystem: ActorSystem,
                            sessionHandler: Boolean): GroupDispatcher = {
    val studyDao = mock(classOf[StudyDao])
    when(studyDao.findGroupSessionWriteScope(org.mockito.ArgumentMatchers.anyLong()))
      .thenReturn(GroupSessionWriteScope.SHARED)
    new GroupDispatcher(
      new StubHandler(sessionHandler),
      new StubBuilder,
      studyDao,
      messagePublisher,
      nodeIdentity,
      mock(classOf[Common]),
      actorSystem)
  }

  private def channel(ref: ActorRef): GroupChannelActor = {
    val result = mock(classOf[GroupChannelActor])
    when(result.self).thenReturn(ref)
    result
  }

  private def assertAction(expected: String, message: GroupMsg): Unit = {
    assertEquals(expected, (message.json \ "action").as[String])
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
    val system = ActorSystem("distributed-group-test", config)
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

  private class StubHandler(sessionHandler: Boolean) extends GroupActionHandler(null, null) {
    override def handleActionMsg(msg: GroupMsg,
                                 groupResultId: Long,
                                 studyResultId: Long,
                                 scope: GroupSessionWriteScope): List[GroupMsg] = {
      if (sessionHandler) {
        List(
          GroupMsg(Json.obj("action" -> "SESSION", "sessionVersion" -> 2), TellWhom.All),
          GroupMsg(Json.obj("action" -> "SESSION_ACK"), TellWhom.SenderOnly))
      } else {
        Nil
      }
    }
  }

  private class StubBuilder extends GroupActionMsgBuilder(null) {
    override def build(groupResultId: Long,
                       studyResultId: Long,
                       channelStudyResultIds: Option[Iterable[Long]],
                       includeSessionData: Boolean,
                       action: GroupAction.Value,
                       tellWhom: TellWhom.Value): GroupMsg = {
      var json = Json.obj("action" -> action.toString, "memberId" -> studyResultId.toString)
      channelStudyResultIds.foreach(ids =>
        json = json + ("channels" -> Json.toJson(ids.map(_.toString).toSeq)))
      GroupMsg(json, tellWhom)
    }
  }

  private class ReadyGroupReceiver(groupDispatcher: GroupDispatcher) extends ChannelMessageReceiver {
    private val delegate = new DispatcherChannelMessageReceiver(mock(classOf[BatchDispatcher]), groupDispatcher)
    private val readyLatch = new CountDownLatch(1)

    override def receiveBatch(message: BatchClusterMessage): Unit = delegate.receiveBatch(message)

    override def receiveGroup(message: GroupClusterMessage): Unit = delegate.receiveGroup(message)

    override def receiveGroupDirectMsg(message: GroupDirectMsgDeliveryRequest): Boolean =
      delegate.receiveGroupDirectMsg(message)

    override def receiveGroupDirectMsgDeliveryAck(message: GroupDirectMsgDeliveryAck): Unit =
      delegate.receiveGroupDirectMsgDeliveryAck(message)

    override def receiveGroupChannelPresenceRequest(message: GroupChannelPresenceRequest): Boolean =
      delegate.receiveGroupChannelPresenceRequest(message)

    override def receiveGroupChannelPresenceAck(message: GroupChannelPresenceAck): Unit =
      delegate.receiveGroupChannelPresenceAck(message)

    override def receiveGroupOpenChannelsRequest(message: GroupOpenChannelsRequest): Set[String] =
      delegate.receiveGroupOpenChannelsRequest(message)

    override def receiveGroupOpenChannelsResponse(message: GroupOpenChannelsResponse): Unit =
      delegate.receiveGroupOpenChannelsResponse(message)

    override def receiveGroupReassignmentRequest(message: GroupReassignmentRequest): Unit =
      delegate.receiveGroupReassignmentRequest(message)

    override def receiveGroupChannelCloseRequest(message: GroupChannelCloseRequest): Unit =
      delegate.receiveGroupChannelCloseRequest(message)

    override def ready(): Unit = readyLatch.countDown()

    def awaitReady(): Unit = assert(readyLatch.await(10, TimeUnit.SECONDS), "Gateway did not subscribe in time")
  }

  private class QueueActor(queue: LinkedBlockingQueue[GroupMsg]) extends Actor {
    override def receive: Receive = {
      case message: GroupMsg => queue.offer(message)
    }
  }

  private class FixedNodeIdentity(override val id: String) extends NodeIdentity
}
