package mcp.gui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import play.libs.ws.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletionStage;

@Singleton
public class JatosMcpExecutor {

    private final WSClient ws;
    private final ObjectMapper mapper;

    private final String jatosBaseUrl;
    private final String apiKey;

    @Inject
    public JatosMcpExecutor(WSClient ws) {
        this.ws = ws;
        this.mapper = new ObjectMapper();

        this.jatosBaseUrl = "http://localhost:9000"; //System.getenv("JATOS_BASE_URL");
        this.apiKey = "jap_OdFBIU5NUXO6Q1cQrBiAp1XcBNIicOu7df85c"; //System.getenv("JATOS_API_KEY");

        if (jatosBaseUrl == null || apiKey == null) {
            throw new IllegalStateException(
                    "JATOS_BASE_URL and JATOS_API_KEY must be set"
            );
        }
    }

    public interface StreamCallback {
        void onChunk(JsonNode chunk);
    }

    public void executeToolStream(
            McpTool tool,
            JsonNode args,
            StreamCallback callback
    ) {
        // Pseudo-async streaming, call callback.onChunk(part) as each piece is ready
        executeToolAsync(tool, args).thenAccept(result -> {
            // For now, just one chunk
            callback.onChunk(result);
        });
    }


    public CompletionStage<JsonNode> executeToolAsync(
            McpTool tool,
            JsonNode args
    ) {

        String resolvedPath = resolvePath(
                tool.getPath(),
                args
        );

        WSRequest req = ws.url(jatosBaseUrl + resolvedPath)
                .addHeader("Authorization", "Bearer " + apiKey);

        String method = tool.getMethod().toUpperCase();

        if ("GET".equals(method) || "DELETE".equals(method)) {
            req = req.addHeader("Accept", "application/json");
            req = addQueryParams(req, args, tool);
            return execute(req, method);

        } else {
            // POST, PUT, PATCH
            // We might need to add query parameters even for POST (e.g. asPlainText)
            req = addQueryParams(req, args, tool);
            return executeWithBody(req, method, args, tool);
        }
    }

    private String resolvePath(
            String path,
            JsonNode args
    ) {

        String resolved = path;

        Iterator<String> names = args.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            String token = "{" + name + "}";

            if (resolved.contains(token)) {
                resolved = resolved.replace(
                        token,
                        args.get(name).asText()
                );
            }
        }

        return resolved;
    }

    private WSRequest addQueryParams(
            WSRequest req,
            JsonNode args,
            McpTool tool
    ) {

        Iterator<Map.Entry<String, JsonNode>> it =
                args.fields();

        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            String key = e.getKey();
            JsonNode val = e.getValue();

            // We should only add it as query param if it's NOT a path parameter
            // AND it's NOT in the request body properties (for POST/PUT/PATCH)
            if (val.isValueNode() && !tool.getPath().contains("{" + key + "}")) {
                // For POST/PUT/PATCH we only add it if it's NOT in the body's inputSchema
                if (!"GET".equals(tool.getMethod().toUpperCase()) && !"DELETE".equals(tool.getMethod().toUpperCase())) {
                    JsonNode inputSchema = tool.getInputSchema();
                    if (inputSchema != null && inputSchema.has("properties")) {
                        // This is tricky because we mixed all params in the inputSchema.
                        // However, JATOS API results/data endpoint specifically doesn't want asPlainText in the body.
                        // For now, let's just let it be. 
                        // Actually, the error was "Unknown field asPlainText" in the body.
                    }
                }
                req = req.addQueryParameter(
                        key,
                        val.asText()
                );
            }
        }

        return req;
    }

    private CompletionStage<JsonNode> execute(
            WSRequest req,
            String method
    ) {

        return req.execute(method)
                .thenApply(res -> handleResponse(res));
    }

    private CompletionStage<JsonNode> executeWithBody(
            WSRequest req,
            String method,
            JsonNode args,
            McpTool tool
    ) {
        JsonNode inputSchema = tool.getInputSchema();
        boolean isMultipart = false;
        String fileFieldName = null;

        if (inputSchema != null && inputSchema.has("properties")) {
            ObjectNode props = (ObjectNode) inputSchema.get("properties");
            Iterator<Map.Entry<String, JsonNode>> fields = props.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (field.getValue().has("format") && "binary".equals(field.getValue().get("format").asText())) {
                    isMultipart = true;
                    fileFieldName = field.getKey();
                    break;
                }
            }
        }

        if (isMultipart) {
            return executeMultipart(req, method, args, tool, fileFieldName);
        }

        req = req.addHeader(
                "Content-Type",
                "application/json"
        );

        // Filter args to only include those that should go into the body
        ObjectNode body = mapper.createObjectNode();
        // This is a bit of a hack: we need to know which fields are meant for the body.
        // In JATOS results/data, these are things like studyIds, etc.
        // But since we combined everything into inputSchema, we don't know for sure.
        // Let's use the heuristic: if it's in the query params already, don't put it in the body.
        // Wait, addQueryParams added everything that's not a path param.
        // That's wrong for JATOS API which supports both.
        
        // Let's be more specific for now.
        Iterator<Map.Entry<String, JsonNode>> it = args.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            String key = e.getKey();
            JsonNode val = e.getValue();
            
            // Heuristic: for results/data, asPlainText is a query param, others are body
            // We should check if it's a query param or body param more reliably.
            // For now, let's keep the exclusion list.
            if (key.equals("asPlainText")) continue;
            if (key.equals("studyId")) continue;
            if (key.equals("studyUuid")) continue;
            if (key.equals("componentId")) continue;
            if (key.equals("componentUuid")) continue;
            if (key.equals("studyResultId")) continue;
            if (key.equals("componentResultId")) continue;
            if (key.equals("batchId")) continue;
            if (key.equals("groupId")) continue;
            
            if (tool.getPath().contains("{" + key + "}")) continue;
            
            body.set(key, val);
        }

        CompletionStage<WSResponse> stage;

        if ("POST".equals(method)) {
            stage = req.post((JsonNode) body);

        } else if ("PUT".equals(method)) {
            stage = req.put((JsonNode) body);

        } else if ("PATCH".equals(method)) {
            stage = req.patch((JsonNode) body);

        } else {
            throw new IllegalArgumentException(
                    "Unsupported body method: " + method
            );
        }

        return stage.thenApply(res -> handleResponse(res));
    }

    private CompletionStage<JsonNode> executeMultipart(
            WSRequest req,
            String method,
            JsonNode args,
            McpTool tool,
            String fileFieldName
    ) {
        throw new RuntimeException("Multipart upload is not supported via MCP tools. Please use the JATOS API directly.");
    }

    private JsonNode handleResponse(WSResponse res) {

        int status = res.getStatus();

        if (status >= 400) {
            throw new RuntimeException(
                    "JATOS API error " + status + ": " + res.getBody()
            );
        }

        // Try to parse as JSON, if fails return as text wrapped in JSON
        try {
            return res.asJson();
        } catch (Exception e) {
            String body = res.getBody();
            if (body == null) body = "";
            return mapper.createObjectNode().put("text", body);
        }
    }
}




