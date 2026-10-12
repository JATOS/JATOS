package controllers.gui.mcp;

import general.common.Common;
import play.mvc.Action;
import play.mvc.Http;
import play.mvc.Result;
import services.gui.mcp.McpApiClient;

import javax.inject.Inject;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static play.mvc.Http.Status.*;

/**
 * Applies MCP access gates and token authentication before invoking either endpoint.
 */
public class McpTransportAction extends Action.Simple {
    private final McpApiClient api;

    /**
     * Uses the same loopback API client as tool execution to authenticate tokens.
     */
    @Inject
    public McpTransportAction(McpApiClient api) {
        this.api = api;
    }

    /**
     * Prevents caching of every action response, including access and authentication rejections.
     */
    @Override
    public CompletionStage<Result> call(Http.Request request) {
        return CompletableFuture.completedFuture(request)
                .thenCompose(this::authenticate)
                .exceptionally(e -> Mcp.failureResult(null, e))
                .thenApply(result -> result.withHeader("Cache-Control", "no-store"));
    }

    /**
     * Checks endpoint availability and browser origins, then authenticates before delegation.
     */
    private CompletionStage<Result> authenticate(Http.Request request) {
        if (!Common.isJatosMcpEnabled()) return CompletableFuture.completedFuture(notFound());

        // Native clients normally omit Origin. Browser origins must be explicitly configured.
        List<String> origins = request.headers().getAll("Origin");
        if (!origins.isEmpty() && (origins.size() != 1
                || !Common.getJatosMcpAllowedOrigins().contains(origins.getFirst()))) {
            return CompletableFuture.completedFuture(forbidden());
        }

        String authorizationHeader = request.header("Authorization").orElse("");
        if (!authorizationHeader.startsWith("Bearer ") || authorizationHeader.substring(7).isBlank()) {
            return CompletableFuture.completedFuture(unauthorized().withHeader("WWW-Authenticate", "Bearer"));
        }
        return api.get("/admin/token", authorizationHeader).thenCompose(reply -> switch (reply.status()) {
            case 200 -> handleAuthenticated(request);
            case 401 -> CompletableFuture.completedFuture(unauthorized().withHeader("WWW-Authenticate", "Bearer"));
            case 403 -> CompletableFuture.completedFuture(forbidden());
            default -> CompletableFuture.completedFuture(status(BAD_GATEWAY));
        });
    }

    /**
     * Checks POST media requirements after authentication; unsupported methods go directly to their endpoint.
     */
    private CompletionStage<Result> handleAuthenticated(Http.Request request) {
        if ("POST".equals(request.method())) {
            if (!request.contentType().orElse("").equalsIgnoreCase("application/json")) {
                return CompletableFuture.completedFuture(status(UNSUPPORTED_MEDIA_TYPE));
            }
            if (!request.accepts("application/json") || !request.accepts("text/event-stream")) {
                return CompletableFuture.completedFuture(status(NOT_ACCEPTABLE));
            }
        }
        return delegate.call(request);
    }

}
