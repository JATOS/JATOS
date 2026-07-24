package controllers.gui.api;

import com.fasterxml.jackson.databind.JsonNode;
import models.common.Study;
import org.junit.Test;
import play.libs.ws.WSResponse;
import testutils.JatosTest;

import static org.assertj.core.api.Assertions.assertThat;
import static play.test.Helpers.OK;

/**
 * Integration tests for {@link controllers.gui.api.ResultApi}.
 */
public class ResultApiTest extends JatosTest {

    // ---------- /jatos/api/v1/results ----------

    @Test
    public void exportResults_returnsZipArchive() {
        Study study = importAndGetExampleStudy();

        WSResponse resp = api("/jatos/api/v1/results").queryParam("studyId", String.valueOf(study.getId())).get();

        assertThat(resp.getStatus()).isEqualTo(OK);
        assertThat(resp.getSingleHeader("Content-Type")).contains("application/zip");
    }

    // ---------- /jatos/api/v1/results/metadata ----------

    @Test
    public void exportResultMetadata_returnsMetadataJson() {
        Study study = importAndGetExampleStudy();

        WSResponse resp = api("/jatos/api/v1/results/metadata").queryParam("studyId", String.valueOf(study.getId())).get();

        assertThat(resp.getStatus()).isEqualTo(OK);
        JsonNode body = resp.asJson();
        assertThat(body).isNotNull();
        assertThat(body.get("apiVersion")).isNotNull();
    }

    // ---------- /jatos/api/v1/results/data ----------

    @Test
    public void exportResultData_returnsDataZip() {
        Study study = importAndGetExampleStudy();

        WSResponse resp = api("/jatos/api/v1/results/data").queryParam("studyId", String.valueOf(study.getId())).get();

        assertThat(resp.getStatus()).isEqualTo(OK);
        assertThat(resp.getSingleHeader("Content-Type")).contains("application/zip");
    }

    // ---------- /jatos/api/v1/results/files ----------

    @Test
    public void exportResultFiles_returnsFilesZip() {
        Study study = importAndGetExampleStudy();

        WSResponse resp = api("/jatos/api/v1/results/files").queryParam("studyId", String.valueOf(study.getId())).get();

        assertThat(resp.getStatus()).isEqualTo(OK);
        assertThat(resp.getSingleHeader("Content-Type")).contains("application/zip");
    }
}