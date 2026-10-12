package services.gui.mcp;

import org.junit.Test;
import play.libs.Json;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the tool catalog's API mappings and argument validation.
 */
public class McpToolsTest {
    /** Resolved calls select decoding and apply summaries only to the study listing. */
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

    /** Discovery's advertised log defaults and bounds agree with argument validation. */
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
                .isInstanceOf(McpTools.InvalidToolInputException.class);
        assertThatThrownBy(() -> apiPath("get_study_log",
                Json.newObject().put("study_id", 1).putNull("entry_limit")))
                .isInstanceOf(McpTools.InvalidToolInputException.class);
    }

    /** Extracts the URL from a fully resolved call for endpoint and input validation assertions. */
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
    /** Checks the new API routes, bounded log queries and URL-safe asset paths. */
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

    /** Accepts any suffix or no suffix; the API client validates the downloaded bytes as UTF-8. */
    @Test
    public void acceptsAssetPathsRegardlessOfExtension() {
        for (String path : new String[]{"README", ".settings", "source.ts", "image.png", "data.custom"}) {
            assertThat(apiPath("read_study_asset", Json.newObject().put("study_id", 1).put("filepath", path)))
                    .isEqualTo("/studies/1/assets/" + path);
        }
    }

    /** Ensures filepaths cannot traverse or inject URL parameters, and log limits stay bounded. */
    @Test
    public void rejectsUnsafeAssetPathsAndLogLimits() {
        for (String path : new String[]{"", "/a.js", "../a.js", "a/../b.js", "./a.js", "a//b.js",
                "%2e%2e/a.js", "https://host/a.js", "a\\b.js", "a\nb.js"}) {
            assertThatThrownBy(() -> apiPath("read_study_asset",
                    Json.newObject().put("study_id", 1).put("filepath", path)))
                    .isInstanceOf(McpTools.InvalidToolInputException.class);
        }
        assertThat(apiPath("read_study_asset", Json.newObject().put("study_id", 1).put("filepath", "a?#.js")))
                .isEqualTo("/studies/1/assets/a%3F%23.js");
        for (int limit : new int[]{-1, 0, 1001}) {
            assertThatThrownBy(() -> apiPath("get_study_log",
                    Json.newObject().put("study_id", 1).put("entry_limit", limit)))
                    .isInstanceOf(McpTools.InvalidToolInputException.class);
        }
    }

}
