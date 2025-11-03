package batch

import java.util.concurrent.{LinkedBlockingQueue, TimeUnit}

import akka.actor.{AbstractActor, ActorRef, ActorSystem, Props}
import batch.BatchDispatcher.{BatchAction, BatchMsg, TellWhom}
import org.junit.Assert._
import org.junit._
import play.api.libs.json.Json

import scala.jdk.javaapi.CollectionConverters

class BatchDispatcherTestScala {

  import BatchDispatcherTestScala._

  private var system: ActorSystem = _

  @Before
  def setup(): Unit = {
    val cfg = com.typesafe.config.ConfigFactory.parseString("akka.loglevel=WARNING\nakka.log-dead-letters=off")
    system = ActorSystem.create("bd-test", cfg)
  }

  @After
  def tearDown(): Unit = {
    if (system != null) system.terminate()
  }

  private def newDispatcher(batchId: Long,
                            registry: BatchDispatcherRegistry = new SilentRegistry(BatchDispatcherTestScala.NoopFactory),
                            handler: BatchActionHandler = new StubHandler(Nil),
                            builder: BatchActionMsgBuilder = new StubBuilder()): BatchDispatcher = {
    new BatchDispatcher(system, registry, handler, builder, batchId)
  }

  @Test
  def registerChannel_sendsOpenedToSenderOnly(): Unit = {
    val openedMsg = BatchMsg(Json.obj("action" -> BatchAction.Opened.toString), TellWhom.SenderOnly)
    val builder = new StubBuilder(openedMsg)
    val dispatcher = newDispatcher(1L, builder = builder)

    val q = new LinkedBlockingQueue[Any]()
    val channel = system.actorOf(Props.create(classOf[RecordingActor], q))

    dispatcher.registerChannel(10L, channel)

    val msg = q.poll(2, TimeUnit.SECONDS)
    assertNotNull("Expected an Opened message", msg)
    msg match {
      case m: BatchMsg =>
        assertEquals(BatchAction.Opened.toString, (m.json \ "action").as[String])
      case other => fail(s"Unexpected message type: $other")
    }
  }

  @Test
  def handleActionMsg_All_broadcastsToAllRegistered(): Unit = {
    val mAll = BatchMsg(Json.obj("t" -> "all"), TellWhom.All)
    val handler = new StubHandler(List(mAll))
    val dispatcher = newDispatcher(2L, handler = handler)

    val q1 = new LinkedBlockingQueue[Any]()
    val q2 = new LinkedBlockingQueue[Any]()
    val ch1 = system.actorOf(Props.create(classOf[RecordingActor], q1))
    val ch2 = system.actorOf(Props.create(classOf[RecordingActor], q2))
    dispatcher.registerChannel(101L, ch1)
    dispatcher.registerChannel(102L, ch2)

    // Discard the initial OPENED message sent to each channel upon register
    q1.poll(2, TimeUnit.SECONDS)
    q2.poll(2, TimeUnit.SECONDS)

    // Sender can be any actor; here we use ch1
    dispatcher.handleActionMsg(BatchMsg(Json.obj("dummy" -> true)), 101L, ch1)

    val r1 = q1.poll(2, TimeUnit.SECONDS)
    val r2 = q2.poll(2, TimeUnit.SECONDS)
    assertTrue(r1.isInstanceOf[BatchMsg])
    assertTrue(r2.isInstanceOf[BatchMsg])
    assertEquals((mAll.json \ "t").as[String], (r1.asInstanceOf[BatchMsg].json \ "t").as[String])
    assertEquals((mAll.json \ "t").as[String], (r2.asInstanceOf[BatchMsg].json \ "t").as[String])
  }

  @Test
  def handleActionMsg_SenderOnly_sendsOnlyBackToSender(): Unit = {
    val mSender = BatchMsg(Json.obj("t" -> "sender"), TellWhom.SenderOnly)
    val handler = new StubHandler(List(mSender))
    val dispatcher = newDispatcher(3L, handler = handler)

    val q1 = new LinkedBlockingQueue[Any]()
    val q2 = new LinkedBlockingQueue[Any]()
    val ch1 = system.actorOf(Props.create(classOf[RecordingActor], q1))
    val ch2 = system.actorOf(Props.create(classOf[RecordingActor], q2))
    dispatcher.registerChannel(201L, ch1)
    dispatcher.registerChannel(202L, ch2)

    // Discard initial OPENED messages
    q1.poll(2, TimeUnit.SECONDS)
    q2.poll(2, TimeUnit.SECONDS)

    dispatcher.handleActionMsg(BatchMsg(Json.obj("dummy" -> true)), 201L, ch1)

    val r1 = q1.poll(2, TimeUnit.SECONDS)
    val r2 = q2.poll(2, TimeUnit.SECONDS)
    assertTrue("Sender should receive a message", r1.isInstanceOf[BatchMsg])
    assertNull("Non-sender should not receive a message", r2)
  }

  @Test
  def unregisterChannel_whenEmpty_unregistersDispatcherInRegistry(): Unit = {
    val factory = new BatchDispatcher.Factory { override def create(batchId: Long): BatchDispatcher = null }
    val registry = new TestRegistry(factory)
    val dispatcher = newDispatcher(42L, registry = registry)

    val q = new LinkedBlockingQueue[Any]()
    val ch = system.actorOf(Props.create(classOf[RecordingActor], q))
    dispatcher.registerChannel(301L, ch)

    dispatcher.unregisterChannel(301L)

    assertTrue("Registry should have been notified to unregister", registry.unregisteredIds.contains(42L))
  }

  @Test
  def unregisterChannel_unknownId_stillTriggersUnregisterIfEmpty(): Unit = {
    val factory = new BatchDispatcher.Factory { override def create(batchId: Long): BatchDispatcher = null }
    val registry = new TestRegistry(factory)
    val dispatcher = newDispatcher(43L, registry = registry)

    dispatcher.unregisterChannel(9999L) // no channel registered, registry should still be called

    assertTrue(registry.unregisteredIds.contains(43L))
  }

  @Test
  def poisonChannel_sendsClosedAndUnregisters(): Unit = {
    val factory = new BatchDispatcher.Factory { override def create(batchId: Long): BatchDispatcher = null }
    val registry = new TestRegistry(factory)
    val dispatcher = newDispatcher(44L, registry = registry)

    val q = new LinkedBlockingQueue[Any]()
    val ch = system.actorOf(Props.create(classOf[RecordingActor], q))
    dispatcher.registerChannel(401L, ch)

    // Discard initial OPENED
    q.poll(2, TimeUnit.SECONDS)

    dispatcher.poisonChannel(401L)

    val msg = q.poll(2, TimeUnit.SECONDS)
    assertNotNull("Expected a Closed message", msg)
    msg match {
      case m: BatchMsg =>
        assertEquals(BatchAction.Closed.toString, (m.json \ "action").as[String])
      case other => fail(s"Unexpected message: $other")
    }

    assertTrue("Dispatcher should unregister itself when empty", registry.unregisteredIds.contains(44L))
  }
}

object BatchDispatcherTestScala {

  class RecordingActor(queue: LinkedBlockingQueue[Any]) extends AbstractActor {
    override def createReceive: AbstractActor.Receive = receiveBuilder().matchAny(msg => queue.offer(msg)).build()
  }

  class StubHandler(resultMsgs: List[BatchMsg]) extends BatchActionHandler(null, null, null) {
    override def handleActionMsg(actionMsg: BatchMsg, batchId: Long): List[BatchMsg] = resultMsgs
  }

  class StubBuilder(opened: BatchMsg) extends BatchActionMsgBuilder(null, null) {
    def this() = this(BatchMsg(Json.obj("action" -> BatchAction.Opened.toString), TellWhom.SenderOnly))
    override def buildSessionData(batchId: Long, action: BatchAction.BatchAction, tellWhom: TellWhom.TellWhom): BatchMsg = opened
  }

  object NoopFactory extends BatchDispatcher.Factory {
    override def create(batchId: Long): BatchDispatcher = null
  }

  class SilentRegistry(factory: BatchDispatcher.Factory) extends BatchDispatcherRegistry(factory) { }

  class TestRegistry(factory: BatchDispatcher.Factory) extends BatchDispatcherRegistry(factory) {
    var unregisteredIds: List[Long] = List.empty
    override def unregister(batchId: Long): Unit = {
      unregisteredIds = batchId :: unregisteredIds
      super.unregister(batchId)
    }
  }
}
