package filters;

import http.common.Http.Context;
import org.apache.pekko.stream.Materializer;
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

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

public class ContextFilterTest {

    @After
    public void tearDown() {
        Context.clear();
    }

    @Test
    public void applyAttachesContextToRequestAttributes() {
        ContextFilter filter = new ContextFilter(mock(Materializer.class));
        RequestHeader request = Helpers.fakeRequest("GET", "/test").build();
        AtomicReference<RequestHeader> requestSeenByNextFilter = new AtomicReference<>();

        Result result = filter.apply(requestHeader -> {
            requestSeenByNextFilter.set(requestHeader);
            return CompletableFuture.completedFuture(Results.ok("done"));
        }, request).toCompletableFuture().join();

        assertEquals(200, result.status());

        RequestHeader enrichedRequest = requestSeenByNextFilter.get();
        assertNotNull(enrichedRequest);
        assertTrue(enrichedRequest.attrs().containsKey(Context.CONTEXT_TYPED_KEY));

        Context attachedContext = enrichedRequest.attrs().get(Context.CONTEXT_TYPED_KEY);
        assertNotNull(attachedContext);
        assertSame(request, attachedContext.requestHeader());
    }

    @Test
    public void applySyncsContextResponseHeadersCookiesSessionAndFlashIntoResult() {
        ContextFilter filter = new ContextFilter(mock(Materializer.class));
        RequestHeader request = Helpers.fakeRequest("GET", "/test").build();

        Result result = filter.apply(header -> {
                    Context context = header.attrs().get(Context.CONTEXT_TYPED_KEY);
                    context.response().setHeader("X-Test", "header-value");
                    context.response().setCookie(Cookie.builder("test-cookie", "cookie-value").build());
                    context.response().discardCookie("discard-cookie");
                    context.response().putSession("session-key", "session-value");
                    context.response().putFlash("flash-key", "flash-value");
                    return CompletableFuture.completedFuture(Results.ok("done"));
                }, request)
                .toCompletableFuture()
                .join();

        assertEquals("header-value", result.header("X-Test").orElse(""));
        assertEquals("cookie-value", result.cookies().get("test-cookie").orElseThrow().value());
        assertTrue(result.cookies().get("discard-cookie").isPresent());
        assertEquals("", result.cookies().get("discard-cookie").get().value());
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

        Result result = filter.apply(header ->
                                CompletableFuture.completedFuture(Results.ok("done")),
                        request)
                .toCompletableFuture()
                .join();

        assertNull(result.session());
        assertNull(result.flash());
    }

    @Test
    public void applyDoesNotModifyThreadLocalContext() {
        ContextFilter filter = new ContextFilter(mock(Materializer.class));
        RequestHeader previousRequest = Helpers.fakeRequest("GET", "/previous").build();
        Context previousContext = new Context(previousRequest);
        Context.setCurrent(previousContext);

        RequestHeader request = Helpers.fakeRequest("GET", "/test").build();
        AtomicReference<Context> contextInsideNextFilter = new AtomicReference<>();

        Result result = filter.apply(header -> {
            try {
                contextInsideNextFilter.set(Context.current());
            } catch (Exception e) {
                contextInsideNextFilter.set(null);
            }
            return CompletableFuture.completedFuture(Results.ok("done"));
        }, request).toCompletableFuture().join();

        assertEquals(200, result.status());
        assertSame(previousContext, contextInsideNextFilter.get());
        assertSame(previousContext, Context.current());
    }

    @Test
    public void applyPropagatesRuntimeExceptionFromResultStage() {
        ContextFilter filter = new ContextFilter(mock(Materializer.class));
        RequestHeader request = Helpers.fakeRequest("GET", "/test").build();
        RuntimeException exception = new IllegalStateException("boom");

        CompletionStage<Result> resultStage = filter.apply(header -> {
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

        CompletionStage<Result> resultStage = filter.apply(header -> {
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

    @Test
    public void unchangedContextPreservesExplicitResultSessionAndFlash() {
        ContextFilter filter = new ContextFilter(mock(Materializer.class));
        Result result = filter.apply(header -> CompletableFuture.completedFuture(Results.ok()
                        .withSession(java.util.Map.of("user", "direct"))
                        .withFlash(java.util.Map.of("notice", "direct"))),
                Helpers.fakeRequest().session("user", "incoming").build()).toCompletableFuture().join();
        assertEquals("direct", result.session().get("user").orElseThrow());
        assertEquals("direct", result.flash().get("notice").orElseThrow());
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Test
    public void discardWinsOverSetCookieAndUsesConfiguredBasePath() {
        try (org.mockito.MockedStatic<general.common.Common> common = org.mockito.Mockito.mockStatic(general.common.Common.class)) {
            common.when(general.common.Common::getJatosUrlBasePath).thenReturn("/jatos/");
            ContextFilter filter = new ContextFilter(mock(Materializer.class));
            Result result = filter.apply(header -> {
                Context context = Context.current(header);
                context.response().setCookie(Cookie.builder("id", "new").withPath("/jatos/").build());
                context.response().discardCookie("id");
                return CompletableFuture.completedFuture(Results.ok());
            }, Helpers.fakeRequest().build()).toCompletableFuture().join();
            Cookie discarded = result.cookies().get("id").orElseThrow();
            assertEquals("/jatos/", discarded.path());
            assertEquals("", discarded.value());
            assertEquals(Integer.valueOf(0), discarded.maxAge());
        }
    }

}