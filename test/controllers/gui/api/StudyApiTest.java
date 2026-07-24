package controllers.gui.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import models.common.Study;
import models.common.User;
import org.junit.Test;
import play.libs.Json;
import play.libs.ws.WSResponse;
import testutils.JatosTest;

import static org.assertj.core.api.Assertions.assertThat;
import static play.test.Helpers.*;

/**
 * Integration tests for {@link controllers.gui.api.StudyApi}.
 */
public class StudyApiTest extends JatosTest {

    // ---------- /jatos/api/v1/studies/:id (HEAD) ----------

    @Test
    public void checkStudyExists_withExistingStudy_returnsNoContent() {
        Study study = importAndGetExampleStudy();

        WSResponse resp = api("/jatos/api/v1/studies/" + study.getId()).raw().execute("HEAD").toCompletableFuture().join();

        assertThat(resp.getStatus()).isEqualTo(NO_CONTENT);
    }

    @Test
    public void checkStudyExists_withNonExistingStudy_returnsNotFound() {
        WSResponse resp = api("/jatos/api/v1/studies/99999").raw().execute("HEAD").toCompletableFuture().join();

        assertThat(resp.getStatus()).isEqualTo(NOT_FOUND);
    }

    // ---------- /jatos/api/v1/studies/properties ----------

    @Test
    public void getAllStudyPropertiesOfSignedinUser_returnsStudiesList() {
        importAndGetExampleStudy();

        WSResponse resp = api("/jatos/api/v1/studies/properties").get();

        assertThat(resp.getStatus()).isEqualTo(OK);
        JsonNode data = resp.asJson().get("data");
        assertThat(data.isArray()).isTrue();
        assertThat(data.size()).isGreaterThanOrEqualTo(1);
    }

    // ---------- /jatos/api/v1/studies ----------

    @Test
    public void createStudy_withValidJson_returnsCreatedStudy() {
        ObjectNode body = Json.newObject();
        body.put("title", "New Api Study");
        body.put("dirName", "new_api_study");
        body.set("studyInput", Json.newObject());

        WSResponse resp = api("/jatos/api/v1/studies").post(body);

        assertThat(resp.getStatus()).isEqualTo(CREATED);
        JsonNode data = resp.asJson().get("data");
        assertThat(data.get("title").asText()).isEqualTo("New Api Study");
        assertThat(data.get("id")).isNotNull();
    }

    // ---------- /jatos/api/v1/studies/:id ----------

    @Test
    public void getStudyProperties_returnsProperties() {
        Study study = importAndGetExampleStudy();

        WSResponse resp = api("/jatos/api/v1/studies/" + study.getId() + "/properties").get();

        assertThat(resp.getStatus()).isEqualTo(OK);
        JsonNode data = resp.asJson().get("data");
        assertThat(data.get("id").asLong()).isEqualTo(study.getId());
    }

    @Test
    public void exportStudy_returnsZipArchive() {
        Study study = importAndGetExampleStudy();

        WSResponse resp = api("/jatos/api/v1/studies/" + study.getId()).get();

        assertThat(resp.getStatus()).isEqualTo(OK);
        assertThat(resp.getSingleHeader("Content-Type").orElseThrow()).contains("application/zip");
        assertThat(resp.asByteArray().length).isGreaterThan(0);
        assertThat(resp.getSingleHeader("Content-Disposition").orElseThrow()).contains("attachment");
    }

    @Test
    public void deleteStudy_removesStudy() {
        Study study = importAndGetExampleStudy();

        WSResponse resp = api("/jatos/api/v1/studies/" + study.getId()).delete();

        assertThat(resp.getStatus()).isEqualTo(OK);
        assertThat(resp.asJson().get("message").asText()).contains("Study deleted successfully");

        WSResponse checkResp = api("/jatos/api/v1/studies/" + study.getId()).raw().execute("HEAD").toCompletableFuture().join();
        assertThat(checkResp.getStatus()).isEqualTo(NOT_FOUND);
    }

    // ---------- Members ----------

    @Test
    public void allMembersOfStudy_returnsMembersList() {
        Study study = importAndGetExampleStudy();

        WSResponse resp = api("/jatos/api/v1/studies/" + study.getId() + "/members").get();

        assertThat(resp.getStatus()).isEqualTo(OK);
        JsonNode data = resp.asJson().get("data");
        assertThat(data.get("members").isArray()).isTrue();
    }

    @Test
    public void addAndRemoveMemberFromStudy() {
        Study study = importAndGetExampleStudy();
        User regularUser = createUser("study-member-user");

        // Add member
        WSResponse addResp = api("/jatos/api/v1/studies/" + study.getId() + "/members/" + regularUser.getId()).put(Json.newObject());
        assertThat(addResp.getStatus()).isEqualTo(OK);

        // Remove member
        WSResponse removeResp = api("/jatos/api/v1/studies/" + study.getId() + "/members/" + regularUser.getId()).delete();
        assertThat(removeResp.getStatus()).isEqualTo(OK);
    }
}