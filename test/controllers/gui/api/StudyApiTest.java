package controllers.gui.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import general.common.Common;
import models.common.Study;
import org.apache.pekko.stream.javadsl.FileIO;
import play.libs.ws.SourceBodyWritable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
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

    @Test
    public void membershipGuiIdentifiesOwnerAndRestrictsControls() throws Exception {
        Study study = importAndGetExampleStudy();
        User member = createUser("gui-member");
        assertThat(api("/jatos/api/v1/studies/" + study.getId() + "/members/" + member.getId())
                .put(Json.newObject()).getStatus()).isEqualTo(OK);
        User superuser = createUser("gui-superuser");
        superuser.updateRoles(User.Role.SUPERUSER);
        superuser.updateRoles(User.Role.VIEWER);
        application.injector().instanceOf(daos.common.UserDao.class).merge(superuser);
        var enabledField = Common.class.getDeclaredField("userRoleAllowSuperuser");
        enabledField.setAccessible(true);
        boolean previouslyEnabled = enabledField.getBoolean(null);
        enabledField.setBoolean(null, true);
        try {
        for (User user : java.util.List.of(admin, member, superuser)) {
            var request = fakeRequest(GET, "/jatos/" + study.getId() + "/memberUsers")
                    .cookie(play.mvc.Http.Cookie.builder("PLAY_SESSION", "test-session").build())
                    .session("username", user.getUsername())
                    .session("signinTime", String.valueOf(System.currentTimeMillis()))
                    .session("lastActivityTime", String.valueOf(System.currentTimeMillis()));
            var result = route(application, request);
            assertThat(result.status()).isEqualTo(OK);
            JsonNode body = Json.parse(contentAsString(result));
            assertThat(body.path("isOwner").asBoolean()).isEqualTo(!user.equals(member));
            for (JsonNode row : body.path("data")) {
                assertThat(row.path("isOwner").asBoolean())
                        .isEqualTo(row.path("username").asText().equals(admin.getUsername()));
                assertThat(row.path("id").asLong()).isPositive();
            }
            request.uri("/jatos/" + study.getId());
            String html = contentAsString(route(application, request));
            assertThat(html.contains("id=\"removeStudy\"" )).isEqualTo(!user.equals(member));
        }
        } finally {
            enabledField.setBoolean(null, previouslyEnabled);
        }
    }

    @Test
    public void ownershipControlsDeletionMembershipAndTransfer() {
        Study study = importAndGetExampleStudy();
        User owner = createUser("owner");
        User member = createUser("member");
        User outsider = createUser("outsider");
        String adminToken = apiToken;
        String path = "/jatos/api/v1/studies/" + study.getId();
        assertThat(api(path + "/members/" + owner.getId()).put(Json.newObject()).getStatus()).isEqualTo(OK);
        assertThat(api(path + "/members/" + member.getId()).put(Json.newObject()).getStatus()).isEqualTo(OK);
        assertThat(api(path + "/owner/" + owner.getId()).put(Json.newObject()).getStatus()).isEqualTo(OK);

        // An administrator who is not the owner cannot manage membership.
        assertThat(api(path + "/members/" + outsider.getId()).put(Json.newObject()).getStatus()).isEqualTo(FORBIDDEN);
        assertThat(api(path + "/members/" + member.getId()).delete().getStatus()).isEqualTo(FORBIDDEN);
        apiToken = createApiToken(member);
        assertThat(api(path).delete().getStatus()).isEqualTo(FORBIDDEN);
        assertThat(api(path + "/members/" + outsider.getId()).put(Json.newObject()).getStatus()).isEqualTo(FORBIDDEN);
        assertThat(api(path + "/members/" + member.getId()).delete().getStatus()).isEqualTo(OK);
        assertThat(api(path + "/members/" + member.getId()).put(Json.newObject()).getStatus()).isEqualTo(FORBIDDEN);
        apiToken = createApiToken(owner);
        assertThat(api(path + "/members/" + member.getId()).put(Json.newObject()).getStatus()).isEqualTo(OK);
        apiToken = createApiToken(member);
        assertThat(api(path + "/members/" + owner.getId()).delete().getStatus()).isEqualTo(FORBIDDEN);
        assertThat(api(path + "/owner/" + member.getId()).put(Json.newObject()).getStatus()).isEqualTo(FORBIDDEN);

        apiToken = createApiToken(owner);
        assertThat(api(path + "/members/" + owner.getId()).delete().getStatus()).isEqualTo(FORBIDDEN);
        assertThat(api(path + "/owner/" + outsider.getId()).put(Json.newObject()).getStatus()).isEqualTo(FORBIDDEN);
        assertThat(api(path + "/members/" + outsider.getId()).put(Json.newObject()).getStatus()).isEqualTo(OK);
        assertThat(api(path + "/members/" + outsider.getId()).delete().getStatus()).isEqualTo(OK);
        assertThat(api(path + "/members/" + admin.getId()).delete().getStatus()).isEqualTo(OK);
        assertThat(api(path + "/owner/" + member.getId()).put(Json.newObject()).getStatus()).isEqualTo(OK);
        assertThat(api(path + "/members").get().asJson().path("data").path("members").toString())
                .contains(owner.getId().toString());
        assertThat(api(path).delete().getStatus()).isEqualTo(FORBIDDEN);

        // Administrators can recover ownership without membership, but cannot delete directly.
        apiToken = adminToken;
        assertThat(api(path).delete().getStatus()).isEqualTo(FORBIDDEN);
        assertThat(api(path + "/owner/" + owner.getId()).put(Json.newObject()).getStatus()).isEqualTo(OK);
        assertThat(api(path).delete().getStatus()).isEqualTo(FORBIDDEN);
        apiToken = createApiToken(owner);
        assertThat(api(path).delete().getStatus()).isEqualTo(OK);
    }

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

    /** Existing-study imports must serialize lazy associations after the import transaction has committed. */
    @Test
    public void importExistingStudy_keepPropertiesAndAssets_returnsCompleteResponse() throws Exception {
        Study study = importAndGetExampleStudy();
        String propertiesUrl = "/jatos/api/v1/studies/" + study.getId() + "/properties";
        WSResponse updated = api(propertiesUrl).patch(Json.newObject().put("title", "Preserved title"));
        assertThat(updated.getStatus()).isEqualTo(OK);
        JsonNode expected = updated.asJson().path("data");
        Path marker = Path.of(Common.getStudyAssetsRootPath(), study.getDirName(), "preserved.txt");
        Files.writeString(marker, "Preserved asset");
        Path archive = Path.of("test/resources/potato_compass.jzip");

        WSResponse response = api("/jatos/api/v1/studies")
                .queryParam("keepProperties", "true")
                .queryParam("keepAssets", "true")
                .raw()
                .addHeader("Content-Length", Long.toString(Files.size(archive)))
                .setBody(new SourceBodyWritable(FileIO.fromPath(archive), "application/zip"))
                .execute("POST").toCompletableFuture().get(30, TimeUnit.SECONDS);

        assertThat(response.getStatus()).as(response.getBody()).isEqualTo(OK);
        JsonNode data = response.asJson().path("data");
        assertThat(data.path("id").asLong()).isEqualTo(study.getId());
        assertThat(data.path("title").asText()).isEqualTo("Preserved title");
        for (String field : new String[]{"components", "batches", "members"}) {
            assertThat(expected.path(field).isArray()).as(field).isTrue();
            assertThat(expected.path(field).size()).as(field).isPositive();
            assertThat(data.path(field)).as(field).isEqualTo(expected.path(field));
        }
        assertThat(Files.readString(marker)).isEqualTo("Preserved asset");
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