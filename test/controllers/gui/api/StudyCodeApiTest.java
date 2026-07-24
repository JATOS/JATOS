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
 * Integration tests for {@link controllers.gui.api.StudyCodeApi}.
 */
public class StudyCodeApiTest extends JatosTest {

    // ---------- /jatos/api/v1/studies/:id/studyCodes ----------

    @Test
    public void getOrGenerateStudyCodes_createsAndReturnsCodes() {
        Study study = importAndGetExampleStudy();
        ObjectNode body = Json.newObject();
        body.put("type", "PersonalSingle");
        body.put("amount", 2);

        WSResponse resp = api("/jatos/api/v1/studies/" + study.getId() + "/studyCodes").post(body);

        assertThat(resp.getStatus()).isEqualTo(OK);
        JsonNode data = resp.asJson().get("data");
        assertThat(data.isArray()).isTrue();
        assertThat(data.size()).isEqualTo(2);
    }

    // ---------- /jatos/api/v1/studyCodes/:code ----------

    @Test
    public void getStudyCode_withUnknownCode_returnsNotFound() {
        WSResponse resp = api("/jatos/api/v1/studyCodes/non-existent-code").get();

        assertThat(resp.getStatus()).isEqualTo(NOT_FOUND);
    }

    @Test
    public void toggleStudyCodeActive_withUnknownCode_returnsNotFound() {
        ObjectNode body = Json.newObject();
        body.put("active", false);

        WSResponse resp = api("/jatos/api/v1/studyCodes/non-existent-code").patch(body);

        assertThat(resp.getStatus()).isEqualTo(NOT_FOUND);
    }
}