package group.session;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import cluster.LocalSessionMessageBus;
import cluster.NodeIdentity;
import cluster.BatchClusterMessage;
import cluster.GroupClusterMessage;
import cluster.GroupDirectMsgDeliveryRequest;
import cluster.GroupRecipients;
import cluster.GroupReassignmentClusterMessage;
import cluster.SessionMessagePublisher;
import daos.common.StudyDao;
import group.GroupActionHandler;
import group.GroupActionMsgBuilder;
import group.GroupChannelActor;
import group.GroupDispatcher;
import general.common.Common;
import models.common.Study.GroupSessionWriteScope;
import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Props;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;
import play.api.libs.json.JsObject;
import play.api.libs.json.Json$;
import scala.Enumeration;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.time.Duration;

import static group.GroupDispatcher.*;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static scala.jdk.javaapi.CollectionConverters.asScala;

/**
 * Unit tests for GroupDispatcher.
 */
public class GroupDispatcherTest {

    private static ActorSystem system;
    private static MockedStatic<Common> common;

    private GroupActionHandler actionHandler;
    private GroupActionMsgBuilder msgBuilder;
    private StudyDao studyDao;

    private GroupDispatcher dispatcher;

    private final long groupResultId = 100L;

    // Simple actor that captures received messages into a queue so assertions can be made
    public static class CapturingActor extends AbstractActor {
        private final BlockingQueue<Object> mailbox;

        public CapturingActor(BlockingQueue<Object> mailbox) {
            this.mailbox = mailbox;
        }

        @Override
        public Receive createReceive() {
            //noinspection ResultOfMethodCallIgnored
            return receiveBuilder().matchAny(mailbox::offer).build();
        }
    }

    @BeforeClass
    public static void setupClass() {
        common = mockStatic(Common.class);
        common.when(Common::getGroupDirectMessageAckTimeout).thenReturn(Duration.ofMillis(200));
        Config config = ConfigFactory.parseString(
                "org.apache.pekko.loglevel=WARNING\norg.apache.pekko.log-dead-letters=off");
        system = ActorSystem.create("gd-test-system", config);
    }

    @AfterClass
    public static void tearDownClass() {
        if (system != null) system.terminate();
        if (common != null) common.close();
    }

    @Before
    public void setup() {
        studyDao = mock(StudyDao.class);
        actionHandler = mock(GroupActionHandler.class);
        msgBuilder = mock(GroupActionMsgBuilder.class);
        when(studyDao.findGroupSessionWriteScope(anyLong())).thenReturn(GroupSessionWriteScope.SHARED);
        dispatcher = new GroupDispatcher(actionHandler, msgBuilder, studyDao,
                new LocalSessionMessageBus(), new NodeIdentity(), system);
    }

    private GroupDispatcher newDistributedDispatcher(RecordingMessagePublisher publisher) {
        NodeIdentity nodeIdentity = new NodeIdentity() {
            @Override
            public String id() {
                return "node-1";
            }
        };
        return new GroupDispatcher(actionHandler, msgBuilder, studyDao, publisher, nodeIdentity, system);
    }

    private JsObject js(String s) {
        return (JsObject) Json$.MODULE$.parse(s);
    }

    private GroupMsg actionMsgToSender(JsObject json) {
        return new GroupMsg(json, TellWhom$.MODULE$.SenderOnly());
    }

    private GroupMsg actionMsgToAllButSender(JsObject json) {
        return new GroupMsg(json, TellWhom$.MODULE$.AllButSender());
    }

    private GroupMsg actionMsgToAll(JsObject json) {
        return new GroupMsg(json, TellWhom$.MODULE$.All());
    }

    private GroupMsg directOrBroadcastMsg(JsObject json) {
        // tellWhom is Unknown for direct/broadcast inputs
        return new GroupMsg(json, TellWhom$.MODULE$.Unknown());
    }

    private Enumeration.Value GA_Opened() {
        return GroupAction$.MODULE$.Opened();
    }

    private Enumeration.Value GA_Closed() {
        return GroupAction$.MODULE$.Closed();
    }

    private Enumeration.Value GA_Joined() {
        return GroupAction$.MODULE$.Joined();
    }

    private Enumeration.Value GA_Left() {
        return GroupAction$.MODULE$.Left();
    }

    private Enumeration.Value TW_SenderOnly() {
        return TellWhom$.MODULE$.SenderOnly();
    }

    private Enumeration.Value TW_AllButSender() {
        return TellWhom$.MODULE$.AllButSender();
    }

    private Enumeration.Value TW_Unknown() {
        return TellWhom$.MODULE$.Unknown();
    }

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
        when(msgBuilder.build(eq(groupResultId), anyLong(), any(), eq(true),
                eq(GA_Opened()), eq(TW_SenderOnly())))
                .thenReturn(actionMsgToSender(js("{\"action\":\"OPENED\",\"who\":\"sender\"}")));
        when(msgBuilder.build(eq(groupResultId), anyLong(), any(), eq(false),
                eq(GA_Opened()), eq(TW_AllButSender())))
                .thenReturn(actionMsgToAllButSender(js("{\"action\":\"OPENED\",\"who\":\"others\"}")));

        // Register first channel -> it should get the sender-only OPENED; no others exist yet
        dispatcher.registerChannel(groupResultId, 1L, ch1);
        assertEquals(1, out1.size());
        assertTrue(out2.isEmpty());

        // Clear first out; now register second channel ->
        // second gets sender-only OPENED, first gets others OPENED
        pollUntilEmpty(out1);
        dispatcher.registerChannel(groupResultId, 2L, ch2);
        Object msg1 = poll(out1);
        Object msg2 = poll(out2);
        assertNotNull(msg1);
        assertNotNull(msg2);
        assertEquals(js("{\"action\":\"OPENED\",\"who\":\"others\"}"), ((GroupMsg) msg1).json());
        assertEquals(js("{\"action\":\"OPENED\",\"who\":\"sender\"}"), ((GroupMsg) msg2).json());
    }

    @Test
    public void unregisterChannel_sendsClosedToOthers_andRemovesEmptyGroup() {
        // Two channels setup
        BlockingQueue<Object> out1 = new LinkedBlockingQueue<>();
        BlockingQueue<Object> out2 = new LinkedBlockingQueue<>();
        ActorRef outActor1 = system.actorOf(Props.create(CapturingActor.class, out1));
        ActorRef outActor2 = system.actorOf(Props.create(CapturingActor.class, out2));
        GroupChannelActor ch1 = mock(GroupChannelActor.class);
        GroupChannelActor ch2 = mock(GroupChannelActor.class);
        when(ch1.self()).thenReturn(outActor1);
        when(ch2.self()).thenReturn(outActor2);

        when(msgBuilder.build(eq(groupResultId), anyLong(), any(), eq(true),
                eq(GA_Opened()), eq(TW_SenderOnly())))
                .thenReturn(actionMsgToSender(js("{\"action\":\"OPENED\"}")));
        when(msgBuilder.build(eq(groupResultId), anyLong(), any(), eq(false),
                eq(GA_Opened()), eq(TW_AllButSender())))
                .thenReturn(actionMsgToAllButSender(js("{\"action\":\"OPENED\"}")));

        when(msgBuilder.build(eq(groupResultId), anyLong(), any(), eq(false),
                eq(GA_Closed()), eq(TW_AllButSender())))
                .thenReturn(actionMsgToAllButSender(js("{\"action\":\"CLOSED\"}")));

        dispatcher.registerChannel(groupResultId, 1L, ch1);
        dispatcher.registerChannel(groupResultId, 2L, ch2);
        // Clear channels
        pollUntilEmpty(out1);
        pollUntilEmpty(out2);

        // Unregister first -> second should get CLOSED
        dispatcher.unregisterChannel(groupResultId, 1L);
        Object msg1 = poll(out1);
        Object msg2 = poll(out2);
        assertNull(msg1);
        assertNotNull(msg2);
        assertEquals(js("{\"action\":\"CLOSED\"}"), ((GroupMsg) msg2).json());
        dispatcher.unregisterChannel(groupResultId, 2L);
        assertFalse(dispatcher.hasChannel(2L));
    }

    @Test
    public void poisonChannel_sendsClosedToChannelAndUnregisters() {
        BlockingQueue<Object> out1 = new LinkedBlockingQueue<>();
        ActorRef outActor1 = system.actorOf(Props.create(CapturingActor.class, out1));
        GroupChannelActor ch1 = mock(GroupChannelActor.class);
        when(ch1.self()).thenReturn(outActor1);

        when(msgBuilder.build(eq(groupResultId), anyLong(), any(), eq(true),
                eq(GA_Opened()), eq(TW_SenderOnly())))
                .thenReturn(actionMsgToSender(js("{\"action\":\"OPENED\"}")));
        when(msgBuilder.build(eq(groupResultId), anyLong(), any(), eq(false),
                eq(GA_Opened()), eq(TW_AllButSender())))
                .thenReturn(actionMsgToAllButSender(js("{\"action\":\"OPENED\"}")));
        when(msgBuilder.build(eq(groupResultId), anyLong(), any(), eq(false),
                eq(GA_Closed()), eq(TW_AllButSender())))
                .thenReturn(actionMsgToAllButSender(js("{\"action\":\"CLOSED\"}")));

        dispatcher.registerChannel(groupResultId, 1L, ch1);
        pollUntilEmpty(out1);

        dispatcher.poisonChannel(groupResultId, 1L);

        // Should receive at least one CLOSED (the direct one before stopping)
        // Give a short time window for async delivery
        Object first = poll(out1);
        assertNotNull("Expected one message to have been delivered to out actor", first);
        assertFalse(dispatcher.hasChannel(1L));
    }

    @Test
    public void reassignChannel_movesChannelAndTriggersJoinedLeft() {
        GroupDispatcher spyDispatcher = spy(dispatcher);

        GroupChannelActor ch = mock(GroupChannelActor.class);
        ActorRef dummyOut = system.actorOf(Props.create(CapturingActor.class, new LinkedBlockingQueue<>()));
        when(ch.self()).thenReturn(dummyOut);

        when(msgBuilder.build(eq(groupResultId), anyLong(), any(), anyBoolean(),
                eq(GA_Opened()), any(Enumeration.Value.class)))
                .thenReturn(actionMsgToSender(js("{\"action\":\"OPENED\"}")));
        when(msgBuilder.build(eq(groupResultId + 1), anyLong(), any(), anyBoolean(),
                eq(GA_Opened()), any(Enumeration.Value.class)))
                .thenReturn(actionMsgToSender(js("{\"action\":\"OPENED\"}")));
        // Stub JOINED for different dispatcher to avoid NPE
        when(msgBuilder.build(eq(groupResultId + 1), anyLong(), any(), eq(false),
                eq(GA_Joined()), eq(TW_AllButSender())))
                .thenReturn(actionMsgToAllButSender(js("{\"action\":\"JOINED\"}")));
        when(msgBuilder.build(eq(groupResultId), anyLong(), any(), eq(false),
                eq(GA_Left()), any()))
                .thenReturn(actionMsgToAllButSender(js("{\"action\":\"LEFT\"}")));

        // Register in first
        stubOpenCloseMessages();
        spyDispatcher.registerChannel(groupResultId, 1L, ch);
        // Reassign
        spyDispatcher.reassignChannel(1L, groupResultId, groupResultId + 1);

        verify(ch).setGroupResultId(groupResultId + 1);
        verify(spyDispatcher).left(groupResultId, 1L);
        verify(spyDispatcher).joined(groupResultId + 1, 1L);
    }

    @Test
    public void reassignChannel_whenChannelIsNotLocal_publishesToCluster() {
        RecordingMessagePublisher publisher = new RecordingMessagePublisher();
        GroupDispatcher distributedDispatcher = newDistributedDispatcher(publisher);

        distributedDispatcher.reassignChannel(1L, groupResultId, groupResultId + 1);

        assertEquals(List.of(new GroupReassignmentClusterMessage(
                "node-1", 1L, groupResultId, groupResultId + 1)),
                publisher.groupReassignmentMessages);
    }

    @Test
    public void reassignFromRemote_whenChannelIsNotLocal_doesNotRepublish() {
        RecordingMessagePublisher publisher = new RecordingMessagePublisher();
        GroupDispatcher distributedDispatcher = newDistributedDispatcher(publisher);

        distributedDispatcher.reassignFromRemote(1L, groupResultId, groupResultId + 1);

        assertTrue(publisher.groupReassignmentMessages.isEmpty());
    }

    private void stubOpenCloseMessages() {
        when(msgBuilder.build(eq(groupResultId), anyLong(), any(), eq(true),
                eq(GA_Opened()), eq(TW_SenderOnly())))
                .thenReturn(actionMsgToSender(js("{\"action\":\"OPENED\"}")));
        when(msgBuilder.build(eq(groupResultId), anyLong(), any(), eq(false),
                eq(GA_Opened()), eq(TW_AllButSender())))
                .thenReturn(actionMsgToAllButSender(js("{\"action\":\"OPENED\"}")));
        when(msgBuilder.build(eq(groupResultId), anyLong(), any(), eq(false),
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
        dispatcher.registerChannel(groupResultId, 1L, ch1);
        dispatcher.registerChannel(groupResultId, 2L, ch2);
        // Wait for OPENED messages to be sent
        pollUntilEmpty(out1);
        pollUntilEmpty(out2);

        // Action handler returns two messages: one to sender only, one broadcast to all
        GroupMsg toSender = actionMsgToSender(js("{\"a\":1}"));
        GroupMsg toAllButSender = actionMsgToAllButSender(js("{\"a\":2}"));
        when(actionHandler.handleActionMsg(
                any(GroupMsg.class),
                eq(groupResultId),
                eq(1L),
                eq(GroupSessionWriteScope.SHARED)))
                .thenReturn(asScala(Arrays.asList(toSender, toAllButSender)).toList());

        JsObject input = js("{\"action\":\"SESSION\"}");
        dispatcher.handleGroupMsg(new GroupMsg(input, TW_Unknown()), groupResultId, 1L, ch1.self());
        Object msg1 = poll(out1);
        Object msg2 = poll(out2);

        // Sender gets sender-only; other gets the all-but-sender
        assertEquals(toSender, msg1);
        assertEquals(toAllButSender, msg2);
    }

    @Test
    public void handleGroupMsg_actionToAll_goesToAllChannels() {
        BlockingQueue<Object> out1 = new LinkedBlockingQueue<>();
        BlockingQueue<Object> out2 = new LinkedBlockingQueue<>();
        ActorRef outActor1 = system.actorOf(Props.create(CapturingActor.class, out1));
        ActorRef outActor2 = system.actorOf(Props.create(CapturingActor.class, out2));
        GroupChannelActor ch1 = mock(GroupChannelActor.class);
        GroupChannelActor ch2 = mock(GroupChannelActor.class);
        when(ch1.self()).thenReturn(outActor1);
        when(ch2.self()).thenReturn(outActor2);

        stubOpenCloseMessages();
        dispatcher.registerChannel(groupResultId, 1L, ch1);
        dispatcher.registerChannel(groupResultId, 2L, ch2);
        pollUntilEmpty(out1);
        pollUntilEmpty(out2);

        GroupMsg sessionPatch = actionMsgToAll(js("{\"action\":\"SESSION\",\"sessionVersion\":2}"));
        when(actionHandler.handleActionMsg(
                any(GroupMsg.class),
                eq(groupResultId),
                eq(1L),
                eq(GroupSessionWriteScope.SHARED)))
                .thenReturn(asScala(List.of(sessionPatch)).toList());

        dispatcher.handleGroupMsg(new GroupMsg(js("{\"action\":\"SESSION\"}"), TW_Unknown()), groupResultId, 1L, ch1.self());

        assertEquals(sessionPatch, poll(out1));
        assertEquals(sessionPatch, poll(out2));
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
        dispatcher.registerChannel(groupResultId, 1L, ch1);
        dispatcher.registerChannel(groupResultId, 2L, ch2);
        // Wait for OPENED messages to be sent
        pollUntilEmpty(out1);
        pollUntilEmpty(out2);

        // Direct message to 2
        JsObject directJson = js("{\"recipient\":\"2\",\"msg\":\"hi\"}");
        dispatcher.handleGroupMsg(directOrBroadcastMsg(directJson), groupResultId, 1L, ch1.self());
        Object msg1 = poll(out1);
        Object msg2 = poll(out2);
        assertNull(msg1);
        assertNotNull(msg2);
        assertEquals(directJson, ((GroupMsg) msg2).json());

        // Direct to unknown -> should create error back to sender
        when(msgBuilder.buildError(eq(groupResultId), anyString(), eq(TW_SenderOnly())))
                .thenReturn(actionMsgToSender(js("{\"action\":\"ERROR\"}")));
        JsObject toUnknown = js("{\"recipient\":\"999\"}");
        dispatcher.handleGroupMsg(directOrBroadcastMsg(toUnknown), groupResultId, 1L, ch1.self());
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
        dispatcher.registerChannel(groupResultId, 1L, ch1);
        dispatcher.registerChannel(groupResultId, 2L, ch2);
        // Wait for OPENED messages to be sent
        pollUntilEmpty(out1);
        pollUntilEmpty(out2);

        JsObject broadcast = js("{\"text\":\"hello all\"}");
        dispatcher.handleGroupMsg(directOrBroadcastMsg(broadcast), groupResultId, 1L, ch1.self());
        Object msg1 = poll(out1);
        Object msg2 = poll(out2);
        assertNull(msg1);
        assertNotNull(msg2);
        assertEquals(broadcast, ((GroupMsg) msg2).json());
    }

    @Test
    public void handleGroupMsg_broadcast_doesNotCrossGroupBoundary() {
        BlockingQueue<Object> senderQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Object> otherGroupQueue = new LinkedBlockingQueue<>();
        GroupChannelActor sender = mock(GroupChannelActor.class);
        GroupChannelActor otherGroup = mock(GroupChannelActor.class);
        when(sender.self()).thenReturn(system.actorOf(Props.create(CapturingActor.class, senderQueue)));
        when(otherGroup.self()).thenReturn(system.actorOf(Props.create(CapturingActor.class, otherGroupQueue)));
        when(msgBuilder.build(anyLong(), anyLong(), any(), eq(true),
                eq(GA_Opened()), eq(TW_SenderOnly())))
                .thenReturn(actionMsgToSender(js("{\"action\":\"OPENED\"}")));
        when(msgBuilder.build(anyLong(), anyLong(), any(), eq(false),
                eq(GA_Opened()), eq(TW_AllButSender())))
                .thenReturn(actionMsgToAllButSender(js("{\"action\":\"OPENED\"}")));
        dispatcher.registerChannel(groupResultId, 1L, sender);
        dispatcher.registerChannel(groupResultId + 1, 2L, otherGroup);
        pollUntilEmpty(senderQueue);
        pollUntilEmpty(otherGroupQueue);

        dispatcher.handleGroupMsg(directOrBroadcastMsg(js("{\"text\":\"hello\"}")),
                groupResultId, 1L, sender.self());

        assertNull(poll(senderQueue));
        assertNull(poll(otherGroupQueue));
    }

    @Test
    public void handleGroupMsg_broadcastPublishesAllButSenderToCluster() {
        RecordingMessagePublisher publisher = new RecordingMessagePublisher();
        GroupDispatcher distributedDispatcher = newDistributedDispatcher(publisher);
        JsObject json = js("{\"text\":\"hello\"}");

        distributedDispatcher.handleGroupMsg(directOrBroadcastMsg(json), groupResultId, 1L, ActorRef.noSender());

        assertEquals(List.of(new GroupClusterMessage(
                "node-1", groupResultId, 1L, Json$.MODULE$.stringify(json),
                new GroupRecipients.AllButSender())), publisher.groupMessages);
    }

    @Test
    public void handleGroupMsg_directPublishesDeliveryRequestAndAckPreventsError() {
        RecordingMessagePublisher publisher = new RecordingMessagePublisher();
        GroupDispatcher distributedDispatcher = newDistributedDispatcher(publisher);
        JsObject json = js("{\"recipient\":\"2\",\"text\":\"hello\"}");

        distributedDispatcher.handleGroupMsg(directOrBroadcastMsg(json), groupResultId, 1L, ActorRef.noSender());

        assertEquals(1, publisher.groupDirectMessages.size());
        GroupDirectMsgDeliveryRequest request = publisher.groupDirectMessages.get(0);
        assertEquals("node-1", request.originNodeId());
        assertEquals(groupResultId, request.groupResultId());
        assertEquals(1L, request.senderStudyResultId());
        assertEquals(2L, request.recipientStudyResultId());
        assertEquals(Json$.MODULE$.stringify(json), request.json());
        distributedDispatcher.acknowledgeDirectDelivery(request.deliveryId());
        verify(msgBuilder, never()).buildError(anyLong(), anyString(), any());
    }

    @Test
    public void handleGroupMsg_directWithoutAckSendsErrorAfterTimeout() throws InterruptedException {
        RecordingMessagePublisher publisher = new RecordingMessagePublisher();
        GroupDispatcher distributedDispatcher = newDistributedDispatcher(publisher);
        BlockingQueue<Object> senderMessages = new LinkedBlockingQueue<>();
        ActorRef sender = system.actorOf(Props.create(CapturingActor.class, senderMessages));
        GroupMsg error = actionMsgToSender(js("{\"action\":\"ERROR\"}"));
        when(msgBuilder.buildError(eq(groupResultId), contains("could not be delivered"), eq(TW_SenderOnly())))
                .thenReturn(error);

        distributedDispatcher.handleGroupMsg(
                directOrBroadcastMsg(js("{\"recipient\":\"999\"}")), groupResultId, 1L, sender);

        assertEquals(error, senderMessages.poll(2, java.util.concurrent.TimeUnit.SECONDS));
    }

    @Test
    public void deliverFromRemote_appliesRecipientsWithoutHandlingOrRepublishing() {
        RecordingMessagePublisher publisher = new RecordingMessagePublisher();
        GroupDispatcher distributedDispatcher = newDistributedDispatcher(publisher);
        BlockingQueue<Object> out1 = new LinkedBlockingQueue<>();
        BlockingQueue<Object> out2 = new LinkedBlockingQueue<>();
        GroupChannelActor ch1 = mock(GroupChannelActor.class);
        GroupChannelActor ch2 = mock(GroupChannelActor.class);
        when(ch1.self()).thenReturn(system.actorOf(Props.create(CapturingActor.class, out1)));
        when(ch2.self()).thenReturn(system.actorOf(Props.create(CapturingActor.class, out2)));
        stubOpenCloseMessages();
        distributedDispatcher.registerChannel(groupResultId, 1L, ch1);
        distributedDispatcher.registerChannel(groupResultId, 2L, ch2);
        pollUntilEmpty(out1);
        pollUntilEmpty(out2);
        publisher.groupMessages.clear();
        clearInvocations(actionHandler);

        JsObject allButSender = js("{\"type\":\"all-but-sender\"}");
        distributedDispatcher.deliverFromRemote(
                groupResultId, 1L, allButSender, new GroupRecipients.AllButSender());
        assertNull(poll(out1));
        assertEquals(allButSender, ((GroupMsg) poll(out2)).json());

        JsObject direct = js("{\"type\":\"direct\"}");
        distributedDispatcher.deliverFromRemote(
                groupResultId, 1L, direct, new GroupRecipients.Recipient(1L));
        assertEquals(direct, ((GroupMsg) poll(out1)).json());
        assertNull(poll(out2));

        assertTrue(publisher.groupMessages.isEmpty());
        verifyNoInteractions(actionHandler);
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

        when(msgBuilder.build(eq(groupResultId), anyLong(), any(), eq(false),
                eq(GA_Joined()), eq(TW_AllButSender())))
                .thenReturn(actionMsgToAllButSender(js("{\"action\":\"JOINED\"}")));
        when(msgBuilder.build(eq(groupResultId), anyLong(), any(), eq(false),
                eq(GA_Left()), any()))
                .thenReturn(actionMsgToAllButSender(js("{\"action\":\"LEFT\"}")));

        stubOpenCloseMessages();
        dispatcher.registerChannel(groupResultId, 1L, ch1);
        dispatcher.registerChannel(groupResultId, 2L, ch2);

        pollUntilEmpty(out1);
        pollUntilEmpty(out2);

        dispatcher.joined(groupResultId, 1L);
        Object msg1 = poll(out1);
        Object msg2 = poll(out2);
        assertNull(msg1);
        assertNotNull(msg2);
        assertEquals(js("{\"action\":\"JOINED\"}"), ((GroupMsg) msg2).json());

        pollUntilEmpty(out1);
        pollUntilEmpty(out2);

        dispatcher.left(groupResultId, 2L);
        Object msg3 = poll(out1);
        Object msg4 = poll(out2);
        assertNotNull(msg3);
        assertEquals(js("{\"action\":\"LEFT\"}"), ((GroupMsg) msg3).json());
        assertNull(msg4);

        pollUntilEmpty(out1);
        pollUntilEmpty(out2);

        // If unknown ID -> we still get the LEFT messages
        dispatcher.left(groupResultId, 999L);
        dispatcher.joined(groupResultId, 999L);
        assertEquals(2, out1.size() + out2.size());
    }

    private void pollUntilEmpty(BlockingQueue<Object> q) {
        //noinspection StatementWithEmptyBody
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

    private static class RecordingMessagePublisher implements SessionMessagePublisher {
        private final List<GroupClusterMessage> groupMessages = new ArrayList<>();
        private final List<GroupDirectMsgDeliveryRequest> groupDirectMessages = new ArrayList<>();
        private final List<GroupReassignmentClusterMessage> groupReassignmentMessages = new ArrayList<>();

        @Override
        public boolean isDistributed() {
            return true;
        }

        @Override
        public void publishBatchMsgToCluster(BatchClusterMessage message) {
        }

        @Override
        public void publishGroupMsgToCluster(GroupClusterMessage message) {
            groupMessages.add(message);
        }

        @Override
        public void publishGroupDirectMsgToCluster(GroupDirectMsgDeliveryRequest message) {
            groupDirectMessages.add(message);
        }

        @Override
        public void publishGroupReassignmentToCluster(GroupReassignmentClusterMessage message) {
            groupReassignmentMessages.add(message);
        }
    }
}
