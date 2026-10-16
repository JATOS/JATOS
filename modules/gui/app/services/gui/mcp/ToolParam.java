package services.gui.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
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

    static final ToolParam STUDY_CODE = new ToolParam(
            "code",
            true,
            schema -> schema.put("type", "string").put("minLength", 1)
                    .put("description", "Study code returned by get_or_generate_study_codes; not a full participation URL."),
            value -> {
                if (value == null || !value.isTextual() || value.asText().isBlank()
                        || value.asText().equals(".") || value.asText().equals("..")
                        || value.asText().codePoints().anyMatch(c -> Character.isISOControl(c)
                        || c == '/' || c == '\\' || c == '%')) {
                    throw new InvalidToolInputException("code must be a non-empty study code, not a path or URL");
                }
                return URLEncoder.encode(value.asText(), StandardCharsets.UTF_8).replace("+", "%20");
            });

    /**
     * Defines study-code request bodies while leaving domain validation to the API.
     */
    static ToolParam studyCodeProperties(boolean generate) {
        ToolParam properties = properties(generate ? List.of("type", "batchId", "comment", "amount")
                : List.of("active"), false);
        String requiredField = generate ? "type" : "active";
        return new ToolParam("properties", true, schema -> {
            properties.describe(schema);
            schema.put("description", generate
                    ? "Study-code options. Omit batchId for the default batch. amount and comment apply only to personal types."
                    : "Set active to true to enable participation or false to disable it.");
            schema.putArray("required").add(requiredField);
        }, value -> {
            properties.validate(value);
            JsonNode field = value.get(requiredField);
            if (field == null || (generate ? !field.isTextual() : !field.isBoolean())) {
                throw new InvalidToolInputException("properties." + requiredField
                        + (generate ? " must be a string" : " must be a boolean"));
            }
            return "";
        });
    }

    /**
     * Defines explicit result selectors. Multiple fields are combined as a union by the API.
     */
    static ToolParam resultSelection() {
        List<String> fields = List.of("studyIds", "studyUuids", "componentIds", "componentUuids",
                "studyResultIds", "componentResultIds", "batchIds", "groupIds");
        return new ToolParam("properties", true, schema -> {
            schema.put("type", "object").put("minProperties", 1).put("additionalProperties", false)
                    .put("description", "Select results by one or more ID/UUID fields. Selections are combined (union), not intersected. "
                            + "Missing results are omitted; the API may omit inaccessible results or reject access. Use IDs or non-empty arrays; ranges are not supported here.");
            var props = schema.putObject("properties");
            for (String field : fields) {
                boolean uuid = field.endsWith("Uuids");
                var alternatives = props.putObject(field).putArray("oneOf");
                var scalar = alternatives.addObject().put("type", uuid ? "string" : "integer");
                if (uuid) scalar.put("format", "uuid");
                else scalar.put("minimum", 1);
                alternatives.addObject().put("type", "array").put("minItems", 1).set("items", scalar.deepCopy());
            }
        }, value -> {
            if (value == null || !value.isObject() || value.isEmpty()) {
                throw new InvalidToolInputException("properties must contain at least one result selector");
            }
            value.fields().forEachRemaining(entry -> {
                if (!fields.contains(entry.getKey()))
                    throw new InvalidToolInputException("Unknown result selector: " + entry.getKey());
                JsonNode values = entry.getValue();
                if (values.isArray() && values.isEmpty())
                    throw new InvalidToolInputException("Result selector arrays must not be empty");
                for (JsonNode item : values.isArray() ? values : List.of(values)) {
                    if (entry.getKey().endsWith("Uuids")) {
                        if (!item.isTextual() || !item.asText().matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}"))
                            throw new InvalidToolInputException("Expected a UUID in " + entry.getKey());
                    } else if (!item.isIntegralNumber() || !item.canConvertToLong() || item.asLong() < 1) {
                        throw new InvalidToolInputException("Expected a positive integer in " + entry.getKey());
                    }
                }
            });
            return "";
        });
    }

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
            case "allowedWorkerTypes" -> schema.put("type", "array").putObject("items").put("type", "string");
            case "type" -> {
                schema.put("type", "string");
                var values = schema.putArray("enum");
                List.of("PersonalSingle", "PersonalMultiple", "GeneralSingle", "GeneralMultiple", "MTurk",
                        "ps", "pm", "gs", "gm", "mt").forEach(values::add);
            }
            case "batchId" -> schema.put("type", "integer").put("minimum", 1);
            case "amount" -> schema.put("type", "integer").put("minimum", 1).put("maximum", 1000).put("default", 1);
            case "comment" -> schema.put("type", "string").put("maxLength", 255);
            case "title", "dirName" -> schema.put("type", "string");
            default -> schema.putArray("type").add("string").add("null");
        }
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

}
