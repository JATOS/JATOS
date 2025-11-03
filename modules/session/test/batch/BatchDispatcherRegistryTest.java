package batch;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * Tests for BatchDispatcherRegistry
 */
public class BatchDispatcherRegistryTest {

    private static BatchDispatcherRegistry newRegistry() {
        // We need a registry reference for constructing BatchDispatcher instances.
        // Create a holder that we can fill once the registry is created.
        final BatchDispatcherRegistry[] holder = new BatchDispatcherRegistry[1];
        BatchDispatcher.Factory registryAwareFactory = batchId -> {
            // Pass null for non-essential dependencies: tests only verify identity semantics.
            return new BatchDispatcher(null, holder[0], null, null, batchId);
        };
        holder[0] = new BatchDispatcherRegistry(registryAwareFactory);
        return holder[0];
    }

    @Test
    public void getOrRegisterReturnsSameDispatcherForSameId() {
        BatchDispatcherRegistry registry = newRegistry();

        long id = 10L;
        BatchDispatcher d1 = registry.getOrRegister(id);
        BatchDispatcher d2 = registry.getOrRegister(id);

        assertNotNull(d1);
        assertEquals("Same id should return same dispatcher", d1, d2);
    }

    @Test
    public void getOrRegisterReturnsDifferentDispatchersForDifferentIds() {
        BatchDispatcherRegistry registry = newRegistry();

        BatchDispatcher d1 = registry.getOrRegister(1L);
        BatchDispatcher d2 = registry.getOrRegister(2L);

        assertNotNull(d1);
        assertNotNull(d2);
        assertNotEquals("Different ids should return different dispatchers", d1, d2);
    }

    @Test
    public void unregisterRemovesDispatcherAndAllowsRecreation() {
        BatchDispatcherRegistry registry = newRegistry();

        long id = 42L;
        BatchDispatcher first = registry.getOrRegister(id);
        assertNotNull(first);

        registry.unregister(id);

        BatchDispatcher second = registry.getOrRegister(id);
        assertNotNull(second);
        assertNotEquals("After unregister, a new dispatcher should be created for same id", first, second);
    }

    @Test
    public void concurrentGetOrRegisterReturnsSameInstance() throws InterruptedException {
        BatchDispatcherRegistry registry = newRegistry();
        final long id = 777L;

        int threads = 8;
        List<BatchDispatcher> refs = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try {
                    ready.countDown();
                    start.await(5, TimeUnit.SECONDS);
                    refs.add(registry.getOrRegister(id));
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }).start();
        }

        assertTrue("Threads not ready in time", ready.await(5, TimeUnit.SECONDS));
        start.countDown();
        assertTrue("Threads did not finish in time", done.await(5, TimeUnit.SECONDS));

        assertFalse(refs.isEmpty());
        BatchDispatcher first = refs.get(0);
        for (BatchDispatcher r : refs) {
            assertEquals("All threads should see same dispatcher", first, r);
        }
    }
}
