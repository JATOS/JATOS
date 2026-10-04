package controllers.gui.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import models.common.Study;
import org.junit.Test;
import play.libs.Json;
import play.libs.ws.WSResponse;
import testutils.JatosTest;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Wire contracts derived from v3.11.3's Api, ApiEnvelope and Component JsonForApi view. */
public class ApiJsonCompatibilityTest extends JatosTest {
    @Test public void componentShapeLegacyAliasAndIdOrUuidRoundTrip() {
        Study study = importAndGetExampleStudy();
        String collection = "/jatos/api/v1/studies/" + study.getUuid() + "/components";
        // jsonData is the historical alias; nested input must be JSON, not a quoted JSON string.
        JsonNode input = Json.parse("{\"text\":\"Grüße 日本語 😀\",\"large\":9007199254740993,\"nil\":null,\"list\":[true,1.25]}");
        ObjectNode body = Json.newObject().put("title", "Compatibility ü").put("htmlFilePath", "index.html");
        body.set("jsonData", input);
        JsonNode data = success(api(collection).post(body), 200);
        assertThat(data.get("id").isIntegralNumber()).isTrue();
        //noinspection ResultOfMethodCallIgnored
        UUID.fromString(data.get("uuid").asText());
        ObjectNode expected = Json.newObject().put("id", data.get("id").asLong())
                .put("uuid", data.get("uuid").asText()).put("title", "Compatibility ü")
                .put("htmlFilePath", "index.html").put("reloadable", false).put("active", true);
        expected.putNull("comments");
        expected.set("componentInput", input);
        assertThat(data).isEqualTo(Json.parse(expected.toString()));
        String byId = "/jatos/api/v1/components/" + data.get("id").asLong();
        assertThat(success(api(byId).get(), 200)).isEqualTo(Json.parse(expected.toString()));
        assertThat(success(api("/jatos/api/v1/components/" + data.get("uuid").asText()).get(), 200)).isEqualTo(Json.parse(expected.toString()));

        // PATCH preserves omitted properties and accepts string-encoded legacy input too.
        ObjectNode patch = Json.newObject().put("componentInput", "[1,null,\"ä\"]").put("active", false);
        JsonNode updated = success(api(byId).patch(patch), 200);
        expected.set("componentInput", Json.parse("[1,null,\"ä\"]"));
        expected.put("active", false);
        assertThat(updated).isEqualTo(Json.parse(expected.toString()));
        assertThat(success(api(byId).patch(Json.newObject().putNull("componentInput")), 200)
                .get("componentInput").isNull()).isTrue();
    }

    @Test public void malformedAndWrongShapeBodiesReturnJson400() {
        Study study = importAndGetExampleStudy();
        String path = "/jatos/api/v1/studies/" + study.getId() + "/components";
        for (String body : new String[]{"{\"title\":", "[]", "null", "true", "\"text\""}) {
            error(api(path).header("Content-Type", "application/json").post(body), 400, "INVALID_JSON");
        }
        error(api(path).header("Content-Type", "application/json").post(""), 400, "INVALID_REQUEST");
    }

    @Test public void invalidPatchDoesNotPartiallyPersist() {
        Study study = importAndGetExampleStudy();
        String path = "/jatos/api/v1/components/" + study.getComponentList().getFirst().getId();
        JsonNode original = success(api(path).get(), 200);
        for (JsonNode invalid : new JsonNode[]{
                Json.newObject().put("title", "Must not persist").put("unknownProperty", 1),
                Json.newObject().put("active", "false"),
                Json.newObject().putNull("active")}) {
            error(api(path).patch(invalid), 400, "VALIDATION_ERROR");
            assertThat(success(api(path).get(), 200)).isEqualTo(original);
        }
    }

    @Test public void errorsKeepVersionAndEnvelopeWithoutSuccessPayload() {
        error(api("/jatos/api/v1/admin/token").noAuth().get(), 401, "AUTH_ERROR");
        error(api("/jatos/api/v1/admin/status").token(createApiToken(createUser("compat-user"))).get(),
                403, "INVALID_API_TOKEN");
        error(api("/jatos/api/v1/does-not-exist").get(), 404, "NOT_FOUND");
        error(api("/jatos/api/v1/components/9223372036854775807").get(), 404, "NOT_FOUND");
    }

    @Test public void tokenDatesRemainIsoStringsAndSecretsStayHidden() {
        JsonNode data = success(api("/jatos/api/v1/admin/token").get(), 200);
        assertThat(data.get("creationDate").isTextual()).isTrue();
        //noinspection ResultOfMethodCallIgnored
        OffsetDateTime.parse(data.get("creationDate").asText());
        assertThat(data.get("expirationDate").isTextual()).isTrue();
        assertThat(OffsetDateTime.parse(data.get("expirationDate").asText()).toInstant())
                .isEqualTo(java.time.Instant.EPOCH);
        assertThat(data.has("tokenHash")).isFalse();
        assertThat(data.has("token")).isFalse();
    }

    @Test public void headStudyHasNoBodyAndSupportsUuid() {
        Study study = importAndGetExampleStudy();
        for (String id : new String[]{study.getId().toString(), study.getUuid()}) {
            WSResponse response = api("/jatos/api/v1/studies/" + id).head();
            assertThat(response.getStatus()).isEqualTo(204);
            assertThat(response.getBody()).isEmpty();
        }
    }

    private JsonNode success(WSResponse response, @SuppressWarnings("SameParameterValue") int status) {
        assertThat(response.getStatus()).as(response.getBody()).isEqualTo(status);
        assertThat(response.getContentType()).startsWith("application/json");
        JsonNode json = response.asJson();
        assertThat(json.get("apiVersion").isTextual()).isTrue();
        assertThat(json.get("apiVersion").asText()).isEqualTo("1.1.0");
        assertThat(json.has("error")).isFalse();
        assertThat(json.has("data")).isTrue();
        return json.get("data");
    }

    private void error(WSResponse response, int status, String code) {
        assertThat(response.getStatus()).as(response.getBody()).isEqualTo(status);
        assertThat(response.getContentType()).startsWith("application/json");
        JsonNode json = response.asJson();
        assertThat(json.get("apiVersion").asText()).isEqualTo("1.1.0");
        assertThat(json.get("error").get("code").asText()).isEqualTo(code);
        assertThat(json.get("error").get("message").isTextual()).isTrue();
        assertThat(json.has("data")).isFalse();
        assertThat(json.has("message")).isFalse();
    }
}
