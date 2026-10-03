package http.common;

import exceptions.common.JatosException;
import http.common.Http.Context;
import org.junit.After;
import org.junit.Test;
import play.libs.typedmap.TypedKey;
import play.mvc.Http.Cookie;
import play.test.Helpers;

import java.util.concurrent.*;

import static org.junit.Assert.*;

public class HttpContextConcurrencyTest {
    private static final TypedKey<String> USER = TypedKey.create("test-user");
    private final ExecutorService workers = Executors.newFixedThreadPool(2);

    @After
    public void close() throws Exception {
        workers.shutdownNow();
        assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
        Context.clear();
    }

    private Context context(String user) {
        return new Context(Helpers.fakeRequest("GET", "/" + user).session("user", user).build());
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private void assertBothWorkersClean() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        Callable<Void> check = () -> {
            await(barrier); // Force both pool threads to participate.
            assertThrows(JatosException.class, Context::current);
            return null;
        };
        Future<Void> first = workers.submit(check);
        Future<Void> second = workers.submit(check);
        first.get(5, TimeUnit.SECONDS);
        second.get(5, TimeUnit.SECONDS);
    }

    @Test
    public void overlappingRequestsKeepIdentityArgumentsAndResponseSeparate() throws Exception {
        Context alice = context("alice");
        Context bob = context("bob");
        CyclicBarrier barrier = new CyclicBarrier(2);
        CompletableFuture<?>[] tasks = new CompletableFuture<?>[2];
        int index = 0;
        for (Context context : new Context[]{alice, bob}) {
            tasks[index++] = CompletableFuture.runAsync(() -> {
                String user = Context.current().response().getSession("user").orElseThrow();
                Context.current().args().put(USER, user);
                Context.current().response().setHeader("X-User", user);
                Context.current().response().setCookie(Cookie.builder("user", user).build());
                Context.current().response().putFlash("user", user);
                await(barrier);
                assertSame(context, Context.current());
                assertEquals("/" + user, Context.current().requestHeader().path());
                assertEquals(user, Context.current().args().get(USER));
                assertEquals(user, Context.current().response().headers().get("X-User"));
                assertEquals(user, Context.current().response().cookie("user").orElseThrow().value());
                assertEquals(user, Context.current().response().flash().get("user").orElseThrow());
            }, Context.contextAwareExecutor(context, workers));
        }
        CompletableFuture.allOf(tasks).get(5, TimeUnit.SECONDS);
        assertBothWorkersClean();
    }

    @Test
    public void nestedFailureRestoresOuterBindingAndThenClearsWorker() throws Exception {
        Context outer = context("outer");
        Context inner = context("inner");
        workers.submit(() -> Context.withContext(outer, () -> {
            IllegalStateException failure = new IllegalStateException("expected");
            assertSame(failure, assertThrows(IllegalStateException.class, () ->
                    Context.withContext(inner, () -> { throw failure; })));
            assertSame(outer, Context.current());
            return null;
        })).get(5, TimeUnit.SECONDS);
        assertBothWorkersClean();
    }

    @Test
    public void delayedCallbackUsesCapturedContextAndRestoresCompletionThread() throws Exception {
        Context request = context("alice");
        Context completing = context("unrelated");
        CompletableFuture<String> gate = new CompletableFuture<>();
        CompletableFuture<String> result = Context.withContext(request, () ->
                gate.thenApply(Context.wrap(request, value -> {
                    assertSame(request, Context.current());
                    Context.current().response().putSession("completed", value);
                    return value;
                })));
        assertThrows(JatosException.class, Context::current);
        workers.submit(() -> Context.withContext(completing, () -> {
            gate.complete("yes");
            assertSame(completing, Context.current());
            return null;
        })).get(5, TimeUnit.SECONDS);
        assertEquals("yes", result.get(5, TimeUnit.SECONDS));
        assertEquals("yes", request.response().getSession("completed").orElseThrow());
        assertBothWorkersClean();
    }

    @Test
    public void failedAsyncCallbackClearsWorker() throws Exception {
        Context request = context("alice");
        CompletableFuture<Void> result = CompletableFuture.runAsync(() -> {
            assertSame(request, Context.current());
            throw new IllegalStateException("expected");
        }, Context.contextAwareExecutor(request, workers));
        assertTrue(assertThrows(ExecutionException.class, () -> result.get(5, TimeUnit.SECONDS))
                .getCause() instanceof IllegalStateException);
        assertBothWorkersClean();
    }

    @Test
    public void cancellationDoesNotClearContextUntilRunningWorkExits() throws Exception {
        Context request = context("alice");
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Void> exited = new CompletableFuture<>();
        CompletableFuture<Void> result = CompletableFuture.runAsync(() -> {
            try {
                assertSame(request, Context.current());
                entered.countDown();
                assertTrue(release.await(5, TimeUnit.SECONDS));
                assertSame(request, Context.current());
                exited.complete(null);
            } catch (Throwable e) {
                exited.completeExceptionally(e);
            }
        }, Context.contextAwareExecutor(request, workers));
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertTrue(result.cancel(true));
        } finally {
            release.countDown();
        }
        exited.get(5, TimeUnit.SECONDS);
        assertTrue(result.isCancelled());
        assertBothWorkersClean();
    }

    @Test
    public void cancellingQueuedWorkDoesNotLeakBindingOrRunBody() {
        Context request = context("alice");
        java.util.concurrent.atomic.AtomicReference<Runnable> queued = new java.util.concurrent.atomic.AtomicReference<>();
        CompletableFuture<Void> result = CompletableFuture.runAsync(() -> fail("Cancelled body ran"),
                Context.contextAwareExecutor(request, queued::set));
        result.cancel(false);
        queued.get().run();
        assertThrows(JatosException.class, Context::current);
    }
}
