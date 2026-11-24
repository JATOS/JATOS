package controllers.gui;

import models.common.User;
import org.junit.Test;
import play.mvc.Http;
import services.gui.importexport.StudyArchiveImportStorage;
import testutils.JatosTest;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;
import static play.test.Helpers.*;
import static services.gui.importexport.StudyArchiveImportExport.SESSION_TEMP_IMPORT_STUDY_DIR;

public class StudyImportCancellationTest extends JatosTest {

    private Http.RequestBuilder request(User user, String pending, String csrf, boolean sendToken) {
        var request = fakeRequest(DELETE, "/jatos/import/study")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Cookie", "PLAY_SESSION=test-session")
                .cookie(Http.Cookie.builder("PLAY_SESSION", "test-session").build())
                .session("username", user.getUsername())
                .session("signinTime", String.valueOf(System.currentTimeMillis()))
                .session("lastActivityTime", String.valueOf(System.currentTimeMillis()))
                .session("csrfToken", csrf)
                .session(SESSION_TEMP_IMPORT_STUDY_DIR, pending);
        if (sendToken) request.header("Csrf-Token", csrf);
        return request;
    }

    @Test public void cancellationRequiresSessionAuthorizationAndCsrfAndIsIdempotent() {
        StudyArchiveImportStorage store = application.injector().instanceOf(StudyArchiveImportStorage.class);
        Path pending = store.prepare(path -> path).getParent();
        Path other = store.prepare(path -> path).getParent();
        String token = application.injector().instanceOf(play.filters.csrf.CSRF.TokenProvider.class).generateToken();
        String name = pending.getFileName().toString();
        var denied = route(application, request(admin, name, token, false));
        assertEquals(contentAsString(denied), FORBIDDEN, denied.status());
        assertTrue(Files.exists(pending));
        User viewer = createUser("cancel-viewer");
        jpaApi.withTransaction(em -> { em.find(User.class, viewer.getUsername()).updateRoles(User.Role.VIEWER); });
        assertEquals(FORBIDDEN, route(application, request(viewer, name, token, true)).status());
        assertTrue(Files.exists(pending));
        assertEquals(NO_CONTENT, route(application, request(admin, name, token, true)).status());
        assertFalse(Files.exists(pending));
        assertTrue(Files.exists(other));
        assertEquals(NO_CONTENT, route(application, request(admin, name, token, true)).status());
        store.remove(other);
    }
}
