package batch.session;

import batch.BatchActionHandler;
import batch.BatchActionMsgBuilder;
import batch.BatchDispatcher;
import batch.BatchDispatcher.BatchMsg;
import batch.BatchDispatcherRegistry;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Props;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import play.api.libs.json.JsObject;
import play.api.libs.json.Json$;
import scala.Enumeration;
import scala.collection.immutable.List;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static batch.BatchDispatcher.*;
import static org.junit.Assert.*;
import static scala.jdk.javaapi.CollectionConverters.asScala;

public class BatchDispatcherTest {

    private ActorSystem system;

    public static class RecordingActor extends AbstractActor {
        private final BlockingQueue<Object> queue;

        public RecordingActor(BlockingQueue<Object> queue) {
            this.queue = queue;
        }

        @Override
        public Receive createReceive() {
            //noinspection ResultOfMethodCallIgnored
            return receiveBuilder().matchAny(queue::offer).build();
        }
    }

    @Before
    public void setup() {
        Config config = ConfigFactory.parseString(
                "org.apache.pekko.loglevel=WARNING\norg.apache.pekko.log-dead-letters=off");
        system = ActorSystem.create("bd-test", config);
    }

    @After
    public void tearDown() {
        if (system != null) system.terminate();
    }

    private BatchDispatcher newDispatcher(long batchId,
                                          BatchDispatcherRegistry registry,
                                          BatchActionHandler handler,
                                          BatchActionMsgBuilder builder) {
        return new BatchDispatcher(registry, handler, builder, batchId);
    }

    @Test
    public void registerChannel_sendsOpenedToSenderOnly() throws InterruptedException {
        BatchMsg openedMsg = msg(js("{\"action\":\"OPENED\"}"), TW_SenderOnly());
        BatchDispatcher dispatcher = newDispatcher(1L, new SilentRegistry(NoopFactory.INSTANCE),
                new StubHandler(messages()), new StubBuilder(openedMsg));

        BlockingQueue<Object> queue = new LinkedBlockingQueue<>();
        ActorRef channel = system.actorOf(Props.create(RecordingActor.class, queue));

        dispatcher.registerChannel(10L, channel);

        assertEquals(openedMsg, queue.poll(2, TimeUnit.SECONDS));
    }

    @Test
    public void handleActionMsg_All_broadcastsToAllRegistered() throws InterruptedException {
        BatchMsg toAll = msg(js("{\"t\":\"all\"}"), TW_All());
        BatchDispatcher dispatcher = newDispatcher(2L, new SilentRegistry(NoopFactory.INSTANCE),
                new StubHandler(messages(toAll)), new StubBuilder());

        BlockingQueue<Object> queue1 = new LinkedBlockingQueue<>();
        BlockingQueue<Object> queue2 = new LinkedBlockingQueue<>();
        ActorRef channel1 = system.actorOf(Props.create(RecordingActor.class, queue1));
        ActorRef channel2 = system.actorOf(Props.create(RecordingActor.class, queue2));
        dispatcher.registerChannel(101L, channel1);
        dispatcher.registerChannel(102L, channel2);
        discardOpened(queue1, queue2);

        dispatcher.handleActionMsg(msg(js("{\"dummy\":true}"), TW_Unknown()), 101L, channel1);

        assertEquals(toAll, queue1.poll(2, TimeUnit.SECONDS));
        assertEquals(toAll, queue2.poll(2, TimeUnit.SECONDS));
    }

    @Test
    public void handleActionMsg_SenderOnly_sendsOnlyBackToSender() throws InterruptedException {
        BatchMsg toSender = msg(js("{\"t\":\"sender\"}"), TW_SenderOnly());
        BatchDispatcher dispatcher = newDispatcher(3L, new SilentRegistry(NoopFactory.INSTANCE),
                new StubHandler(messages(toSender)), new StubBuilder());

        BlockingQueue<Object> queue1 = new LinkedBlockingQueue<>();
        BlockingQueue<Object> queue2 = new LinkedBlockingQueue<>();
        ActorRef channel1 = system.actorOf(Props.create(RecordingActor.class, queue1));
        ActorRef channel2 = system.actorOf(Props.create(RecordingActor.class, queue2));
        dispatcher.registerChannel(201L, channel1);
        dispatcher.registerChannel(202L, channel2);
        discardOpened(queue1, queue2);

        dispatcher.handleActionMsg(msg(js("{\"dummy\":true}"), TW_Unknown()), 201L, channel1);

        assertEquals(toSender, queue1.poll(2, TimeUnit.SECONDS));
        assertNull("Non-sender should not receive a message", queue2.poll(200, TimeUnit.MILLISECONDS));
    }

    @Test
    public void handleActionMsg_sessionUpdate_sendsPatchToAllAndAckToSender() throws InterruptedException {
        BatchMsg patch = msg(js("{\"action\":\"SESSION\",\"version\":2}"), TW_All());
        BatchMsg ack = msg(js("{\"action\":\"SESSION_ACK\",\"id\":10}"), TW_SenderOnly());
        BatchDispatcher dispatcher = newDispatcher(4L, new SilentRegistry(NoopFactory.INSTANCE),
                new StubHandler(messages(patch, ack)), new StubBuilder());

        BlockingQueue<Object> senderQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Object> otherQueue = new LinkedBlockingQueue<>();
        ActorRef sender = system.actorOf(Props.create(RecordingActor.class, senderQueue));
        ActorRef other = system.actorOf(Props.create(RecordingActor.class, otherQueue));
        dispatcher.registerChannel(201L, sender);
        dispatcher.registerChannel(202L, other);
        discardOpened(senderQueue, otherQueue);

        dispatcher.handleActionMsg(msg(js("{\"action\":\"SESSION\"}"), TW_Unknown()), 201L, sender);

        assertEquals(patch, senderQueue.poll(2, TimeUnit.SECONDS));
        assertEquals(ack, senderQueue.poll(2, TimeUnit.SECONDS));
        assertEquals(patch, otherQueue.poll(2, TimeUnit.SECONDS));
        assertNull("Non-sender must not receive the ACK", otherQueue.poll(200, TimeUnit.MILLISECONDS));
    }

    @Test
    public void unregisterChannel_whenEmpty_unregistersDispatcherInRegistry() {
        TestRegistry registry = new TestRegistry(NoopFactory.INSTANCE);
        BatchDispatcher dispatcher = newDispatcher(42L, registry,
                new StubHandler(messages()), new StubBuilder());
        ActorRef channel = system.actorOf(Props.create(RecordingActor.class, new LinkedBlockingQueue<>()));
        dispatcher.registerChannel(301L, channel);

        dispatcher.unregisterChannel(301L);

        assertTrue("Registry should have been notified to unregister", registry.unregisteredIds.contains(42L));
    }

    @Test
    public void unregisterChannel_unknownId_stillTriggersUnregisterIfEmpty() {
        TestRegistry registry = new TestRegistry(NoopFactory.INSTANCE);
        BatchDispatcher dispatcher = newDispatcher(43L, registry,
                new StubHandler(messages()), new StubBuilder());

        dispatcher.unregisterChannel(9999L);

        assertTrue(registry.unregisteredIds.contains(43L));
    }

    @Test
    public void poisonChannel_sendsClosedAndUnregisters() throws InterruptedException {
        TestRegistry registry = new TestRegistry(NoopFactory.INSTANCE);
        BatchDispatcher dispatcher = newDispatcher(44L, registry,
                new StubHandler(messages()), new StubBuilder());
        BlockingQueue<Object> queue = new LinkedBlockingQueue<>();
        ActorRef channel = system.actorOf(Props.create(RecordingActor.class, queue));
        dispatcher.registerChannel(401L, channel);
        queue.poll(2, TimeUnit.SECONDS);

        dispatcher.poisonChannel(401L);

        BatchMsg closed = (BatchMsg) queue.poll(2, TimeUnit.SECONDS);
        assertNotNull("Expected a Closed message", closed);
        assertEquals(js("{\"action\":\"CLOSED\"}"), closed.json());
        assertTrue("Dispatcher should unregister itself when empty", registry.unregisteredIds.contains(44L));
    }

    private void discardOpened(BlockingQueue<Object> queue1,
                               BlockingQueue<Object> queue2) throws InterruptedException {
        queue1.poll(2, TimeUnit.SECONDS);
        queue2.poll(2, TimeUnit.SECONDS);
    }

    private static JsObject js(String json) {
        return (JsObject) Json$.MODULE$.parse(json);
    }

    private static BatchMsg msg(JsObject json, Enumeration.Value tellWhom) {
        return new BatchMsg(json, tellWhom);
    }

    private static List<BatchMsg> messages(BatchMsg... messages) {
        return asScala(Arrays.asList(messages)).toList();
    }

    private static Enumeration.Value TW_All() {
        return TellWhom$.MODULE$.All();
    }

    private static Enumeration.Value TW_SenderOnly() {
        return TellWhom$.MODULE$.SenderOnly();
    }

    private static Enumeration.Value TW_Unknown() {
        return TellWhom$.MODULE$.Unknown();
    }

    private static final class NoopFactory implements BatchDispatcher.Factory {
        private static final NoopFactory INSTANCE = new NoopFactory();

        @Override
        public BatchDispatcher create(long batchId) {
            return null;
        }
    }

    private static class StubHandler extends BatchActionHandler {
        private final List<BatchMsg> resultMessages;

        StubHandler(List<BatchMsg> resultMessages) {
            super(null, null);
            this.resultMessages = resultMessages;
        }

        @Override
        public List<BatchMsg> handleActionMsg(BatchMsg actionMsg, long batchId) {
            return resultMessages;
        }
    }

    private static class StubBuilder extends BatchActionMsgBuilder {
        private final BatchMsg opened;

        StubBuilder() {
            this(msg(js("{\"action\":\"OPENED\"}"), TW_SenderOnly()));
        }

        StubBuilder(BatchMsg opened) {
            super(null);
            this.opened = opened;
        }

        @Override
        public BatchMsg buildSessionData(long batchId, Enumeration.Value action, Enumeration.Value tellWhom) {
            return opened;
        }
    }

    private static class SilentRegistry extends BatchDispatcherRegistry {
        SilentRegistry(BatchDispatcher.Factory factory) {
            super(factory);
        }
    }

    private static class TestRegistry extends BatchDispatcherRegistry {
        private final java.util.List<Long> unregisteredIds = new ArrayList<>();

        TestRegistry(BatchDispatcher.Factory factory) {
            super(factory);
        }

        @Override
        public void unregister(long batchId) {
            unregisteredIds.add(batchId);
            super.unregister(batchId);
        }
    }
}
