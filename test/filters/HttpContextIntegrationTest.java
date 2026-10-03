package filters;

import actions.common.AsyncAction;
import actions.common.ContextActionCreator;
import com.typesafe.config.ConfigFactory;
import exceptions.common.JatosException;
import executor.common.IOExecutor;
import executor.common.StudyAssetsExecutor;
import http.common.Http.Context;
import org.junit.Test;
import play.Application;
import play.inject.guice.GuiceApplicationBuilder;
import play.libs.typedmap.TypedKey;
import play.mvc.Action;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.Results;
import play.routing.Router;
import play.routing.RoutingDsl;
import play.test.Helpers;
import play.test.TestServer;

import javax.inject.Inject;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static play.inject.Bindings.bind;

/** Real HTTP, signed session/flash cookies, filters, and asynchronous Java action composition. */
public class HttpContextIntegrationTest {
    private static final TypedKey<String> IDENTITY = TypedKey.create("probe-identity");

    public static class ProbeState implements AutoCloseable {
        final ExecutorService actions = Executors.newSingleThreadExecutor();
        final ExecutorService callbacks = Executors.newSingleThreadExecutor();
        final CountDownLatch entered = new CountDownLatch(2);
        final CompletableFuture<Void> release = new CompletableFuture<>();
        final IOExecutor io = mock(IOExecutor.class);
        final StudyAssetsExecutor assets = mock(StudyAssetsExecutor.class);

        public ProbeState() {
            doAnswer(invocation -> {
                actions.execute(invocation.getArgument(0));
                return null;
            }).when(io).execute(any(Runnable.class));
        }

        @SuppressWarnings("unchecked")
        CompletionStage<Result> call(Http.Request request) {
            AsyncAction async = new AsyncAction(io, assets);
            async.configuration = mock(AsyncAction.Async.class);
            when(async.configuration.value()).thenReturn(AsyncAction.Executor.IO);
            async.delegate = new Action.Simple() {
                @Override public CompletionStage<Result> call(Http.Request req) {
                    assertSame(Context.current(req), Context.current());
                    Context context = Context.current();
                    String user = context.response().getSession("user").orElse("anonymous");
                    context.args().put(IDENTITY, user);
                    switch (req.path()) {
                        case "/seed":
                            context.response().putSession("user", req.header("X-Test-User").orElseThrow());
                            return CompletableFuture.completedFuture(Results.ok());
                        case "/delayed":
                            entered.countDown();
                            return release.thenApplyAsync(Context.wrap(context, ignored -> {
                                assertSame(context, Context.current());
                                assertEquals(user, Context.current().args().get(IDENTITY));
                                assertEquals(user, Context.current().response().getSession("user").orElseThrow());
                                context.response().setHeader("X-Owner", user);
                                context.response().setCookie(Http.Cookie.builder("owner", "first").build());
                                context.response().setCookie(Http.Cookie.builder("owner", user).build());
                                context.response().putSession("completed", user);
                                context.response().putFlash("notice", user);
                                return Results.ok(user).withHeader("X-Owner", "direct")
                                        .withSession(java.util.Map.of("user", "direct")).withFlash(java.util.Map.of("notice", "direct"));
                            }), callbacks);
                        case "/logout":
                            context.response().clearSession();
                            context.response().putFlash("notice", "signed-out");
                            return CompletableFuture.completedFuture(Results.redirect("/echo"));
                        case "/denied":
                            context.response().putFlash("notice", "denied");
                            return CompletableFuture.completedFuture(Results.unauthorized());
                        case "/failed":
                            return CompletableFuture.completedFuture("").thenApplyAsync(Context.wrap(context, ignored -> {
                                assertSame(context, Context.current());
                                throw new IllegalStateException("expected integration-test failure");
                            }), callbacks);
                        default:
                            return CompletableFuture.completedFuture(Results.ok(user + ":" +
                                    req.flash().get("notice").orElse("none") + ":" +
                                    req.session().get("completed").orElse("none")));
                    }
                }
            };
            Action<Object> creator = new ContextActionCreator().createAction(request, null);
            creator.delegate = async;
            return creator.call(request);
        }

        void assertClean() throws Exception {
            for (ExecutorService worker : List.of(actions, callbacks)) {
                worker.submit(() -> assertThrows(JatosException.class, Context::current)).get(10, TimeUnit.SECONDS);
            }
        }

        @Override public void close() {
            release.complete(null);
            actions.shutdownNow();
            callbacks.shutdownNow();
            try {
                assertTrue(actions.awaitTermination(5, TimeUnit.SECONDS));
                assertTrue(callbacks.awaitTermination(5, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }
    }

    public static class ProbeRouter implements play.api.routing.SimpleRouter {
        private final Router router;
        @Inject public ProbeRouter(RoutingDsl dsl, ProbeState state) {
            router = dsl.GET("/*path").routingAsync((Http.Request request, String path) -> state.call(request)).build();
        }
        @Override public scala.PartialFunction<play.api.mvc.RequestHeader, play.api.mvc.Handler> routes() {
            return router.asScala().routes();
        }
    }

    private HttpRequest request(String base, String path) {
        return HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(15)).GET().build();
    }

    private HttpResponse<String> get(HttpClient client, String base, String path) throws Exception {
        return client.send(request(base, path), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    public void interleavedRequestsSyncOnlyTheirOwnResponsesAndCleanUpAfterErrors() throws Exception {
        try (ProbeState state = new ProbeState();
             HttpClient alice = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
             HttpClient bob = HttpClient.newBuilder().cookieHandler(new CookieManager()).build()) {
            Application app = new GuiceApplicationBuilder()
                    .loadConfig(ConfigFactory.defaultReference())
                    .configure("play.http.router", ProbeRouter.class.getName())
                    .configure("play.filters.enabled", List.of(ContextFilter.class.getName()))
                    .configure("play.http.secret.key", "context-integration-test-secret-at-least-32-characters")
                    .overrides(bind(ProbeState.class).toInstance(state))
                    .build();
            TestServer server = Helpers.testServer(0, app);
            Helpers.start(server);
            try {
                String base = "http://127.0.0.1:" + server.getRunningHttpPort().orElseThrow();
                for (var entry : java.util.Map.of("alice", alice, "bob", bob).entrySet()) {
                    HttpResponse<String> seeded = entry.getValue().send(HttpRequest.newBuilder(URI.create(base + "/seed"))
                            .header("X-Test-User", entry.getKey()).timeout(Duration.ofSeconds(15)).GET().build(),
                            HttpResponse.BodyHandlers.ofString());
                    assertEquals(200, seeded.statusCode());
                }
                CompletableFuture<HttpResponse<String>> a = alice.sendAsync(request(base, "/delayed"), HttpResponse.BodyHandlers.ofString());
                CompletableFuture<HttpResponse<String>> b = bob.sendAsync(request(base, "/delayed"), HttpResponse.BodyHandlers.ofString());
                assertTrue(state.entered.await(10, TimeUnit.SECONDS));
                // Both actions have returned pending futures; their shared worker must already be clean.
                state.assertClean();
                state.release.complete(null);
                for (var entry : java.util.Map.of("alice", a, "bob", b).entrySet()) {
                    HttpResponse<String> response = entry.getValue().get(15, TimeUnit.SECONDS);
                    assertEquals(200, response.statusCode());
                    assertEquals(entry.getKey(), response.body());
                    assertEquals(entry.getKey(), response.headers().firstValue("X-Owner").orElseThrow());
                    assertTrue(response.headers().allValues("Set-Cookie").stream()
                            .anyMatch(cookie -> cookie.startsWith("owner=" + entry.getKey() + ";")));
                }
                assertEquals("alice:alice:alice", get(alice, base, "/echo").body());
                assertEquals("bob:bob:bob", get(bob, base, "/echo").body());
                assertEquals(401, get(alice, base, "/denied").statusCode());
                assertEquals("alice:denied:alice", get(alice, base, "/echo").body());
                assertEquals(500, get(alice, base, "/failed").statusCode());
                HttpResponse<String> afterFailure = get(bob, base, "/echo");
                assertEquals("bob:none:bob", afterFailure.body());
                assertTrue(afterFailure.headers().firstValue("X-Owner").isEmpty());
                HttpResponse<String> logout = get(alice, base, "/logout");
                assertEquals(303, logout.statusCode());
                assertEquals("/echo", logout.headers().firstValue("Location").orElseThrow());
                assertEquals("anonymous:signed-out:none", get(alice, base, "/echo").body());
                state.assertClean();
            } finally {
                state.release.complete(null);
                Helpers.stop(server);
            }
        }
    }
}
