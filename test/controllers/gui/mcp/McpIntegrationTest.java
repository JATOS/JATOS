package controllers.gui.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import daos.common.UserDao;
import general.common.Common;
import models.common.User;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import play.Application;
import play.inject.guice.GuiceApplicationBuilder;
import play.libs.Json;
import play.test.Helpers;
import play.test.TestServer;
import services.gui.ApiTokenService;
import services.gui.UserService;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static services.gui.mcp.McpProtocolUtils.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real HTTP MCP -> loopback API -> database, including Play routes and filters.
 */
public class McpIntegrationTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    /**
     * Exercises the same workflow with a configured URL prefix.
     */
    @Test
    public void rootDeployment() throws Exception {
        checkDeployment("/");
    }

    @Test
    public void prefixedDeployment() throws Exception {
        checkDeployment("/mcp-test/");
    }

    /**
     * Starts an isolated JATOS instance and verifies discovery, all tools, access isolation, limits and token
     * revocation.
     */
    private void checkDeployment(String prefix) throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {port = socket.getLocalPort();}
        String root = temp.getRoot().getAbsolutePath();
        Application app = new GuiceApplicationBuilder()
                .configure("db.default.url", "jdbc:h2:mem:mcp-" + UUID.randomUUID()
                        + ";MODE=MYSQL;DATABASE_TO_UPPER=FALSE;IGNORECASE=TRUE;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1")
                .configure("play.http.context", prefix)
                .configure("jatos.urlBasePath", prefix)
                .configure("jatos.mcp.enabled", true)
                .configure("play.server.http.port", port)
                .configure("jatos.mcp.maxResponseSize", "8KiB")
                .configure("jatos.user.admin.password", "Mcp-Test-Only-42!")
                .configure("jatos.studyAssets.rootPath", root + "/assets")
                .configure("jatos.studyLogs.path", root + "/study-logs")
                .configure("jatos.resultUploads.path", root + "/uploads")
                .configure("jatos.logs.path", root + "/logs")
                .configure("jatos.tmpPath", root + "/tmp")
                .build();
        TestServer server = Helpers.testServer(port, app);
        Helpers.start(server);
        try (HttpClient client = HttpClient.newHttpClient()) {
            assertThat(Path.of(Common.getStudyAssetsRootPath())).isEqualTo(Path.of(root, "assets"));
            User admin = app.injector().instanceOf(UserDao.class).findByUsernameWithStudies(UserService.ADMIN_USERNAME);
            var tokenPair = app.injector().instanceOf(ApiTokenService.class).create(admin, "mcp-test", 0);
            String token = tokenPair.getRight();
            String base = "http://127.0.0.1:" + port + prefix + "jatos";
            HttpResponse<String> initialized = post(client, base + "/mcp", token, """
                    {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25",
                    "capabilities":{},"clientInfo":{"name":"integration-test","version":"1"}}}
                    """);
            assertThat(initialized.statusCode()).isEqualTo(200);
            assertThat(Json.parse(initialized.body()).path("result").path("serverInfo").path("name").asText()).isEqualTo("JATOS");
            assertThat(initialized.headers().firstValue("MCP-Session-Id")).isEmpty();
            assertThat(post(client, base + "/mcp", token, "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}").statusCode()).isEqualTo(202);
            JsonNode tools = Json.parse(post(client, base + "/mcp", token,
                    "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}").body()).path("result").path("tools");
            assertThat(tools.size()).isEqualTo(18);

            HttpResponse<String> created = post(client, base + "/api/v1/studies", token,
                    "{\"title\":\"MCP study\",\"dirName\":\"mcp_study\",\"studyInput\":{}}");
            assertThat(created.statusCode()).isEqualTo(201);
            long studyId = Json.parse(created.body()).path("data").path("id").asLong();
            JsonNode component = Json.parse(post(client, base + "/api/v1/studies/" + studyId + "/components", token,
                    "{\"title\":\"MCP component\",\"htmlFilePath\":\"index.html\"}").body()).path("data");
            long componentId = component.path("id").asLong();
            assertThat(componentId).isPositive();
            JsonNode batches = Json.parse(client.send(HttpRequest.newBuilder(URI.create(base + "/api/v1/studies/" + studyId + "/batches"))
                            .header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.ofString())
                    .body()).path("data");
            long batchId = batches.get(0).path("id").asLong();
            assertThat(batchId).isPositive();
            Path assetDir = Path.of(root, "assets", "mcp_study");
            Files.createDirectories(assetDir.resolve("js"));
            String assetText = "const message = '你好';\n";
            Files.writeString(assetDir.resolve("js/my file.js"), assetText);

            for (String version : new String[]{VERSION_2025_03_26, VERSION_2025_06_18, VERSION_2025_11_25, VERSION_2026_07_28}) {
                if (version.equals(VERSION_2026_07_28)) {
                    JsonNode discovery = Json.parse(postVersion(client, base + "/mcp", token,
                            "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"server/discover\"}", version).body()).path("result");
                    assertThat(discovery.path("supportedVersions").size()).isEqualTo(4);
                    assertThat(discovery.path("resultType").asText()).isEqualTo("complete");
                } else {
                    var init = Json.newObject().put("jsonrpc", "2.0").put("id", 3).put("method", "initialize");
                    var params = init.putObject("params").put("protocolVersion", version);
                    params.putObject("capabilities");
                    params.putObject("clientInfo").put("name", "integration-test").put("version", "1");
                    JsonNode handshake = Json.parse(postVersion(client, base + "/mcp", token,
                            Json.stringify(init), version).body()).path("result");
                    assertThat(handshake.path("protocolVersion").asText()).isEqualTo(version);
                }
                for (String name : new String[]{"list_batches", "get_batch", "get_batch_session", "list_components", "get_component",
                        "list_studies", "get_study", "get_study_log", "list_study_assets", "read_study_asset"}) {
                    var args = Json.newObject();
                    if (name.equals("get_component")) args.put("component_id", componentId);
                    else if (name.equals("get_batch") || name.equals("get_batch_session"))
                        args.put("batch_id", batchId);
                    else if (!name.equals("list_studies")) args.put("study_id", studyId);
                    if (name.equals("read_study_asset")) args.put("filepath", "js/my file.js");
                    if (name.equals("get_study_log")) args.put("entry_limit", 5);
                    var message = Json.newObject().put("jsonrpc", "2.0").put("id", 4).put("method", "tools/call");
                    message.putObject("params").put("name", name).set("arguments", args);
                    HttpResponse<String> response = postVersion(client, base + "/mcp", token, Json.stringify(message), version);
                    assertThat(response.statusCode()).isEqualTo(200);
                    JsonNode result = Json.parse(response.body()).path("result");
                    assertThat(result.path("isError").asBoolean()).as(version + " " + name + ": " + result).isFalse();
                    assertThat(result.has("structuredContent")).isEqualTo(!version.equals(VERSION_2025_03_26));
                    assertThat(result.has("resultType")).isEqualTo(version.equals(VERSION_2026_07_28));
                    JsonNode content = Json.parse(result.path("content").get(0).path("text").asText()).path("data");
                    assertThat(content.isMissingNode()).isFalse();
                    if (name.equals("get_study")) assertThat(content.path("id").asLong()).isEqualTo(studyId);
                    if (name.equals("get_component")) assertThat(content.path("id").asLong()).isEqualTo(componentId);
                    if (name.equals("get_batch")) assertThat(content.path("id").asLong()).isEqualTo(batchId);
                    if (name.equals("get_study_log")) assertThat(content.isArray()).isTrue();
                    if (name.equals("read_study_asset")) assertThat(content.asText()).isEqualTo(assetText);
                }
            }

            checkWriteTools(client, base, token, studyId);

            // Another valid user may discover tools, but cannot access this user's study.
            User other = new User();
            other.setUsername("mcp-other");
            other.setName("Other user");
            other = app.injector().instanceOf(UserService.class)
                    .createAndPersistUser(other, "Mcp-Test-Only-43!", false, User.AuthMethod.DB);
            String otherToken = app.injector().instanceOf(ApiTokenService.class).create(other, "other", 0).getRight();
            assertThat(tool(client, base, otherToken, "list_studies", Json.newObject())
                    .path("structuredContent").path("data").size()).isZero();
            assertThat(tool(client, base, otherToken, "get_study", Json.newObject().put("study_id", studyId))
                    .path("isError").asBoolean()).isTrue();
            var forbiddenUpdate = Json.newObject().put("study_id", studyId);
            forbiddenUpdate.putObject("properties").put("title", "Unauthorized change");
            assertThat(tool(client, base, otherToken, "update_study", forbiddenUpdate).path("isError").asBoolean()).isTrue();
            assertThat(tool(client, base, otherToken, "export_study", Json.newObject().put("study_id", studyId))
                    .path("isError").asBoolean()).isTrue();
            assertThat(post(client, base + "/mcp", "invalid", "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/list\"}").statusCode()).isEqualTo(401);
            // Origin validation happens before the API call, including when a valid token is present.
            HttpResponse<String> origin = client.send(HttpRequest.newBuilder(URI.create(base + "/mcp"))
                    .header("Authorization", "Bearer " + token).header("Origin", "https://untrusted.example")
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{}"))
                    .build(), HttpResponse.BodyHandlers.ofString());
            assertThat(origin.statusCode()).isEqualTo(403);
            HttpResponse<String> get = client.send(HttpRequest.newBuilder(URI.create(base + "/mcp"))
                    .header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(get.statusCode()).isEqualTo(405);
            HttpResponse<String> anonymous = client.send(HttpRequest.newBuilder(URI.create(base + "/mcp"))
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{}"))
                    .build(), HttpResponse.BodyHandlers.ofString());
            assertThat(anonymous.statusCode()).isEqualTo(401);
            assertThat(anonymous.headers().firstValue("WWW-Authenticate")).contains("Bearer");
            assertThat(post(client, base + "/mcp", token, " ".repeat(65537)).statusCode()).isEqualTo(413);

            var largeProps = Json.newObject();
            largeProps.putObject("studyInput").put("large", "x".repeat(10000));
            HttpResponse<String> updated = client.send(HttpRequest.newBuilder(URI.create(base + "/api/v1/studies/" + studyId + "/properties"))
                    .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(Json.stringify(largeProps))).build(), HttpResponse.BodyHandlers.ofString());
            assertThat(updated.statusCode()).isEqualTo(200);
            JsonNode oversized = tool(client, base, token, "get_study", Json.newObject().put("study_id", studyId));
            assertThat(oversized.path("isError").asBoolean()).isTrue();
            assertThat(oversized.path("content").get(0).path("text").asText())
                    .isEqualTo("JATOS API response exceeds the configured size limit");

            HttpResponse<String> revoked = client.send(HttpRequest.newBuilder(URI.create(base + "/api/v1/tokens/" + tokenPair.getLeft().getId()))
                    .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString("{\"active\":false}")).build(), HttpResponse.BodyHandlers.ofString());
            assertThat(revoked.statusCode()).isEqualTo(200);
            assertThat(post(client, base + "/mcp", token, "{\"jsonrpc\":\"2.0\",\"id\":20,\"method\":\"tools/list\"}").statusCode()).isEqualTo(401);
        } finally {
            Helpers.stop(server);
        }
    }

    /**
     * Invokes an MCP tool over HTTP, checks the response envelope and returns its result.
     */
    private JsonNode tool(HttpClient client, String base, String token, String name, JsonNode args) throws Exception {
        var message = Json.newObject().put("jsonrpc", "2.0").put("id", 10).put("method", "tools/call");
        message.putObject("params").put("name", name).set("arguments", args);
        HttpResponse<String> response = post(client, base + "/mcp", token, Json.stringify(message));
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = Json.parse(response.body());
        assertThat(body.has("result")).as(response.body()).isTrue();
        return body.get("result");
    }

    /**
     * Exercises create/update properties and authenticated archive round trips through MCP.
     */
    private void checkWriteTools(HttpClient client, String base, String token, long studyId) throws Exception {
        JsonNode created = toolData(client, base, token, "create_study",
                Json.parse("{\"properties\":{\"title\":\"Created through MCP\"}}"));
        long targetId = created.path("id").asLong();
        assertThat(targetId).isPositive().isNotEqualTo(studyId);
        var updateStudy = Json.newObject().put("study_id", targetId);
        updateStudy.putObject("properties").put("title", "Updated through MCP").putNull("comments");
        assertThat(toolData(client, base, token, "update_study", updateStudy).path("title").asText())
                .isEqualTo("Updated through MCP");

        var componentArgs = Json.newObject().put("study_id", targetId);
        componentArgs.putObject("properties").put("title", "New component").put("htmlFilePath", "index.html");
        JsonNode component = toolData(client, base, token, "create_component", componentArgs);
        var componentUpdate = Json.newObject().put("component_id", component.path("id").asLong());
        componentUpdate.putObject("properties").put("active", false);
        assertThat(toolData(client, base, token, "update_component", componentUpdate).path("active").asBoolean()).isFalse();
        var batchArgs = Json.newObject().put("study_id", targetId);
        batchArgs.putObject("properties").put("title", "New batch");
        JsonNode batch = toolData(client, base, token, "create_batch", batchArgs);
        var batchUpdate = Json.newObject().put("batch_id", batch.path("id").asLong());
        batchUpdate.putObject("properties").put("maxTotalWorkers", 5);
        assertThat(toolData(client, base, token, "update_batch", batchUpdate).path("maxTotalWorkers").asInt()).isEqualTo(5);
        JsonNode exported = toolData(client, base, token, "export_study", Json.newObject().put("study_id", studyId));
        assertThat(exported.path("url").asText()).isEqualTo(base + "/api/v1/studies/" + studyId);
        assertThat(exported.toString()).doesNotContain(token);
        URI download = URI.create(exported.path("url").asText());
        assertThat(client.send(HttpRequest.newBuilder(download).GET().build(), HttpResponse.BodyHandlers.ofString())
                .statusCode()).isEqualTo(401);
        HttpResponse<byte[]> archive = client.send(HttpRequest.newBuilder(download).header("Authorization", "Bearer " + token)
                .GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(archive.statusCode()).isEqualTo(200);
        var change = Json.newObject().put("study_id", studyId);
        change.putObject("properties").put("title", "Changed after export");
        toolData(client, base, token, "update_study", change);
        var imported = Json.newObject().put("archive_base64", java.util.Base64.getEncoder().encodeToString(archive.body()))
                .put("keep_properties", true).put("keep_assets", true);
        JsonNode kept = toolData(client, base, token, "import_study", imported);
        assertThat(kept.path("id").asLong()).isEqualTo(studyId);
        assertThat(kept.path("title").asText()).isEqualTo("Changed after export");
        imported.put("keep_properties", false);
        assertThat(toolData(client, base, token, "import_study", imported).path("title").asText()).isEqualTo("MCP study");
    }

    /**
     * Returns the data from a successful tool call, keeping failures informative in integration assertions.
     */
    private JsonNode toolData(HttpClient client, String base, String token, String name, JsonNode args) throws Exception {
        JsonNode result = tool(client, base, token, name, args);
        assertThat(result.path("isError").asBoolean()).as(name + ": " + result).isFalse();
        return result.path("structuredContent").path("data");
    }

    /**
     * Sends an authenticated JSON POST with MCP-compatible Accept headers and a bounded timeout.
     */
    private HttpResponse<String> post(HttpClient client, String url, String token, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .header("MCP-Protocol-Version", VERSION_2025_11_25)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Sends a version-specific MCP request, adding modern metadata and mirrored headers when needed.
     */
    private HttpResponse<String> postVersion(HttpClient client, String url, String token, String body, String version) throws Exception {
        var message = (com.fasterxml.jackson.databind.node.ObjectNode) Json.parse(body);
        var request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream").header("MCP-Protocol-Version", version);
        if (version.equals(VERSION_2026_07_28)) {
            var params = message.withObject("params");
            params.putObject("_meta").put("io.modelcontextprotocol/protocolVersion", version)
                    .putObject("io.modelcontextprotocol/clientCapabilities");
            request.header("Mcp-Method", message.path("method").asText());
            if (params.has("name")) request.header("Mcp-Name", params.path("name").asText());
        }
        return client.send(request.POST(HttpRequest.BodyPublishers.ofString(Json.stringify(message))).build(),
                HttpResponse.BodyHandlers.ofString());
    }

}
