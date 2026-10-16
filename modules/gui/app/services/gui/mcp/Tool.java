package services.gui.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import static services.gui.mcp.McpApiClient.ResponseFormat.ENVELOPED_JSON;

/**
 * Complete definition of a tool's discovery metadata and execution behavior.
 *
 * @param name        unique name advertised by tools/list and accepted by tools/call
 * @param description explanation that helps clients select and use the tool
 * @param params      ordered parameter definitions used for both discovery and validation
 * @param path        constructs an API-relative URL from validated, normalized arguments
 * @param format      describes how to decode the API response
 * @param postProcess transforms successful API data into the tool's output
 * @param method      HTTP verb
 * @param readOnly    whether the operation only reads data, regardless of its HTTP verb
 */
record Tool(String name, String description, List<ToolParam> params,
            Function<Map<String, String>, String> path, McpApiClient.ResponseFormat format,
            UnaryOperator<JsonNode> postProcess, String method, boolean readOnly) {
    /**
     * Defaults existing operations to read-only for GET and mutating for other verbs.
     */
    Tool(String name, String description, List<ToolParam> params,
         Function<Map<String, String>, String> path, McpApiClient.ResponseFormat format,
         UnaryOperator<JsonNode> postProcess, String method) {
        this(name, description, params, path, format, postProcess, method, method.equals("GET"));
    }

    /**
     * Standard read tools return the API data unchanged.
     */
    Tool(String name, String description, List<ToolParam> params,
         Function<Map<String, String>, String> path) {
        this(name, description, params, path, ENVELOPED_JSON, UnaryOperator.identity());
    }

    /**
     * Read tools can select a different response format or output transformation.
     */
    Tool(String name, String description, List<ToolParam> params,
         Function<Map<String, String>, String> path, McpApiClient.ResponseFormat format,
         UnaryOperator<JsonNode> postProcess) {
        this(name, description, params, path, format, postProcess, "GET");
    }

    /**
     * Write tools send properties as JSON.
     */
    Tool(String name, String description, List<ToolParam> params,
         Function<Map<String, String>, String> path, String method) {
        this(name, description, params, path, ENVELOPED_JSON, UnaryOperator.identity(), method);
    }

    /**
     * Builds the discovery schema and operation annotations for this tool.
     */
    ObjectNode describe() {
        ObjectNode node = JsonNodeFactory.instance.objectNode().put("name", name()).put("description", description());
        ObjectNode schema = node.putObject("inputSchema").put("type", "object").put("additionalProperties", false);
        ObjectNode properties = schema.putObject("properties");
        ArrayNode required = schema.putArray("required");
        for (ToolParam param : params()) {
            param.describe(properties.putObject(param.name()));
            if (param.required()) required.add(param.name());
        }
        node.putObject("annotations").put("readOnlyHint", readOnly)
                .put("destructiveHint", method().equals("PATCH"))
                .put("idempotentHint", readOnly).put("openWorldHint", false);
        return node;
    }

    /**
     * Validates arguments and constructs the API operation for this tool.
     */
    ResolvedCall resolve(JsonNode arguments) {
        if (arguments == null) arguments = JsonNodeFactory.instance.objectNode();
        if (!arguments.isObject()) throw new InvalidToolRequestException("arguments must be an object");
        var allowed = params().stream().map(ToolParam::name).collect(Collectors.toSet());
        arguments.fieldNames().forEachRemaining(field -> {
            if (!allowed.contains(field)) throw new InvalidToolInputException("Unexpected argument: " + field);
        });
        Map<String, String> normalized = new HashMap<>();
        for (ToolParam param : params()) {
            normalized.put(param.name(), param.validate(arguments.get(param.name())));
        }
        JsonNode body = arguments.get("properties");
        return new ResolvedCall(path().apply(normalized), format(), postProcess(), method(), body);
    }

}
