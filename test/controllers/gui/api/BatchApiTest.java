package controllers.gui.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import models.common.Study;
import org.junit.Test;
import play.libs.Json;
import play.libs.ws.WSResponse;
import testutils.JatosTest;

import static org.assertj.core.api.Assertions.assertThat;
import static play.test.Helpers.*;

/**
 * Integration tests for {@link controllers.gui.api.BatchApi}.
 */
public class BatchApiTest extends JatosTest {

    // ---------- /jatos/api/v1/studies/:studyId/batches ----------

    @Test
    public void getBatchesByStudy_returnsBatchList() {
        Study study = importAndGetExampleStudy();

        WSResponse resp = api("/jatos/api/v1/studies/" + study.getId() + "/batches").get();

        assertThat(resp.getStatus()).isEqualTo(OK);
        JsonNode data = resp.asJson().get("data");
        assertThat(data.isArray()).isTrue();
        assertThat(data.size()).isGreaterThanOrEqualTo(1); // Default batch
    }

    @Test
    public void createBatch_withValidData_returnsCreatedBatch() {
        Study study = importAndGetExampleStudy();
        ObjectNode body = Json.newObject();
        body.put("title", "New Integration Test Batch");
        body.put("active", true);
        ObjectNode batchInput = Json.newObject();
        body.set("batchInput", batchInput);

        WSResponse resp = api("/jatos/api/v1/studies/" + study.getId() + "/batches").post(body);

        assertThat(resp.getStatus()).isEqualTo(OK);
        JsonNode data = resp.asJson().get("data");
        assertThat(data.get("title").asText()).isEqualTo("New Integration Test Batch");
        assertThat(data.get("id")).isNotNull();
    }

    // ---------- /jatos/api/v1/batches/:id ----------

    @Test
    public void getBatch_returnsBatchData() {
        Study study = importAndGetExampleStudy();
        Long batchId = study.getDefaultBatch().getId();

        WSResponse resp = api("/jatos/api/v1/batches/" + batchId).get();

        assertThat(resp.getStatus()).isEqualTo(OK);
        JsonNode data = resp.asJson().get("data");
        assertThat(data.get("id").asLong()).isEqualTo(batchId);
    }

    @Test
    public void updateBatch_changesProperties() {
        Study study = importAndGetExampleStudy();
        Long batchId = study.getDefaultBatch().getId();

        ObjectNode body = Json.newObject();
        body.put("title", "Updated Batch Title");

        WSResponse resp = api("/jatos/api/v1/batches/" + batchId).patch(body);

        assertThat(resp.getStatus()).isEqualTo(OK);
        assertThat(resp.asJson().get("data").get("title").asText()).isEqualTo("Updated Batch Title");
    }

    @Test
    public void deleteBatch_removesBatch() {
        Study study = importAndGetExampleStudy();
        // Create a new batch so we don't delete the default one if others depend on it
        ObjectNode body = Json.newObject();
        body.put("title", "New Integration Test Batch");
        body.put("active", true);
        ObjectNode batchInput = Json.newObject();
        body.set("batchInput", batchInput);
        JsonNode created = api("/jatos/api/v1/studies/" + study.getId() + "/batches").post(body).asJson().get("data");
        String batchId = created.get("id").asText();

        WSResponse resp = api("/jatos/api/v1/batches/" + batchId).delete();

        assertThat(resp.getStatus()).isEqualTo(OK);
        assertThat(resp.asJson().get("message").asText()).contains("Batch deleted successfully");

        WSResponse getResp = api("/jatos/api/v1/batches/" + batchId).get();
        assertThat(getResp.getStatus()).isEqualTo(NOT_FOUND);
    }

    // ---------- /jatos/api/v1/batches/:id/session ----------

    @Test
    public void updateBatchSession_updatesDataAndIncrementsVersion() {
        Study study = importAndGetExampleStudy();
        Long batchId = study.getDefaultBatch().getId();
        String sessionData = "{\"key\": \"value\"}";

        WSResponse resp = api("/jatos/api/v1/batches/" + batchId + "/session").patch(sessionData);

        assertThat(resp.getStatus()).isEqualTo(OK);
        JsonNode data = resp.asJson().get("data");
        assertThat(data.get("version").asLong()).isGreaterThan(0L);

        // Verify content
        WSResponse getResp = api("/jatos/api/v1/batches/" + batchId + "/session").get();
        assertThat(getResp.asJson().get("data").toString()).contains("value");
    }

    @Test
    public void updateBatchSession_withVersionConflict_returnsForbidden() {
        Study study = importAndGetExampleStudy();
        Long batchId = study.getDefaultBatch().getId();

        // Try to update with a wrong version (e.g., 999)
        WSResponse resp = api("/jatos/api/v1/batches/" + batchId + "/session?version=999").patch("{}");

        assertThat(resp.getStatus()).isEqualTo(FORBIDDEN);
        assertThat(resp.asJson().get("error").get("message").asText()).contains("version conflict");
    }
}