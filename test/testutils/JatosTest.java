package testutils;

import akka.stream.IOResult;
import akka.stream.Materializer;
import akka.stream.javadsl.FileIO;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Guice;
import daos.common.StudyDao;
import daos.common.UserDao;
import exceptions.common.NotFoundException;
import general.common.Common;
import http.common.Http.Context;
import models.common.Study;
import models.common.User;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import play.Application;
import play.ApplicationLoader;
import play.Environment;
import play.db.jpa.JPAApi;
import play.inject.guice.GuiceApplicationBuilder;
import play.inject.guice.GuiceApplicationLoader;
import play.libs.Files.TemporaryFileCreator;
import play.libs.Json;
import play.libs.ws.WSClient;
import play.libs.ws.WSRequest;
import play.libs.ws.WSResponse;
import play.mvc.Http.MultipartFormData.FilePart;
import play.mvc.Http.RequestBuilder;
import play.mvc.Result;
import play.test.Helpers;
import play.test.TestServer;
import services.gui.ApiTokenService;
import services.gui.UserService;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static play.test.Helpers.*;

/**
 * Parent class for all tests that need the JATOS application running.
 */
public class JatosTest {

    public static final String TEST_RESOURCES_POTATO_COMPASS_JZIP = "/test/resources/potato_compass.jzip";

    @Inject
    protected Application application;

    @Inject
    protected JPAApi jpaApi;

    protected User admin;

    protected String apiToken;

    // Lazily initialized only when a test uses api(...)/apiGet(...)/etc.
    private TestServer testServer;
    private int testServerPort;
    private WSClient ws;

    @Before
    public void startApp() {
        application = Helpers.fakeApplication();

        GuiceApplicationBuilder builder = new GuiceApplicationLoader()
                .builder(new ApplicationLoader.Context(Environment.simple()));
        Guice.createInjector(builder.applicationModule()).injectMembers(this);

        Helpers.start(application);

        admin = getAdmin();
        apiToken = createApiToken(admin);

        Context.setCurrent(new Context(Helpers.fakeRequest().build()));
    }

    @After
    public void stopApp() throws Exception {
        dropDatabase(); // Remove studies because H2 doesn't get cleared between tests

        if (testServer != null) {
            testServer.stop();
            testServer = null;
        }

        Helpers.stop(application);

        removeAllStudyAssets();
        removeAllResultUploads();
        removeAllStudyLogs();
        removeAllLogs();

        Context.clear();
    }

    /**
     * Starts the {@link TestServer} on demand so tests that only use {@code route(application, ...)} don't pay the
     * cost.
     */
    private void ensureTestServerStarted() {
        if (testServer != null) return;
        testServerPort = findFreePort();
        testServer = Helpers.testServer(testServerPort, application);
        testServer.start();
        ws = application.injector().instanceOf(WSClient.class);
    }

    private static int findFreePort() {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (java.io.IOException e) {
            throw new RuntimeException("Could not find a free port", e);
        }
    }

    /**
     * Returns a fluent {@link ApiCall} builder that already targets the running test server
     * and carries the admin API token. Verb methods on {@code ApiCall} block until the response
     * arrives and return a {@link WSResponse}, so tests read like:
     *
     * <pre>
     *     WSResponse r = api("/jatos/api/v1/admin/status").get();
     * </pre>
     *
     * Use {@link ApiCall#noAuth()} / {@link ApiCall#token(String)} to change the auth header,
     * and {@link ApiCall#header(String, String)} / {@link ApiCall#queryParam(String, String)}
     * for anything else.
     */
    protected ApiCall api(String path) {
        ensureTestServerStarted();
        return new ApiCall(ws, "http://localhost:" + testServerPort + path, apiToken);
    }

    /**
     * Fluent one-shot HTTP call. Each verb method awaits the response and returns a
     * {@link WSResponse}. The default timeout is 10 seconds and can be changed via
     * {@link #timeout(Duration)}.
     */
    public static final class ApiCall {

        private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(15);

        private final WSClient ws;
        private final String url;
        private String token;
        private final Map<String, String> headers = new java.util.LinkedHashMap<>();
        private final List<Entry<String, String>> queryParams = new ArrayList<>();
        private Duration timeout = DEFAULT_TIMEOUT;

        private ApiCall(WSClient ws, String url, String token) {
            this.ws = ws;
            this.url = url;
            this.token = token;
        }

        public ApiCall token(String token) {
            this.token = token;
            return this;
        }

        public ApiCall noAuth() {
            this.token = null;
            return this;
        }

        public ApiCall header(String name, String value) {
            headers.put(name, value);
            return this;
        }

        public ApiCall queryParam(String name, String value) {
            queryParams.add(Map.entry(name, value));
            return this;
        }

        public ApiCall timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /** Escape hatch for cases the fluent API doesn't cover (multipart, streaming, ...). */
        public WSRequest raw() {
            WSRequest req = ws.url(url);
            if (token != null) req = req.addHeader("Authorization", "Bearer " + token);
            for (var h : headers.entrySet()) req = req.addHeader(h.getKey(), h.getValue());
            for (var qp : queryParams) req = req.addQueryParameter(qp.getKey(), qp.getValue());
            req = req.setRequestTimeout(timeout);
            return req;
        }

        public WSResponse get()                     { return await(raw().get()); }
        public WSResponse head()                    { return await(raw().head()); }
        public WSResponse delete()                  { return await(raw().delete()); }
        public WSResponse post(JsonNode body)       { return await(raw().post(body)); }
        public WSResponse post(String body)         { return await(raw().post(body)); }
        public WSResponse put(JsonNode body)        { return await(raw().put(body)); }
        public WSResponse put(String body)          { return await(raw().put(body)); }
        public WSResponse patch(JsonNode body)      { return await(raw().patch(body)); }
        public WSResponse patch(String body)        { return await(raw().patch(body)); }

        private WSResponse await(java.util.concurrent.CompletionStage<? extends WSResponse> stage) {
            try {
                return stage.toCompletableFuture().get(timeout.toMillis(), MILLISECONDS);
            } catch (TimeoutException e) {
                throw new AssertionError("HTTP call to " + url + " timed out after " + timeout, e);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                if (cause instanceof RuntimeException) throw (RuntimeException) cause;
                throw new RuntimeException(cause);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
    }

    public Long importExampleStudy() {
        TemporaryFileCreator temporaryFileCreator = application.injector().instanceOf(TemporaryFileCreator.class);
        Materializer materializer = application.injector().instanceOf(Materializer.class);
        Path studyPath = Paths.get(Common.getBasepath(), TEST_RESOURCES_POTATO_COMPASS_JZIP);
        Source<ByteString, CompletionStage<IOResult>> source = FileIO.fromPath(studyPath);
        FilePart<Source<ByteString, ?>> part = new FilePart<>("study", "filename", "text/plain", source);
        RequestBuilder request = new RequestBuilder()
                .method(POST)
                .header("Authorization", "Bearer " + apiToken)
                .bodyRaw(Collections.singletonList(part), temporaryFileCreator, materializer)
                .uri("/jatos/api/v1/study?keepProperties=false&keepAssets=false&keepCurrentAssetsName=false&renameAssets=false");

        Result result = route(application, request);
        JsonNode content = Json.parse(contentAsString(result));
        Long studyId = content.get("data").get("id").asLong();
        return studyId;
    }

    public User getAdmin() {
        UserDao userDao = application.injector().instanceOf(UserDao.class);
        return userDao.findByUsernameWithStudies(UserService.ADMIN_USERNAME);
    }

    public User createUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setName("Foo Bar");
        UserService userService = application.injector().instanceOf(UserService.class);
        return userService.createAndPersistUser(user, "fooPassword", false, User.AuthMethod.DB);
    }

    public String createApiToken(User user) {
        ApiTokenService apiTokenService = application.injector().instanceOf(ApiTokenService.class);
        return apiTokenService.create(user, "test-token", 0).getRight();
    }

    public Study getStudy(Long id) {
        StudyDao studyDao = application.injector().instanceOf(StudyDao.class);
        return studyDao.findByIdWithComponentsAndBatches(id);
    }

    public Study importAndGetExampleStudy() {
        Long studyId = importExampleStudy();
        return getStudy(studyId);
    }

    public void dropDatabase() {
        jpaApi.withTransaction(em -> {
            em.createNativeQuery("DROP ALL OBJECTS").executeUpdate();
            return null;
        });
    }

    public void removeUser(String username) {
        UserService userService = application.injector().instanceOf(UserService.class);
        try {
            userService.removeUser(username);
        } catch (NotFoundException e) {
            // We don't care
        }
    }

    public void removeAllStudyAssets() throws IOException {
        FileUtils.deleteDirectory(new File(Common.getStudyAssetsRootPath()));
    }

    public void removeAllStudyLogs() throws IOException {
        FileUtils.deleteDirectory(new File(Common.getStudyLogsPath()));
    }

    public void removeAllResultUploads() throws IOException {
        FileUtils.deleteDirectory(new File(Common.getResultUploadsPath()));
    }

    public void removeAllLogs() throws IOException {
        FileUtils.deleteDirectory(new File(Common.getLogsPath()));
    }

}
