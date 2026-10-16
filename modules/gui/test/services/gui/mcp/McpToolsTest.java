package services.gui.mcp;

import org.junit.Test;
import play.libs.Json;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the tool catalog's API mappings and argument validation.
 */
public class McpToolsTest {
    /**
     * Membership tools use bodyless API requests; user listings expose only IDs.
     */
    @Test
    public void resolvesMembershipTools() {
        var args = Json.newObject().put("study_id", 3).put("user_id", 7);
        for (String name : java.util.List.of("add_study_member", "remove_study_member")) {
            var call = McpTools.resolveCall(name, args);
            assertThat(call.path()).isEqualTo("/studies/3/members/7");
            assertThat(call.method()).isEqualTo(name.startsWith("add") ? "PUT" : "DELETE");
            assertThat(call.body()).isNull();
        }
        assertThat(McpTools.resolveCall("list_study_members", Json.newObject().put("study_id", 3)).path())
                .isEqualTo("/studies/3/members");
        var list = McpTools.resolveCall("list_users", null);
        assertThat(list.path()).isEqualTo("/users");
        assertThat(list.postProcess().apply(Json.parse("[{\"id\":7,\"email\":\"private@example.org\"}]")))
                .isEqualTo(Json.parse("[7]"));
        args.put("user_id", 0);
        assertThatThrownBy(() -> McpTools.resolveCall("add_study_member", args))
                .isInstanceOf(InvalidToolInputException.class);
    }

    /**
     * Session writes send the complete object and expected version, including empty sessions.
     */
    @Test
    public void resolvesGroupAndSessionTools() {
        assertThat(McpTools.resolveCall("list_groups", Json.newObject().put("batch_id", 3)).path())
                .isEqualTo("/batches/3/groups");
        for (String kind : java.util.List.of("batch", "group")) {
            String id = kind + "_id";
            String path = kind.equals("batch") ? "/batches/3/session" : "/groups/3/session";
            assertThat(McpTools.resolveCall("get_" + kind + "_session", Json.newObject().put(id, 3)).path()).isEqualTo(path);
            var args = Json.newObject().put(id, 3).put("version", 0);
            var data = args.putObject("properties").putNull("nullable");
            data.putObject("nested").putArray("values").add("你好").add(1);
            var call = McpTools.resolveCall("update_" + kind + "_session", args);
            assertThat(call.path()).isEqualTo(path + "?version=0");
            assertThat(call.method()).isEqualTo("PATCH");
            assertThat(call.body()).isEqualTo(data);
            args.putObject("properties");
            assertThat(McpTools.resolveCall("update_" + kind + "_session", args).body().isEmpty()).isTrue();
            for (String invalid : java.util.List.of("null", "-1", "1.5", "\"1\"")) {
                args.set("version", Json.parse(invalid));
                assertThatThrownBy(() -> McpTools.resolveCall("update_" + kind + "_session", args))
                        .isInstanceOf(InvalidToolInputException.class);
            }
            args.remove("version");
            assertThatThrownBy(() -> McpTools.resolveCall("update_" + kind + "_session", args))
                    .isInstanceOf(InvalidToolInputException.class);
            args.put("version", 1).set("properties", Json.parse("[]"));
            assertThatThrownBy(() -> McpTools.resolveCall("update_" + kind + "_session", args))
                    .isInstanceOf(InvalidToolInputException.class);
        }
    }

    /**
     * Result selectors retain JSON types and reject empty or malformed selections.
     */
    @Test
    public void resolvesResultInspection() {
        var args = Json.newObject();
        var selection = args.putObject("properties");
        selection.putArray("studyResultIds").add(3).add(4);
        selection.put("studyIds", 1);
        for (String name : java.util.List.of("get_result_metadata", "get_result_data", "get_result_files", "get_results")) {
            var call = McpTools.resolveCall(name, args);
            assertThat(call.method()).isEqualTo("POST");
            assertThat(call.body()).isEqualTo(selection);
            var schema = java.util.stream.StreamSupport.stream(McpTools.list().spliterator(), false)
                    .filter(t -> t.path("name").asText().equals(name)).findFirst().orElseThrow();
            assertThat(schema.path("annotations").path("readOnlyHint").asBoolean()).isTrue();
            assertThat(schema.path("annotations").path("destructiveHint").asBoolean()).isFalse();
        }
        assertThat(McpTools.resolveCall("get_result_data", args).path())
                .isEqualTo("/results/data?asPlainText=true&download=false");
        assertThat(McpTools.resolveCall("get_results", args).format()).isEqualTo(McpApiClient.ResponseFormat.DOWNLOAD_REQUEST);
        for (String invalid : java.util.List.of("{}", "{\"studyIds\":[]}", "{\"studyIds\":0}",
                "{\"studyIds\":\"1-10\"}", "{\"studyUuids\":\"bad\"}", "{\"unknown\":1}")) {
            args.set("properties", Json.parse(invalid));
            assertThatThrownBy(() -> McpTools.resolveCall("get_result_metadata", args))
                    .isInstanceOf(InvalidToolInputException.class);
        }
    }

    /**
     * Study-code operations preserve API JSON options and encode code path segments.
     */
    @Test
    public void resolvesStudyCodeOperations() {
        var args = Json.newObject().put("study_id", 7);
        var properties = args.putObject("properties").put("type", "PersonalSingle")
                .put("batchId", 3).put("amount", 2).put("comment", "Participant group");
        var generate = McpTools.resolveCall("get_or_generate_study_codes", args);
        assertThat(generate.method()).isEqualTo("POST");
        assertThat(generate.path()).isEqualTo("/studies/7/studyCodes");
        assertThat(generate.body()).isEqualTo(properties);
        var read = McpTools.resolveCall("get_study_code", Json.newObject().put("code", "abc?x#y"));
        assertThat(read.path()).isEqualTo("/studyCodes/abc%3Fx%23y");
        assertThat(read.method()).isEqualTo("GET");
        var toggleArgs = Json.newObject().put("code", "abc");
        toggleArgs.putObject("properties").put("active", false);
        var toggle = McpTools.resolveCall("set_study_code_active", toggleArgs);
        assertThat(toggle.method()).isEqualTo("PATCH");
        assertThat(toggle.body()).isEqualTo(Json.parse("{\"active\":false}"));
        for (String code : java.util.List.of("", "../other", "%2f", "..")) {
            assertThatThrownBy(() -> McpTools.resolveCall("get_study_code", Json.newObject().put("code", code)))
                    .isInstanceOf(InvalidToolInputException.class);
        }
        toggleArgs.putObject("properties").put("active", "false");
        assertThatThrownBy(() -> McpTools.resolveCall("set_study_code_active", toggleArgs))
                .isInstanceOf(InvalidToolInputException.class);
        properties.remove("type");
        assertThatThrownBy(() -> McpTools.resolveCall("get_or_generate_study_codes", args))
                .isInstanceOf(InvalidToolInputException.class);
    }

    /**
     * Removed upload tools cannot be discovered or invoked.
     */
    @Test
    public void excludesUploadTools() {
        for (String name : java.util.List.of("import_study", "write_study_asset")) {
            assertThat(McpTools.list().toString()).doesNotContain(name);
            assertThatThrownBy(() -> McpTools.resolveCall(name, Json.newObject()))
                    .isInstanceOf(InvalidToolRequestException.class);
        }
    }

    /**
     * Write operations preserve explicit nulls and nested JSON rather than converting them to strings.
     */
    @Test
    public void resolvesMutations() {
        var args = Json.newObject().put("study_id", 7);
        var props = args.putObject("properties").put("title", "New").putNull("comments");
        props.putObject("studyInput").put("x", 1);
        var update = McpTools.resolveCall("update_study", args);
        assertThat(update.method()).isEqualTo("PATCH");
        assertThat(update.path()).isEqualTo("/studies/7/properties");
        assertThat(update.body()).isEqualTo(props);
        assertThat(update.body().get("comments").isNull()).isTrue();
        assertThat(update.body().path("studyInput").isObject()).isTrue();

    }

    /**
     * Prevents accidental identity writes and preserves archive download links.
     */
    @Test
    public void validatesWriteArgumentsAndExport() {
        assertThatThrownBy(() -> McpTools.resolveCall("create_study", Json.parse("{\"properties\":{}}")))
                .isInstanceOf(InvalidToolInputException.class);
        assertThatThrownBy(() -> McpTools.resolveCall("update_study", Json.parse("{\"study_id\":1,\"properties\":{\"id\":2}}")))
                .isInstanceOf(InvalidToolInputException.class);
        assertThat(McpTools.resolveCall("export_study", Json.newObject().put("study_id", 1)).format())
                .isEqualTo(McpApiClient.ResponseFormat.DOWNLOAD_LINK);
    }

    /**
     * Resolved calls select decoding and apply summaries only to the study listing.
     */
    @Test
    public void resolvesResponseFormatsAndOutputTransformations() {
        var studies = McpTools.resolveCall("list_studies", null);
        var data = Json.parse("[{\"id\":1,\"uuid\":\"study\",\"title\":\"Test\",\"extra\":true}]");
        assertThat(studies.format()).isEqualTo(McpApiClient.ResponseFormat.ENVELOPED_JSON);
        assertThat(studies.postProcess().apply(data))
                .isEqualTo(Json.parse("[{\"id\":1,\"uuid\":\"study\",\"title\":\"Test\"}]"));
        assertThat(data.get(0).has("extra")).isTrue();

        var log = McpTools.resolveCall("get_study_log", Json.newObject().put("study_id", 1));
        assertThat(log.format()).isEqualTo(McpApiClient.ResponseFormat.RAW_JSON);
        assertThat(log.postProcess().apply(data)).isSameAs(data);
        var asset = McpTools.resolveCall("read_study_asset",
                Json.newObject().put("study_id", 1).put("filepath", "README"));
        assertThat(asset.format()).isEqualTo(McpApiClient.ResponseFormat.RAW_TEXT);
        assertThat(asset.postProcess().apply(data)).isSameAs(data);
    }

    /**
     * Discovery's advertised log defaults and bounds agree with argument validation.
     */
    @Test
    public void logSchemaMatchesResolvedLimits() {
        var catalog = McpTools.list();
        var log = java.util.stream.StreamSupport.stream(catalog.spliterator(), false)
                .filter(tool -> tool.path("name").asText().equals("get_study_log")).findFirst().orElseThrow();
        var limit = log.path("inputSchema").path("properties").path("entry_limit");
        assertThat(apiPath("get_study_log", Json.newObject().put("study_id", 1)))
                .endsWith("entryLimit=" + limit.path("default").asInt());
        int maximum = limit.path("maximum").asInt();
        assertThat(apiPath("get_study_log", Json.newObject().put("study_id", 1).put("entry_limit", maximum)))
                .endsWith("entryLimit=" + maximum);
        assertThatThrownBy(() -> apiPath("get_study_log",
                Json.newObject().put("study_id", 1).put("entry_limit", maximum + 1)))
                .isInstanceOf(InvalidToolInputException.class);
        assertThatThrownBy(() -> apiPath("get_study_log",
                Json.newObject().put("study_id", 1).putNull("entry_limit")))
                .isInstanceOf(InvalidToolInputException.class);
    }

    /**
     * Extracts the URL from a fully resolved call for endpoint and input validation assertions.
     */
    private String apiPath(String name, com.fasterxml.jackson.databind.JsonNode arguments) {
        return McpTools.resolveCall(name, arguments).path();
    }

    /**
     * Checks each supported tool resolves to its intended read-only API endpoint.
     */
    @Test
    public void mapsEveryToolToItsReadOnlyApiPath() {
        assertThat(apiPath("list_studies", null)).isEqualTo("/studies/properties");
        assertThat(apiPath("get_study", Json.parse("{\"study_id\":1}"))).isEqualTo("/studies/1/properties");
        assertThat(apiPath("list_components", Json.parse("{\"study_id\":1}"))).isEqualTo("/studies/1/components");
        assertThat(apiPath("get_component", Json.parse("{\"component_id\":2}"))).isEqualTo("/components/2");
        assertThat(apiPath("list_batches", Json.parse("{\"study_id\":1}"))).isEqualTo("/studies/1/batches");
    }

    /**
     * Checks unknown tools and invalid or unexpected arguments cannot produce API paths.
     */
    @Test
    public void rejectsMissingExtraNonIntegerNegativeAndOverflowingArguments() {
        for (String arguments : new String[]{"{}", "null", "[]", "{\"study_id\":0}", "{\"study_id\":-1}",
                "{\"study_id\":1.5}", "{\"study_id\":\"1\"}", "{\"study_id\":9223372036854775808}",
                "{\"study_id\":1,\"url\":\"https://example.org\"}"}) {
            assertThatThrownBy(() -> apiPath("get_study", Json.parse(arguments)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> apiPath("delete_study", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> apiPath("list_studies", Json.parse("{\"extra\":true}")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Checks the new API routes, bounded log queries and URL-safe asset paths.
     */
    @Test
    public void mapsBatchLogAndAssetTools() {
        assertThat(apiPath("get_batch", Json.parse("{\"batch_id\":2}"))).isEqualTo("/batches/2");
        assertThat(apiPath("get_batch_session", Json.parse("{\"batch_id\":2}"))).isEqualTo("/batches/2/session");
        assertThat(apiPath("list_study_assets", Json.parse("{\"study_id\":1}")))
                .isEqualTo("/studies/1/assets/structure");
        assertThat(apiPath("get_study_log", Json.parse("{\"study_id\":1}")))
                .isEqualTo("/studies/1/log?download=false&entryLimit=100");
        assertThat(apiPath("get_study_log", Json.parse("{\"study_id\":1,\"entry_limit\":5}")))
                .isEqualTo("/studies/1/log?download=false&entryLimit=5");
        assertThat(apiPath("read_study_asset", Json.newObject().put("study_id", 1).put("filepath", "js/my file.js")))
                .isEqualTo("/studies/1/assets/js/my%20file.js");
    }

    /**
     * Accepts any suffix or no suffix; the API client validates the downloaded bytes as UTF-8.
     */
    @Test
    public void acceptsAssetPathsRegardlessOfExtension() {
        for (String path : new String[]{"README", ".settings", "source.ts", "image.png", "data.custom"}) {
            assertThat(apiPath("read_study_asset", Json.newObject().put("study_id", 1).put("filepath", path)))
                    .isEqualTo("/studies/1/assets/" + path);
        }
    }

    /**
     * Ensures filepaths cannot traverse or inject URL parameters, and log limits stay bounded.
     */
    @Test
    public void rejectsUnsafeAssetPathsAndLogLimits() {
        for (String path : new String[]{"", "/a.js", "../a.js", "a/../b.js", "./a.js", "a//b.js",
                "%2e%2e/a.js", "https://host/a.js", "a\\b.js", "a\nb.js"}) {
            assertThatThrownBy(() -> apiPath("read_study_asset",
                    Json.newObject().put("study_id", 1).put("filepath", path)))
                    .isInstanceOf(InvalidToolInputException.class);
        }
        assertThat(apiPath("read_study_asset", Json.newObject().put("study_id", 1).put("filepath", "a?#.js")))
                .isEqualTo("/studies/1/assets/a%3F%23.js");
        for (int limit : new int[]{-1, 0, 1001}) {
            assertThatThrownBy(() -> apiPath("get_study_log",
                    Json.newObject().put("study_id", 1).put("entry_limit", limit)))
                    .isInstanceOf(InvalidToolInputException.class);
        }
    }

}
