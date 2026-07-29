package controllers.gui.api;

import org.apache.pekko.stream.Materializer;
import com.fasterxml.jackson.databind.JsonNode;
import general.common.Common;
import models.common.User;
import org.junit.Test;
import play.libs.Json;
import play.libs.ws.WSResponse;
import play.mvc.Http;
import play.mvc.Result;
import testutils.JatosTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static play.test.Helpers.*;

/**
 * Integration tests for {@link controllers.gui.api.AdminApi}.
 */
public class AdminApiTest extends JatosTest {

    // ---------- /jatos/api/v1/admin/status ----------

    @Test
    public void status_withAdminApiToken_returnsAdminStatusJson() {
        Http.RequestBuilder request = new Http.RequestBuilder()
                .method(GET)
                .header("Authorization", "Bearer " + apiToken)
                .uri("/jatos/api/v1/admin/status");

        Result result = route(application, request);

        assertThat(result.status()).isEqualTo(OK);
        JsonNode body = Json.parse(contentAsString(result));
        JsonNode data = body.get("data");
        assertThat(data).isNotNull();
        assertThat(data.get("studyCount").asInt()).isGreaterThanOrEqualTo(0);
        assertThat(data.get("studyResultCount").asInt()).isGreaterThanOrEqualTo(0);
        assertThat(data.get("workerCount").asInt()).isGreaterThanOrEqualTo(0);
        assertThat(data.get("userCount").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(data.get("serverTime").asLong()).isGreaterThan(0L);
        assertThat(data.get("latestUsers").isArray()).isTrue();
        assertThat(data.get("latestStudyRuns").isArray()).isTrue();
    }

    @Test
    public void status_withoutAuth_isUnauthorized() {
        WSResponse resp = api("/jatos/api/v1/admin/status").noAuth().get();

        assertThat(resp.getStatus()).isEqualTo(UNAUTHORIZED);
        assertThat(resp.asJson().get("error").get("message").asText()).contains("Failed authentication");
        assertThat(resp.asJson().get("error").get("code").asText()).isEqualTo("AUTH_ERROR");
    }

    @Test
    public void status_withNonAdminUserToken_isForbidden() {
        User user = createUser("regular-user");
        String userToken = createApiToken(user);

        WSResponse resp = api("/jatos/api/v1/admin/status").token(userToken).get();

        assertThat(resp.getStatus()).isEqualTo(FORBIDDEN);
        assertThat(resp.asJson().get("error").get("message").asText()).contains("Invalid api token");
        assertThat(resp.asJson().get("error").get("code").asText()).isEqualTo("INVALID_API_TOKEN");
    }

    // ---------- /jatos/api/v1/admin/logs/list ----------

    @Test
    public void listLogs_withAdminApiToken_returnsLogsDirectoryStructure() throws Exception {
        // Ensure the logs directory exists and has at least one file
        Path logsDir = Paths.get(Common.getLogsPath());
        Files.createDirectories(logsDir);
        Path logFile = logsDir.resolve("application.log");
        Files.writeString(logFile, "line one\nline two\n");

        WSResponse resp = api("/jatos/api/v1/admin/logs/list").get();

        assertThat(resp.getStatus()).isEqualTo(OK);
        JsonNode data = resp.asJson().get("data");
        assertThat(data).isNotNull();
        assertThat(data.toString()).contains("application.log");
    }

    @Test
    public void listLogs_withoutAuth_isUnauthorized() {
        WSResponse resp = api("/jatos/api/v1/admin/logs/list").noAuth().get();

        assertThat(resp.getStatus()).isEqualTo(UNAUTHORIZED);
        assertThat(resp.asJson().get("error").get("message").asText()).contains("Failed authentication");
        assertThat(resp.asJson().get("error").get("code").asText()).isEqualTo("AUTH_ERROR");
    }

    // ---------- /jatos/api/v1/admin/logs/:filename ----------

    @Test
    public void logs_downloadMode_returnsFileContentWithContentDispositionHeader() throws Exception {
        // I couldn't make this test run with full test server integration via api().get(). Somehow the file was always
        // missing, although it is present if I run it with a dev/prod environment.

        Path logsDir = Paths.get(Common.getLogsPath());
        Files.createDirectories(logsDir);
        Path logFile = logsDir.resolve("application-test.log");
        Files.writeString(logFile, "hello logs");

        Http.RequestBuilder request = new Http.RequestBuilder()
                .method(GET)
                .header("Authorization", "Bearer " + apiToken)
                .uri("/jatos/api/v1/admin/logs/application-test.log");

        Result result = route(application, request);
        Materializer materializer = application.injector().instanceOf(Materializer.class);

        assertThat(result.status()).isEqualTo(OK);
        assertThat(result.header(CONTENT_DISPOSITION)).isPresent();
        assertThat(result.header(CONTENT_DISPOSITION).orElseThrow()).contains("jatos_logs_application-test.log");
        assertThat(contentAsString(result, materializer)).isEqualTo("hello logs");
    }

    @Test
    public void logs_reverseMode_returnsChunkedPlainTextInReverseOrder() throws Exception {
        // I couldn't make this test run with full test server integration via api().get(). Somehow the file was always
        // missing, although it is present if I run it with a dev/prod environment.

        Path logsDir = Paths.get(Common.getLogsPath());
        Files.createDirectories(logsDir);
        Path logFile = logsDir.resolve("application.log");
        Files.writeString(logFile, "first\nsecond\nthird\n");

        Http.RequestBuilder request = new Http.RequestBuilder()
                .method(GET)
                .header("Authorization", "Bearer " + apiToken)
                .uri("/jatos/api/v1/admin/logs/application.log?reverse=true&limit=10");

        Result result = route(application, request);
        Materializer materializer = application.injector().instanceOf(Materializer.class);

        assertThat(result.status()).isEqualTo(OK);
        assertThat(result.contentType()).isPresent();
        assertThat(result.contentType().orElseThrow()).contains("text/plain");

        String body = contentAsString(result, materializer);
        assertThat(body).contains("first", "second", "third");
        // reverse order: "third" should appear before "first"
        assertThat(body.indexOf("third")).isLessThan(body.indexOf("first"));
    }

    @Test
    public void logs_withUnknownFilename_returnsNotFound() {
        WSResponse resp = api("/jatos/api/v1/admin/logs/does-not-exist.log").get();

        assertThat(resp.getStatus()).isEqualTo(NOT_FOUND);
    }

    @Test
    public void logs_withInsecureFilename_returnsNotFound() {
        // A traversal attempt must be rejected
        WSResponse resp = api("/jatos/api/v1/admin/logs/" + java.net.URLEncoder.encode("../secret.txt", java.nio.charset.StandardCharsets.UTF_8)).get();

        assertThat(resp.getStatus()).isEqualTo(NOT_FOUND);
    }

    @Test
    public void logs_withoutAuth_isUnauthorized() {
        WSResponse resp = api("/jatos/api/v1/admin/logs/does-not-exist.log").noAuth().get();

        assertThat(resp.getStatus()).isEqualTo(UNAUTHORIZED);
    }

    @Test
    public void logs_withNonAdminUserToken_isForbidden() throws Exception {
        Path logsDir = Paths.get(Common.getLogsPath());
        Files.createDirectories(logsDir);
        Path logFile = logsDir.resolve("application-test.log");
        Files.writeString(logFile, "hello logs");

        User user = createUser("regular-user-2");
        String userToken = createApiToken(user);

        WSResponse resp = api("/jatos/api/v1/admin/logs/application-test.log").token(userToken).get();

        assertThat(resp.getStatus()).isEqualTo(FORBIDDEN);

    }

}