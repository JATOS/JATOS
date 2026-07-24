package controllers.gui.api;

import com.fasterxml.jackson.databind.JsonNode;
import models.common.Study;
import org.junit.Test;
import play.libs.ws.WSResponse;
import testutils.JatosTest;

import static org.assertj.core.api.Assertions.assertThat;
import static play.test.Helpers.*;

/**
 * Integration tests for {@link controllers.gui.api.GroupApi}.
 */
public class GroupApiTest extends JatosTest {

    // ---------- /jatos/api/v1/batches/:id/groups ----------

    @Test
    public void getGroupsOfBatch_returnsGroupsList() {
        Study study = importAndGetExampleStudy();
        Long batchId = study.getDefaultBatch().getId();

        WSResponse resp = api("/jatos/api/v1/batches/" + batchId + "/groups").get();

        assertThat(resp.getStatus()).isEqualTo(OK);
        JsonNode data = resp.asJson().get("data");
        assertThat(data.isArray()).isTrue();
    }

    // ---------- /jatos/api/v1/groups/:id/session ----------

    @Test
    public void getGroupSession_withNonExistentGroup_returnsNotFound() {
        WSResponse resp = api("/jatos/api/v1/groups/99999/session").get();

        assertThat(resp.getStatus()).isEqualTo(NOT_FOUND);
    }

    @Test
    public void updateGroupSession_withNonExistentGroup_returnsNotFound() {
        WSResponse resp = api("/jatos/api/v1/groups/99999/session").patch("{}");

        assertThat(resp.getStatus()).isEqualTo(NOT_FOUND);
    }
}