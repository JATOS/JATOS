package services.gui.mcp;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Test;
import play.libs.Json;
import play.mvc.Http;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static play.test.Helpers.fakeRequest;
import static services.gui.mcp.McpProtocol.*;

/**
 * Checks protocol validation independently of API authentication and tool execution.
 */
@SuppressWarnings("DataFlowIssue")
public class McpProtocolTest {
    /**
     * Creates modern metadata for a read-only tool invocation.
     */
    private ObjectNode message() {
        ObjectNode message = Json.newObject().put("jsonrpc", "2.0").put("id", 42).put("method", "tools/call");
        message.putObject("params").put("name", "list_studies").putObject("_meta")
                .put(VERSION_KEY, VERSION_2026_07_28).putObject(CAPABILITIES_KEY);
        return message;
    }

    /**
     * Creates matching HTTP headers for the modern test message.
     */
    private Http.RequestBuilder request() {
        return fakeRequest().header("MCP-Protocol-Version", VERSION_2026_07_28)
                .header("Mcp-Method", "tools/call").header("Mcp-Name", "list_studies");
    }

    /**
     * Checks missing metadata is rejected as invalid params instead of silently using legacy semantics.
     */
    @Test
    public void requiresMetadataOnEveryModernRequest() {
        for (String field : new String[]{VERSION_KEY, CAPABILITIES_KEY}) {
            ObjectNode message = message();
            ((ObjectNode) message.path("params").path("_meta")).remove(field);
            assertThat(validateRequestMetadata(request().build(), message).path("error").path("code").asInt()).isEqualTo(-32602);
        }
    }

    /**
     * Checks all required mirrored headers, including duplicate values, are validated.
     */
    @Test
    public void rejectsMissingMismatchedAndDuplicateHeaders() {
        for (String header : new String[]{"MCP-Protocol-Version", "Mcp-Method", "Mcp-Name"}) {
            for (String value : new String[]{"", "wrong", " padded "}) {
                assertThat(validateRequestMetadata(request().header(header, value).build(), message())
                        .path("error").path("code").asInt()).isEqualTo(-32020);
            }
        }
        assertThat(validateRequestMetadata(fakeRequest().build(), message()).path("error").path("code").asInt()).isEqualTo(-32020);
        assertThat(validateRequestMetadata(request().header("Mcp-Name", java.util.List.of("list_studies", "list_studies")).build(), message())
                .path("error").path("code").asInt()).isEqualTo(-32020);
    }

    /**
     * Checks unsupported versions report the requested version and all supported alternatives.
     */
    @Test
    public void unsupportedVersionRetainsIdAndOffersAlternatives() {
        ObjectNode message = message();
        ((ObjectNode) message.path("params").path("_meta")).put(VERSION_KEY, "2099-01-01");
        ObjectNode error = validateRequestMetadata(request().header("MCP-Protocol-Version", "2099-01-01").build(), message);
        assertThat(error.path("id").asInt()).isEqualTo(42);
        assertThat(error.path("error").path("code").asInt()).isEqualTo(-32022);
        assertThat(error.path("error").path("data").path("requested").asText()).isEqualTo("2099-01-01");
        assertThat(error.path("error").path("data").path("supported")).isEqualTo(Json.toJson(VERSIONS));
    }

    /**
     * Checks Base64 header decoding supports Unicode and rejects invalid Base64 and UTF-8.
     */
    @Test
    public void validatesEncodedNames() {
        ObjectNode message = message();
        ((ObjectNode) message.get("params")).put("name", "工具");
        String encoded = "=?base64?" + Base64.getEncoder().encodeToString("工具".getBytes(StandardCharsets.UTF_8)) + "?=";
        assertThat(validateRequestMetadata(request().header("Mcp-Name", encoded).build(), message)).isNull();
        for (String invalid : new String[]{"=?base64?%%%?=", "=?base64?/w==?="}) {
            assertThat(validateRequestMetadata(request().header("Mcp-Name", invalid).build(), message)
                    .path("error").path("code").asInt()).isEqualTo(-32020);
        }
    }

    /**
     * Checks clients predating version headers retain the March 2025 default.
     */
    @Test
    public void acceptsLegacyRequestsWithoutModernMetadata() {
        ObjectNode message = Json.newObject().put("jsonrpc", "2.0").put("id", 1).put("method", "tools/list");
        assertThat(version(fakeRequest().build())).isEqualTo(VERSION_2025_03_26);
        assertThat(validateRequestMetadata(fakeRequest().build(), message)).isNull();
        assertThat(validateRequestMetadata(fakeRequest().header("Mcp-Method", "tools/list").build(), message)).isNull();
        for (String version : VERSIONS.subList(1, VERSIONS.size())) {
            assertThat(validateRequestMetadata(fakeRequest().header("MCP-Protocol-Version", version).build(), message)).isNull();
        }
    }
}
