package actions.common;

import exceptions.common.JatosException;
import executor.common.IOExecutor;
import executor.common.StudyAssetsExecutor;
import http.common.Http.Context;
import org.junit.After;
import org.junit.Test;
import play.mvc.Action;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.Results;
import play.test.Helpers;

import java.util.concurrent.*;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class AsyncActionTest {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    @After public void close() throws Exception {
        worker.shutdownNow();
        assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
        Context.clear();
    }

    private AsyncAction action(AsyncAction.Executor mode, Action.Simple delegate) {
        IOExecutor io = mock(IOExecutor.class);
        StudyAssetsExecutor assets = mock(StudyAssetsExecutor.class);
        doAnswer(invocation -> { worker.execute(invocation.getArgument(0)); return null; })
                .when(io).execute(any(Runnable.class));
        doAnswer(invocation -> { worker.execute(invocation.getArgument(0)); return null; })
                .when(assets).execute(any(Runnable.class));
        AsyncAction action = new AsyncAction(io, assets);
        action.configuration = mock(AsyncAction.Async.class);
        when(action.configuration.value()).thenReturn(mode);
        action.delegate = delegate;
        return action;
    }

    @Test public void allExecutorModesBindRequestAndReleaseThreadBeforeResultCompletes() throws Exception {
        for (AsyncAction.Executor mode : AsyncAction.Executor.values()) {
            Http.Request request = Helpers.fakeRequest().build();
            Context context = new Context(request);
            CompletableFuture<Result> delayed = new CompletableFuture<>();
            CountDownLatch entered = new CountDownLatch(1);
            AsyncAction action = action(mode, new Action.Simple() {
                @Override public CompletionStage<Result> call(Http.Request req) {
                    assertSame(context, Context.current());
                    assertSame(context, Context.current(req));
                    entered.countDown();
                    return delayed;
                }
            });
            CompletionStage<Result> result = action.call(request.addAttr(Context.CONTEXT_TYPED_KEY, context));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertThrows(JatosException.class, Context::current);
            worker.submit(() -> assertThrows(JatosException.class, Context::current)).get(5, TimeUnit.SECONDS);
            assertFalse(result.toCompletableFuture().isDone());
            delayed.complete(Results.ok());
            assertEquals(200, result.toCompletableFuture().get(5, TimeUnit.SECONDS).status());
        }
    }

    @Test public void workerIsCleanAfterDelegateThrowsOrReturnsFailedStage() throws Exception {
        for (boolean synchronous : new boolean[]{true, false}) {
            Http.Request request = Helpers.fakeRequest().build();
            Context context = new Context(request);
            IllegalStateException failure = new IllegalStateException("expected");
            AsyncAction action = action(AsyncAction.Executor.IO, new Action.Simple() {
                @Override public CompletionStage<Result> call(Http.Request req) {
                    assertSame(context, Context.current());
                    if (synchronous) throw failure;
                    return CompletableFuture.failedFuture(failure);
                }
            });
            CompletionStage<Result> result = action.call(request.addAttr(Context.CONTEXT_TYPED_KEY, context));
            assertSame(failure, assertThrows(ExecutionException.class,
                    () -> result.toCompletableFuture().get(5, TimeUnit.SECONDS)).getCause());
            worker.submit(() -> assertThrows(JatosException.class, Context::current)).get(5, TimeUnit.SECONDS);
        }
    }
}
