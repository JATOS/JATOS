package batch.session;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Props;
import batch.BatchChannelRegistry;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import scala.Option;
import scala.collection.mutable.Set;

import static org.junit.Assert.*;

public class BatchChannelRegistryTest {

    private static ActorSystem system;

    private BatchChannelRegistry registry;
    private ActorRef a1;
    private ActorRef a2;

    public static class DummyActor extends AbstractActor {
        @Override
        public Receive createReceive() {
            return receiveBuilder().matchAny(_ -> { /* no-op */ }).build();
        }
    }

    @BeforeClass
    public static void setupClass() {
        Config cfg = ConfigFactory.parseString("org.apache.pekko.loglevel=WARNING\norg.apache.pekko.log-dead-letters=off");
        system = ActorSystem.create("test-system", cfg);
    }

    @AfterClass
    public static void tearDownClass() {
        if (system != null) {
            system.terminate();
        }
    }

    @Before
    public void setup() {
        registry = new BatchChannelRegistry();
        a1 = system.actorOf(Props.create(DummyActor.class));
        a2 = system.actorOf(Props.create(DummyActor.class));
    }

    @Test
    public void initiallyEmpty() {
        assertTrue(registry.isEmpty());
        assertFalse(registry.containsChannel(1L));
    }

    @Test
    public void registerAndLookupByIdAndChannel() {
        Option<ActorRef> prev = Option.apply(registry.register(1L, a1));
        // ChannelRegistry.register returns previous value from underlying map; first insert -> null
        assertTrue(prev.isEmpty());

        Option<ActorRef> ch = registry.getChannel(1L);
        assertTrue(ch.isDefined());
        assertEquals(a1, ch.get());

        assertFalse(registry.isEmpty());
        assertTrue(registry.containsChannel(1L));
    }

    @Test
    public void unregisterRemovesAndReturns() {
        registry.register(1L, a1);
        Option<ActorRef> removed = registry.unregister(1L);
        assertTrue(removed.isDefined());
        assertEquals(a1, removed.get());

        // After removal the registry is empty again and lookups fail
        assertTrue(registry.isEmpty());
        assertFalse(registry.containsChannel(1L));
        assertTrue(registry.getChannel(1L).isEmpty());
        assertFalse(registry.containsChannel(1L));

        // Removing non-existing returns empty Option
        assertTrue(registry.unregister(1L).isEmpty());
    }

    @Test
    public void registerOnExistingIdReturnsPreviousAndReplaces() {
        registry.register(1L, a1);
        ActorRef previous = registry.register(1L, a2);
        assertEquals("Second register should return previous ActorRef", a1, previous);

        // Now id -> a2
        assertEquals(a2, registry.getChannel(1L).get());
    }

    @Test
    public void allIdsAndChannelsExposeSetsWithCorrectContents() {
        registry.register(1L, a1);
        registry.register(2L, a2);

        Set<Object> ids = registry.getAllStudyResultIds();
        Set<ActorRef> chans = registry.getAllChannels();

        assertEquals(2, ids.size());
        assertEquals(2, chans.size());

        assertTrue(ids.contains(1L));
        assertTrue(ids.contains(2L));
        assertTrue(chans.contains(a1));
        assertTrue(chans.contains(a2));
    }
}
