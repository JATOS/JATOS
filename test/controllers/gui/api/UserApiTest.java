package controllers.gui.api;

import models.common.Study;
import models.common.User;
import org.junit.Test;
import play.libs.Json;
import testutils.JatosTest;

import static org.assertj.core.api.Assertions.assertThat;
import static play.test.Helpers.OK;

public class UserApiTest extends JatosTest {
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
