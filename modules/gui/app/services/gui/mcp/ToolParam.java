package services.gui.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A parameter's discovery schema and validation share one definition.
 *
 * @param name      argument key accepted from the client
 * @param required  whether the discovery schema requires this argument
 * @param schema    writes the property's JSON schema
 * @param normalize validates the value (null when absent) and returns URL-ready text, including optional defaults
 */
record ToolParam(String name, boolean required, Consumer<ObjectNode> schema,
                 Function<JsonNode, String> normalize) {

    static final int DEFAULT_STUDY_LOG_ENTRIES = 100;
    static final int MAX_STUDY_LOG_ENTRIES = 1000;

    static final List<String> STUDY_FIELDS = List.of("title", "description", "active", "groupStudy",
            "linearStudy", "allowPreview", "dirName", "comments", "studyInput", "endRedirectUrl", "studyEntryMsg");
    static final List<String> COMPONENT_FIELDS = List.of("title", "htmlFilePath", "reloadable", "active",
            "comments", "componentInput");
    static final List<String> BATCH_FIELDS = List.of("title", "active", "maxActiveMembers", "maxTotalMembers",
            "maxTotalWorkers", "allowedWorkerTypes", "comments", "batchInput");

    static final ToolParam STUDY_ID = positiveId("study_id");
    static final ToolParam BATCH_ID = positiveId("batch_id");
    static final ToolParam COMPONENT_ID = positiveId("component_id");
    static final ToolParam ENTRY_LIMIT = new ToolParam(
            "entry_limit",
            false,
            schema -> schema.put("type", "integer")
                    .put("minimum", 1)
                    .put("maximum", MAX_STUDY_LOG_ENTRIES)
                    .put("default", DEFAULT_STUDY_LOG_ENTRIES)
                    .put("description", "Maximum number of recent log entries to read."),
            value -> Integer.toString(studyLogEntryLimit(value)));
    static final ToolParam FILEPATH = new ToolParam(
            "filepath",
            true,
            schema -> schema.put("type", "string")
                    .put("minLength", 1)
                    .put("description", "Unencoded path relative to the study assets directory; no traversal or URL."),
            ToolParam::encodeAssetPath);

    static final ToolParam ARCHIVE = new ToolParam("archive_base64", true,
            schema -> schema.put("type", "string")
                    .put("minLength", 1)
                    .put("contentEncoding", "base64")
                    .put("description", "Base64-encoded JATOS .jzip/ZIP archive; subject to jatos.mcp.maxRequestSize."),
            ToolParam::validateArchive);

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

    /**
     * Defines a required positive JATOS ID with matching discovery and validation rules.
     */
    static ToolParam positiveId(String name) {
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
     * Describes JSON API properties and rejects accidental IDs, unknown fields and missing creation titles.
     */
    static ToolParam properties(List<String> fields, boolean create) {
        return new ToolParam("properties", true, schema -> {
            schema.put("type", "object").put("additionalProperties", false)
                    .put("description", "JATOS API properties. Omitted fields are unchanged on update; explicit null clears nullable fields.");
            ObjectNode props = schema.putObject("properties");
            for (String field : fields) describeProperty(props.putObject(field), field);
            if (create) schema.putArray("required").add("title");
        }, value -> {
            if (value == null || !value.isObject()) throw new InvalidToolInputException("properties must be an object");
            value.fieldNames().forEachRemaining(field -> {
                if (!fields.contains(field)) throw new InvalidToolInputException("Unexpected property: " + field);
            });
            if (create && (!value.path("title").isTextual() || value.path("title").asText().isBlank())) {
                throw new InvalidToolInputException("properties.title must be a non-empty string");
            }
            // The API owns field type validation and domain rules; retain the original JSON values in the request body.
            return "";
        });
    }

    /**
     * Documents the API's editable property types without duplicating its domain validation.
     */
    private static void describeProperty(ObjectNode schema, String field) {
        switch (field) {
            case "active", "groupStudy", "linearStudy", "allowPreview", "reloadable", "htmlFileRename" ->
                    schema.put("type", "boolean");
            case "maxActiveMembers", "maxTotalMembers", "maxTotalWorkers" -> {
                schema.putArray("type").add("integer").add("null");
                if (field.equals("maxTotalWorkers")) schema.put("minimum", 1);
            }
            case "studyInput", "componentInput", "batchInput" -> {
                schema.putArray("type").add("object").add("string").add("null");
                schema.put("description", "JSON input data as an object or JSON string, or null to clear.");
            }
            case "allowedWorkerTypes" -> {
                schema.put("type", "array").putObject("items").put("type", "string");
            }
            case "title", "dirName" -> schema.put("type", "string");
            default -> schema.putArray("type").add("string").add("null");
        }
    }

    /**
     * Defines an optional boolean API query parameter with an explicit default.
     */
    static ToolParam booleanParam(String name, boolean defaultValue, String description) {
        return new ToolParam(name, false,
                schema -> schema.put("type", "boolean").put("default", defaultValue).put("description", description),
                value -> {
                    if (value == null) return Boolean.toString(defaultValue);
                    if (!value.isBoolean()) throw new InvalidToolInputException(name + " must be a boolean");
                    return value.asText();
                });
    }

    /**
     * Validates the optional log limit, preventing unbounded log requests.
     */
    private static int studyLogEntryLimit(JsonNode limit) {
        if (limit == null) return DEFAULT_STUDY_LOG_ENTRIES;
        if (!limit.isIntegralNumber() || !limit.canConvertToInt() || limit.intValue() < 1 || limit.intValue() > MAX_STUDY_LOG_ENTRIES) {
            throw new InvalidToolInputException("entry_limit must be an integer between 1 and " + MAX_STUDY_LOG_ENTRIES);
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
     * Validates a Base64 archive before it is passed to the API client.
     */
    private static String validateArchive(JsonNode value) {
        if (value == null || !value.isTextual() || value.asText().isEmpty()) {
            throw new InvalidToolInputException("archive_base64 must be a non-empty Base64 string");
        }
        try {
            Base64.getDecoder().decode(value.asText());
        } catch (IllegalArgumentException e) {
            throw new InvalidToolInputException("archive_base64 is not valid Base64");
        }
        return value.asText();
    }
}
