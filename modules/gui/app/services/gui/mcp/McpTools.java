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
            // Current token
            new Tool(
                    "get_current_token",
                    "Read metadata of the current API token, including its owner, roles and expiry. Does not return the"
                            + " token secret or hash.",
                    List.of(),
                    args -> "/admin/token"
            ),
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
                    "update_batch_session",
                    "Replace the entire shared batch session with properties. Read get_batch_session first and supply "
                            + "its version. Omitted keys are removed; {} clears the session. A stale version is rejected. "
                            + "Requires USER permission.",
                    List.of(BATCH_ID, SESSION_VERSION, SESSION_PROPERTIES),
                    args -> "/batches/%s/session?version=%s".formatted(args.get("batch_id"), args.get("version")),
                    "PATCH"
            ),
            new Tool(
                    "create_batch",
                    "Create a batch in a study. Does not create study runs or participants.",
                    List.of(STUDY_ID, properties(BATCH_FIELDS, true)),
                    args -> "/studies/%s/batches".formatted(args.get("study_id")),
                    "POST"
            ),
            new Tool(
                    "set_batch_active",
                    "Enable or disable a batch using properties.active. Requires USER permission and study access.",
                    List.of(BATCH_ID, studyCodeProperties(false)),
                    args -> "/batches/%s".formatted(args.get("batch_id")),
                    ENVELOPED_JSON,
                    UnaryOperator.identity(),
                    "PATCH",
                    false,
                    false
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
                    "set_component_active",
                    "Enable or disable a component using properties.active. Requires USER permission and study access.",
                    List.of(COMPONENT_ID, studyCodeProperties(false)),
                    args -> "/components/%s".formatted(args.get("component_id")),
                    ENVELOPED_JSON,
                    UnaryOperator.identity(),
                    "PATCH",
                    false,
                    false
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
                    "Read a study's properties and configuration. Optionally include full component and batch properties; "
                            + "does not include assets, results or session data.",
                    List.of(STUDY_ID,
                            optionalBoolean("with_components", "Include full component properties in execution order."),
                            optionalBoolean("with_batches", "Include full batch properties.")),
                    args -> "/studies/%s/properties?withComponentProperties=%s&withBatchProperties=%s"
                            .formatted(args.get("study_id"), args.get("with_components"), args.get("with_batches"))
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
                    "set_study_active",
                    "Enable or disable a study using properties.active. Requires ADMIN permission.",
                    List.of(STUDY_ID, studyCodeProperties(false)),
                    args -> "/studies/%s/properties".formatted(args.get("study_id")),
                    ENVELOPED_JSON,
                    UnaryOperator.identity(),
                    "PATCH",
                    false,
                    true
            ),
            new Tool(
                    "update_study",
                    "Update only the supplied study properties. Changing dirName can rename the assets directory.",
                    List.of(STUDY_ID, properties(STUDY_FIELDS, false)),
                    args -> "/studies/%s/properties".formatted(args.get("study_id")),
                    "PATCH"
            ),
            new Tool(
                    "study_import",
                    "Return HTTP instructions to import a local .jzip archive. Does not upload or import it. "
                            + "The client must send the binary file directly to the API with USER permission. "
                            + "Existing study properties and assets are overwritten unless the keep options are enabled.",
                    List.of(optionalBoolean("keep_properties", "Preserve existing study properties on overwrite."),
                            optionalBoolean("keep_assets", "Preserve existing study assets on overwrite."),
                            optionalBoolean("keep_current_assets_name", "Keep the existing assets directory name on overwrite.", true),
                            optionalBoolean("rename_assets", "Rename the assets directory if its name belongs to another study.", true)),
                    args -> "/studies?keepProperties=%s&keepAssets=%s&keepCurrentAssetsName=%s&renameAssets=%s"
                            .formatted(args.get("keep_properties"), args.get("keep_assets"),
                                    args.get("keep_current_assets_name"), args.get("rename_assets")),
                    UPLOAD_REQUEST,
                    McpTools::studyImportInstructions,
                    "POST",
                    true
            ),
            new Tool(
                    "export_study",
                    "Return an authenticated API download link for a study and its assets (.jzip archive), not results. "
                            + "Download with your API Bearer token or signed-in JATOS session; credentials are not "
                            + "included in the link.",
                    List.of(STUDY_ID),
                    args -> "/studies/%s/properties".formatted(args.get("study_id")),
                    DOWNLOAD_LINK,
                    UnaryOperator.identity()
            ),

            new Tool(
                    "download_study_log",
                    "Return an authenticated GET download URL for the complete study log. Download directly through the API; "
                            + "permissions and file existence are checked when downloading.",
                    List.of(STUDY_ID),
                    args -> "/studies/%s/log?download=true".formatted(args.get("study_id")),
                    DOWNLOAD_REQUEST,
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
            new Tool(
                    "download_study_asset",
                    "Return an authenticated GET download URL for any study asset, including binary and large files. "
                            + "Use an unencoded relative filepath from list_study_assets. Permissions and existence are "
                            + "checked when downloading.",
                    List.of(STUDY_ID, FILEPATH),
                    args -> "/studies/%s/assets/%s".formatted(args.get("study_id"), args.get("filepath")),
                    DOWNLOAD_REQUEST,
                    UnaryOperator.identity()
            ),
            new Tool(
                    "write_study_asset",
                    "Write UTF-8 text to a study asset through the API. Creates missing parent directories and overwrites existing files. "
                            + "Send the complete content; empty content truncates the file. Requires USER permission and study access. "
                            + "The MCP request and API asset size limits apply; use upload_study_asset for large or binary files.",
                    List.of(STUDY_ID, FILEPATH, CONTENT),
                    args -> "/studies/%s/assets/%s".formatted(args.get("study_id"), args.get("filepath")),
                    "POST"
            ),
            new Tool(
                    "upload_study_asset",
                    "Return multipart HTTP upload instructions for a study asset. Does not upload a file. "
                            + "The client must execute the request with USER permission and study access. "
                            + "Use an unencoded relative filepath including the destination filename. Existing files are overwritten.",
                    List.of(STUDY_ID, FILEPATH),
                    args -> "/studies/%s/assets/%s".formatted(args.get("study_id"), args.get("filepath")),
                    UPLOAD_REQUEST,
                    McpTools::assetUploadInstructions,
                    "POST",
                    true
            ),
            // Study membership
            new Tool(
                    "list_study_members",
                    "List the user IDs belonging to a study. Returns the study ID, UUID and members array.",
                    List.of(STUDY_ID),
                    args -> "/studies/%s/members".formatted(args.get("study_id"))
            ),
            new Tool(
                    "add_study_member",
                    "Grant an existing user membership in a study. Requires USER permission and study access. "
                            + "Already-existing membership is unchanged. Returns the updated members list.",
                    List.of(STUDY_ID, USER_ID),
                    args -> "/studies/%s/members/%s".formatted(args.get("study_id"), args.get("user_id")),
                    "PUT"
            ),
            new Tool(
                    "remove_study_member",
                    "Remove a user's membership in a study, revoking membership-based access. "
                            + "Requires USER permission and study access. Returns the updated members list; API "
                            + "membership safeguards apply.",
                    List.of(STUDY_ID, USER_ID),
                    args -> "/studies/%s/members/%s".formatted(args.get("study_id"), args.get("user_id")),
                    "DELETE"
            ),
            // Groups
            new Tool(
                    "list_groups",
                    "List all groups belonging to a batch, including active and finished groups. "
                            + "Use list_batches first and call this for each batch to inspect all groups of a study.",
                    List.of(BATCH_ID),
                    args -> "/batches/%s/groups".formatted(args.get("batch_id"))
            ),
            new Tool(
                    "get_group_session",
                    "Read a group's shared sessionData JSON and current version.",
                    List.of(GROUP_ID),
                    args -> "/groups/%s/session".formatted(args.get("group_id"))
            ),
            new Tool(
                    "update_group_session",
                    "Replace the entire shared group session with properties. Read get_group_session first and supply its version. "
                            + "Omitted keys are removed; {} clears the session. A stale version is rejected. Requires USER permission.",
                    List.of(GROUP_ID, SESSION_VERSION, SESSION_PROPERTIES),
                    args -> "/groups/%s/session?version=%s".formatted(args.get("group_id"), args.get("version")),
                    "PATCH"
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
            ),
            // Results
            new Tool(
                    "get_result_file",
                    "Return an authenticated GET download URL for one result file. Supply the component-result ID and exact filename "
                            + "from get_result_metadata. Permissions and file existence are checked when downloading.",
                    List.of(COMPONENT_RESULT_ID, FILENAME),
                    args -> "/results/files/%s?componentResultId=%s".formatted(args.get("filename"), args.get("component_result_id")),
                    DOWNLOAD_REQUEST,
                    UnaryOperator.identity()
            ),
            new Tool(
                    "get_result_metadata",
                    "Inspect metadata of selected study runs and component results, including uploaded-file metadata. "
                            + "Bounded by the MCP response limit; narrow the selection or use get_results for large exports.",
                    List.of(resultSelection()),
                    args -> "/results/metadata?download=false",
                    ENVELOPED_JSON,
                    UnaryOperator.identity(),
                    "POST",
                    true
            ),
            new Tool(
                    "get_result_data",
                    "Read selected component-result data as text, one result per line. Data need not be JSON. "
                            + "Bounded by the MCP response limit; use get_results for larger exports.",
                    List.of(resultSelection()),
                    args -> "/results/data?asPlainText=true&download=false",
                    RAW_TEXT,
                    UnaryOperator.identity(),
                    "POST",
                    true
            ),
            new Tool(
                    "get_result_files",
                    "Return an authenticated POST download request for a ZIP of selected result files. "
                            + "Includes URL and JSON body; requires an HTTP-capable client. Access is checked when downloaded. "
                            + "Use get_result_metadata to inspect file information.",
                    List.of(resultSelection()),
                    args -> "/results/files",
                    DOWNLOAD_REQUEST,
                    UnaryOperator.identity(),
                    "POST",
                    true
            ),
            new Tool(
                    "get_results",
                    "Return an authenticated POST download request for a ZIP containing selected result metadata, data and files. "
                            + "Includes URL and JSON body; requires an HTTP-capable client. Access is checked when downloaded. "
                            + "The archive bypasses MCP response-size limits.",
                    List.of(resultSelection()),
                    args -> "/results",
                    DOWNLOAD_REQUEST,
                    UnaryOperator.identity(),
                    "POST",
                    true
            ),
            // Users
            new Tool(
                    "list_users",
                    "List all JATOS user IDs for membership operations. Requires ADMIN permission. Returns only numeric IDs.",
                    List.of(),
                    args -> "/users",
                    ENVELOPED_JSON,
                    McpTools::userIds
            ),
            new Tool(
                    "add_user",
                    "Create a JATOS account. Requires ADMIN permission. Supports DB (password required) and LDAP accounts; "
                            + "role is USER (default) or VIEWER. Password arguments may be retained by the MCP client.",
                    List.of(userProperties(true)),
                    args -> "/users",
                    "POST"
            ),
            new Tool(
                    "get_user",
                    "Read a user's profile and roles. Requires ADMIN permission or the user's own token.",
                    List.of(USER_ID),
                    args -> "/users/%s".formatted(args.get("user_id"))
            ),
            new Tool(
                    "set_user_active",
                    "Enable or disable a user using properties.active. Requires ADMIN permission.",
                    List.of(USER_ID, studyCodeProperties(false)),
                    args -> "/users/%s".formatted(args.get("user_id")),
                    ENVELOPED_JSON,
                    UnaryOperator.identity(),
                    "PATCH",
                    false,
                    true
            ),
            new Tool(
                    "update_user",
                    "Update supplied user profile fields (name, email, password, active). Requires ADMIN permission or self. "
                            + "Does not change username, authentication method or roles. Password arguments may be "
                            + "retained by the MCP client.",
                    List.of(USER_ID, userProperties(false)),
                    args -> "/users/%s".formatted(args.get("user_id")),
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

    /** Adds raw archive upload requirements and the configured API file limit. */
    private static JsonNode studyImportInstructions(JsonNode request) {
        return ((ObjectNode) request).put("contentType", "application/zip")
                .put("bodyInstructions", "Send the local .jzip file as the raw binary request body, not JSON or Base64. No import has been performed.")
                .put("maxFileSizeBytes", general.common.Common.getStudyArchiveMaxSize())
                .put("overwriteBehavior", "An existing study is overwritten; keepProperties and keepAssets preserve the corresponding existing content.");
    }

    /** Adds the multipart field required by the asset API and its configured file limit. */
    private static JsonNode assetUploadInstructions(JsonNode request) {
        return ((ObjectNode) request).put("contentType", "multipart/form-data")
                .put("fileField", "studyAssetsFile")
                .put("bodyInstructions", "Attach the local file to the studyAssetsFile multipart field. Let the HTTP client generate the Content-Type boundary. No upload has been performed.")
                .put("maxFileSizeBytes", general.common.Common.getStudyAssetsMaxFileSize())
                .put("overwriteBehavior", "Existing files at the destination are overwritten; missing parent directories are created.");
    }

    /**
     * Extracts user IDs from the API user listing without exposing additional profile fields.
     */
    private static JsonNode userIds(JsonNode data) {
        ArrayNode ids = JsonNodeFactory.instance.arrayNode();
        for (JsonNode user : data) ids.add(user.get("id"));
        return ids;
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
