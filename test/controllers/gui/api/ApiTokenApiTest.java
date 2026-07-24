package controllers.gui.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import models.common.User;
import org.junit.Test;
import play.libs.Json;
import play.libs.ws.WSResponse;
import testutils.JatosTest;

import static org.assertj.core.api.Assertions.assertThat;
import static play.test.Helpers.*;

/**
 * Integration tests for {@link controllers.gui.api.ApiTokenApi}.
 */
public class ApiTokenApiTest extends JatosTest {

    // ---------- /jatos/api/v1/tokens/current ----------

    @Test
    public void currentApiTokenMetadata_withValidToken_returnsMetadata() {
        WSResponse resp = api("/jatos/api/v1/admin/token").get();

        assertThat(resp.getStatus()).isEqualTo(OK);
        JsonNode data = resp.asJson().get("data");
        assertThat(data.get("name").asText()).isEqualTo("test-token");
        assertThat(data.get("username").asText()).isEqualTo(admin.getUsername());
    }

    @Test
    public void currentApiTokenMetadata_withoutAuth_isUnauthorized() {
        WSResponse resp = api("/jatos/api/v1/admin/token").noAuth().get();
        assertThat(resp.getStatus()).isEqualTo(UNAUTHORIZED);
    }

    // ---------- /jatos/api/v1/users/:userId/tokens ----------

    @Test
    public void generateApiToken_withValidData_createsToken() {
        ObjectNode body = Json.newObject();
        body.put("name", "new-token");

        WSResponse resp = api("/jatos/api/v1/users/" + admin.getId() + "/tokens").post(body);

        assertThat(resp.getStatus()).isEqualTo(CREATED);
        JsonNode data = resp.asJson().get("data");
        assertThat(data.get("name").asText()).isEqualTo("new-token");
        assertThat(data.get("token").asText()).isNotBlank();
    }

    @Test
    public void allApiTokenMetadataByUser_returnsTokensList() {
        WSResponse resp = api("/jatos/api/v1/users/" + admin.getId() + "/tokens").get();

        assertThat(resp.getStatus()).isEqualTo(OK);
        JsonNode data = resp.asJson().get("data");
        assertThat(data.isArray()).isTrue();
        assertThat(data.size()).isGreaterThanOrEqualTo(1);
    }

    @Test
    public void allApiTokenMetadataByUser_anotherUser_isForbidden() {
        // Admin can query every user's tokens
        User otherUser = createUser("other-user");
        WSResponse resp = api("/jatos/api/v1/users/" + otherUser.getId() + "/tokens").get();
        assertThat(resp.getStatus()).isEqualTo(OK);

        // Admin can access other users, so we test with a regular user
        String regularToken = createApiToken(createUser("regular"));
        WSResponse respRegular = api("/jatos/api/v1/users/" + otherUser.getId() + "/tokens")
                .token(regularToken).get();
        assertThat(respRegular.getStatus()).isEqualTo(FORBIDDEN);
    }

    // ---------- /jatos/api/v1/tokens/:id ----------

    @Test
    public void toggleApiTokenActive_updatesStatus() {
        // First, generate a token to get an ID
        ObjectNode body = Json.newObject();
        body.put("name", "toggle-test");
        JsonNode createdData = api("/jatos/api/v1/users/" + admin.getId() + "/tokens").post(body).asJson().get("data");
        long tokenId = createdData.get("id").asLong();

        // Deactivate
        ObjectNode toggleBody = Json.newObject();
        toggleBody.put("active", false);
        WSResponse resp = api("/jatos/api/v1/tokens/" + tokenId).patch(toggleBody);

        assertThat(resp.getStatus()).isEqualTo(OK);
        assertThat(resp.asJson().get("data").get("active").asBoolean()).isFalse();
    }

    @Test
    public void deleteApiToken_removesToken() {
        ObjectNode body = Json.newObject();
        body.put("name", "delete-test");
        JsonNode createdData = api("/jatos/api/v1/users/" + admin.getId() + "/tokens").post(body).asJson().get("data");
        long tokenId = createdData.get("id").asLong();

        WSResponse resp = api("/jatos/api/v1/tokens/" + tokenId).delete();

        assertThat(resp.getStatus()).isEqualTo(OK);
        assertThat(resp.asJson().get("message").asText()).contains("Token deleted successfully");

        // Verify it's gone
        WSResponse getResp = api("/jatos/api/v1/tokens/" + tokenId).get();
        assertThat(getResp.getStatus()).isEqualTo(NOT_FOUND);
    }
}