package batch.session;

import batch.BatchActionHandler;
import batch.BatchActionMsgBuilder;
import batch.BatchDispatcher;
import batch.BatchDispatcher.BatchMsg;
import cluster.BatchClusterMessage;
import cluster.GroupClusterMessage;
import cluster.NodeIdentity;
import cluster.SessionMessagePublisher;
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

    private BatchDispatcher newDispatcher(BatchActionHandler handler,
                                          BatchActionMsgBuilder builder) {
        return newDispatcher(handler, builder, new RecordingMessageBus());
    }

    private BatchDispatcher newDispatcher(BatchActionHandler handler,
                                          BatchActionMsgBuilder builder,
                                          SessionMessagePublisher messagePublisher) {
        NodeIdentity nodeIdentity = new NodeIdentity() {
            @Override
            public String id() {
                return "node-1";
            }
        };
        return new BatchDispatcher(handler, builder, messagePublisher, nodeIdentity);
    }

    @Test
    public void registerChannel_sendsOpenedToSenderOnly() throws InterruptedException {
        BatchMsg openedMsg = msg(js("{\"action\":\"OPENED\"}"), TW_SenderOnly());
        BatchDispatcher dispatcher = newDispatcher(new StubHandler(messages()), new StubBuilder(openedMsg));

        BlockingQueue<Object> queue = new LinkedBlockingQueue<>();
        ActorRef channel = system.actorOf(Props.create(RecordingActor.class, queue));

        dispatcher.registerChannel(1L, 10L, channel);

        assertEquals(openedMsg, queue.poll(2, TimeUnit.SECONDS));
    }

    @Test
    public void handleActionMsg_All_broadcastsToAllRegistered() throws InterruptedException {
        BatchMsg toAll = msg(js("{\"t\":\"all\"}"), TW_All());
        BatchDispatcher dispatcher = newDispatcher(new StubHandler(messages(toAll)), new StubBuilder());

        BlockingQueue<Object> queue1 = new LinkedBlockingQueue<>();
        BlockingQueue<Object> queue2 = new LinkedBlockingQueue<>();
        ActorRef channel1 = system.actorOf(Props.create(RecordingActor.class, queue1));
        ActorRef channel2 = system.actorOf(Props.create(RecordingActor.class, queue2));
        dispatcher.registerChannel(2L, 101L, channel1);
        dispatcher.registerChannel(2L, 102L, channel2);
        discardOpened(queue1, queue2);

        dispatcher.handleActionMsg(msg(js("{\"dummy\":true}"), TW_Unknown()), 2L, 101L, channel1);

        assertEquals(toAll, queue1.poll(2, TimeUnit.SECONDS));
        assertEquals(toAll, queue2.poll(2, TimeUnit.SECONDS));
    }

    @Test
    public void handleActionMsg_All_doesNotCrossBatchBoundary() throws InterruptedException {
        BatchMsg toAll = msg(js("{\"t\":\"all\"}"), TW_All());
        BatchDispatcher dispatcher = newDispatcher(new StubHandler(messages(toAll)), new StubBuilder());
        BlockingQueue<Object> firstBatch = new LinkedBlockingQueue<>();
        BlockingQueue<Object> secondBatch = new LinkedBlockingQueue<>();
        ActorRef sender = system.actorOf(Props.create(RecordingActor.class, firstBatch));
        ActorRef otherBatch = system.actorOf(Props.create(RecordingActor.class, secondBatch));
        dispatcher.registerChannel(10L, 101L, sender);
        dispatcher.registerChannel(11L, 102L, otherBatch);
        discardOpened(firstBatch, secondBatch);

        dispatcher.handleActionMsg(msg(js("{\"dummy\":true}"), TW_Unknown()), 10L, 101L, sender);

        assertEquals(toAll, firstBatch.poll(2, TimeUnit.SECONDS));
        assertNull(secondBatch.poll(200, TimeUnit.MILLISECONDS));
    }

    @Test
    public void handleActionMsg_SenderOnly_sendsOnlyBackToSender() throws InterruptedException {
        BatchMsg toSender = msg(js("{\"t\":\"sender\"}"), TW_SenderOnly());
        BatchDispatcher dispatcher = newDispatcher(new StubHandler(messages(toSender)), new StubBuilder());

        BlockingQueue<Object> queue1 = new LinkedBlockingQueue<>();
        BlockingQueue<Object> queue2 = new LinkedBlockingQueue<>();
        ActorRef channel1 = system.actorOf(Props.create(RecordingActor.class, queue1));
        ActorRef channel2 = system.actorOf(Props.create(RecordingActor.class, queue2));
        dispatcher.registerChannel(3L, 201L, channel1);
        dispatcher.registerChannel(3L, 202L, channel2);
        discardOpened(queue1, queue2);

        dispatcher.handleActionMsg(msg(js("{\"dummy\":true}"), TW_Unknown()), 3L, 201L, channel1);

        assertEquals(toSender, queue1.poll(2, TimeUnit.SECONDS));
        assertNull("Non-sender should not receive a message", queue2.poll(200, TimeUnit.MILLISECONDS));
    }

    @Test
    public void handleActionMsg_sessionUpdate_sendsPatchToAllAndAckToSender() throws InterruptedException {
        BatchMsg patch = msg(js("{\"action\":\"SESSION\",\"version\":2}"), TW_All());
        BatchMsg ack = msg(js("{\"action\":\"SESSION_ACK\",\"id\":10}"), TW_SenderOnly());
        BatchDispatcher dispatcher = newDispatcher(new StubHandler(messages(patch, ack)), new StubBuilder());

        BlockingQueue<Object> senderQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Object> otherQueue = new LinkedBlockingQueue<>();
        ActorRef sender = system.actorOf(Props.create(RecordingActor.class, senderQueue));
        ActorRef other = system.actorOf(Props.create(RecordingActor.class, otherQueue));
        dispatcher.registerChannel(4L, 201L, sender);
        dispatcher.registerChannel(4L, 202L, other);
        discardOpened(senderQueue, otherQueue);

        dispatcher.handleActionMsg(msg(js("{\"action\":\"SESSION\"}"), TW_Unknown()), 4L, 201L, sender);

        assertEquals(patch, senderQueue.poll(2, TimeUnit.SECONDS));
        assertEquals(ack, senderQueue.poll(2, TimeUnit.SECONDS));
        assertEquals(patch, otherQueue.poll(2, TimeUnit.SECONDS));
        assertNull("Non-sender must not receive the ACK", otherQueue.poll(200, TimeUnit.MILLISECONDS));
    }

    @Test
    public void handleActionMsg_publishesAllButNotSenderOnly() {
        BatchMsg patch = msg(js("{\"action\":\"SESSION\"}"), TW_All());
        BatchMsg ack = msg(js("{\"action\":\"SESSION_ACK\"}"), TW_SenderOnly());
        RecordingMessageBus messageBus = new RecordingMessageBus();
        BatchDispatcher dispatcher = newDispatcher(
                new StubHandler(messages(patch, ack)), new StubBuilder(), messageBus);
        ActorRef sender = system.actorOf(Props.create(RecordingActor.class, new LinkedBlockingQueue<>()));

        dispatcher.handleActionMsg(msg(js("{\"action\":\"SESSION\"}"), TW_Unknown()),
                4L, 201L, sender);

        assertEquals(1, messageBus.batchMessages.size());
        assertEquals(new BatchClusterMessage("node-1", 4L, "{\"action\":\"SESSION\"}"),
                messageBus.batchMessages.getFirst());
    }

    @Test
    public void handleActionMsg_singleNodeDoesNotCallPublish() {
        BatchMsg patch = msg(js("{\"action\":\"SESSION\"}"), TW_All());
        BatchDispatcher dispatcher = newDispatcher(
                new StubHandler(messages(patch)), new StubBuilder(), new DisabledMessageBus());

        dispatcher.handleActionMsg(msg(js("{\"action\":\"SESSION\"}"), TW_Unknown()),
                4L, 201L, ActorRef.noSender());
    }

    @Test
    public void deliverFromRemote_deliversLocallyWithoutHandlingOrRepublishing() throws InterruptedException {
        StubHandler handler = new StubHandler(messages());
        RecordingMessageBus messageBus = new RecordingMessageBus();
        BatchDispatcher dispatcher = newDispatcher(handler, new StubBuilder(), messageBus);
        BlockingQueue<Object> queue = new LinkedBlockingQueue<>();
        ActorRef channel = system.actorOf(Props.create(RecordingActor.class, queue));
        dispatcher.registerChannel(5L, 301L, channel);
        queue.poll(2, TimeUnit.SECONDS);

        JsObject json = js("{\"action\":\"SESSION\",\"version\":2}");
        dispatcher.deliverFromRemote(5L, json);

        BatchMsg delivered = (BatchMsg) queue.poll(2, TimeUnit.SECONDS);
        assertNotNull(delivered);
        assertEquals(json, delivered.json());
        assertEquals(0, handler.callCount);
        assertTrue(messageBus.batchMessages.isEmpty());
    }

    @Test
    public void unregisterChannel_whenEmpty_removesBatchEntry() throws InterruptedException {
        BatchMsg toAll = msg(js("{\"t\":\"all\"}"), TW_All());
        BatchDispatcher dispatcher = newDispatcher(new StubHandler(messages(toAll)), new StubBuilder());
        BlockingQueue<Object> queue = new LinkedBlockingQueue<>();
        ActorRef channel = system.actorOf(Props.create(RecordingActor.class, queue));
        dispatcher.registerChannel(42L, 301L, channel);

        dispatcher.unregisterChannel(42L, 301L);
        queue.poll(2, TimeUnit.SECONDS);
        dispatcher.handleActionMsg(msg(js("{\"dummy\":true}"), TW_Unknown()), 42L, 301L, channel);

        assertNull(queue.poll(200, TimeUnit.MILLISECONDS));
    }

    @Test
    public void unregisterChannel_unknownId_doesNothing() {
        BatchDispatcher dispatcher = newDispatcher(new StubHandler(messages()), new StubBuilder());

        dispatcher.unregisterChannel(43L, 9999L);
    }

    @Test
    public void poisonChannel_sendsClosedAndUnregisters() throws InterruptedException {
        BatchDispatcher dispatcher = newDispatcher(new StubHandler(messages()), new StubBuilder());
        BlockingQueue<Object> queue = new LinkedBlockingQueue<>();
        ActorRef channel = system.actorOf(Props.create(RecordingActor.class, queue));
        dispatcher.registerChannel(44L, 401L, channel);
        queue.poll(2, TimeUnit.SECONDS);

        dispatcher.poisonChannel(44L, 401L);

        BatchMsg closed = (BatchMsg) queue.poll(2, TimeUnit.SECONDS);
        assertNotNull("Expected a Closed message", closed);
        assertEquals(js("{\"action\":\"CLOSED\"}"), closed.json());
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

    private static class StubHandler extends BatchActionHandler {
        private final List<BatchMsg> resultMessages;
        private int callCount;

        StubHandler(List<BatchMsg> resultMessages) {
            super(null, null);
            this.resultMessages = resultMessages;
        }

        @Override
        public List<BatchMsg> handleActionMsg(BatchMsg actionMsg, long batchId) {
            callCount++;
            return resultMessages;
        }
    }

    private static class RecordingMessageBus implements SessionMessagePublisher {
        private final java.util.List<BatchClusterMessage> batchMessages = new ArrayList<>();

        @Override
        public boolean isDistributed() {
            return true;
        }

        @Override
        public void publishBatchToCluster(BatchClusterMessage message) {
            batchMessages.add(message);
        }

        @Override
        public void publishGroupToCluster(GroupClusterMessage message) {
        }
    }

    private static class DisabledMessageBus implements SessionMessagePublisher {
        @Override
        public boolean isDistributed() {
            return false;
        }

        @Override
        public void publishBatchToCluster(BatchClusterMessage message) {
            fail("A single-node dispatcher must not publish batch messages");
        }

        @Override
        public void publishGroupToCluster(GroupClusterMessage message) {
            fail("A single-node dispatcher must not publish group messages");
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

}
