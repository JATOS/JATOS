package group;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import play.api.libs.json.JsObject;
import play.api.libs.json.Json$;

import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GroupDispatcher.
 */
public class GroupDispatcherTest {

    private static ActorSystem system;

    private GroupDispatcherRegistry registry;
    private GroupActionHandler actionHandler;
    private GroupActionMsgBuilder msgBuilder;

    private GroupDispatcher dispatcher;

    private final long groupResultId = 100L;

    // Simple actor that captures received messages into a queue so assertions can be made
    public static class CapturingActor extends AbstractActor {
        private final BlockingQueue<Object> mailbox;
        public CapturingActor(BlockingQueue<Object> mailbox) { this.mailbox = mailbox; }
        @Override public Receive createReceive() {
            return receiveBuilder().matchAny(msg -> mailbox.offer(msg)).build();
        }
    }

    @BeforeClass
    public static void setupClass() {
        Config cfg = ConfigFactory.parseString("akka.loglevel=WARNING\nakka.log-dead-letters=off");
        system = ActorSystem.create("gd-test-system", cfg);
    }

    @AfterClass
    public static void tearDownClass() {
        if (system != null) system.terminate();
    }

    @Before
    public void setup() {
        registry = mock(GroupDispatcherRegistry.class);
        actionHandler = mock(GroupActionHandler.class);
        msgBuilder = mock(GroupActionMsgBuilder.class);
        dispatcher = new GroupDispatcher(system, registry, actionHandler, msgBuilder, groupResultId);
    }

    private JsObject js(String s) { return (JsObject) Json$.MODULE$.parse(s); }

    private GroupDispatcher.GroupMsg actionMsgToSender(JsObject json) {
        return new GroupDispatcher.GroupMsg(json, group.GroupDispatcher.TellWhom$.MODULE$.SenderOnly());
    }

    private GroupDispatcher.GroupMsg actionMsgToAllButSender(JsObject json) {
        return new GroupDispatcher.GroupMsg(json, group.GroupDispatcher.TellWhom$.MODULE$.AllButSender());
    }

    private GroupDispatcher.GroupMsg directOrBroadcastMsg(JsObject json) {
        // tellWhom is Unknown for direct/broadcast inputs
        return new GroupDispatcher.GroupMsg(json, group.GroupDispatcher.TellWhom$.MODULE$.Unknown());
    }

    private scala.Enumeration.Value GA_Opened() { return group.GroupDispatcher.GroupAction$.MODULE$.Opened(); }
    private scala.Enumeration.Value GA_Closed() { return group.GroupDispatcher.GroupAction$.MODULE$.Closed(); }
    private scala.Enumeration.Value GA_Joined() { return group.GroupDispatcher.GroupAction$.MODULE$.Joined(); }
    private scala.Enumeration.Value GA_Left() { return group.GroupDispatcher.GroupAction$.MODULE$.Left(); }

    private scala.Enumeration.Value TW_SenderOnly() { return group.GroupDispatcher.TellWhom$.MODULE$.SenderOnly(); }
    private scala.Enumeration.Value TW_AllButSender() { return group.GroupDispatcher.TellWhom$.MODULE$.AllButSender(); }
    private scala.Enumeration.Value TW_Unknown() { return group.GroupDispatcher.TellWhom$.MODULE$.Unknown(); }

    @Test
    public void registerChannel_sendsOpenedToSelfAndOthers() {
        // Prepare two fake channels with distinct out actors
        BlockingQueue<Object> out1 = new LinkedBlockingQueue<>();
        BlockingQueue<Object> out2 = new LinkedBlockingQueue<>();
        ActorRef outActor1 = system.actorOf(Props.create(CapturingActor.class, out1));
        ActorRef outActor2 = system.actorOf(Props.create(CapturingActor.class, out2));

        GroupChannelActor ch1 = mock(GroupChannelActor.class);
        GroupChannelActor ch2 = mock(GroupChannelActor.class);
        when(ch1.self()).thenReturn(outActor1);
        when(ch2.self()).thenReturn(outActor2);

        // Configure msg builder: OPENED -> one to sender, one to others
        when(msgBuilder.build(eq(groupResultId), anyLong(), any(GroupChannelRegistry.class), eq(true),
                eq(GA_Opened()), eq(TW_SenderOnly())))
            .thenReturn(actionMsgToSender(js("{\"action\":\"OPENED\",\"who\":\"sender\"}")));
        when(msgBuilder.build(eq(groupResultId), anyLong(), any(GroupChannelRegistry.class), eq(false),
                eq(GA_Opened()), eq(TW_AllButSender())))
            .thenReturn(actionMsgToAllButSender(js("{\"action\":\"OPENED\",\"who\":\"others\"}")));

        // Register first channel -> it should get the sender-only OPENED; no others exist yet
        dispatcher.registerChannel(1L, ch1);
        assertEquals(1, out1.size());
        assertTrue(out2.isEmpty());

        // Clear first out; now register second channel ->
        // second gets sender-only OPENED, first gets others OPENED
        pollUntilEmpty(out1);
        dispatcher.registerChannel(2L, ch2);
        Object msg1 = poll(out1);
        Object msg2 = poll(out2);
        assertNotNull("Expected an Opened message", msg1);
        assertNotNull("Expected an Opened message", msg2);
    }

    @Test
    public void unregisterChannel_sendsClosedToOthers_andUnregistersDispatcherIfEmpty() {
        // Two channels setup
        BlockingQueue<Object> out1 = new LinkedBlockingQueue<>();
        BlockingQueue<Object> out2 = new LinkedBlockingQueue<>();
        ActorRef outActor1 = system.actorOf(Props.create(CapturingActor.class, out1));
        ActorRef outActor2 = system.actorOf(Props.create(CapturingActor.class, out2));
        GroupChannelActor ch1 = mock(GroupChannelActor.class);
        GroupChannelActor ch2 = mock(GroupChannelActor.class);
        when(ch1.self()).thenReturn(outActor1);
        when(ch2.self()).thenReturn(outActor2);

        when(msgBuilder.build(eq(groupResultId), anyLong(), any(GroupChannelRegistry.class), eq(true),
                eq(GA_Opened()), eq(TW_SenderOnly())))
            .thenReturn(actionMsgToSender(js("{\"action\":\"OPENED\"}")));
        when(msgBuilder.build(eq(groupResultId), anyLong(), any(GroupChannelRegistry.class), eq(false),
                eq(GA_Opened()), eq(TW_AllButSender())))
            .thenReturn(actionMsgToAllButSender(js("{\"action\":\"OPENED\"}")));

        when(msgBuilder.build(eq(groupResultId), anyLong(), any(GroupChannelRegistry.class), eq(false),
                eq(GA_Closed()), eq(TW_AllButSender())))
            .thenReturn(actionMsgToAllButSender(js("{\"action\":\"CLOSED\"}")));

        dispatcher.registerChannel(1L, ch1);
        dispatcher.registerChannel(2L, ch2);
        // Clear channels
        pollUntilEmpty(out1);
        pollUntilEmpty(out2);

        // Unregister first -> second should get CLOSED
        dispatcher.unregisterChannel(1L);
        Object msg1 = poll(out1);
        Object msg2 = poll(out2);
        assertNull(msg1);
        assertNotNull(msg2);
        verify(registry, never()).unregister(anyLong());

        // Unregister second -> now empty, registry should unregister dispatcher
        dispatcher.unregisterChannel(2L);
        verify(registry).unregister(groupResultId);
    }

    @Test
    public void poisonChannel_sendsClosedToChannelAndUnregisters() {
        BlockingQueue<Object> out1 = new LinkedBlockingQueue<>();
        ActorRef outActor1 = system.actorOf(Props.create(CapturingActor.class, out1));
        GroupChannelActor ch1 = mock(GroupChannelActor.class);
        when(ch1.self()).thenReturn(outActor1);

        when(msgBuilder.build(eq(groupResultId), anyLong(), any(GroupChannelRegistry.class), eq(true),
                eq(GA_Opened()), eq(TW_SenderOnly())))
            .thenReturn(actionMsgToSender(js("{\"action\":\"OPENED\"}")));
        when(msgBuilder.build(eq(groupResultId), anyLong(), any(GroupChannelRegistry.class), eq(false),
                eq(GA_Opened()), eq(TW_AllButSender())))
            .thenReturn(actionMsgToAllButSender(js("{\"action\":\"OPENED\"}")));
        when(msgBuilder.build(eq(groupResultId), anyLong(), any(GroupChannelRegistry.class), eq(false),
                eq(GA_Closed()), eq(TW_AllButSender())))
            .thenReturn(actionMsgToAllButSender(js("{\"action\":\"CLOSED\"}")));

        dispatcher.registerChannel(1L, ch1);
        pollUntilEmpty(out1);

        dispatcher.poisonChannel(1L);

        // Should receive at least one CLOSED (the direct one before stopping)
        // Give a short time window for async delivery
        Object first = poll(out1);
        assertNotNull("Expected one message to have been delivered to out actor", first);
        // After poison and unregister, dispatcher registry should be called as channels are empty
        verify(registry).unregister(groupResultId);
    }

    @Test
    public void reassignChannel_movesChannelAndTriggersJoinedLeft() {
        GroupDispatcher different = spy(new GroupDispatcher(system, registry, actionHandler, msgBuilder, groupResultId + 1));
        GroupDispatcher spyDispatcher = spy(dispatcher);

        GroupChannelActor ch = mock(GroupChannelActor.class);
        ActorRef dummyOut = system.actorOf(Props.create(CapturingActor.class, new LinkedBlockingQueue<>()));
        when(ch.self()).thenReturn(dummyOut);

        when(msgBuilder.build(eq(groupResultId), anyLong(), any(GroupChannelRegistry.class), anyBoolean(),
                eq(GA_Opened()), any(scala.Enumeration.Value.class)))
            .thenReturn(actionMsgToSender(js("{\"action\":\"OPENED\"}")));
        when(msgBuilder.build(eq(groupResultId + 1), anyLong(), any(GroupChannelRegistry.class), anyBoolean(),
                eq(GA_Opened()), any(scala.Enumeration.Value.class)))
            .thenReturn(actionMsgToSender(js("{\"action\":\"OPENED\"}")));
        // Stub JOINED for different dispatcher to avoid NPE
        when(msgBuilder.build(eq(groupResultId + 1), anyLong(), any(GroupChannelRegistry.class), eq(false),
                eq(GA_Joined()), eq(TW_AllButSender())))
            .thenReturn(actionMsgToAllButSender(js("{\"action\":\"JOINED\"}")));

        // Register in first
        stubOpenCloseMessages();
        spyDispatcher.registerChannel(1L, ch);
        // Reassign
        spyDispatcher.reassignChannel(1L, different);

        verify(ch).setGroupDispatcher(different);
        verify(spyDispatcher).left(1L);
        verify(different).joined(1L);
    }

    private void stubOpenCloseMessages() {
        when(msgBuilder.build(eq(groupResultId), anyLong(), any(GroupChannelRegistry.class), eq(true),
                eq(GA_Opened()), eq(TW_SenderOnly())))
            .thenReturn(actionMsgToSender(js("{\"action\":\"OPENED\"}")));
        when(msgBuilder.build(eq(groupResultId), anyLong(), any(GroupChannelRegistry.class), eq(false),
                eq(GA_Opened()), eq(TW_AllButSender())))
            .thenReturn(actionMsgToAllButSender(js("{\"action\":\"OPENED\"}")));
        when(msgBuilder.build(eq(groupResultId), anyLong(), any(GroupChannelRegistry.class), eq(false),
                eq(GA_Closed()), eq(TW_AllButSender())))
            .thenReturn(actionMsgToAllButSender(js("{\"action\":\"CLOSED\"}")));
    }

    @Test
    public void handleGroupMsg_actionMessage_routedViaActionHandler() {
        BlockingQueue<Object> out1 = new LinkedBlockingQueue<>();
        BlockingQueue<Object> out2 = new LinkedBlockingQueue<>();
        ActorRef outActor1 = system.actorOf(Props.create(CapturingActor.class, out1));
        ActorRef outActor2 = system.actorOf(Props.create(CapturingActor.class, out2));
        GroupChannelActor ch1 = mock(GroupChannelActor.class);
        GroupChannelActor ch2 = mock(GroupChannelActor.class);
        when(ch1.self()).thenReturn(outActor1);
        when(ch2.self()).thenReturn(outActor2);

        stubOpenCloseMessages();
        dispatcher.registerChannel(1L, ch1);
        dispatcher.registerChannel(2L, ch2);
        // Wait for OPENED messages to be sent
        pollUntilEmpty(out1);
        pollUntilEmpty(out2);

        // Action handler returns two messages: one to sender only, one broadcast to all
        GroupDispatcher.GroupMsg toSender = actionMsgToSender(js("{\"a\":1}"));
        GroupDispatcher.GroupMsg toAllButSender = actionMsgToAllButSender(js("{\"a\":2}"));
        when(actionHandler.handleActionMsg(any(GroupDispatcher.GroupMsg.class), eq(groupResultId), eq(1L)))
                .thenReturn(scala.jdk.javaapi.CollectionConverters.asScala(Arrays.asList(toSender, toAllButSender)).toList());

        JsObject input = js("{\"action\":\"SESSION\"}");
        dispatcher.handleGroupMsg(new GroupDispatcher.GroupMsg(input, TW_Unknown()), 1L, ch1.self());
        Object msg1 = poll(out1);
        Object msg2 = poll(out2);

        // Sender gets sender-only; other gets the all-but-sender
        assertNotNull(msg1);
        assertNotNull(msg2);
    }

    @Test
    public void handleGroupMsg_directMessage_goesToRecipientOnly_orErrors() {
        BlockingQueue<Object> out1 = new LinkedBlockingQueue<>();
        BlockingQueue<Object> out2 = new LinkedBlockingQueue<>();
        ActorRef outActor1 = system.actorOf(Props.create(CapturingActor.class, out1));
        ActorRef outActor2 = system.actorOf(Props.create(CapturingActor.class, out2));
        GroupChannelActor ch1 = mock(GroupChannelActor.class);
        GroupChannelActor ch2 = mock(GroupChannelActor.class);
        when(ch1.self()).thenReturn(outActor1);
        when(ch2.self()).thenReturn(outActor2);

        stubOpenCloseMessages();
        dispatcher.registerChannel(1L, ch1);
        dispatcher.registerChannel(2L, ch2);
        // Wait for OPENED messages to be sent
        pollUntilEmpty(out1);
        pollUntilEmpty(out2);

        // Direct message to 2
        JsObject directJson = js("{\"recipient\":\"2\",\"msg\":\"hi\"}");
        dispatcher.handleGroupMsg(directOrBroadcastMsg(directJson), 1L, ch1.self());
        Object msg1 = poll(out1);
        Object msg2 = poll(out2);
        assertNull(msg1);
        assertNotNull(msg2);

        // Direct to unknown -> should create error back to sender
        when(msgBuilder.buildError(eq(groupResultId), anyString(), eq(TW_SenderOnly())))
                .thenReturn(actionMsgToSender(js("{\"action\":\"ERROR\"}")));
        JsObject toUnknown = js("{\"recipient\":\"999\"}");
        dispatcher.handleGroupMsg(directOrBroadcastMsg(toUnknown), 1L, ch1.self());
        Object msg3 = poll(out1);
        assertNotNull(msg3);
    }

    @Test
    public void handleGroupMsg_broadcast_goesToAllButSender() {
        BlockingQueue<Object> out1 = new LinkedBlockingQueue<>();
        BlockingQueue<Object> out2 = new LinkedBlockingQueue<>();
        ActorRef outActor1 = system.actorOf(Props.create(CapturingActor.class, out1));
        ActorRef outActor2 = system.actorOf(Props.create(CapturingActor.class, out2));
        GroupChannelActor ch1 = mock(GroupChannelActor.class);
        GroupChannelActor ch2 = mock(GroupChannelActor.class);
        when(ch1.self()).thenReturn(outActor1);
        when(ch2.self()).thenReturn(outActor2);

        stubOpenCloseMessages();
        dispatcher.registerChannel(1L, ch1);
        dispatcher.registerChannel(2L, ch2);
        // Wait for OPENED messages to be sent
        pollUntilEmpty(out1);
        pollUntilEmpty(out2);

        JsObject broadcast = js("{\"text\":\"hello all\"}");
        dispatcher.handleGroupMsg(directOrBroadcastMsg(broadcast), 1L, ch1.self());
        Object msg1 = poll(out1);
        Object msg2 = poll(out2);
        assertNull(msg1);
        assertNotNull(msg2);
    }

    @Test
    public void joinedAndLeft_sendActionToOthersOnlyWhenChannelKnown() {
        BlockingQueue<Object> out1 = new LinkedBlockingQueue<>();
        BlockingQueue<Object> out2 = new LinkedBlockingQueue<>();
        ActorRef outActor1 = system.actorOf(Props.create(CapturingActor.class, out1));
        ActorRef outActor2 = system.actorOf(Props.create(CapturingActor.class, out2));
        GroupChannelActor ch1 = mock(GroupChannelActor.class);
        GroupChannelActor ch2 = mock(GroupChannelActor.class);
        when(ch1.self()).thenReturn(outActor1);
        when(ch2.self()).thenReturn(outActor2);

        when(msgBuilder.build(eq(groupResultId), anyLong(), any(GroupChannelRegistry.class), eq(false),
                eq(GA_Joined()), eq(TW_AllButSender())))
            .thenReturn(actionMsgToAllButSender(js("{\"action\":\"JOINED\"}")));
        when(msgBuilder.build(eq(groupResultId), anyLong(), any(GroupChannelRegistry.class), eq(false),
                eq(GA_Left()), eq(TW_AllButSender())))
            .thenReturn(actionMsgToAllButSender(js("{\"action\":\"LEFT\"}")));

        stubOpenCloseMessages();
        dispatcher.registerChannel(1L, ch1);
        dispatcher.registerChannel(2L, ch2);

        pollUntilEmpty(out1);
        pollUntilEmpty(out2);

        dispatcher.joined(1L);
        Object msg1 = poll(out1);
        Object msg2 = poll(out2);
        assertNull(msg1);
        assertNotNull(msg2);

        pollUntilEmpty(out1);
        pollUntilEmpty(out2);

        dispatcher.left(2L);
        Object msg3 = poll(out1);
        Object msg4 = poll(out2);
        assertNotNull(msg3);
        assertNull(msg4);

        pollUntilEmpty(out1);
        pollUntilEmpty(out2);

        // If unknown ID -> no messages
        dispatcher.left(999L);
        dispatcher.joined(999L);
        assertEquals(0, out1.size() + out2.size());
    }

    private void pollUntilEmpty(BlockingQueue<Object> q) {
        while (poll(q) != null) {}
    }

    private Object poll(BlockingQueue<Object> q) {
        try {
            return q.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }
}
