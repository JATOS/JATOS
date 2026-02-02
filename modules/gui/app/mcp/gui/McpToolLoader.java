package mcp.gui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import general.common.Common;
import play.Environment;
import play.libs.Json;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class McpToolLoader {

    private final Environment env;

    @Inject
    public McpToolLoader(Environment env) {
        this.env = env;
    }

    public List<McpTool> load() {
        List<String> paths = Common.getMcpToolPaths();
        List<McpTool> tools = new ArrayList<>();

        for (String p : paths) {
            try (InputStream in = env.resourceAsStream(p)) {
                if (in == null) {
                    throw new RuntimeException("Missing resource: " + p);
                }
                tools.add(parseTool(in, p));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return tools;
    }

    private static McpTool parseTool(InputStream in, String source) throws IOException {
        JsonNode rootNode = Json.mapper().readTree(in);
        if (!(rootNode instanceof ObjectNode)) {
            throw new IllegalArgumentException(
                    "Tool config must be a JSON object: " + source
            );
        }
        ObjectNode root = (ObjectNode) rootNode;

        String name = requireText(root, "name", source);
        String description = text(root, "description");

        JsonNode inputSchema = root.get("inputSchema");
        if (inputSchema == null) {
            throw new IllegalArgumentException(
                    "Missing inputSchema in " + source
            );
        }

        JsonNode metadata = root.get("metadata");

        return new McpTool(root, name, description, inputSchema, metadata);
    }

    private static String requireText(JsonNode node, String field, String source) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) {
            throw new IllegalArgumentException(
                    "Missing text field '" + field + "' in " + source
            );
        }
        return value.asText();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) {
            return "";
        }
        return value.asText();
    }
}
