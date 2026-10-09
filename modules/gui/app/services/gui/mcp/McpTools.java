package services.gui.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import play.libs.Json;

import java.util.List;

/**
 * The deliberately small, read-only API surface exposed to MCP clients.
 */
public final class McpTools {

    /** Tool-specific input failure that can be reported as actionable feedback to the model. */
    public static class InvalidToolInputException extends IllegalArgumentException {
        /** Describes how the caller must correct the tool arguments. */
        public InvalidToolInputException(String message) {
            super(message);
        }
    }

    /**
     * Tool metadata and API path template; a null parameter denotes an argument-free tool.
     *
     * @param name        unique tool name advertised by tools/list and accepted by tools/call
     * @param description explanation shown to MCP clients to help them select and use the tool
     * @param parameter   name of the required positive integer ID argument, or null if no arguments are needed
     * @param path        endpoint path relative to /jatos/api/v1, with a %s placeholder for the ID when the parameter
     *                    is non-null
     */
    private record Tool(String name, String description, String parameter, String path) {}

    private static final List<Tool> TOOLS = List.of(
            new Tool("list_studies",
                    "List accessible studies as compact summaries with IDs, UUIDs and titles. "
                            + "Use get_study to inspect a study's configuration.",
                    null,
                    "/studies/properties"),
            new Tool("get_study",
                    "Read a study's properties and configuration. A study contains ordered components "
                            + "and batches of participants.",
                    "study_id",
                    "/studies/%s/properties"),
            new Tool("list_components",
                    "Read a study's components in execution order. Each component is one "
                            + "part of the study, usually an HTML page.",
                    "study_id",
                    "/studies/%s/components"),
            new Tool("get_component",
                    "Read one component's properties and configuration.",
                    "component_id",
                    "/components/%s"),
            new Tool("list_batches",
                    "Read a study's batches and their settings. A batch groups study runs "
                            + "and controls participation settings.",
                    "study_id",
                    "/studies/%s/batches")
    );

    /**
     * Prevents instantiation of this static tool catalog.
     */
    private McpTools() {
    }

    /**
     * Builds tool discovery metadata, including descriptions, input schemas and read-only annotations.
     */
    public static ArrayNode list() {
        ArrayNode result = Json.newArray();
        for (Tool tool : TOOLS) {
            ObjectNode node = result.addObject().put("name", tool.name()).put("description", tool.description());
            ObjectNode schema = node.putObject("inputSchema").put("type", "object").put("additionalProperties", false);
            ObjectNode properties = schema.putObject("properties");
            ArrayNode required = schema.putArray("required");
            if (tool.parameter() != null) {
                properties.putObject(tool.parameter()).put("type", "integer").put("minimum", 1)
                        .put("description", "Positive numeric JATOS ID returned by the corresponding list tool.");
                required.add(tool.parameter());
            }
            node.putObject("annotations").put("readOnlyHint", true).put("destructiveHint", false)
                    .put("idempotentHint", true).put("openWorldHint", false);
        }
        return result;
    }

    /**
     * Validates a tool name and its arguments, then resolves the corresponding API-relative path. Throws
     * IllegalArgumentException for unknown tools or malformed argument containers, and
     * InvalidToolInputException for arguments that do not match the tool input schema.
     */
    public static String path(String name, JsonNode arguments) {
        Tool tool = TOOLS.stream().filter(t -> t.name().equals(name)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown tool: " + name));
        if (arguments == null) arguments = Json.newObject();
        if (!arguments.isObject()) throw new IllegalArgumentException("arguments must be an object");
        if (tool.parameter() == null) {
            if (!arguments.isEmpty()) throw new InvalidToolInputException("This tool takes no arguments");
            return tool.path();
        }
        JsonNode id = arguments.get(tool.parameter());
        if (arguments.size() != 1 || id == null || !id.isIntegralNumber() || !id.canConvertToLong() || id.asLong() < 1) {
            throw new InvalidToolInputException("Expected only a positive integer " + tool.parameter());
        }
        return tool.path().formatted(id.asLong());
    }

    /**
     * Reduces study lists to ID, UUID and title summaries; returns other tool payloads unchanged.
     */
    public static JsonNode output(String name, JsonNode data) {
        if (!"list_studies".equals(name) || !data.isArray()) return data;
        ArrayNode summaries = Json.newArray();
        for (JsonNode study : data) {
            ObjectNode summary = summaries.addObject();
            for (String field : List.of("id", "uuid", "title")) {
                if (study.has(field)) summary.set(field, study.get(field));
            }
        }
        return summaries;
    }
}
