package services.gui.mcp;

import org.apache.pekko.util.ByteString;
import org.junit.Test;
import general.common.Common;
import org.apache.pekko.stream.Materializer;
import play.libs.ws.WSClient;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import static org.mockito.Mockito.*;
import play.libs.Json;

import static org.assertj.core.api.Assertions.*;
import static services.gui.mcp.McpApiClient.ResponseFormat.*;

/**
 * Checks decoding of raw API responses without making HTTP requests.
 */
public class McpApiClientTest {
    private final McpApiClient client = new McpApiClient(mock(WSClient.class), mock(Materializer.class),
            new json.common.DefaultJson());

    /** Study creation sends JSON properties and never retries a failed POST. */
    @Test
    public void sendsJsonWithoutRetry() {
        try (var common = mockStatic(Common.class)) {
            common.when(Common::getJatosHttpAddress).thenReturn("127.0.0.1");
            common.when(Common::getJatosHttpPort).thenReturn(9000);
            common.when(Common::getJatosUrlBasePath).thenReturn("/");
            common.when(Common::getJatosMcpApiTimeout).thenReturn(java.time.Duration.ofSeconds(1));
            WSClient ws = mock(WSClient.class);
            var request = mock(play.libs.ws.WSRequest.class, RETURNS_SELF);
            when(ws.url(anyString())).thenReturn(request);
            when(request.stream()).thenReturn(java.util.concurrent.CompletableFuture.failedFuture(new TimeoutException()));
            var api = new McpApiClient(ws, mock(Materializer.class), new json.common.DefaultJson());
            var args = Json.newObject();
            args.putObject("properties").put("title", "Test").put("dirName", "test");
            var call = McpTools.resolveCall("create_study", args);
            assertThatThrownBy(() -> api.execute(call, "Bearer test").toCompletableFuture().join())
                    .isInstanceOf(CompletionException.class).hasCauseInstanceOf(McpApiClient.ApiFailure.class);
            verify(request).setContentType("application/json");
            verify(request).setBody(Json.stringify(args.get("properties")));
            verify(request).setMethod("POST");
            verify(request).addHeader("Authorization", "Bearer test");
            verify(request, times(1)).stream();
        }
    }

    /** The API returns 201 for newly created studies, with a normal data envelope. */
    @Test
    public void acceptsCreatedResponses() {
        var body = client.decode(ByteString.fromString("{\"data\":{\"id\":9}}"), 201, ENVELOPED_JSON);
        assertThat(body.path("data").path("id").asInt()).isEqualTo(9);
    }

    /**
     * Preserves source text and wraps the API's raw log array as data.
     */
    @Test
    public void decodesAssetsAndLogs() {
        String source = "<script>const greeting = '你好';</script>\n";
        assertThat(client.decode(ByteString.fromString(source), 200, RAW_TEXT).path("data").asText()).isEqualTo(source);
        assertThat(client.decode(ByteString.fromString("[{\"event\":\"start\"},\"Log cut\"]"), 200, RAW_JSON)
                .path("data").size()).isEqualTo(2);
        assertThat(client.decode(ByteString.fromString("[]"), 200, RAW_JSON).path("data").isArray()).isTrue();
    }

    /**
     * Preserves valid UTF-8 control characters through JSON serialization and parsing.
     */
    @Test
    public void preservesTextControlCharacters() {
        String text = "before\0\f\u001bafter";
        var result = client.decode(ByteString.fromString(text), 200, RAW_TEXT);
        assertThat(Json.parse(Json.stringify(result)).path("data").asText()).isEqualTo(text);
    }

    /**
     * Rejects malformed UTF-8 and JSON, and preserves API errors for every response format.
     */
    @Test
    public void rejectsMalformedContentAndKeepsApiErrors() {
        assertThatThrownBy(() -> client.decode(ByteString.fromArray(new byte[]{(byte) 0xff}), 200, RAW_TEXT))
                .isInstanceOf(McpApiClient.ApiFailure.class).hasMessage("Asset is not valid UTF-8 text");
        assertThatThrownBy(() -> client.decode(ByteString.fromString("not JSON"), 200, RAW_JSON))
                .isInstanceOf(RuntimeException.class);
        String error = "{\"error\":{\"message\":\"Access denied\"}}";
        for (var format : McpApiClient.ResponseFormat.values()) {
            assertThat(client.decode(ByteString.fromString(error), 403, format)).isEqualTo(Json.parse(error));
        }
    }
    /** Error status handling must not depend on a valid JSON response body. */
    @Test
    public void acceptsNonJsonHttpErrorBodies() {
        for (int status : new int[]{401, 403, 404, 500}) {
            for (String body : new String[]{"", "<html>Error</html>", "null", "[]"}) {
                assertThat(client.decode(ByteString.fromString(body), status, ENVELOPED_JSON)
                        .path("error").path("message").asText()).isEqualTo("Request failed");
            }
        }
    }

    /** Request construction exceptions are returned as failed stages rather than thrown to the caller. */
    @Test
    public void capturesSynchronousConstructionFailures() {
        try (var common = mockStatic(Common.class)) {
            WSClient ws = mock(WSClient.class);
            IllegalStateException failure = new IllegalStateException("construction bug");
            when(ws.url(anyString())).thenThrow(failure);
            common.when(Common::getJatosHttpAddress).thenReturn("127.0.0.1");
            common.when(Common::getJatosHttpPort).thenReturn(9000);
            common.when(Common::getJatosUrlBasePath).thenReturn("/");
            var stage = new McpApiClient(ws, mock(Materializer.class), new json.common.DefaultJson()).get("/admin/token", "Bearer test");
            assertThatThrownBy(() -> stage.toCompletableFuture().join())
                    .isInstanceOf(CompletionException.class).hasCause(failure);
        }
    }

    /** Expected transport failures get controlled messages; programming errors retain their identity. */
    @Test
    public void distinguishesExpectedFailuresFromBugs() {
        assertThat(McpApiClient.classifyFailure(new CompletionException(new TimeoutException())))
                .isInstanceOf(McpApiClient.ApiFailure.class).hasMessage("JATOS API request timed out");
        assertThat(McpApiClient.classifyFailure(new org.apache.pekko.stream.StreamLimitReachedException(10)))
                .isInstanceOf(McpApiClient.ApiFailure.class).hasMessage("JATOS API response exceeds the configured size limit");
        var bug = new NullPointerException("private details");
        assertThat(McpApiClient.classifyFailure(bug)).isSameAs(bug);
    }

}
