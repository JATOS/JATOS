package services.gui.mcp;

import org.junit.Test;
import play.libs.Json;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the tool catalog's API mappings and argument validation.
 */
public class McpToolsTest {
    /**
     * Checks each supported tool resolves to its intended read-only API endpoint.
     */
    @Test
    public void mapsEveryToolToItsReadOnlyApiPath() {
        assertThat(McpTools.path("list_studies", null)).isEqualTo("/studies/properties");
        assertThat(McpTools.path("get_study", Json.parse("{\"study_id\":1}"))).isEqualTo("/studies/1/properties");
        assertThat(McpTools.path("list_components", Json.parse("{\"study_id\":1}"))).isEqualTo("/studies/1/components");
        assertThat(McpTools.path("get_component", Json.parse("{\"component_id\":2}"))).isEqualTo("/components/2");
        assertThat(McpTools.path("list_batches", Json.parse("{\"study_id\":1}"))).isEqualTo("/studies/1/batches");
    }

    /**
     * Checks unknown tools and invalid or unexpected arguments cannot produce API paths.
     */
    @Test
    public void rejectsMissingExtraNonIntegerNegativeAndOverflowingArguments() {
        for (String arguments : new String[]{"{}", "null", "[]", "{\"study_id\":0}", "{\"study_id\":-1}",
                "{\"study_id\":1.5}", "{\"study_id\":\"1\"}", "{\"study_id\":9223372036854775808}",
                "{\"study_id\":1,\"url\":\"https://example.org\"}"}) {
            assertThatThrownBy(() -> McpTools.path("get_study", Json.parse(arguments)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> McpTools.path("delete_study", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> McpTools.path("list_studies", Json.parse("{\"extra\":true}")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
