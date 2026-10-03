package filters;

import actions.common.AsyncAction;
import actions.common.ContextActionCreator;
import executor.common.IOExecutor;
import executor.common.StudyAssetsExecutor;
import http.common.Http.Context;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import play.Application;
import play.http.ActionCreator;
import play.inject.guice.GuiceApplicationBuilder;
import play.mvc.Http;
import play.mvc.Result;
import play.test.Helpers;
import play.test.TestServer;

import javax.inject.Inject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

import static org.junit.Assert.*;
import static play.inject.Bindings.bind;

/** Uses production generated routes and application.conf, without manually composing actions. */
public class ContextWiringIntegrationTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    public static class CheckingAsyncAction extends AsyncAction {
        @Inject public CheckingAsyncAction(IOExecutor io, StudyAssetsExecutor assets) {
            super(io, assets);
        }

        @Override public CompletionStage<Result> call(Http.Request request) {
            // This assertion is BEFORE AsyncAction's own binding, so it verifies the action creator's order.
            Context context = Context.current();
            assertSame(Context.current(request), context);
            context.response().setHeader("X-Context-Before-Async", request.path());
            return super.call(request);
        }
    }

    @Test
    public void generatedJavaRoutesReceiveContextBeforeActionAnnotations() throws Exception {
        String root = temp.getRoot().getAbsolutePath();
        Application app = new GuiceApplicationBuilder()
                .configure("db.default.url", "jdbc:h2:mem:context-wiring-" + UUID.randomUUID() +
                        ";MODE=MYSQL;DATABASE_TO_UPPER=FALSE;IGNORECASE=TRUE;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1")
                .configure("jatos.studyAssetsRootPath", root + "/assets")
                .configure("jatos.studyLogs.path", root + "/study-logs")
                .configure("jatos.resultUploads.path", root + "/uploads")
                .configure("jatos.logs.path", root + "/logs")
                .configure("jatos.tmpPath", root + "/tmp")
                .overrides(bind(AsyncAction.class).to(CheckingAsyncAction.class))
                .build();
        TestServer server = Helpers.testServer(0, app);
        Helpers.start(server);
        try (HttpClient client = HttpClient.newHttpClient()) {
            assertTrue(app.injector().instanceOf(ActionCreator.class) instanceof ContextActionCreator);
            assertTrue(app.config().getBoolean("play.http.actionComposition.executeActionCreatorActionFirst"));
            String base = "http://127.0.0.1:" + server.getRunningHttpPort().orElseThrow();
            HttpResponse<String> signin = client.send(HttpRequest.newBuilder(URI.create(base + "/jatos/signin"))
                    .timeout(Duration.ofSeconds(15)).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, signin.statusCode());
            assertEquals("/jatos/signin", signin.headers().firstValue("X-Context-Before-Async").orElseThrow());

            // This generated route uses @Async(IO), with no authentication annotation ahead of it.
            HttpResponse<String> failedLogin = client.send(HttpRequest.newBuilder(URI.create(base + "/jatos/signin/local"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Csrf-Token", "nocheck")
                    .POST(HttpRequest.BodyPublishers.ofString("username=context-probe-missing-user&password=invalid"))
                    .build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(401, failedLogin.statusCode());
            // Also proves ContextFilter synchronized the very same context's response.
            assertEquals("/jatos/signin/local", failedLogin.headers().firstValue("X-Context-Before-Async").orElseThrow());
        } finally {
            Helpers.stop(server);
        }
    }
}
