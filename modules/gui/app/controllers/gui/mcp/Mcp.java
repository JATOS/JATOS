package controllers.gui.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import general.common.Common;
import play.libs.Json;
import play.mvc.BodyParser;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.gui.mcp.McpApiClient;
import services.gui.mcp.McpTools;
import services.gui.mcp.McpProtocol;

import static services.gui.mcp.McpProtocol.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Stateless Streamable HTTP MCP with legacy initialization and modern per-request discovery. Returns JSON responses
 * without SSE or server-initiated requests.
 */
@Singleton
public class Mcp extends Controller {

    private static final String API_FAILURE =
            "JATOS API unavailable, timed out, returned invalid JSON, or exceeded the response size limit";

    private final McpApiClient api;

    /**
     * Creates the endpoint with its loopback API client.
     */
    @Inject
    public Mcp(McpApiClient api) {
        this.api = api;
    }

    /**
     * Accepts a JSON-RPC message through the bounded MCP body parser.
     */
    @BodyParser.Of(McpBodyParser.class)
    public CompletionStage<Result> post(Http.Request request) {
        return handle(request, true);
    }

    /**
     * Checks authentication before rejecting GET streams and DELETE session requests.
     */
    public CompletionStage<Result> unsupported(Http.Request request) {
        return handle(request, false);
    }

    /**
     * Validates access and authenticates each request before processing its message.
     */
    private CompletionStage<Result> handle(Http.Request request, boolean post) {
        Result rejection = validateAccess(request);
        if (rejection != null) return completed(rejection);

        String authorization = request.header("Authorization").orElse("");
        // Authenticate even discovery and notifications; token revocation takes effect immediately.
        return api.get("/admin/token", authorization)
                .thenCompose(auth -> {
                    Result authError = authenticationError(auth.status());
                    return authError != null ? completed(authError) : handleAuthenticated(request, post, authorization);
                })
                .exceptionally(e -> status(BAD_GATEWAY, error(null, -32603, API_FAILURE)))
                .thenApply(result -> result.withHeader("Cache-Control", "no-store"));
    }

    /**
     * Returns an access rejection or null when the request may authenticate.
     */
    private Result validateAccess(Http.Request request) {
        if (!Common.isJatosMcpEnabled()) return notFound();
        // Native clients normally omit Origin. Browser origins must be explicitly configured.
        List<String> origins = request.headers().getAll("Origin");
        if (!origins.isEmpty() && (origins.size() != 1 || !Common.getJatosMcpAllowedOrigins().contains(origins.getFirst()))) {
            return forbidden();
        }
        String authorization = request.header("Authorization").orElse("");
        if (!authorization.startsWith("Bearer ") || authorization.substring(7).isBlank()) {
            return unauthorized().withHeader("WWW-Authenticate", "Bearer");
        }
        return null;
    }

    /**
     * Maps the token API status to an HTTP rejection or null for successful authentication.
     */
    private Result authenticationError(int statusCode) {
        return switch (statusCode) {
            case 200 -> null;
            case 401 -> unauthorized().withHeader("WWW-Authenticate", "Bearer");
            case 403 -> forbidden();
            default -> status(BAD_GATEWAY);
        };
    }

    /**
     * Checks transport requirements and parses the authenticated request as JSON.
     */
    private CompletionStage<Result> handleAuthenticated(Http.Request request, boolean post, String authorization) {
        if (!post) return completed(status(METHOD_NOT_ALLOWED).withHeader("Allow", "POST"));
        if (!request.contentType().orElse("").equalsIgnoreCase("application/json")) {
            return completed(status(UNSUPPORTED_MEDIA_TYPE));
        }
        if (!request.accepts("application/json") || !request.accepts("text/event-stream")) {
            return completed(status(NOT_ACCEPTABLE));
        }
        JsonNode message;
        try {
            message = Json.parse(request.body().asText());
        } catch (RuntimeException e) {
            return completed(badRequest(error(null, -32700, "Parse error")));
        }
        return dispatch(message, authorization, request);
    }

    /**
     * Validates the message, ignores notifications, and routes requests to their MCP operation.
     */
    private CompletionStage<Result> dispatch(JsonNode message, String authorization, Http.Request request) {
        ObjectNode validationError = validateMessage(message, request);
        if (validationError != null) return completed(badRequest(validationError));

        JsonNode id = message.get("id");
        // Unknown notifications are ignored; they never execute tools.
        if (id == null) return completed(status(ACCEPTED));

        JsonNode params = message.has("params") ? message.get("params") : Json.newObject();
        String version = McpProtocol.version(request);
        return switch (message.get("method").asText()) {
            case "server/discover" -> completed(discover(id, version));
            case "initialize" -> completed(initialize(id, params, version));
            case "ping" -> completed(ok(success(id, Json.newObject(), version)));
            case "tools/list" -> completed(listTools(id, params, version));
            case "tools/call" -> callTool(id, params, version, authorization);
            default -> completed(status(VERSION_2026_07_28.equals(version) ? NOT_FOUND : OK,
                    error(id, -32601, "Method not found")));
        };
    }

    /**
     * Returns a JSON-RPC or protocol validation error, or null for a valid message.
     */
    private ObjectNode validateMessage(JsonNode message, Http.Request request) {
        if (message == null || !message.isObject() || !"2.0".equals(message.path("jsonrpc").asText())
                || !message.path("method").isTextual() || message.has("result") || message.has("error")) {
            return error(null, -32600, "Invalid JSON-RPC request");
        }
        JsonNode id = message.get("id");
        if (id != null && !(id.isTextual() || id.isIntegralNumber())) {
            return error(null, -32600, "Request ID must be a string or integer");
        }
        if (message.has("params") && !message.get("params").isObject()) {
            return error(id, -32602, "params must be an object");
        }
        return McpProtocol.validateRequestMetadata(request, message);
    }

    /**
     * Describes the server for protocol versions supporting per-request discovery.
     */
    private Result discover(JsonNode id, String version) {
        if (!VERSION_2026_07_28.equals(version)) return ok(error(id, -32601, "Method not found"));
        ObjectNode discovery = Json.newObject();
        discovery.set("supportedVersions", Json.toJson(VERSIONS));
        discovery.set("capabilities", capabilities(true));
        discovery.put("instructions", INSTRUCTIONS);
        return ok(success(id, discovery, version));
    }

    /**
     * Validates legacy initialization parameters and negotiates a supported legacy version.
     */
    private Result initialize(JsonNode id, JsonNode params, String version) {
        if (VERSION_2026_07_28.equals(version)) {
            return notFound(error(id, -32601, "Use server/discover with this protocol version"));
        }
        if (!params.path("protocolVersion").isTextual() || !params.path("capabilities").isObject()
                || !params.path("clientInfo").path("name").isTextual()
                || !params.path("clientInfo").path("version").isTextual()) {
            return invalidParams(id, "initialize requires protocolVersion, capabilities and clientInfo", version);
        }
        String requested = params.get("protocolVersion").asText();
        String negotiated = VERSIONS.contains(requested) && !VERSION_2026_07_28.equals(requested) ? requested : VERSION_2025_11_25;
        ObjectNode init = Json.newObject().put("protocolVersion", negotiated);
        init.set("capabilities", capabilities(false));
        init.set("serverInfo", serverInfo());
        init.put("instructions", INSTRUCTIONS);
        return ok(success(id, init, version));
    }

    /**
     * Returns the complete tool catalog; pagination is not supported.
     */
    private Result listTools(JsonNode id, JsonNode params, String version) {
        if (params.has("cursor")) {
            return invalidParams(id, "Tool discovery is not paginated; omit cursor", version);
        }
        ObjectNode tools = Json.newObject();
        tools.set("tools", McpTools.list());
        return ok(success(id, tools, version));
    }

    /**
     * Validates tool arguments and calls the corresponding API endpoint with the caller's token.
     */
    private CompletionStage<Result> callTool(JsonNode id, JsonNode params, String version, String authorization) {
        if (!params.path("name").isTextual()) return completed(invalidParams(id, "Tool name is required", version));
        String name = params.get("name").asText();
        String path;
        try {
            path = McpTools.path(name, params.get("arguments"));
        } catch (McpTools.InvalidToolInputException e) {
            if (VERSION_2026_07_28.equals(version)) {
                return completed(ok(success(id, toolError(e.getMessage()), version)));
            }
            return completed(invalidParams(id, e.getMessage(), version));
        } catch (IllegalArgumentException e) {
            return completed(invalidParams(id, e.getMessage(), version));
        }
        return api.get(path, authorization)
                .thenApply(reply -> ok(success(id, toolResult(name, reply, version), version)))
                .exceptionally(e -> ok(success(id, toolError(API_FAILURE), version)));
    }

    /** Returns HTTP 400 for modern invalid-parameter errors, retaining legacy HTTP 200 responses. */
    private Result invalidParams(JsonNode id, String message, String version) {
        return status(VERSION_2026_07_28.equals(version) ? BAD_REQUEST : OK, error(id, -32602, message));
    }

    /**
     * Converts an API reply to tool content or a tool execution error.
     */
    private ObjectNode toolResult(String name, McpApiClient.Reply reply, String version) {
        if (reply.status() != 200) {
            return toolError("JATOS API returned HTTP " + reply.status() + ": "
                    + reply.body().path("error").path("message").asText("Request failed"));
        }
        JsonNode data = reply.body().get("data");
        if (data == null) return toolError("JATOS API response has no data field");

        ObjectNode structured = Json.newObject();
        structured.set("data", McpTools.output(name, data));
        ObjectNode result = Json.newObject().put("isError", false);
        if (!VERSION_2025_03_26.equals(version)) result.set("structuredContent", structured);
        result.putArray("content").addObject().put("type", "text").put("text", Json.stringify(structured));
        return result;
    }

    /**
     * Builds an MCP tool execution error, distinct from a JSON-RPC protocol error.
     */
    private static ObjectNode toolError(String message) {
        ObjectNode result = Json.newObject().put("isError", true);
        result.putArray("content").addObject().put("type", "text").put("text", message);
        return result;
    }

    /**
     * Wraps an immediate HTTP result in the asynchronous return type used by the controller.
     */
    private static CompletionStage<Result> completed(Result result) {
        return CompletableFuture.completedFuture(result);
    }
}
