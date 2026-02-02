package mcp.gui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class McpTool {

    private final ObjectNode raw;      // full JSON object from the tool config file
    private final String name;
    private final String description;
    private final JsonNode inputSchema;
    private final JsonNode metadata;   // e.g. { "openapi": { ... } }
    private final String path;
    private final String method;

    public McpTool(
            ObjectNode raw,
            String name,
            String description,
            JsonNode inputSchema,
            JsonNode metadata
    ) {
        this.raw = raw;
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
        this.metadata = metadata;
        this.path = null;
        this.method = null;
    }

    /**
     * Full original tool JSON (deep-copied to avoid accidental mutation).
     */
    public ObjectNode toJson() {
        return raw == null ? null : raw.deepCopy();
    }

    public JsonNode getMetadata() {
        return metadata;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public JsonNode getInputSchema() {
        return inputSchema;
    }

    public String getPath() {
        return path;
    }

    public String getMethod() {
        return method;
    }
}

