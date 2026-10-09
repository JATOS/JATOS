package services.gui.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import general.common.Common;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.util.ByteString;
import play.Logger;
import play.libs.Json;
import play.libs.ws.WSClient;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.CompletionStage;

/**
 * Calls only fixed API paths on this installation, never a client-supplied host or URL.
 * */
@Singleton
public class McpApiClient {

    private static final Logger.ALogger LOGGER = Logger.of(McpApiClient.class);

    /** HTTP status and parsed JSON body returned by the local JATOS API. */
    public record Reply(int status, JsonNode body) {}

    private final WSClient ws;
    private final Materializer materializer;

    /**
     * Creates the loopback API client with its HTTP client and stream materializer.
     */
    @Inject
    public McpApiClient(WSClient ws, Materializer materializer) {
        this.ws = ws;
        this.materializer = materializer;
    }

    /**
     * Performs an asynchronous GET using the caller's Authorization header and a trusted API-relative path.
     * Redirects are disabled; transport, timeout, size-limit and JSON parsing failures complete the stage exceptionally.
     */
    public CompletionStage<Reply> get(String path, String authorization) {
        int port = Common.getJatosHttpPort();
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Invalid play.server.http.port");
        String context = Common.getJatosUrlBasePath().replaceAll("/$", "");
        String baseUrl = "http://127.0.0.1:" + port + context + "/jatos/api/v1";
        return ws.url(baseUrl + path)
                .setFollowRedirects(false)
                .setRequestTimeout(Common.getJatosMcpApiTimeout())
                .addHeader("Authorization", authorization)
                .addHeader("Accept", "application/json")
                .setMethod("GET").stream()
                .thenCompose(response -> response.getBodyAsSource()
                        .limitWeighted(Common.getJatosMcpMaxResponseSize(), bytes -> (long) bytes.size())
                        .completionTimeout(Common.getJatosMcpApiTimeout())
                        .runFold(ByteString.emptyByteString(), ByteString::concat, materializer)
                        .thenApply(bytes -> new Reply(response.getStatus(), Json.parse(bytes.toArray()))))
                .whenComplete((reply, failure) -> {
                    if (failure != null) logFailure(path, failure);
                });
    }

    /** Logs the underlying failure type once without exception messages that might expose response content. */
    private static void logFailure(String path, Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        LOGGER.warn("MCP loopback API GET {} failed: {}", path, cause.getClass().getName());
    }
}
