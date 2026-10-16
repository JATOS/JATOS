package controllers.gui.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import general.common.Common;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import play.libs.Json;
import play.mvc.Http;
import play.mvc.Result;
import services.gui.mcp.McpApiClient;
import services.gui.mcp.ResolvedCall;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static play.test.Helpers.contentAsString;
import static play.test.Helpers.fakeRequest;
import static services.gui.mcp.McpProtocolUtils.*;

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
        controller = new Mcp(api, new json.common.DefaultJson());
    }

    /**
     * Exercises the transport action as Play would before invoking the POST controller.
     */
    private java.util.concurrent.CompletionStage<Result> post(Http.Request request) {
        return throughTransport(request, controller::post);
    }

    /**
     * Exercises authentication before the controller rejects an unsupported HTTP method.
     */
    private java.util.concurrent.CompletionStage<Result> unsupported(Http.Request request) {
        return throughTransport(request, req -> controller.unsupported());
    }

    /**
     * Composes the real transport action with a controller endpoint without starting Play.
     */
    private java.util.concurrent.CompletionStage<Result> throughTransport(Http.Request request,
                                                                          java.util.function.Function<Http.Request, java.util.concurrent.CompletionStage<Result>> endpoint) {
        McpTransportAction action = new McpTransportAction(api);
        action.delegate = new play.mvc.Action.Simple() {
            @Override
            public java.util.concurrent.CompletionStage<Result> call(Http.Request request) {
                return endpoint.apply(request);
            }
        };
        return action.call(request).thenApply(result -> {
            assertThat(result.header("Cache-Control")).contains("no-store");
            return result;
        });
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
        return post(request(body).build()).toCompletableFuture().join();
    }

    /**
     * Parses an HTTP result body for protocol assertions.
     */
    private JsonNode json(Result result) {
        return Json.parse(contentAsString(result));
    }

    /**
     * Checks version negotiation and that initialization advertises tools and resources.
     */
    @Test
    public void initializeNegotiatesVersionAndAdvertisesToolsAndResources() {
        JsonNode result = json(call("""
                {"jsonrpc":"2.0","id":"init","method":"initialize","params":{
                "protocolVersion":"future-version","capabilities":{},"clientInfo":{"name":"test","version":"1"}}}
                """));
        assertThat(result.path("id").asText()).isEqualTo("init");
        assertThat(result.path("result").path("protocolVersion").asText()).isEqualTo(VERSION_2025_11_25);
        assertThat(result.path("result").path("capabilities").size()).isEqualTo(2);
        assertThat(result.path("result").path("capabilities").has("tools")).isTrue();
        assertThat(result.path("result").path("capabilities").has("resources")).isTrue();
    }

    /**
     * Checks resource discovery and deployment-specific instructions across all supported revisions.
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Test
    public void readsApiUsageResourceAcrossVersions() {
        commonMock.when(Common::getJatosUrlBasePath).thenReturn("/prefix/");
        commonMock.when(Common::getStudyArchiveMaxSize).thenReturn(2147483648L);
        commonMock.when(Common::getStudyAssetsMaxFileSize).thenReturn(104857600L);
        for (String version : VERSIONS) {
            for (String method : java.util.List.of("resources/list", "resources/read")) {
                var params = Json.newObject();
                if (method.equals("resources/read")) params.put("uri", "jatos://docs/api-usage");
                var message = Json.newObject().put("jsonrpc", "2.0").put("id", 1).put("method", method);
                message.set("params", params);
                var builder = version.equals(VERSION_2026_07_28) ? modern(method, params)
                        : request(Json.stringify(message)).header("MCP-Protocol-Version", version);
                Result response = post(builder.build()).toCompletableFuture().join();
                assertThat(response.status()).isEqualTo(200);
                JsonNode result = json(response).path("result");
                assertThat(result.has("resultType")).isEqualTo(version.equals(VERSION_2026_07_28));
                if (method.equals("resources/list")) {
                    assertThat(result.path("resources").size()).isEqualTo(1);
                    assertThat(result.path("resources").get(0).path("uri").asText()).isEqualTo("jatos://docs/api-usage");
                } else {
                    JsonNode content = result.path("contents").get(0);
                    assertThat(content.path("mimeType").asText()).isEqualTo("text/markdown");
                    assertThat(content.path("text").asText()).contains("/prefix/jatos/api/v1", "/prefix/openapi.yaml", "GB",
                            "MB", "studyAssetsFile", "--data-binary").doesNotContain(AUTH);
                }
            }
        }
        verify(api, times(8)).get("/admin/token", AUTH);
        verifyNoMoreInteractions(api);
    }

    /**
     * Rejects unsupported resource requests instead of treating arbitrary URIs as fetch targets.
     */
    @Test
    public void rejectsInvalidResourceRequests() {
        assertThat(json(call("""
                {"jsonrpc":"2.0","id":1,"method":"resources/read","params":{}}
                """)).path("error").path("code").asInt()).isEqualTo(INVALID_PARAMS);
        assertThat(json(call("""
                {"jsonrpc":"2.0","id":1,"method":"resources/list","params":{"cursor":"next"}}
                """)).path("error").path("code").asInt()).isEqualTo(INVALID_PARAMS);
        Result response = post(modern("resources/read", Json.newObject().put("uri", "https://example.org/"))
                .build()).toCompletableFuture().join();
        assertThat(response.status()).isEqualTo(404);
        assertThat(json(response).path("error").path("code").asInt()).isEqualTo(RESOURCE_NOT_FOUND);
    }

    /**
     * Checks discovery distinguishes read and write tools and rejects unknown arguments.
     */
    @Test
    public void listsReadAndWriteTools() {
        JsonNode tools = json(call("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}"))
                .path("result").path("tools");
        assertThat(tools.size()).isEqualTo(45);
        for (JsonNode tool : tools) {
            String name = tool.path("name").asText();
            boolean write = name.startsWith("create_") || name.startsWith("update_") || name.startsWith("add_") || name.startsWith("remove_")
                    || name.equals("get_or_generate_study_codes") || name.startsWith("set_");
            assertThat(tool.path("annotations").path("readOnlyHint").asBoolean()).isEqualTo(!write);
            assertThat(tool.path("inputSchema").path("additionalProperties").asBoolean()).isFalse();
        }
    }

    /**
     * Export instructions include public URLs and selectors without fetching ZIPs or revealing credentials.
     */
    @Test
    public void uploadInstructionsDoNotExecuteUploads() {
        commonMock.when(Common::getJatosUrlBasePath).thenReturn("/prefix/");
        commonMock.when(Common::getStudyArchiveMaxSize).thenReturn(123456L);
        commonMock.when(Common::getStudyAssetsMaxFileSize).thenReturn(654321L);
        for (String name : java.util.List.of("study_import", "upload_study_asset")) {
            var message = Json.newObject().put("jsonrpc", "2.0").put("id", 1).put("method", "tools/call");
            var args = message.putObject("params").put("name", name).putObject("arguments");
            if (name.equals("upload_study_asset")) args.put("study_id", 7).put("filepath", "media/my file.bin");
            var response = post(request(Json.stringify(message)).header("Host", "jatos.example.org").build())
                    .toCompletableFuture().join();
            JsonNode data = json(response).path("result").path("structuredContent").path("data");
            assertThat(data.path("url").asText()).startsWith("http://jatos.example.org/prefix/jatos/api/v1/studies");
            assertThat(data.path("method").asText()).isEqualTo("POST");
            assertThat(data.path("maxFileSizeBytes").asLong()).isEqualTo(name.equals("study_import") ? 123456L : 654321L);
            assertThat(data.path("contentType").asText()).isEqualTo(name.equals("study_import") ? "application/zip" : "multipart/form-data");
            assertThat(data.toString()).doesNotContain(AUTH);
            assertThat(data.has("body")).isFalse();
        }
        verify(api, times(2)).get("/admin/token", AUTH);
        verifyNoMoreInteractions(api);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Test
    public void resultDownloadsAreAuthenticatedPostInstructions() {
        commonMock.when(Common::getJatosUrlBasePath).thenReturn("/prefix/");
        for (String name : java.util.List.of("get_results", "get_result_files")) {
            var message = Json.newObject().put("jsonrpc", "2.0").put("id", 1).put("method", "tools/call");
            message.putObject("params").put("name", name).putObject("arguments")
                    .putObject("properties").put("studyIds", 9);
            Result response = post(request(Json.stringify(message)).header("Host", "jatos.example.org").build())
                    .toCompletableFuture().join();
            JsonNode data = json(response).path("result").path("structuredContent").path("data");
            assertThat(data.path("url").asText()).startsWith("http://jatos.example.org/prefix/jatos/api/v1/results");
            assertThat(data.path("method").asText()).isEqualTo("POST");
            assertThat(data.path("body").path("studyIds").asInt()).isEqualTo(9);
            assertThat(data.toString()).doesNotContain(AUTH);
        }
        verify(api, times(2)).get("/admin/token", AUTH);
        verifyNoMoreInteractions(api);
    }

    /**
     * Result text and nested metadata retain their original API contents.
     */
    @Test
    public void resultInspectionPreservesApiData() {
        for (String name : java.util.List.of("get_result_metadata", "get_result_data")) {
            JsonNode data = name.equals("get_result_data") ? com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.textNode("answer=42\n")
                    : Json.parse("[{\"studyResults\":[{\"id\":7}]}]");
            when(api.execute(any(), eq(AUTH))).thenReturn(CompletableFuture.completedFuture(
                    new McpApiClient.Reply(200, Json.newObject().set("data", data))));
            var message = Json.newObject().put("jsonrpc", "2.0").put("id", 1).put("method", "tools/call");
            message.putObject("params").put("name", name).putObject("arguments")
                    .putObject("properties").put("studyResultIds", 7);
            assertThat(json(call(Json.stringify(message))).path("result").path("structuredContent").path("data"))
                    .isEqualTo(data);
        }
    }

    /**
     * Checks token forwarding and matching structured and text representations of compact study summaries.
     */
    @Test
    public void toolCallsUseCallersTokenAndReturnCompactStructuredData() {
        when(api.execute(argThat(call -> call.path().equals("/studies/properties")), eq(AUTH))).thenReturn(CompletableFuture.completedFuture(
                new McpApiClient.Reply(200, Json.parse("{\"data\":[{\"id\":7,\"uuid\":\"abc\",\"title\":\"Study\",\"jsonData\":\"large\"}]}"))));
        JsonNode result = json(call("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"list_studies\"}}"))
                .path("result");
        assertThat(result.path("isError").asBoolean()).isFalse();
        assertThat(result.path("structuredContent").path("data").get(0).size()).isEqualTo(3);
        assertThat(Json.parse(result.path("content").get(0).path("text").asText())).isEqualTo(result.path("structuredContent"));
        var order = inOrder(api);
        order.verify(api).get("/admin/token", AUTH);
        order.verify(api).execute(argThat(call -> call.path().equals("/studies/properties")), eq(AUTH));
        verifyNoMoreInteractions(api);
    }

    /**
     * Checks API access denials become MCP tool errors containing the API status and message.
     */
    @Test
    public void apiPermissionErrorsAreToolErrors() {
        when(api.execute(argThat(call -> call.path().equals("/studies/7/properties?withComponentProperties=false&withBatchProperties=false")), eq(AUTH))).thenReturn(CompletableFuture.completedFuture(
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
        assertThat(post(request("{}").build()).toCompletableFuture().join().status()).isEqualTo(404);
        commonMock.when(Common::isJatosMcpEnabled).thenReturn(true);
        assertThat(post(fakeRequest("POST", "/jatos/mcp").bodyText("{}").build()).toCompletableFuture().join().status()).isEqualTo(401);
        assertThat(post(request("{}").header("Origin", "https://evil.example").build()).toCompletableFuture().join().status()).isEqualTo(403);
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
        assertThat(post(request("{}").header("MCP-Protocol-Version", "unknown").build()).toCompletableFuture().join().status()).isEqualTo(400);
        for (String method : java.util.List.of("GET", "DELETE")) {
            assertThat(unsupported(request("{}").method(method).build())
                    .toCompletableFuture().join().status()).isEqualTo(405);
        }
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
        verify(api, never()).execute(any(ResolvedCall.class), anyString());
    }

    /**
     * Checks path-like tool arguments are rejected after authentication without calling the tool endpoint.
     */
    @Test
    public void rejectsUnsafeArgumentsWithoutCallingToolEndpoint() {
        JsonNode response = json(call("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"get_study\",\"arguments\":{\"study_id\":\"../admin/token\"}}}"));
        assertThat(response.path("error").path("code").asInt()).isEqualTo(-32602);
        verify(api, times(1)).get("/admin/token", AUTH);
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
        if (params.has("uri")) request.header("Mcp-Name", params.get("uri").asText());
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
            Result response = post(modern("tools/call", params).build()).toCompletableFuture().join();
            assertThat(response.status()).isEqualTo(400);
            assertThat(json(response).path("error").path("code").asInt()).isEqualTo(-32602);
        }

        var invalidInput = Json.newObject().put("name", "get_study");
        invalidInput.putObject("arguments").put("study_id", 0);
        Result response = post(modern("tools/call", invalidInput).build()).toCompletableFuture().join();
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
        Result response = post(modern("tools/list", Json.newObject().put("cursor", "next"))
                .build()).toCompletableFuture().join();
        assertThat(response.status()).isEqualTo(400);
        assertThat(json(response).path("error").path("code").asInt()).isEqualTo(-32602);
    }

    /**
     * Checks modern discovery advertises all four versions without requiring initialization.
     */
    @Test
    public void modernDiscoveryAndDirectToolCalls() {
        JsonNode discovery = json(post(modern("server/discover", Json.newObject()).build())
                .toCompletableFuture().join()).path("result");
        assertThat(discovery.path("supportedVersions")).isEqualTo(Json.parse(
                "[\"2026-07-28\",\"2025-11-25\",\"2025-06-18\",\"2025-03-26\"]"));
        assertThat(discovery.path("resultType").asText()).isEqualTo("complete");
        assertThat(discovery.path("_meta").path("io.modelcontextprotocol/serverInfo").path("name").asText()).isEqualTo("JATOS");
        assertThat(discovery.path("capabilities").path("tools").isEmpty()).isTrue();
        when(api.execute(argThat(call -> call.path().equals("/studies/properties")), eq(AUTH))).thenReturn(CompletableFuture.completedFuture(
                new McpApiClient.Reply(200, Json.parse("{\"data\":[]}"))));
        JsonNode result = json(post(modern("tools/call", Json.newObject().put("name", "list_studies"))
                .build()).toCompletableFuture().join()).path("result");
        assertThat(result.path("resultType").asText()).isEqualTo("complete");
        assertThat(result.has("structuredContent")).isTrue();
    }

    /**
     * Ensures legacy initialization stays in the legacy era and all legacy versions remain usable.
     */
    @Test
    public void legacyVersionsKeepTheirResponseShapes() {
        when(api.execute(argThat(call -> call.path().equals("/studies/properties")), eq(AUTH))).thenReturn(CompletableFuture.completedFuture(
                new McpApiClient.Reply(200, Json.parse("{\"data\":[]}"))));
        for (String version : java.util.List.of(VERSION_2025_03_26, VERSION_2025_06_18, VERSION_2025_11_25)) {
            var init = Json.newObject().put("jsonrpc", "2.0").put("id", 1).put("method", "initialize");
            var params = init.putObject("params").put("protocolVersion", version);
            params.putObject("capabilities");
            params.putObject("clientInfo").put("name", "legacy").put("version", "1");
            JsonNode initialized = json(post(request(Json.stringify(init))
                    .header("MCP-Protocol-Version", version).build()).toCompletableFuture().join()).path("result");
            assertThat(initialized.path("protocolVersion").asText()).isEqualTo(version);
            assertThat(initialized.has("resultType")).isFalse();
            String body = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"list_studies\"}}";
            JsonNode result = json(post(request(body).header("MCP-Protocol-Version", version).build())
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
            Result result = post(request.header(header, "wrong").build()).toCompletableFuture().join();
            assertThat(result.status()).isEqualTo(400);
            assertThat(json(result).path("error").path("code").asInt()).isEqualTo(-32020);
        }
        verify(api, never()).execute(any(ResolvedCall.class), anyString());
        Result unknown = post(modern("unknown", Json.newObject()).build()).toCompletableFuture().join();
        assertThat(unknown.status()).isEqualTo(404);
        assertThat(json(unknown).path("error").path("code").asInt()).isEqualTo(-32601);
    }

    /**
     * Verifies modern ping and tool failures carry completion metadata, including encoded tool names.
     */
    @Test
    public void modernResultsIncludeCompletionMetadata() {
        JsonNode ping = json(post(modern("ping", Json.newObject()).build()).toCompletableFuture().join());
        assertThat(ping.path("result").path("resultType").asText()).isEqualTo("complete");
        when(api.execute(argThat(call -> call.path().equals("/studies/properties")), eq(AUTH)))
                .thenReturn(CompletableFuture.failedFuture(new McpApiClient.ApiFailure(
                        McpApiClient.FailureReason.UNAVAILABLE, new java.io.IOException("backend failed"))));
        String encoded = "=?base64?" + java.util.Base64.getEncoder().encodeToString(
                "list_studies".getBytes(java.nio.charset.StandardCharsets.UTF_8)) + "?=";
        Result response = post(modern("tools/call", Json.newObject().put("name", "list_studies"))
                .header("Mcp-Name", encoded).build()).toCompletableFuture().join();
        assertThat(response.status()).isEqualTo(200);
        JsonNode result = json(response).path("result");
        assertThat(result.path("isError").asBoolean()).isTrue();
        assertThat(result.path("resultType").asText()).isEqualTo("complete");
    }

    /**
     * Preserves tool endpoint authentication failures even after the initial token lookup succeeds.
     */
    @Test
    public void toolEndpointAuthenticationFailuresRemainUnauthorized() {
        when(api.execute(argThat(call -> call.path().equals("/studies/properties")), eq(AUTH))).thenReturn(CompletableFuture.completedFuture(
                new McpApiClient.Reply(401, Json.newObject())));
        var params = Json.newObject().put("name", "list_studies");
        var message = Json.newObject().put("jsonrpc", "2.0").put("id", 1).put("method", "tools/call");
        message.set("params", params);
        Result legacy = call(Json.stringify(message));
        Result modernResult = post(modern("tools/call", params).build()).toCompletableFuture().join();
        for (Result result : java.util.List.of(legacy, modernResult)) {
            assertThat(result.status()).isEqualTo(401);
            assertThat(result.header("WWW-Authenticate")).contains("Bearer");
            assertThat(result.header("Cache-Control")).contains("no-store");
        }
        verify(api, times(2)).get("/admin/token", AUTH);
        verify(api, times(2)).execute(argThat(call -> call.path().equals("/studies/properties")), eq(AUTH));
        verifyNoMoreInteractions(api);
    }

    /**
     * Rejects invalid tokens before validating tool arguments.
     */
    @Test
    public void invalidToolArgumentsAuthenticateFirst() {
        when(api.get("/admin/token", AUTH)).thenReturn(CompletableFuture.completedFuture(
                new McpApiClient.Reply(401, Json.newObject())));
        var params = Json.newObject().put("name", "get_study");
        params.putObject("arguments").put("study_id", 0);
        Result result = post(modern("tools/call", params).build()).toCompletableFuture().join();
        assertThat(result.status()).isEqualTo(401);
        verify(api).get("/admin/token", AUTH);
        verifyNoMoreInteractions(api);
    }

    /**
     * Authenticates malformed messages and local protocol errors before returning them.
     */
    @Test
    public void localProtocolErrorsAuthenticateFirst() {
        Result malformed = call("{");
        assertThat(malformed.status()).isEqualTo(400);
        Result unknown = post(modern("unknown/method", Json.newObject()).build())
                .toCompletableFuture().join();
        assertThat(unknown.status()).isEqualTo(404);
        Result invalidListing = post(modern("tools/list", Json.newObject().put("cursor", "next")).build())
                .toCompletableFuture().join();
        assertThat(invalidListing.status()).isEqualTo(400);
        verify(api, times(3)).get("/admin/token", AUTH);
        verifyNoMoreInteractions(api);
    }

    /**
     * Invalid credentials take precedence over HTTP and JSON-RPC errors, and prevent tool execution.
     */
    @Test
    public void failedAuthenticationStopsAllRequestProcessing() {
        when(api.get("/admin/token", AUTH)).thenReturn(CompletableFuture.completedFuture(
                new McpApiClient.Reply(401, Json.newObject())));
        var requests = java.util.List.of(
                request("{"),
                request("{}").method("GET"),
                request("{}").header("Content-Type", "text/plain"),
                modern("unknown/method", Json.newObject()),
                modern("tools/call", Json.newObject().put("name", "list_studies")),
                request("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}"));
        for (var request : requests) {
            Result result = post(request.build()).toCompletableFuture().join();
            assertThat(result.status()).isEqualTo(401);
            assertThat(result.header("WWW-Authenticate")).contains("Bearer");
        }
        verify(api, times(requests.size())).get("/admin/token", AUTH);
        verifyNoMoreInteractions(api);
    }

    /**
     * Unexpected tool response bugs retain the known request ID and are not reported as API failures.
     */
    @Test
    public void unexpectedToolFailurePreservesId() {
        when(api.execute(argThat(call -> call.path().equals("/studies/properties")), eq(AUTH))).thenReturn(CompletableFuture.completedFuture(
                new McpApiClient.Reply(200, null)));
        Result result = post(modern("tools/call", Json.newObject().put("name", "list_studies"))
                .build()).toCompletableFuture().join();
        assertThat(result.status()).isEqualTo(500);
        assertThat(json(result).path("id").asText()).isEqualTo("modern");
        assertThat(json(result).path("error").path("code").asInt()).isEqualTo(INTERNAL_ERROR);
        assertThat(json(result).path("error").path("message").asText()).isEqualTo("Internal error");
    }

    /**
     * Expected API failures provide actionable tool messages without exposing their underlying cause.
     */
    @Test
    public void expectedToolFailureHasSafeSpecificMessage() {
        when(api.execute(argThat(call -> call.path().equals("/studies/properties")), eq(AUTH))).thenReturn(CompletableFuture.failedFuture(
                new McpApiClient.ApiFailure(McpApiClient.FailureReason.TOO_LARGE,
                        new RuntimeException("private response"))));
        Result result = post(modern("tools/call", Json.newObject().put("name", "list_studies"))
                .build()).toCompletableFuture().join();
        assertThat(result.status()).isEqualTo(200);
        JsonNode body = json(result);
        assertThat(body.path("id").asText()).isEqualTo("modern");
        assertThat(body.path("result").path("isError").asBoolean()).isTrue();
        assertThat(body.path("result").path("content").get(0).path("text").asText())
                .isEqualTo("JATOS API response exceeds the configured size limit");
    }

}
