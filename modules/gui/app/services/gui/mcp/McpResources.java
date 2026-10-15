package services.gui.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import general.common.Common;
import utils.common.StringUtils;

import static services.gui.mcp.McpProtocolUtils.*;

/**
 * Read-only documentation resources exposed by the JATOS MCP server.
 */
public final class McpResources {

    public static final String API_USAGE_URI = "jatos://docs/api-usage";
    private static final String MIME_TYPE = "text/markdown";

    private McpResources() {
    }

    /**
     * Lists the single API guide; the resource catalog does not require pagination.
     */
    public static ObjectNode list(JsonNode id, JsonNode params, String version) {
        if (params.has("cursor")) return error(id, INVALID_PARAMS, "Resource discovery is not paginated; omit cursor");
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        result.putArray("resources").addObject()
                .put("uri", API_USAGE_URI)
                .put("name", "JATOS API usage")
                .put("description", "API authentication, OpenAPI reference, and direct HTTP transfers for study archives and assets.")
                .put("mimeType", MIME_TYPE);
        return success(id, result, version);
    }

    /**
     * Reads the API guide without exposing credentials or fetching external documentation.
     */
    public static ObjectNode read(JsonNode id, JsonNode params, String version) {
        if (!params.path("uri").isTextual()) return error(id, INVALID_PARAMS, "Resource URI is required");
        if (!API_USAGE_URI.equals(params.path("uri").asText())) {
            return error(id, RESOURCE_NOT_FOUND, "Resource not found");
        }
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        result.putArray("contents").addObject().put("uri", API_USAGE_URI)
                .put("mimeType", MIME_TYPE).put("text", apiUsage());
        return success(id, result, version);
    }

    /**
     * Builds deployment-specific instructions; all endpoint paths are relative to the public server origin.
     */
    private static String apiUsage() {
        return """
                # Using the JATOS HTTP API
                
                Use the same public scheme and host as your MCP connection. The API base path on that host is:
                `%s`
                Preserve the deployment prefix. Do not use the server's internal loopback address.
                
                ## OpenAPI specification
                Fetch this installation's OpenAPI specification with GET `%s` on the same public
                scheme and host as your MCP connection.
                The YAML's paths already include `/jatos/api/v1`; prepend only the deployment prefix,
                not the API base path again. This resource provides guidance, not the full specification.
                
                ## Authentication and responses
                Send `Authorization: Bearer <personal-api-token>` with API requests. Obtain the token
                securely from the user/environment; credentials configured for MCP may not be available
                to your HTTP tool. Never include credentials in URLs or model-visible output.
                The API also supports signed-in JATOS sessions. Token permissions and study membership apply.
                JSON endpoints return API envelopes with `data`, `message`, or `error`. Check the HTTP
                status and envelope; downloads return file bytes instead. Do not automatically retry
                writes after a timeout: inspect the resulting state first.
                
                ## Direct HTTP file transfers
                These operations require an HTTP-capable client and access to the source/destination file.
                Reading this resource does not provide those capabilities. Stream files from/to disk;
                do not put large files or their Base64 representation into model context or MCP messages.
                The following endpoint paths are relative to the API base path above:
                
                - Import a study: POST `/studies` with `Content-Type: application/zip` and the raw
                  `.jzip` file body (for example curl's `--data-binary @study.jzip`). Multipart upload
                  is also supported with file field `study`. A `.jzip` must be a valid JATOS study
                  archive, not merely a ZIP of assets. Importing an existing study UUID can overwrite
                  data; consult the specification and choose `keepProperties`, `keepAssets`,
                  `keepCurrentAssetsName`, and `renameAssets` deliberately.
                - Export a study: GET `/studies/{id}` and save the response bytes as a `.jzip` file.
                  Export requires USER permission. To inspect JSON properties instead, use
                  GET `/studies/{id}/properties`.
                - Upload an asset: POST `/studies/{id}/assets/{filepath}` as multipart/form-data,
                  with file field `studyAssetsFile`. Let the HTTP library generate the multipart
                  boundary. The filepath is relative to the study assets directory; URL-encode each
                  path segment. Existing files are replaced. Binary assets are supported by this API.
                - Download an asset: GET `/studies/{id}/assets/{filepath}` and save the raw response.
                
                Configured file limits on this installation: study archives %s; study assets %s.
                Reverse proxies may impose additional limits. Direct API transfers do not pass through
                the MCP request/response size limits. For other operations and exact schemas, consult OpenAPI.
                """.formatted(Common.getJatosUrlBasePath() + "jatos/api/v1",
                Common.getJatosUrlBasePath() + "openapi.yaml",
                StringUtils.humanReadableByteCount(Common.getStudyArchiveMaxSize()),
                StringUtils.humanReadableByteCount(Common.getStudyAssetsMaxFileSize()));
    }
}
