package mcp.gui;

import akka.stream.javadsl.Source;
import akka.util.ByteString;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import general.common.Common;
import play.Logger;
import play.libs.Json;
import play.mvc.BodyParser;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import play.Environment;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

// todo check with Junie and VSCode Copilot
// todo check AI generated code
// todo move package mcp to common? There might be different APIs for gui and publix in the future.
// todo iostd impl? (would need additional MCP server running parallel to JATOS)
// todo split API into jatos and publix?
// todo which API endpoints to publish as MCP tools?
// todo fix API Bearer token handling
// todo add proper streaming to MCP tools? (at least this is what ChatGPT suggested)
// todo allow running a study for development via MCP tools
@Singleton
public class Mcp extends Controller {

    private static final Logger.ALogger LOGGER = Logger.of(Mcp.class);

    private final Environment env;
    private final List<McpTool> tools;
    private final JatosMcpExecutor executor;

    @Inject
    public Mcp(Environment env, McpToolRegistry registry, JatosMcpExecutor executor) {
        this.env = env;
        this.tools = registry.getTools();
        this.executor = executor;
    }

    /**
     * POST /mcp Handle JSON-RPC requests: tools/list and tools/call
     */
    @BodyParser.Of(BodyParser.Json.class)
    public Result handle(Http.Request request) {
        JsonNode reqJson = request.body().asJson();
        if (reqJson == null) return badRequest("Expected JSON body");

        String method = reqJson.has("method") ? reqJson.get("method").asText() : null;
        if (method == null) return badRequest("Missing 'method' field");

        if ("notifications/initialized".equals(method)) {
            return noContent();
        }

        switch (method) {
            case "initialize":
                return initialize(reqJson);
            case "tools/list":
                return streamToolsList(reqJson);
            case "tools/call":
                return streamToolCall(reqJson);
            case "resources/list":
                return streamResourcesList(reqJson);
            case "resources/read":
                return streamResourceRead(reqJson);
            default:
                return badRequest("Unknown MCP method: " + method);
        }
    }

    /**
     * Return 'initialize' handshake JSON-RPC response (non-streaming)
     */
    private Result initialize(JsonNode reqJson) {
        JsonNode params = reqJson.get("params");
        String protocolVersion = "unknown";
        if (params != null && params.hasNonNull("protocolVersion")) {
            protocolVersion = params.get("protocolVersion").asText("unknown");
        }

        ObjectNode result = loadStaticServerInfo();
        result.put("protocolVersion", protocolVersion);

        ObjectNode responseNode = wrapJsonRpc(reqJson, result);
        return ok(responseNode);
    }

    private ObjectNode loadStaticServerInfo() {
        String classpathLocation = "mcp/server-info.json";
        try (InputStream in = env.resourceAsStream(classpathLocation)) {
            if (in == null) {
                throw new RuntimeException("Missing resource: " + classpathLocation);
            }
            return Json.mapper().readValue(in, ObjectNode.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read classpath resource: " + classpathLocation, e);
        }
    }

    /**
     * GET /mcp Simple 200 response for MCP clients probing the endpoint
     */
    public Result probe() {
        return ok().as("text/event-stream");
    }

    /**
     * OPTIONS /mcp Probe / discovery endpoint (also useful for CORS preflight).
     */
    public Result options() {
        return noContent()
                .withHeader(Http.HeaderNames.ALLOW, "GET, POST, OPTIONS")
                .withHeader(Http.HeaderNames.ACCESS_CONTROL_ALLOW_METHODS, "GET, POST, OPTIONS")
                .withHeader(Http.HeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
                .withHeader(Http.HeaderNames.ACCESS_CONTROL_ALLOW_HEADERS, "Content-Type, Authorization");
    }

    /**
     * GET /mcp/events SSE stream that emits a heartbeat every 30 seconds.
     */
    public Result events() {
        Source<ByteString, ?> source = Source.tick(
                Duration.ofSeconds(30),
                Duration.ofSeconds(30),
                ByteString.fromString(": heartbeat\n\n")
        );
        return ok()
                .chunked(source)
                .as("text/event-stream")
                .withHeader(Http.HeaderNames.CACHE_CONTROL, "no-cache")
                .withHeader(Http.HeaderNames.CONNECTION, "keep-alive");
    }

    /**
     * Stream tools/list as a single SSE JSON-RPC event
     */
    private Result streamToolsList(JsonNode reqJson) {
        ArrayNode arr = Json.mapper().createArrayNode();
        for (McpTool t : tools) {
            arr.add(t.toJson());
        }

        ObjectNode result = Json.mapper().createObjectNode();
        result.set("tools", arr);

        ObjectNode responseNode = wrapJsonRpc(reqJson, result);
        String sse = "data: " + responseNode + "\n\n";

        Source<ByteString, ?> source = Source.single(ByteString.fromString(sse));
        return ok().chunked(source).as("text/event-stream");
    }

    /**
     * Stream tools/call as a single SSE JSON-RPC event (async)
     */
    private Result streamToolCall(JsonNode reqJson) {
        JsonNode params = reqJson.get("params");
        if (params == null) return badRequest("Missing 'params'");

        String toolName = params.has("name") ? params.get("name").asText() : null;
        JsonNode arguments = params.get("arguments");

        McpTool tool = findTool(toolName);
        if (tool == null) return badRequest("Unknown tool: " + toolName);

        // Special handling for tools returning ZIP files or requiring file upload
        if (isZipReturningTool(toolName, arguments)) {
            return returnResourceUri(reqJson, tool, arguments, true);
        }
        if (isFileUploadTool(toolName)) {
            return returnResourceUri(reqJson, tool, arguments, false);
        }

        CompletableFuture<String> sseFuture = new CompletableFuture<>();

        executor.executeToolAsync(tool, arguments)
                .thenAccept(resultJson -> {
                    try {
                        ObjectNode event = Json.mapper().createObjectNode();
                        ArrayNode content = Json.mapper().createArrayNode();
                        ObjectNode text = content.addObject();
                        text.put("type", "text");
                        text.put("text", resultJson.toString());
                        event.set("content", content);

                        ObjectNode responseNode = wrapJsonRpc(reqJson, event);
                        String sse = "data: " + responseNode.toString() + "\n\n";
                        LOGGER.info("Sending SSE response: " + sse);
                        sseFuture.complete(sse);

                    } catch (Exception e) {
                        LOGGER.error("Error building SSE response", e);
                        sseFuture.completeExceptionally(e);
                    }
                })
                .exceptionally(ex -> {
                    LOGGER.error("Error executing tool async", ex);
                    sseFuture.completeExceptionally(ex);
                    return null;
                });

        Source<ByteString, ?> source = Source.fromCompletionStage(sseFuture)
                .map(ByteString::fromString);

        return ok().chunked(source).as("text/event-stream");
    }

    private boolean isZipReturningTool(String toolName, JsonNode arguments) {
        switch (toolName) {
            case "results_get":
            case "results_files_get":
            case "study_export":
            case "study_log_get":
            case "admin_log_get":
            case "study_assets_file_get":
            case "results_file_get":
                return true;
            case "results_data_get":
                boolean asPlainText = arguments != null && arguments.has("asPlainText") && arguments.get("asPlainText").asBoolean();
                return !asPlainText;
            default:
                return false;
        }
    }

    private boolean isFileUploadTool(String toolName) {
        switch (toolName) {
            case "study_import":
            case "study_assets_file_upload":
                return true;
            default:
                return false;
        }
    }

    private Result returnResourceUri(JsonNode reqJson, McpTool tool, JsonNode arguments, boolean isDownload) {
        ObjectNode event = Json.mapper().createObjectNode();
        ArrayNode content = Json.mapper().createArrayNode();
        ObjectNode text = content.addObject();
        text.put("type", "text");

        String path = tool.getPath();
        if (arguments != null && arguments.isObject()) {
            Iterator<String> names = arguments.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                String token = "{" + name + "}";
                if (path.contains(token)) {
                    path = path.replace(token, arguments.get(name).asText());
                }
            }
        }

        StringBuilder uri = new StringBuilder("http://localhost:9000").append(path);
        boolean firstParam = true;
        if (arguments != null && arguments.isObject()) {
            Iterator<String> names = arguments.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                if (tool.getPath().contains("{" + name + "}")) continue;

                if (firstParam) {
                    uri.append("?");
                    firstParam = false;
                } else {
                    uri.append("&");
                }
                String value = arguments.get(name).toString().replace("\"", ""); // Remove quotes from string values
                uri.append(name).append("=").append(value);
            }
        }

        if (isDownload) {
            text.put("text", "ZIP file ready for download. Use this direct download link: " + uri.toString()
                    + "\n\nNote: You will need to provide your API key in the Authorization header if downloading via curl:\n"
                    + "curl -H \"Authorization: Bearer YOUR_API_KEY\" \"" + uri.toString() + "\" -o download.zip");
        } else {
            text.put("text", "This tool requires a file upload. Please use the JATOS API directly to perform this upload.\n"
                    + "Endpoint: " + tool.getMethod().toUpperCase() + " " + uri.toString() + "\n\n"
                    + "Example using curl:\n"
                    + "curl -H \"Authorization: Bearer YOUR_API_KEY\" -F \"FILE_FIELD_NAME=@/path/to/file\" \"" + uri.toString() + "\"");
        }
        event.set("content", content);

        ObjectNode responseNode = wrapJsonRpc(reqJson, event);
        String sse = "data: " + responseNode.toString() + "\n\n";
        Source<ByteString, ?> source = Source.single(ByteString.fromString(sse));
        return ok().chunked(source).as("text/event-stream");
    }

    private Result streamResourcesList(JsonNode reqJson) {
        ObjectNode result = Json.mapper().createObjectNode();
        ArrayNode resources = result.putArray("resources");

        ObjectNode res = resources.addObject();
        res.put("uri", "jatos://results/data");
        res.put("name", "Study Results ZIP");
        res.put("description", "Download study results as a ZIP file. Support query parameters like studyIds.");
        res.put("mimeType", "application/zip");

        ObjectNode responseNode = wrapJsonRpc(reqJson, result);
        String sse = "data: " + responseNode.toString() + "\n\n";
        Source<ByteString, ?> source = Source.single(ByteString.fromString(sse));
        return ok().chunked(source).as("text/event-stream");
    }

    private Result streamResourceRead(JsonNode reqJson) {
        JsonNode params = reqJson.get("params");
        if (params == null || !params.has("uri")) return badRequest("Missing 'uri'");

        String uri = params.get("uri").asText();
        if (!uri.startsWith("jatos://results/data")) return badRequest("Unsupported resource URI");

        // Convert URI parameters back to tool arguments
        ObjectNode arguments = Json.mapper().createObjectNode();
        if (uri.contains("?")) {
            String query = uri.substring(uri.indexOf("?") + 1);
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                String[] kv = pair.split("=");
                if (kv.length == 2) {
                    try {
                        arguments.set(kv[0], Json.mapper().readTree(kv[1]));
                    } catch (Exception e) {
                        arguments.put(kv[0], kv[1]);
                    }
                }
            }
        }

        McpTool tool = findTool("results_data_get");
        if (tool == null) return badRequest("Results tool not found");

        CompletableFuture<String> sseFuture = new CompletableFuture<>();

        executor.executeToolAsync(tool, arguments)
                .thenAccept(resultJson -> {
                    try {
                        ObjectNode result = Json.mapper().createObjectNode();
                        ArrayNode contents = result.putArray("contents");
                        ObjectNode content = contents.addObject();
                        content.put("uri", uri);
                        content.put("mimeType", "application/zip");

                        // Result will be {"text": "PK..."}
                        String blob = resultJson.get("text").asText();
                        content.put("text", blob); // Protocol says text or blob

                        ObjectNode responseNode = wrapJsonRpc(reqJson, result);
                        String sse = "data: " + responseNode.toString() + "\n\n";
                        sseFuture.complete(sse);
                    } catch (Exception e) {
                        sseFuture.completeExceptionally(e);
                    }
                })
                .exceptionally(ex -> {
                    sseFuture.completeExceptionally(ex);
                    return null;
                });

        Source<ByteString, ?> source = Source.fromCompletionStage(sseFuture)
                .map(ByteString::fromString);
        return ok().chunked(source).as("text/event-stream");
    }

    /**
     * Wrap a JsonNode result in a JSON-RPC object with the same id as the request
     */
    private ObjectNode wrapJsonRpc(JsonNode reqJson, JsonNode result) {
        ObjectNode res = Json.mapper().createObjectNode();
        res.put("jsonrpc", "2.0");

        // Junie requires "id" to be present in all responses
        JsonNode idNode = reqJson.get("id");
        if (idNode != null) {
            res.set("id", idNode);
        } else {
            // Should not happen in normal Junie flow, but fallback just in case
            res.put("id", "unknown");
        }

        res.set("result", result);
        return res;
    }

    /**
     * Find a tool by name
     */
    private McpTool findTool(String name) {
        for (McpTool t : tools) {
            if (t.getName().equals(name)) return t;
        }
        return null;
    }
}
