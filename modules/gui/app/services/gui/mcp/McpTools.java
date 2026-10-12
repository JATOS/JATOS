package services.gui.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static services.gui.mcp.McpApiClient.ResponseFormat.*;

import java.util.stream.Collectors;

/**
 * The deliberately small, read-only API surface exposed to MCP clients.
 */
public final class McpTools {

    /**
     * A malformed tool request, distinct from invalid inputs to a known tool.
     */
    public static class InvalidToolRequestException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;

        /**
         * Describes the malformed request without exposing backend details.
         */
        public InvalidToolRequestException(String message) {
            super(message);
        }
    }

    /**
     * Tool-specific input failure that can be reported as actionable feedback to the model.
     */
    public static class InvalidToolInputException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;

        /**
         * Describes how the caller must correct the tool arguments.
         */
        public InvalidToolInputException(String message) {
            super(message);
        }
    }

    private static final int DEFAULT_LOG_ENTRIES = 100;
    private static final int MAX_LOG_ENTRIES = 1000;

    /**
     * A parameter's discovery schema and validation share one definition.
     *
     * @param name      argument key accepted from the client
     * @param required  whether the discovery schema requires this argument
     * @param schema    writes the property's JSON schema
     * @param normalize validates the value (null when absent) and returns URL-ready text, including optional defaults
     */
    private record ToolParam(String name, boolean required, Consumer<ObjectNode> schema,
                             Function<JsonNode, String> normalize) {
        /**
         * Describes this parameter in the tool's input schema.
         */
        void describe(ObjectNode propertySchema) {
            schema.accept(propertySchema);
        }

        /**
         * Validates and normalizes an argument, or throws InvalidToolInputException.
         */
        String validate(JsonNode value) {
            return normalize.apply(value);
        }
    }

    /**
     * Complete definition of a tool's discovery metadata and execution behavior.
     *
     * @param name        unique name advertised by tools/list and accepted by tools/call
     * @param description explanation that helps clients select and use the tool
     * @param params      ordered parameter definitions used for both discovery and validation
     * @param path        constructs an API-relative URL from validated, normalized arguments
     * @param format      describes how to decode the API response
     * @param postProcess transforms successful API data into the tool's output
     */
    private record Tool(String name, String description, List<ToolParam> params,
                        Function<Map<String, String>, String> path, McpApiClient.ResponseFormat format,
                        UnaryOperator<JsonNode> postProcess) {
        /** Most tools return the API's data unchanged in its standard JSON envelope. */
        private Tool(String name, String description, List<ToolParam> params,
                     Function<Map<String, String>, String> path) {
            this(name, description, params, path, ENVELOPED_JSON, UnaryOperator.identity());
        }
    }

    /**
     * Validated execution plan that lets the controller call a tool without knowing its name.
     *
     * @param path        API-relative URL with normalized arguments
     * @param format      decoding mode for the successful API response
     * @param postProcess transformation applied to successful API data
     */
    public record ResolvedCall(String path, McpApiClient.ResponseFormat format,
                               UnaryOperator<JsonNode> postProcess) {}

    private static final ToolParam STUDY_ID = positiveId("study_id");
    private static final ToolParam BATCH_ID = positiveId("batch_id");
    private static final ToolParam COMPONENT_ID = positiveId("component_id");
    private static final ToolParam ENTRY_LIMIT = new ToolParam(
            "entry_limit",
            false,
            schema -> schema.put("type", "integer")
                    .put("minimum", 1)
                    .put("maximum", MAX_LOG_ENTRIES)
                    .put("default", DEFAULT_LOG_ENTRIES)
                    .put("description", "Maximum number of recent log entries to read."),
            value -> Integer.toString(studyLogEntryLimit(value)));
    private static final ToolParam FILEPATH = new ToolParam(
            "filepath",
            true,
            schema -> schema.put("type", "string")
                    .put("minLength", 1)
                    .put("description", "Unencoded path relative to the study assets directory; no traversal or URL."),
            McpTools::encodeAssetPath);

    // Keep discovery in the same concern order as the API endpoints.
    private static final List<Tool> TOOLS = List.of(
            // Batches
            new Tool("list_batches", "Read a study's batches and their participation settings.", List.of(STUDY_ID),
                    args -> "/studies/%s/batches".formatted(args.get("study_id"))),
            new Tool("get_batch", "Read one batch's properties and participation settings.", List.of(BATCH_ID),
                    args -> "/batches/%s".formatted(args.get("batch_id"))),
            new Tool("get_batch_session", "Read shared batch session data and its version.", List.of(BATCH_ID),
                    args -> "/batches/%s/session".formatted(args.get("batch_id"))),
            // Components
            new Tool("list_components", "Read a study's components in execution order.", List.of(STUDY_ID),
                    args -> "/studies/%s/components".formatted(args.get("study_id"))),
            new Tool("get_component", "Read one component's properties and configuration.", List.of(COMPONENT_ID),
                    args -> "/components/%s".formatted(args.get("component_id"))),
            // Studies
            new Tool("list_studies", "List accessible studies as compact summaries with IDs, UUIDs and titles. "
                    + "Use get_study to inspect a study's configuration.", List.of(),
                    args -> "/studies/properties", ENVELOPED_JSON, McpTools::summarizeStudies),
            new Tool("get_study", "Read a study's properties and configuration.", List.of(STUDY_ID),
                    args -> "/studies/%s/properties".formatted(args.get("study_id"))),
            new Tool("get_study_log", "Read recent study log entries, newest first. Defaults to %d entries; maximum %d."
                    .formatted(DEFAULT_LOG_ENTRIES, MAX_LOG_ENTRIES), List.of(STUDY_ID, ENTRY_LIMIT),
                    args -> "/studies/%s/log?download=false&entryLimit=%s"
                            .formatted(args.get("study_id"), args.get("entry_limit")), RAW_JSON, UnaryOperator.identity()),
            // Study assets
            new Tool("list_study_assets", "List the study's asset directory tree to find HTML, JavaScript and other files.",
                    List.of(STUDY_ID), args -> "/studies/%s/assets/structure".formatted(args.get("study_id"))),
            new Tool("read_study_asset", "Read a UTF-8 text asset. Use an unencoded relative filepath from list_study_assets. "
                    + "Any filename or extension is accepted. The entire file must decode as UTF-8 and fit "
                    + "within the configured response size limit.", List.of(STUDY_ID, FILEPATH),
                    args -> "/studies/%s/assets/%s".formatted(args.get("study_id"), args.get("filepath")),
                    RAW_TEXT, UnaryOperator.identity())
    );
    private static final Map<String, Tool> TOOLS_BY_NAME = TOOLS.stream()
            .collect(Collectors.toUnmodifiableMap(Tool::name, Function.identity()));

    /**
     * Defines a required positive JATOS ID with matching discovery and validation rules.
     */
    private static ToolParam positiveId(String name) {
        return new ToolParam(name, true,
                schema -> schema.put("type", "integer").put("minimum", 1)
                        .put("description", "Positive numeric JATOS ID returned by the corresponding list tool."),
                value -> {
                    if (value == null || !value.isIntegralNumber() || !value.canConvertToLong() || value.asLong() < 1) {
                        throw new InvalidToolInputException("Expected a positive integer " + name);
                    }
                    return Long.toString(value.asLong());
                });
    }

    /**
     * Prevents instantiation of this static tool catalog.
     */
    private McpTools() {
    }

    /**
     * Builds tool discovery metadata, including descriptions, input schemas and read-only annotations.
     */
    public static ArrayNode list() {
        ArrayNode result = JsonNodeFactory.instance.arrayNode();
        for (Tool tool : TOOLS) {
            ObjectNode node = result.addObject().put("name", tool.name()).put("description", tool.description());
            ObjectNode schema = node.putObject("inputSchema").put("type", "object").put("additionalProperties", false);
            ObjectNode properties = schema.putObject("properties");
            ArrayNode required = schema.putArray("required");
            for (ToolParam param : tool.params()) {
                param.describe(properties.putObject(param.name()));
                if (param.required()) required.add(param.name());
            }
            node.putObject("annotations").put("readOnlyHint", true).put("destructiveHint", false)
                    .put("idempotentHint", true).put("openWorldHint", false);
        }
        return result;
    }

    /**
     * Resolves a tool into its API path, decoding mode and output transformation. Unknown tools or malformed argument
     * containers raise InvalidToolRequestException; schema violations raise InvalidToolInputException.
     */
    public static ResolvedCall resolveCall(String name, JsonNode arguments) {
        Tool tool = TOOLS_BY_NAME.get(name);
        if (tool == null) throw new InvalidToolRequestException("Unknown tool: " + name);
        if (arguments == null) arguments = JsonNodeFactory.instance.objectNode();
        if (!arguments.isObject()) throw new InvalidToolRequestException("arguments must be an object");
        var allowed = tool.params().stream().map(ToolParam::name).collect(Collectors.toSet());
        arguments.fieldNames().forEachRemaining(field -> {
            if (!allowed.contains(field)) throw new InvalidToolInputException("Unexpected argument: " + field);
        });
        Map<String, String> normalized = new HashMap<>();
        for (ToolParam param : tool.params()) {
            normalized.put(param.name(), param.validate(arguments.get(param.name())));
        }
        return new ResolvedCall(tool.path().apply(normalized), tool.format(), tool.postProcess());
    }

    /**
     * Validates the optional log limit, preventing unbounded log requests.
     */
    private static int studyLogEntryLimit(JsonNode limit) {
        if (limit == null) return DEFAULT_LOG_ENTRIES;
        if (!limit.isIntegralNumber() || !limit.canConvertToInt() || limit.intValue() < 1 || limit.intValue() > MAX_LOG_ENTRIES) {
            throw new InvalidToolInputException("entry_limit must be an integer between 1 and " + MAX_LOG_ENTRIES);
        }
        return limit.intValue();
    }

    /**
     * Validates the MCP filepath argument and encodes each segment for the API URL. Filesystem access checks remain the
     * responsibility of the JATOS API.
     */
    private static String encodeAssetPath(JsonNode value) {
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new InvalidToolInputException("filepath must be a non-empty relative path");
        }
        String path = value.asText();
        // Reject percent escapes too: the API decodes paths again after route matching.
        if (path.contains("\\") || path.contains("%") || path.contains(":")
                || path.codePoints().anyMatch(Character::isISOControl)) {
            throw new InvalidToolInputException("filepath must be an unencoded relative path without control characters");
        }
        String[] segments = path.split("/", -1);
        if (Arrays.stream(segments).anyMatch(segment -> segment.isEmpty() || segment.equals(".") || segment.equals(".."))) {
            throw new InvalidToolInputException("filepath must not contain empty, dot or parent path segments");
        }
        return Arrays.stream(segments).map(segment -> URLEncoder.encode(segment, StandardCharsets.UTF_8)
                .replace("+", "%20")).collect(Collectors.joining("/"));
    }

    /**
     * Builds compact study summaries containing ID, UUID and title; preserves non-array API data unchanged.
     */
    public static JsonNode summarizeStudies(JsonNode data) {
        if (!data.isArray()) return data;
        ArrayNode summaries = JsonNodeFactory.instance.arrayNode();
        for (JsonNode study : data) {
            ObjectNode summary = summaries.addObject();
            for (String field : List.of("id", "uuid", "title")) {
                if (study.has(field)) summary.set(field, study.get(field));
            }
        }
        return summaries;
    }
}
