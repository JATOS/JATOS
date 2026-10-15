package services.gui.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import static services.gui.mcp.McpApiClient.ResponseFormat.*;
import static services.gui.mcp.ToolParam.*;

/**
 * Study, component and batch tools exposed through the JATOS API, in API concern order.
 */
public final class McpTools {

    // Keep discovery in the same concern order as the API endpoints.
    private static final List<Tool> TOOLS = List.of(
            // Batches
            new Tool(
                    "list_batches",
                    "Read a study's batches and their participation settings.",
                    List.of(STUDY_ID),
                    args -> "/studies/%s/batches".formatted(args.get("study_id"))
            ),
            new Tool(
                    "get_batch",
                    "Read one batch's properties and participation settings.",
                    List.of(BATCH_ID),
                    args -> "/batches/%s".formatted(args.get("batch_id"))
            ),
            new Tool(
                    "get_batch_session",
                    "Read shared batch session data and its version.",
                    List.of(BATCH_ID),
                    args -> "/batches/%s/session".formatted(args.get("batch_id"))
            ),
            new Tool(
                    "create_batch",
                    "Create a batch in a study. Does not create study runs or participants.",
                    List.of(STUDY_ID, properties(BATCH_FIELDS, true)),
                    args -> "/studies/%s/batches".formatted(args.get("study_id")),
                    "POST"
            ),
            new Tool(
                    "update_batch",
                    "Update only the supplied batch properties. Existing runs and sessions are retained.",
                    List.of(BATCH_ID, properties(BATCH_FIELDS, false)),
                    args -> "/batches/%s".formatted(args.get("batch_id")),
                    "PATCH"
            ),
            // Components
            new Tool(
                    "list_components",
                    "Read a study's components in execution order.",
                    List.of(STUDY_ID),
                    args -> "/studies/%s/components".formatted(args.get("study_id"))
            ),
            new Tool(
                    "get_component",
                    "Read one component's properties and configuration.",
                    List.of(COMPONENT_ID),
                    args -> "/components/%s".formatted(args.get("component_id"))
            ),
            new Tool(
                    "create_component",
                    "Create a component in a study. References an existing HTML asset; does not upload files.",
                    List.of(STUDY_ID, properties(COMPONENT_FIELDS, true)),
                    args -> "/studies/%s/components".formatted(args.get("study_id")),
                    "POST"
            ),
            new Tool(
                    "update_component",
                    "Update only the supplied component properties. htmlFileRename=true also renames the HTML file.",
                    List.of(COMPONENT_ID, properties(java.util.stream.Stream.concat(COMPONENT_FIELDS.stream(),
                            java.util.stream.Stream.of("htmlFileRename")).toList(), false)),
                    args -> "/components/%s".formatted(args.get("component_id")),
                    "PATCH"
            ),
            // Studies
            new Tool(
                    "list_studies",
                    "List accessible studies as compact summaries with IDs, UUIDs and titles. "
                    + "Use get_study to inspect a study's configuration.",
                    List.of(),
                    args -> "/studies/properties",
                    ENVELOPED_JSON,
                    McpTools::summarizeStudies
            ),
            new Tool(
                    "get_study",
                    "Read a study's properties and configuration.",
                    List.of(STUDY_ID),
                    args -> "/studies/%s/properties".formatted(args.get("study_id"))
            ),
            new Tool(
                    "get_study_log",
                    "Read recent study log entries, newest first. Defaults to %d entries; maximum %d."
                    .formatted(DEFAULT_STUDY_LOG_ENTRIES, MAX_STUDY_LOG_ENTRIES),
                    List.of(STUDY_ID, ENTRY_LIMIT),
                    args -> "/studies/%s/log?download=false&entryLimit=%s"
                            .formatted(args.get("study_id"), args.get("entry_limit")),
                    RAW_JSON,
                    UnaryOperator.identity()
            ),
            new Tool(
                    "create_study",
                    "Create a study with a default batch. Omit dirName to let JATOS choose an assets directory.",
                    List.of(properties(STUDY_FIELDS, true)),
                    args -> "/studies",
                    "POST"
            ),
            new Tool(
                    "update_study",
                    "Update only the supplied study properties. Changing dirName can rename the assets directory.",
                    List.of(STUDY_ID, properties(STUDY_FIELDS, false)),
                    args -> "/studies/%s/properties".formatted(args.get("study_id")),
                    "PATCH"
            ),
            new Tool(
                    "export_study",
                    "Return an authenticated API download link for a study and its assets (.jzip archive), not results. "
                    + "Download with your API Bearer token or signed-in JATOS session; credentials are not included in the link.",
                    List.of(STUDY_ID),
                    args -> "/studies/%s/properties".formatted(args.get("study_id")),
                    DOWNLOAD_LINK,
                    UnaryOperator.identity()
            ),

            // Study assets
            new Tool(
                    "list_study_assets",
                    "List the study's asset directory tree to find HTML, JavaScript and other files.",
                    List.of(STUDY_ID),
                    args -> "/studies/%s/assets/structure".formatted(args.get("study_id"))
            ),
            new Tool(
                    "read_study_asset",
                    "Read a UTF-8 text asset. Use an unencoded relative filepath from list_study_assets. "
                    + "Any filename or extension is accepted. The entire file must decode as UTF-8 and fit "
                    + "within the configured response size limit.",
                    List.of(STUDY_ID, FILEPATH),
                    args -> "/studies/%s/assets/%s".formatted(args.get("study_id"), args.get("filepath")),
                    RAW_TEXT,
                    UnaryOperator.identity()
            ),
            // Study codes (participation links)
            new Tool(
                    "get_or_generate_study_codes",
                    "Obtain study codes for a study and optional batch. Personal types create new codes on every call; "
                    + "general and MTurk types reuse existing links or create them if needed. Requires USER permission. "
                    + "Use get_study_code to obtain participation paths. Do not retry blindly after a timeout.",
                    List.of(STUDY_ID, studyCodeProperties(true)),
                    args -> "/studies/%s/studyCodes".formatted(args.get("study_id")),
                    "POST"
            ),
            new Tool(
                    "get_study_code",
                    "Inspect a study code's type, batch, active flag, comment and participation paths. "
                    + "Resolve returned paths against the public JATOS origin, preserving any deployment prefix.",
                    List.of(STUDY_CODE),
                    args -> "/studyCodes/%s".formatted(args.get("code"))
            ),
            new Tool(
                    "set_study_code_active",
                    "Enable or disable participation through a study code by setting properties.active. "
                    + "Returns the updated code settings. Requires USER permission.",
                    List.of(STUDY_CODE, studyCodeProperties(false)),
                    args -> "/studyCodes/%s".formatted(args.get("code")),
                    "PATCH"
            )
    );

    private static final Map<String, Tool> TOOLS_BY_NAME = TOOLS.stream()
            .collect(Collectors.toUnmodifiableMap(Tool::name, Function.identity()));

    /**
     * Prevents instantiation of this static tool catalog.
     */
    private McpTools() {
    }

    /**
     * Builds discovery metadata for all tools in catalog order.
     */
    public static ArrayNode list() {
        ArrayNode result = JsonNodeFactory.instance.arrayNode();
        for (Tool tool : TOOLS) result.add(tool.describe());
        return result;
    }

    /**
     * Finds a tool and resolves its arguments into a validated API operation.
     */
    public static ResolvedCall resolveCall(String name, JsonNode arguments) {
        Tool tool = TOOLS_BY_NAME.get(name);
        if (tool == null) throw new InvalidToolRequestException("Unknown tool: " + name);
        return tool.resolve(arguments);
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
