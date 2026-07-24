package controllers.gui.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import models.common.Study;
import org.junit.Test;
import play.libs.Json;
import play.libs.ws.WSResponse;
import testutils.JatosTest;

import static org.assertj.core.api.Assertions.assertThat;
import static play.test.Helpers.NOT_FOUND;
import static play.test.Helpers.OK;

/**
 * Integration tests for {@link controllers.gui.api.ComponentApi}.
 */
public class ComponentApiTest extends JatosTest {

    // ---------- /jatos/api/v1/studies/:id/components ----------

    @Test
    public void getComponentsByStudy_returnsComponentList() {
        Study study = importAndGetExampleStudy();

        WSResponse resp = api("/jatos/api/v1/studies/" + study.getId() + "/components").get();

        assertThat(resp.getStatus()).isEqualTo(OK);
        JsonNode data = resp.asJson().get("data");
        assertThat(data.isArray()).isTrue();
        assertThat(data.size()).isGreaterThanOrEqualTo(1);
    }

    @Test
    public void createComponent_withValidData_returnsCreatedComponent() {
        Study study = importAndGetExampleStudy();
        ObjectNode body = Json.newObject();
        body.put("title", "New Integration Test Component");
        body.put("htmlFilePath", "index.html");
        body.set("componentInput", Json.newObject());

        WSResponse resp = api("/jatos/api/v1/studies/" + study.getId() + "/components").post(body);

        assertThat(resp.getStatus()).isEqualTo(OK);
        JsonNode data = resp.asJson().get("data");
        assertThat(data.get("title").asText()).isEqualTo("New Integration Test Component");
        assertThat(data.get("id")).isNotNull();
    }

    // ---------- /jatos/api/v1/components/:id ----------

    @Test
    public void getComponent_returnsComponentData() {
        Study study = importAndGetExampleStudy();
        Long componentId = study.getComponentList().get(0).getId();

        WSResponse resp = api("/jatos/api/v1/components/" + componentId).get();

        assertThat(resp.getStatus()).isEqualTo(OK);
        JsonNode data = resp.asJson().get("data");
        assertThat(data.get("id").asLong()).isEqualTo(componentId);
    }

    @Test
    public void updateComponent_changesProperties() {
        Study study = importAndGetExampleStudy();
        Long componentId = study.getComponentList().get(0).getId();

        ObjectNode body = Json.newObject();
        body.put("title", "Updated Component Title");

        WSResponse resp = api("/jatos/api/v1/components/" + componentId).patch(body);

        assertThat(resp.getStatus()).isEqualTo(OK);
        assertThat(resp.asJson().get("data").get("title").asText()).isEqualTo("Updated Component Title");
    }

    @Test
    public void deleteComponent_removesComponent() {
        Study study = importAndGetExampleStudy();
        // Create a component first so we can delete it safely
        ObjectNode body = Json.newObject();
        body.put("title", "To Be Deleted");
        body.put("htmlFilePath", "index.html");
        JsonNode created = api("/jatos/api/v1/studies/" + study.getId() + "/components").post(body).asJson().get("data");
        String componentId = created.get("id").asText();

        WSResponse resp = api("/jatos/api/v1/components/" + componentId).delete();

        assertThat(resp.getStatus()).isEqualTo(OK);
        assertThat(resp.asJson().get("message").asText()).contains("Component deleted successfully");

        WSResponse getResp = api("/jatos/api/v1/components/" + componentId).get();
        assertThat(getResp.getStatus()).isEqualTo(NOT_FOUND);
    }
}