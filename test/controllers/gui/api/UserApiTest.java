package controllers.gui.api;

import com.fasterxml.jackson.databind.node.ObjectNode;
import models.common.Study;
import models.common.User;
import org.junit.Test;
import play.libs.Json;
import services.gui.importexport.GitCredentialsService;
import testutils.JatosTest;

import static org.assertj.core.api.Assertions.assertThat;
import static play.test.Helpers.OK;

public class UserApiTest extends JatosTest {

    private ObjectNode credentialUpdate(String secret) {
        var body = Json.newObject();
        body.putObject("gitCredentials").put("origin", "https://github.com")
                .put("username", "git-user").put("secret", secret);
        return body;
    }

    @Test
    public void settingsValidationReturnsErrorsForIndividualFields() {
        String path = "/jatos/api/v1/users/" + admin.getId();
        var profile = api(path).patch(Json.newObject().put("name", "").put("email", "<b>invalid</b>"));
        assertThat(profile.getStatus()).isEqualTo(400);
        var fields = profile.asJson().path("error").path("fields");
        assertThat(fields.path("name").isArray()).isTrue();
        assertThat(fields.path("email").isArray()).isTrue();
        assertThat(profile.asJson().path("error").path("code").asText()).isEqualTo("VALIDATION_ERROR");

        var invalidOrigin = credentialUpdate("private-token");
        ((ObjectNode) invalidOrigin.get("gitCredentials")).put("origin", "http://github.com");
        var origin = api(path).patch(invalidOrigin);
        assertThat(origin.getStatus()).isEqualTo(400);
        assertThat(origin.asJson().path("error").path("fields").path("gitCredentials.origin").isArray()).isTrue();
        assertThat(origin.getBody()).doesNotContain("private-token");

        var invalidUsername = credentialUpdate("private-token");
        ((ObjectNode) invalidUsername.get("gitCredentials")).put("username", "");
        var username = api(path).patch(invalidUsername);
        assertThat(username.getStatus()).isEqualTo(400);
        assertThat(username.asJson().path("error").path("fields").path("gitCredentials.username").isArray()).isTrue();
        var secret = api(path).patch(credentialUpdate(""));
        assertThat(secret.getStatus()).isEqualTo(400);
        assertThat(secret.asJson().path("error").path("fields").path("gitCredentials.secret").isArray()).isTrue();
        assertThat(api(path).get().asJson().path("data").path("gitCredentials").path("configured").asBoolean()).isFalse();
    }

    @Test
    public void gitCredentialChangesAreLimitedToTheAuthenticatedUser() {
        User user = createUser("git-credential-owner");
        String path = "/jatos/api/v1/users/" + user.getId();
        String token = createApiToken(user);
        assertThat(api(path).token(token).patch(credentialUpdate("personal-token")).getStatus()).isEqualTo(OK);
        assertThat(api(path).get().asJson().path("data").has("gitCredentials")).isFalse();
        assertThat(api(path).patch(credentialUpdate("admin-token")).getStatus()).isEqualTo(403);
        var removal = Json.newObject();
        removal.putObject("gitCredentials").put("removeCredentials", true);
        assertThat(api(path).patch(removal).getStatus()).isEqualTo(403);
        assertThat(api(path).token(token).get().asJson().path("data").path("gitCredentials").path("configured").asBoolean()).isTrue();
        assertThat(api(path).token(token).patch(removal).getStatus()).isEqualTo(OK);
    }

    @Test
    public void ownSettingsSupportPartialCredentialUpdatesWithoutExposingSecrets() {
        String path = "/jatos/api/v1/users/" + admin.getId();
        var saved = api(path).patch(credentialUpdate("first-token").put("name", "New name"));
        assertThat(saved.getStatus()).isEqualTo(OK);
        assertThat(saved.asJson().path("data").path("name").asText()).isEqualTo("New name");
        assertThat(saved.asJson().path("data").path("gitCredentials").path("configured").asBoolean()).isTrue();
        assertThat(saved.asJson().path("data").path("gitCredentials").size()).isEqualTo(3);
        assertThat(saved.getBody()).doesNotContain("first-token", "encryptedSecret");
        assertThat(api(path).patch(Json.newObject().put("email", "test@example.org")).getStatus()).isEqualTo(OK);
        assertThat(api(path).get().asJson().path("data").path("gitCredentials").path("configured").asBoolean()).isTrue();

        var replacement = Json.newObject();
        replacement.putObject("gitCredentials").put("secret", "replacement-token");
        assertThat(api(path).patch(replacement).getStatus()).isEqualTo(OK);
        var preserve = Json.newObject();
        preserve.putObject("gitCredentials").put("secret", "");
        assertThat(api(path).patch(preserve).getStatus()).isEqualTo(OK);
        var loaded = api(path).get();
        assertThat(loaded.asJson().path("data").path("gitCredentials").path("username").asText()).isEqualTo("git-user");
        assertThat(loaded.getBody()).doesNotContain("replacement-token", "encryptedSecret");
        var removal = Json.newObject();
        removal.putObject("gitCredentials").put("removeCredentials", true);
        assertThat(api(path).patch(removal).getStatus()).isEqualTo(OK);
        assertThat(api(path).get().asJson().path("data").path("gitCredentials").path("configured").asBoolean()).isFalse();
    }

    @Test
    public void invalidSettingsLeaveBothProfileAndCredentialsUnchanged() {
        String path = "/jatos/api/v1/users/" + admin.getId();
        assertThat(api(path).patch(credentialUpdate("original-token")).getStatus()).isEqualTo(OK);
        String originalName = api(path).get().asJson().path("data").path("name").asText();
        var invalidCredentials = credentialUpdate("").put("name", "Should not be saved");
        ((ObjectNode) invalidCredentials.get("gitCredentials")).put("username", "changed");
        assertThat(api(path).patch(invalidCredentials).getStatus()).isEqualTo(400);
        assertThat(api(path).get().asJson().path("data").path("name").asText()).isEqualTo(originalName);
        assertThat(api(path).patch(credentialUpdate("replacement-token").put("name", "")).getStatus()).isEqualTo(400);
        var conflicting = credentialUpdate("replacement-token");
        ((ObjectNode) conflicting.get("gitCredentials")).put("removeCredentials", true);
        assertThat(api(path).patch(conflicting).getStatus()).isEqualTo(400);
        var crypto = application.injector().instanceOf(utils.common.GitCredentialEncryption.class);
        jpaApi.withTransaction(em -> {
            var credential = em.find(User.class, admin.getUsername()).getGitCredentials();
            assertThat(crypto.decrypt(credential.getEncryptedSecret(), GitCredentialsService.encryptionContext(credential)))
                    .isEqualTo("original-token");
        });
    }

    @Test
    public void externalAccountsCanUpdateCredentialsButNotTheirProfile() {
        User user = createUser("external-git-owner");
        jpaApi.withTransaction(em -> { em.find(User.class, user.getUsername()).setAuthMethod(User.AuthMethod.OIDC); });
        String token = createApiToken(user);
        String path = "/jatos/api/v1/users/" + user.getId();
        assertThat(api(path).token(token).patch(credentialUpdate("token")).getStatus()).isEqualTo(OK);
        assertThat(api(path).token(token).patch(Json.newObject().put("name", "Changed")).getStatus()).isEqualTo(403);
    }

    @Test
    public void settingsRequireAuthenticationAndValidInput() {
        String path = "/jatos/api/v1/users/" + admin.getId();
        assertThat(api(path).noAuth().get().getStatus()).isEqualTo(401);
        assertThat(api(path).noAuth().patch(credentialUpdate("token")).getStatus()).isEqualTo(401);
        assertThat(api(path).patch(Json.newArray()).getStatus()).isEqualTo(400);
        assertThat(api(path).patch(Json.newObject().putNull("gitCredentials")).getStatus()).isEqualTo(400);
    }

    @Test
    public void deletionReturnsOwnershipReassignments() {
        Study study = importAndGetExampleStudy();
        User owner = createUser("departing-owner");
        String studyPath = "/jatos/api/v1/studies/" + study.getId();
        assertThat(api(studyPath + "/members/" + owner.getId()).put(Json.newObject()).getStatus()).isEqualTo(OK);
        assertThat(api(studyPath + "/owner/" + owner.getId()).put(Json.newObject()).getStatus()).isEqualTo(OK);

        var response = api("/jatos/api/v1/users/" + owner.getId()).delete();
        assertThat(response.getStatus()).isEqualTo(OK);
        assertThat(response.asJson().path("message").asText()).isEqualTo("User deleted successfully");
        var changes = response.asJson().path("data").path("ownershipReassignments");
        assertThat(changes.isArray()).isTrue();
        assertThat(changes.size()).isEqualTo(1);
        assertThat(changes.get(0).path("studyId").asLong()).isEqualTo(study.getId());
        assertThat(changes.get(0).path("studyTitle").asText()).isEqualTo(study.getTitle());
        assertThat(changes.get(0).path("newOwnerUsername").asText()).isEqualTo(admin.getUsername());
    }

    @Test
    public void deletionWithoutReassignmentReturnsEmptyArray() {
        User user = createUser("no-studies");
        var response = api("/jatos/api/v1/users/" + user.getId()).delete();
        assertThat(response.getStatus()).isEqualTo(OK);
        var changes = response.asJson().path("data").path("ownershipReassignments");
        assertThat(changes.isArray()).isTrue();
        assertThat(changes.size()).isZero();
    }
}
