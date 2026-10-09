package controllers.gui.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import general.common.Common;
import org.junit.After;
import org.mockito.MockedStatic;
import org.junit.Before;
import org.junit.Test;
import play.libs.Json;
import play.mvc.Http;
import play.mvc.Result;
import services.gui.mcp.McpApiClient;

import java.util.concurrent.CompletableFuture;

import static services.gui.mcp.McpProtocol.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static play.test.Helpers.contentAsString;
import static play.test.Helpers.fakeRequest;

/**
 * Unit tests for MCP protocol handling, authentication gates and API result mapping.
 */
public class McpTest {
    private McpApiClient api;
    private Mcp controller;
    private MockedStatic<Common> commonMock;
    private static final String AUTH = "Bearer test-token";

    /**
     * Creates a controller backed by a mock API client that accepts the test token.
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Before
    public void setup() {
        api = mock(McpApiClient.class);
        when(api.get("/admin/token", AUTH)).thenReturn(CompletableFuture.completedFuture(
                new McpApiClient.Reply(200, Json.newObject())));
        commonMock = mockStatic(Common.class);
        commonMock.when(Common::isJatosMcpEnabled).thenReturn(true);
        commonMock.when(Common::getJatosMcpAllowedOrigins).thenReturn(java.util.List.of("https://jatos.example.org"));
        controller = new Mcp(api);
    }

    /**
     * Releases static configuration mocks after each test.
     */
    @After
    public void tearDown() {
        commonMock.close();
    }

    /**
     * Builds an authenticated JSON POST request with the MCP Accept header.
     */
    private Http.RequestBuilder request(String body) {
        return fakeRequest("POST", "/jatos/mcp").bodyText(body)
                .header("Content-Type", "application/json")
                .header("MCP-Protocol-Version", VERSION_2025_11_25)
                .header("Accept", "application/json, text/event-stream")
                .header("Authorization", AUTH);
    }

    /**
     * Dispatches a test message to the controller and waits for its HTTP result.
     */
    private Result call(String body) {
        return controller.post(request(body).build()).toCompletableFuture().join();
    }

    /**
     * Parses an HTTP result body for protocol assertions.
     */
    private JsonNode json(Result result) {
        return Json.parse(contentAsString(result));
    }

    /**
     * Checks version negotiation and that initialization advertises only the tools capability.
     */
    @Test
    public void initializeNegotiatesVersionAndAdvertisesOnlyTools() {
        JsonNode result = json(call("""
                {"jsonrpc":"2.0","id":"init","method":"initialize","params":{
                "protocolVersion":"future-version","capabilities":{},"clientInfo":{"name":"test","version":"1"}}}
                """));
        assertThat(result.path("id").asText()).isEqualTo("init");
        assertThat(result.path("result").path("protocolVersion").asText()).isEqualTo(VERSION_2025_11_25);
        assertThat(result.path("result").path("capabilities").size()).isEqualTo(1);
        assertThat(result.path("result").path("capabilities").has("tools")).isTrue();
    }

    /**
     * Checks discovery returns all five tools with read-only annotations and strict input schemas.
     */
    @Test
    public void listsFiveReadOnlyTools() {
        JsonNode tools = json(call("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}"))
                .path("result").path("tools");
        assertThat(tools.size()).isEqualTo(5);
        for (JsonNode tool : tools) {
            assertThat(tool.path("annotations").path("readOnlyHint").asBoolean()).isTrue();
            assertThat(tool.path("inputSchema").path("additionalProperties").asBoolean()).isFalse();
        }
    }

    /**
     * Checks token forwarding and matching structured and text representations of compact study summaries.
     */
    @Test
    public void toolCallsUseCallersTokenAndReturnCompactStructuredData() {
        when(api.get("/studies/properties", AUTH)).thenReturn(CompletableFuture.completedFuture(
                new McpApiClient.Reply(200, Json.parse("{\"data\":[{\"id\":7,\"uuid\":\"abc\",\"title\":\"Study\",\"jsonData\":\"large\"}]}"))));
        JsonNode result = json(call("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"list_studies\"}}"))
                .path("result");
        assertThat(result.path("isError").asBoolean()).isFalse();
        assertThat(result.path("structuredContent").path("data").get(0).size()).isEqualTo(3);
        assertThat(Json.parse(result.path("content").get(0).path("text").asText())).isEqualTo(result.path("structuredContent"));
        verify(api).get("/studies/properties", AUTH);
    }

    /**
     * Checks API access denials become MCP tool errors containing the API status and message.
     */
    @Test
    public void apiPermissionErrorsAreToolErrors() {
        when(api.get("/studies/7/properties", AUTH)).thenReturn(CompletableFuture.completedFuture(
                new McpApiClient.Reply(403, Json.parse("{\"error\":{\"message\":\"Access denied\"}}"))));
        JsonNode result = json(call("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"get_study\",\"arguments\":{\"study_id\":7}}}"))
                .path("result");
        assertThat(result.path("isError").asBoolean()).isTrue();
        assertThat(result.path("content").get(0).path("text").asText()).contains("403", "Access denied");
    }

    /**
     * Checks disabled access, missing credentials and unlisted origins are rejected before API access.
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Test
    public void rejectsDisabledMissingCredentialsAndUnlistedOriginsBeforeCallingApi() {
        commonMock.when(Common::isJatosMcpEnabled).thenReturn(false);
        assertThat(controller.post(request("{}").build()).toCompletableFuture().join().status()).isEqualTo(404);
        commonMock.when(Common::isJatosMcpEnabled).thenReturn(true);
        assertThat(controller.post(fakeRequest("POST", "/jatos/mcp").bodyText("{}").build()).toCompletableFuture().join().status()).isEqualTo(401);
        assertThat(controller.post(request("{}").header("Origin", "https://evil.example").build()).toCompletableFuture().join().status()).isEqualTo(403);
        verifyNoInteractions(api);
    }

    /**
     * Checks an invalid token prevents tool discovery.
     */
    @Test
    public void rejectsInvalidTokenBeforeDiscovery() {
        when(api.get("/admin/token", AUTH)).thenReturn(CompletableFuture.completedFuture(new McpApiClient.Reply(401, Json.newObject())));
        assertThat(call("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}").status()).isEqualTo(401);
    }

    /**
     * Checks unsupported protocol versions and unsupported HTTP operations are rejected.
     */
    @Test
    public void rejectsUnsupportedVersionsAndDoesNotOfferSseOrSessions() {
        assertThat(controller.post(request("{}").header("MCP-Protocol-Version", "unknown").build()).toCompletableFuture().join().status()).isEqualTo(400);
        assertThat(controller.unsupported(request("{}").build()).toCompletableFuture().join().status()).isEqualTo(405);
    }

    /**
     * Checks malformed messages and unknown methods fail, while notifications never invoke tool endpoints.
     */
    @Test
    public void validatesProtocolAndNeverExecutesToolsFromNotifications() {
        assertThat(json(call("not json")).path("error").path("code").asInt()).isEqualTo(-32700);
        assertThat(json(call("[]")).path("error").path("code").asInt()).isEqualTo(-32600);
        assertThat(json(call("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"unknown\"}")).path("error").path("code").asInt()).isEqualTo(-32601);
        assertThat(call("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}").status()).isEqualTo(202);
        assertThat(call("{\"jsonrpc\":\"2.0\",\"method\":\"tools/call\",\"params\":{\"name\":\"list_studies\"}}").status()).isEqualTo(202);
        verify(api, never()).get(eq("/studies/properties"), anyString());
    }

    /**
     * Checks path-like tool arguments are rejected after authentication without invoking a tool endpoint.
     */
    @Test
    public void rejectsUnsafeArgumentsWithoutCallingToolEndpoint() {
        JsonNode response = json(call("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"get_study\",\"arguments\":{\"study_id\":\"../admin/token\"}}}"));
        assertThat(response.path("error").path("code").asInt()).isEqualTo(-32602);
        verify(api).get("/admin/token", AUTH);
        verifyNoMoreInteractions(api);
    }

    /**
     * Builds a modern request with matching transport headers and per-request metadata.
     */
    private Http.RequestBuilder modern(String method, com.fasterxml.jackson.databind.node.ObjectNode params) {
        params.putObject("_meta").put("io.modelcontextprotocol/protocolVersion", VERSION_2026_07_28)
                .putObject("io.modelcontextprotocol/clientCapabilities");
        var message = Json.newObject().put("jsonrpc", "2.0").put("id", "modern").put("method", method);
        message.set("params", params);
        var request = request(Json.stringify(message)).header("MCP-Protocol-Version", VERSION_2026_07_28)
                .header("Mcp-Method", method);
        if (params.has("name")) request.header("Mcp-Name", params.get("name").asText());
        return request;
    }

    /**
     * Distinguishes modern protocol errors from actionable tool input failures without calling the API tool.
     */
    @Test
    public void modernToolValidationUsesDistinctErrorCategories() {
        var unknown = Json.newObject().put("name", "unknown_tool");
        var malformed = Json.newObject().put("name", "get_study");
        malformed.putArray("arguments");
        for (var params : java.util.List.of(unknown, malformed)) {
            Result response = controller.post(modern("tools/call", params).build()).toCompletableFuture().join();
            assertThat(response.status()).isEqualTo(400);
            assertThat(json(response).path("error").path("code").asInt()).isEqualTo(-32602);
        }

        var invalidInput = Json.newObject().put("name", "get_study");
        invalidInput.putObject("arguments").put("study_id", 0);
        Result response = controller.post(modern("tools/call", invalidInput).build()).toCompletableFuture().join();
        assertThat(response.status()).isEqualTo(200);
        JsonNode body = json(response);
        assertThat(body.has("error")).isFalse();
        assertThat(body.path("result").path("isError").asBoolean()).isTrue();
        assertThat(body.path("result").path("resultType").asText()).isEqualTo("complete");
        assertThat(body.path("result").path("content").get(0).path("text").asText()).contains("study_id");
        verify(api, times(3)).get("/admin/token", AUTH);
        verifyNoMoreInteractions(api);
    }

    /**
     * Checks modern method parameter errors use HTTP 400.
     */
    @Test
    public void modernToolListingRejectsCursorWithBadRequest() {
        Result response = controller.post(modern("tools/list", Json.newObject().put("cursor", "next"))
                .build()).toCompletableFuture().join();
        assertThat(response.status()).isEqualTo(400);
        assertThat(json(response).path("error").path("code").asInt()).isEqualTo(-32602);
    }

    /**
     * Checks modern discovery advertises all four versions without requiring initialization.
     */
    @Test
    public void modernDiscoveryAndDirectToolCalls() {
        JsonNode discovery = json(controller.post(modern("server/discover", Json.newObject()).build())
                .toCompletableFuture().join()).path("result");
        assertThat(discovery.path("supportedVersions")).isEqualTo(Json.parse(
                "[\"2026-07-28\",\"2025-11-25\",\"2025-06-18\",\"2025-03-26\"]"));
        assertThat(discovery.path("resultType").asText()).isEqualTo("complete");
        assertThat(discovery.path("_meta").path("io.modelcontextprotocol/serverInfo").path("name").asText()).isEqualTo("JATOS");
        assertThat(discovery.path("capabilities").path("tools").isEmpty()).isTrue();
        when(api.get("/studies/properties", AUTH)).thenReturn(CompletableFuture.completedFuture(
                new McpApiClient.Reply(200, Json.parse("{\"data\":[]}"))));
        JsonNode result = json(controller.post(modern("tools/call", Json.newObject().put("name", "list_studies"))
                .build()).toCompletableFuture().join()).path("result");
        assertThat(result.path("resultType").asText()).isEqualTo("complete");
        assertThat(result.has("structuredContent")).isTrue();
    }

    /**
     * Ensures legacy initialization stays in the legacy era and all legacy versions remain usable.
     */
    @Test
    public void legacyVersionsKeepTheirResponseShapes() {
        when(api.get("/studies/properties", AUTH)).thenReturn(CompletableFuture.completedFuture(
                new McpApiClient.Reply(200, Json.parse("{\"data\":[]}"))));
        for (String version : java.util.List.of(VERSION_2025_03_26, VERSION_2025_06_18, VERSION_2025_11_25)) {
            var init = Json.newObject().put("jsonrpc", "2.0").put("id", 1).put("method", "initialize");
            var params = init.putObject("params").put("protocolVersion", version);
            params.putObject("capabilities");
            params.putObject("clientInfo").put("name", "legacy").put("version", "1");
            JsonNode initialized = json(controller.post(request(Json.stringify(init))
                    .header("MCP-Protocol-Version", version).build()).toCompletableFuture().join()).path("result");
            assertThat(initialized.path("protocolVersion").asText()).isEqualTo(version);
            assertThat(initialized.has("resultType")).isFalse();
            String body = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"list_studies\"}}";
            JsonNode result = json(controller.post(request(body).header("MCP-Protocol-Version", version).build())
                    .toCompletableFuture().join()).path("result");
            assertThat(result.has("resultType")).isFalse();
            assertThat(result.has("structuredContent")).isEqualTo(!version.equals(VERSION_2025_03_26));
            assertThat(result.path("content").size()).isEqualTo(1);
        }
    }

    /**
     * Checks modern transport validation and error status without invoking the tool's API endpoint.
     */
    @Test
    public void modernHeadersMustMatchBody() {
        for (String header : java.util.List.of("MCP-Protocol-Version", "Mcp-Method", "Mcp-Name")) {
            var request = modern("tools/call", Json.newObject().put("name", "list_studies"));
            Result result = controller.post(request.header(header, "wrong").build()).toCompletableFuture().join();
            assertThat(result.status()).isEqualTo(400);
            assertThat(json(result).path("error").path("code").asInt()).isEqualTo(-32020);
        }
        verify(api, never()).get(eq("/studies/properties"), anyString());
        Result unknown = controller.post(modern("unknown", Json.newObject()).build()).toCompletableFuture().join();
        assertThat(unknown.status()).isEqualTo(404);
        assertThat(json(unknown).path("error").path("code").asInt()).isEqualTo(-32601);
    }

    /**
     * Verifies modern ping and tool failures carry completion metadata, including encoded tool names.
     */
    @Test
    public void modernResultsIncludeCompletionMetadata() {
        JsonNode ping = json(controller.post(modern("ping", Json.newObject()).build()).toCompletableFuture().join());
        assertThat(ping.path("result").path("resultType").asText()).isEqualTo("complete");
        when(api.get("/studies/properties", AUTH)).thenReturn(CompletableFuture.failedFuture(new RuntimeException("backend failed")));
        String encoded = "=?base64?" + java.util.Base64.getEncoder().encodeToString(
                "list_studies".getBytes(java.nio.charset.StandardCharsets.UTF_8)) + "?=";
        JsonNode result = json(controller.post(modern("tools/call", Json.newObject().put("name", "list_studies"))
                .header("Mcp-Name", encoded).build()).toCompletableFuture().join()).path("result");
        assertThat(result.path("isError").asBoolean()).isTrue();
        assertThat(result.path("resultType").asText()).isEqualTo("complete");
    }

}
