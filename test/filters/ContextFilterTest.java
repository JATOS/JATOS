package filters;

import akka.stream.Materializer;
import exceptions.common.JatosException;
import http.common.Http.Context;
import org.junit.After;
import org.junit.Test;
import play.mvc.Http.Cookie;
import play.mvc.Http.RequestHeader;
import play.mvc.Result;
import play.mvc.Results;
import play.test.Helpers;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

public class ContextFilterTest {

    @After
    public void tearDown() {
        Context.clear();
    }

    @Test
    public void applyAttachesContextToRequestAndBindsItWhileInvokingNextFilter() {
        ContextFilter filter = new ContextFilter(mock(Materializer.class));
        RequestHeader request = Helpers.fakeRequest("GET", "/test").build();
        AtomicReference<RequestHeader> requestSeenByNextFilter = new AtomicReference<>();
        AtomicReference<Context> currentContextSeenByNextFilter = new AtomicReference<>();

        Result result = filter.apply(nextFilter(requestSeenByNextFilter, currentContextSeenByNextFilter), request)
                .toCompletableFuture()
                .join();

        assertEquals(200, result.status());

        RequestHeader enrichedRequest = requestSeenByNextFilter.get();
        assertNotNull(enrichedRequest);
        assertTrue(enrichedRequest.attrs().containsKey(Context.CONTEXT_TYPED_KEY));

        Context attachedContext = enrichedRequest.attrs().get(Context.CONTEXT_TYPED_KEY);
        assertSame(attachedContext, currentContextSeenByNextFilter.get());
        assertSame(request, attachedContext.requestHeader());
    }

    @Test
    public void applyClearsThreadLocalImmediatelyAfterNextFilterReturns() {
        ContextFilter filter = new ContextFilter(mock(Materializer.class));
        RequestHeader request = Helpers.fakeRequest("GET", "/test").build();
        CompletableFuture<Result> pendingResult = new CompletableFuture<>();

        CompletionStage<Result> resultStage = filter.apply(requestHeader -> pendingResult, request);

        try {
            Context.current();
            fail("Expected Context.current() to fail after nextFilter returned synchronously");
        } catch (JatosException e) {
            assertEquals("There is no HTTP Context available from here.", e.getMessage());
        }

        pendingResult.complete(Results.ok("done"));
        assertEquals(200, resultStage.toCompletableFuture().join().status());
    }

    @Test
    public void applySyncsContextResponseHeadersCookiesSessionAndFlashIntoResult() {
        ContextFilter filter = new ContextFilter(mock(Materializer.class));
        RequestHeader request = Helpers.fakeRequest("GET", "/test").build();

        Result result = filter.apply(requestHeader -> {
                    Context context = Context.current();
                    context.response().setHeader("X-Test", "header-value");
                    context.response().setCookie(Cookie.builder("test-cookie", "cookie-value").build());
                    context.response().putSession("session-key", "session-value");
                    context.response().putFlash("flash-key", "flash-value");
                    return CompletableFuture.completedFuture(Results.ok("done"));
                }, request)
                .toCompletableFuture()
                .join();

        assertEquals("header-value", result.header("X-Test").orElse(""));
        assertEquals("cookie-value", result.cookies().get("test-cookie").orElseThrow().value());
        assertEquals("session-value", result.session().get("session-key").orElse(""));
        assertEquals("flash-value", result.flash().get("flash-key").orElse(""));
    }

    @Test
    public void applyDoesNotReplaceSessionOrFlashIfTheyWereNotChanged() {
        ContextFilter filter = new ContextFilter(mock(Materializer.class));
        RequestHeader request = Helpers.fakeRequest("GET", "/test")
                .session("existing-session-key", "existing-session-value")
                .flash("existing-flash-key", "existing-flash-value")
                .build();

        Result result = filter.apply(requestHeader ->
                                CompletableFuture.completedFuture(Results.ok("done")),
                        request)
                .toCompletableFuture()
                .join();

        assertNull(result.session());
        assertNull(result.flash());
    }

    @Test
    public void asyncCodeCanUseContextFromRequestAttrsWhenExplicitlyBound() {
        ContextFilter filter = new ContextFilter(mock(Materializer.class));
        RequestHeader request = Helpers.fakeRequest("GET", "/test").build();
        CompletableFuture<Result> pendingResult = new CompletableFuture<>();

        CompletionStage<Result> resultStage = filter.apply(requestHeader -> {
            Context contextFromRequest = requestHeader.attrs().get(Context.CONTEXT_TYPED_KEY);

            return pendingResult.thenApply(result ->
                    Context.withContext(contextFromRequest, () -> {
                        Context.current().response().setHeader("X-Async", "available");
                        return result;
                    }));
        }, request);

        pendingResult.complete(Results.ok("done"));

        Result result = resultStage.toCompletableFuture().join();

        assertEquals("available", result.header("X-Async").orElse(""));

        try {
            Context.current();
            fail("Expected Context.current() to fail after completion handler finished");
        } catch (JatosException e) {
            assertEquals("There is no HTTP Context available from here.", e.getMessage());
        }
    }

    @Test
    public void applyRestoresPreviousThreadLocalContextAfterCompletionHandlerRuns() {
        ContextFilter filter = new ContextFilter(mock(Materializer.class));
        RequestHeader previousRequest = Helpers.fakeRequest("GET", "/previous").build();
        Context previousContext = new Context(previousRequest);
        RequestHeader request = Helpers.fakeRequest("GET", "/test").build();
        CompletableFuture<Result> pendingResult = new CompletableFuture<>();

        CompletionStage<Result> resultStage = filter.apply(requestHeader -> pendingResult, request);

        Context.setCurrent(previousContext);
        pendingResult.complete(Results.ok("done"));

        assertEquals(200, resultStage.toCompletableFuture().join().status());
        assertSame(previousContext, Context.current());
    }

    @Test
    public void applyPropagatesRuntimeExceptionFromResultStage() {
        ContextFilter filter = new ContextFilter(mock(Materializer.class));
        RequestHeader request = Helpers.fakeRequest("GET", "/test").build();
        RuntimeException exception = new IllegalStateException("boom");

        CompletionStage<Result> resultStage = filter.apply(requestHeader -> {
            CompletableFuture<Result> failed = new CompletableFuture<>();
            failed.completeExceptionally(exception);
            return failed;
        }, request);

        try {
            resultStage.toCompletableFuture().join();
            fail("Expected exception");
        } catch (CompletionException e) {
            assertSame(exception, e.getCause());
        }
    }

    @Test
    public void applyWrapsCheckedExceptionFromResultStageInCompletionException() {
        ContextFilter filter = new ContextFilter(mock(Materializer.class));
        RequestHeader request = Helpers.fakeRequest("GET", "/test").build();
        Exception exception = new Exception("checked boom");

        CompletionStage<Result> resultStage = filter.apply(requestHeader -> {
            CompletableFuture<Result> failed = new CompletableFuture<>();
            failed.completeExceptionally(exception);
            return failed;
        }, request);

        try {
            resultStage.toCompletableFuture().join();
            fail("Expected exception");
        } catch (CompletionException e) {
            assertTrue(e.getCause() instanceof Exception);
            assertTrue(e.getCause().getMessage().contains("checked boom"));
            assertSame(exception, e.getCause());
        }
    }

    private static Function<RequestHeader, CompletionStage<Result>> nextFilter(
            AtomicReference<RequestHeader> requestSeenByNextFilter,
            AtomicReference<Context> currentContextSeenByNextFilter) {
        return requestHeader -> {
            requestSeenByNextFilter.set(requestHeader);
            currentContextSeenByNextFilter.set(Context.current());
            return CompletableFuture.completedFuture(Results.ok("done"));
        };
    }

}