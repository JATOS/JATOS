package group;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import scala.Option;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class GroupDispatcherRegistryTest {

    private GroupDispatcherRegistry registry;
    private GroupDispatcher.Factory factory;

    @Before
    public void setUp() {
        factory = mock(GroupDispatcher.Factory.class);
        registry = new GroupDispatcherRegistry(factory);
    }

    @Test
    public void get_onEmpty_returnsNone() {
        Option<GroupDispatcher> d = registry.get(1L);
        assertTrue("Expected empty Option on unknown id", d.isEmpty());
    }

    @Test
    public void getOrRegister_createsOncePerId_andCaches() {
        GroupDispatcher d1 = mock(GroupDispatcher.class);
        GroupDispatcher d2 = mock(GroupDispatcher.class);

        when(factory.create(eq(10L))).thenReturn(d1);
        when(factory.create(eq(11L))).thenReturn(d2);

        // First time for 10L -> create
        GroupDispatcher r1a = registry.getOrRegister(10L);
        assertSame(d1, r1a);
        // Second time for 10L -> cached, factory not called again
        GroupDispatcher r1b = registry.getOrRegister(10L);
        assertSame(d1, r1b);

        // Different id -> different dispatcher
        GroupDispatcher r2 = registry.getOrRegister(11L);
        assertSame(d2, r2);

        // Verify factory interactions
        InOrder inOrder = inOrder(factory);
        inOrder.verify(factory).create(10L);
        inOrder.verify(factory).create(11L);
        verifyNoMoreInteractions(factory);

        // get should now return Some for both ids
        assertTrue(registry.get(10L).isDefined());
        assertTrue(registry.get(11L).isDefined());
    }

    @Test
    public void hasChannel_delegatesToRegisteredDispatchers() {
        GroupDispatcher d1 = mock(GroupDispatcher.class);
        GroupDispatcher d2 = mock(GroupDispatcher.class);
        when(factory.create(eq(1L))).thenReturn(d1);
        when(factory.create(eq(2L))).thenReturn(d2);

        registry.getOrRegister(1L);
        registry.getOrRegister(2L);

        // None of the dispatchers reports channel -> false
        when(d1.hasChannel(100L)).thenReturn(false);
        when(d2.hasChannel(100L)).thenReturn(false);
        assertFalse(registry.hasChannel(100L));

        // One of them returns true -> true
        when(d2.hasChannel(100L)).thenReturn(true);
        assertTrue(registry.hasChannel(100L));

        verify(d1, atLeastOnce()).hasChannel(100L);
        verify(d2, atLeastOnce()).hasChannel(100L);
    }

    @Test
    public void unregister_removesDispatcher_andIsIdempotent() {
        GroupDispatcher d1 = mock(GroupDispatcher.class);
        when(factory.create(eq(5L))).thenReturn(d1);

        registry.getOrRegister(5L);
        assertTrue(registry.get(5L).isDefined());

        // Remove existing
        registry.unregister(5L);
        assertTrue(registry.get(5L).isEmpty());

        // Remove again (should be no-op)
        registry.unregister(5L);
        assertTrue(registry.get(5L).isEmpty());
    }
}
