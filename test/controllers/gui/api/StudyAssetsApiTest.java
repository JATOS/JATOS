package controllers.gui.api;

import com.fasterxml.jackson.databind.JsonNode;
import models.common.Study;
import org.junit.Test;
import play.libs.ws.WSResponse;
import testutils.JatosTest;

import static org.assertj.core.api.Assertions.assertThat;
import static play.mvc.Http.HeaderNames.CONTENT_DISPOSITION;
import static play.test.Helpers.NOT_FOUND;
import static play.test.Helpers.OK;

/**
 * Integration tests for {@link controllers.gui.api.StudyAssetsApi}.
 */
public class StudyAssetsApiTest extends JatosTest {

    // ---------- /jatos/api/v1/studies/:id/assets/structure ----------

    @Test
    public void getStudyAssetsStructure_returnsStructureJson() {
        Study study = importAndGetExampleStudy();

        WSResponse resp = api("/jatos/api/v1/studies/" + study.getId() + "/assets/structure").get();

        assertThat(resp.getStatus()).isEqualTo(OK);
        JsonNode data = resp.asJson().get("data");
        assertThat(data).isNotNull();
    }

    // ---------- /jatos/api/v1/studies/:id/assets/*filepath ----------

    @Test
    public void downloadStudyAssetsFile_withUnknownFile_returnsNotFound() {
        Study study = importAndGetExampleStudy();

        WSResponse resp = api("/jatos/api/v1/studies/" + study.getId() + "/assets/non-existent.txt").get();

        assertThat(resp.getStatus()).isEqualTo(NOT_FOUND);
    }

    @Test
    public void downloadStudyAssetsFile_returnsFile() {
        Study study = importAndGetExampleStudy();

        WSResponse resp = api("/jatos/api/v1/studies/" + study.getId() + "/assets/demographics.html").get();

        assertThat(resp.getStatus()).isEqualTo(OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getSingleHeader(CONTENT_DISPOSITION).orElseThrow()).isEqualTo("attachment; filename=\"demographics.html\"");
    }

    @Test
    public void deleteStudyAssetsFile_withUnknownFile_returnsNotFound() {
        Study study = importAndGetExampleStudy();

        WSResponse resp = api("/jatos/api/v1/studies/" + study.getId() + "/assets/non-existent.txt").delete();

        assertThat(resp.getStatus()).isEqualTo(NOT_FOUND);
    }
}