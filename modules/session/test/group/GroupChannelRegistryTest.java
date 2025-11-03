package group;

import akka.actor.ActorRef;
import org.junit.Before;
import org.junit.Test;
import scala.Option;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class GroupChannelRegistryTest {

    private GroupChannelRegistry registry;

    @Before
    public void setUp() {
        registry = new GroupChannelRegistry();
    }

    @Test
    public void emptyRegistry_properties() {
        assertTrue(registry.isEmpty());
        assertFalse(registry.containsStudyResult(1L));
        assertTrue(registry.getChannel(1L).isEmpty());
        assertTrue(registry.getChannelActor(1L).isEmpty());
        assertEquals(0, registry.getAllStudyResultIds().size());
        assertEquals(0, registry.getAllChannels().size());
    }

    @Test
    public void register_addsEntry_andReturnsPrevious_onOverwrite() {
        GroupChannelActor ch1 = mock(GroupChannelActor.class);
        GroupChannelActor ch2 = mock(GroupChannelActor.class);

        // First register -> no previous value
        GroupChannelActor prev1 = registry.register(42L, ch1);
        assertNull(prev1);
        assertFalse(registry.isEmpty());
        assertTrue(registry.containsStudyResult(42L));
        assertTrue(registry.getChannel(42L).isDefined());
        assertSame(ch1, registry.getChannel(42L).get());

        // Overwrite same id -> returns previous channel
        GroupChannelActor prev2 = registry.register(42L, ch2);
        assertSame(ch1, prev2);
        assertTrue(registry.containsStudyResult(42L));
        assertSame(ch2, registry.getChannel(42L).get());
    }

    @Test
    public void getChannelActor_returnsActorRef_fromChannelSelf() {
        GroupChannelActor ch = mock(GroupChannelActor.class, withSettings().defaultAnswer(RETURNS_DEEP_STUBS));
        ActorRef ref = mock(ActorRef.class);
        when(ch.self()).thenReturn(ref);

        registry.register(7L, ch);

        Option<ActorRef> opt = registry.getChannelActor(7L);
        assertTrue(opt.isDefined());
        assertSame(ref, opt.get());
    }

    @Test
    public void unregister_removes_andIsIdempotent() {
        GroupChannelActor ch = mock(GroupChannelActor.class);
        registry.register(100L, ch);
        assertTrue(registry.getChannel(100L).isDefined());

        // First unregister returns Some(ch)
        Option<GroupChannelActor> removed1 = registry.unregister(100L);
        assertTrue(removed1.isDefined());
        assertSame(ch, removed1.get());
        assertTrue(registry.getChannel(100L).isEmpty());
        assertFalse(registry.containsStudyResult(100L));
        assertTrue(registry.isEmpty());

        // Second unregister returns None
        Option<GroupChannelActor> removed2 = registry.unregister(100L);
        assertTrue(removed2.isEmpty());
    }

    @Test
    public void getAll_members_collectionsReflectState() {
        GroupChannelActor ch1 = mock(GroupChannelActor.class);
        GroupChannelActor ch2 = mock(GroupChannelActor.class);
        registry.register(1L, ch1);
        registry.register(2L, ch2);

        // The Scala Set contains Longs; verify size and membership via contains
        assertEquals(2, registry.getAllStudyResultIds().size());
        assertTrue(registry.getAllStudyResultIds().contains(1L));
        assertTrue(registry.getAllStudyResultIds().contains(2L));

        assertEquals(2, registry.getAllChannels().size());
        assertTrue(registry.getAllChannels().contains(ch1));
        assertTrue(registry.getAllChannels().contains(ch2));
    }
}
