package services.gui.mcp;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.function.UnaryOperator;

/**
 * Validated API operation, independent of the tool's public name.
 *
 * @param path        API-relative destination URL
 * @param format      response decoding mode
 * @param postProcess transformation applied to successful response data
 * @param method      HTTP method, or IMPORT for an archive upload
 * @param body        JSON properties or Base64 archive text; null for reads
 */
public record ResolvedCall(String path, McpApiClient.ResponseFormat format, UnaryOperator<JsonNode> postProcess,
                           String method, JsonNode body) {}
