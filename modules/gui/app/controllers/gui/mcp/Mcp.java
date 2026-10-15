package controllers.gui.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import exceptions.common.JatosException;
import json.common.DefaultJson;
import play.Logger;
import play.Logger.ALogger;
import play.mvc.*;
import services.gui.mcp.McpApiClient;
import services.gui.mcp.McpProtocolUtils;
import services.gui.mcp.McpTools;
import services.gui.mcp.McpResources;
import services.gui.mcp.InvalidToolRequestException;
import services.gui.mcp.InvalidToolInputException;
import services.gui.mcp.ResolvedCall;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

import static services.gui.mcp.McpProtocolUtils.*;

/**
 * Stateless Streamable HTTP MCP with legacy initialization and modern per-request discovery. Returns JSON responses
 * without SSE or server-initiated requests.
 */
@Singleton
@With(McpTransportAction.class)
public class Mcp extends Controller {

    private static final ALogger LOGGER = Logger.of(Mcp.class);

    private final McpApiClient mcpApi;
    private final DefaultJson defaultJson;

    /**
     * Creates the endpoint with its loopback API client and shared JSON mapper.
     */
    @Inject
    public Mcp(McpApiClient mcpApi, DefaultJson defaultJson) {
        this.mcpApi = mcpApi;
        this.defaultJson = defaultJson;
    }

    /**
     * Accepts a JSON-RPC message through the bounded MCP body parser.
     */
    @BodyParser.Of(McpBodyParser.class)
    public CompletionStage<Result> post(Http.Request request) {
        JsonNode message;
        try {
            message = defaultJson.jsonAsJsonNode(request.body().asText());
        } catch (JatosException e) {
            return completed(badRequest(error(null, PARSE_ERROR, "Parse error")));
        }
        JsonNode id = message == null ? null : message.get("id");
        JsonNode responseId = id != null && (id.isTextual() || id.isIntegralNumber()) ? id : null;
        return CompletableFuture.completedFuture(message)
                .thenCompose(m -> dispatch(m, request))
                .exceptionally(e -> failureResult(responseId, e));
    }

    /**
     * Returns 405 after the transport action has authenticated the request.
     */
    @BodyParser.Of(BodyParser.Empty.class)
    public CompletionStage<Result> unsupported(Http.Request request) {
        return completed(status(METHOD_NOT_ALLOWED).withHeader("Allow", "POST"));
    }

    /**
     * Validates the message, ignores notifications, and routes requests to their MCP operation.
     */
    private CompletionStage<Result> dispatch(JsonNode message, Http.Request request) {
        ObjectNode validationError = validateMessage(request, message);
        if (validationError != null) return completed(badRequest(validationError));

        JsonNode id = message.get("id");
        // Unknown notifications are ignored; they never execute tools.
        if (id == null) return completed(status(ACCEPTED));

        JsonNode params = message.has("params") ? message.get("params") : JsonNodeFactory.instance.objectNode();
        String version = McpProtocolUtils.version(request);
        String method = message.get("method").asText();
        if (method.equals("tools/call")) {
            return callTool(id, params, version, request);
        }

        ObjectNode result = switch (method) {
            case "server/discover" -> discover(id, version);
            case "initialize" -> initialize(id, params, version);
            case "ping" -> success(id, JsonNodeFactory.instance.objectNode(), version);
            case "tools/list" -> listTools(id, params, version);
            case "resources/list" -> McpResources.list(id, params, version);
            case "resources/read" -> McpResources.read(id, params, version);
            default -> error(id, METHOD_NOT_FOUND, "Method not found");
        };
        int statusCode = OK;
        if (result.has("error") && VERSION_2026_07_28.equals(version)) {
            int errorCode = result.path("error").path("code").asInt();
            statusCode = errorCode == METHOD_NOT_FOUND || errorCode == RESOURCE_NOT_FOUND ? NOT_FOUND : BAD_REQUEST;
        }
        return completed(status(statusCode, result));
    }

    /**
     * Describes the server for protocol versions supporting per-request discovery.
     */
    private ObjectNode discover(JsonNode id, String version) {
        if (!VERSION_2026_07_28.equals(version)) return error(id, METHOD_NOT_FOUND, "Method not found");
        ObjectNode discovery = JsonNodeFactory.instance.objectNode();
        discovery.set("supportedVersions", defaultJson.objAsJsonNode(VERSIONS));
        discovery.set("capabilities", capabilities(true));
        discovery.put("instructions", INSTRUCTIONS);
        return success(id, discovery, version);
    }

    /**
     * Validates legacy initialization parameters and negotiates a supported legacy version.
     */
    private ObjectNode initialize(JsonNode id, JsonNode params, String version) {
        if (VERSION_2026_07_28.equals(version)) {
            return error(id, METHOD_NOT_FOUND, "Use server/discover with this protocol version");
        }
        if (!params.path("protocolVersion").isTextual()
                || !params.path("capabilities").isObject()
                || !params.path("clientInfo").path("name").isTextual()
                || !params.path("clientInfo").path("version").isTextual()) {
            return error(id, INVALID_PARAMS, "initialize requires protocolVersion, capabilities and clientInfo");
        }
        String requested = params.get("protocolVersion").asText();
        String negotiated = VERSIONS.contains(requested) && !VERSION_2026_07_28.equals(requested) ? requested : VERSION_2025_11_25;
        ObjectNode init = JsonNodeFactory.instance.objectNode().put("protocolVersion", negotiated);
        init.set("capabilities", capabilities(false));
        init.set("serverInfo", serverInfo());
        init.put("instructions", INSTRUCTIONS);
        return success(id, init, version);
    }

    /**
     * Returns the complete tool catalog; pagination is not supported.
     */
    private ObjectNode listTools(JsonNode id, JsonNode params, String version) {
        if (params.has("cursor")) {
            return error(id, INVALID_PARAMS, "Tool discovery is not paginated; omit cursor");
        }
        ObjectNode tools = JsonNodeFactory.instance.objectNode();
        tools.set("tools", McpTools.list());
        return success(id, tools, version);
    }

    /**
     * Validates tool arguments and calls the corresponding API endpoint with the caller's token.
     */
    private CompletionStage<Result> callTool(JsonNode id, JsonNode params, String version, Http.Request request) {
        if (!params.path("name").isTextual())
            return completed(invalidParams(id, "Tool name is required", version));
        String name = params.get("name").asText();
        ResolvedCall call;
        try {
            call = McpTools.resolveCall(name, params.get("arguments"));
        } catch (InvalidToolInputException e) {
            if (VERSION_2026_07_28.equals(version)) {
                return completed(ok(success(id, toolError(e.getMessage()), version)));
            }
            return completed(invalidParams(id, e.getMessage(), version));
        } catch (InvalidToolRequestException e) {
            return completed(invalidParams(id, e.getMessage(), version));
        }
        return mcpApi.execute(call, request.header("Authorization").orElse(""))
                .thenApply(reply -> reply.status() == UNAUTHORIZED
                        ? unauthorized().withHeader("WWW-Authenticate", "Bearer")
                        : ok(success(id, toolResult(call, reply, version, request), version)))
                .exceptionally(e -> {
                    Throwable cause = McpApiClient.unwrapFailure(e);
                    if (cause instanceof McpApiClient.ApiFailure) {
                        return ok(success(id, toolError(cause.getMessage()), version));
                    }
                    throw new CompletionException(cause);
                });
    }

    /**
     * Reports known API failures separately from unexpected bugs, retaining the request ID when available.
     */
    static Result failureResult(JsonNode id, Throwable failure) {
        Throwable cause = McpApiClient.unwrapFailure(failure);
        if (cause instanceof McpApiClient.ApiFailure) {
            return status(BAD_GATEWAY, error(id, INTERNAL_ERROR, cause.getMessage()));
        }
        // Keep the exception type and stack location but never log messages that may contain request/response data.
        RuntimeException diagnostic = new RuntimeException(cause.getClass().getName());
        diagnostic.setStackTrace(cause.getStackTrace());
        LOGGER.error("Unexpected MCP request processing failure", diagnostic);
        return internalServerError(error(id, INTERNAL_ERROR, "Internal error"));
    }

    /**
     * Returns HTTP 400 for modern invalid-parameter errors, retaining legacy HTTP 200 responses.
     */
    private Result invalidParams(JsonNode id, String message, String version) {
        return status(VERSION_2026_07_28.equals(version) ? BAD_REQUEST : OK, error(id, INVALID_PARAMS, message));
    }

    /**
     * Converts an API reply to tool content or a tool execution error.
     */
    private ObjectNode toolResult(ResolvedCall call, McpApiClient.Reply reply, String version, Http.Request request) {
        if (!McpApiClient.isSuccess(reply.status())) {
            return toolError("JATOS API returned HTTP " + reply.status() + ": "
                    + reply.body().path("error").path("message").asText("Request failed"));
        }
        JsonNode data = reply.body().get("data");
        if (data == null) return toolError("JATOS API response has no data field");

        ObjectNode structured = JsonNodeFactory.instance.objectNode();
        if (call.format() == McpApiClient.ResponseFormat.DOWNLOAD_LINK) {
            String url = (request.secure() ? "https://" : "http://") + request.host()
                    + general.common.Common.getJatosUrlBasePath() + "jatos/api/v1/studies/" + data.path("id").asLong();
            data = JsonNodeFactory.instance.objectNode().put("url", url).put("mimeType", "application/zip")
                    .put("filename", "jatos_study_" + data.path("uuid").asText() + ".jzip")
                    .put("authentication", "Use an API Bearer token or a signed-in JATOS session. Export requires USER permission.");
        }
        structured.set("data", call.postProcess().apply(data));
        ObjectNode result = JsonNodeFactory.instance.objectNode().put("isError", false);
        if (!VERSION_2025_03_26.equals(version)) result.set("structuredContent", structured);
        result.putArray("content").addObject().put("type", "text").put("text", defaultJson.objAsJson(structured));
        return result;
    }

    /**
     * Builds an MCP tool execution error, distinct from a JSON-RPC protocol error.
     */
    private static ObjectNode toolError(String message) {
        ObjectNode result = JsonNodeFactory.instance.objectNode().put("isError", true);
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
