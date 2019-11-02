package controllers.gui.useraccess;

import daos.common.StudyDao;
import general.TestHelper;
import general.common.Common;
import models.common.Study;
import models.common.User;
import play.Application;
import play.api.mvc.Call;
import play.db.jpa.JPAApi;
import play.mvc.Http;
import play.mvc.Http.RequestBuilder;
import play.mvc.Result;
import services.gui.AuthenticationService;

import javax.inject.Inject;
import javax.inject.Singleton;

import static org.fest.assertions.Assertions.assertThat;
import static play.mvc.Http.Status.*;
import static play.test.Helpers.route;

/**
 * Helper methods for testing user access to controller actions
 *
 * @author Kristian Lange (2015 - 2017)
 */
@Singleton
public class UserAccessTestHelpers {

    @Inject
    private Application fakeApplication;

    @Inject
    private TestHelper testHelper;

    @Inject
    private JPAApi jpaApi;

    @Inject
    private StudyDao studyDao;

    /**
     * Calls the given call/action without a user in the session: nobody is
     * logged in. This should trigger a redirect to the log-in page and a HTTP
     * status 303 (See Other).
     */
    public void checkDeniedAccessAndRedirectToLogin(Call call) {
        Result result = route(fakeApplication, call);
        assertThat(result.status()).isEqualTo(SEE_OTHER);
        assertThat(result.redirectLocation().get()).contains(Common.getPlayHttpContext() + "jatos/login");
    }

    public void checkAccessGranted(Call call, String method, User user) {
        checkAccessGranted(call, method, user, null);
    }

    public void checkAccessGranted(Call call, String method, User user, String bodyText) {
        Http.Session session = testHelper.mockSessionCookieandCache(user);
        RequestBuilder request = new RequestBuilder()
                .method(method)
                .session(session)
                .remoteAddress(TestHelper.WWW_EXAMPLE_COM)
                .uri(call.url());
        if (bodyText != null) {
            request = request.bodyText(bodyText)
                    .header("Content-Type", "application/json");
        }
        Result result = route(fakeApplication, request);

        assertThat(result.status()).isEqualTo(OK);
    }

    /**
     * Calls the given action/call with the given method (GET, POST, ...) with
     * an User that does not have the ADMIN role. This must lead to an HTTP
     * FORBIDDEN.
     */
    public void checkDeniedAccessDueToAuthorization(Call call, String method) {
        // Persist User without ADMIN role
        User user = testHelper.createAndPersistUser(TestHelper.BLA_EMAIL, "Bla Bla", "bla");
        Http.Session session = testHelper.mockSessionCookieandCache(user);

        RequestBuilder request = new RequestBuilder()
                .method(method)
                .session(session)
                .remoteAddress(TestHelper.WWW_EXAMPLE_COM)
                .uri(call.url());
        Result result = route(fakeApplication, request);

        assertThat(result.status()).isEqualTo(FORBIDDEN);
    }

    /**
     * Removes the admin user (!) from the users who have permission in this
     * study. Then calls the action with the admin user logged-in (in the
     * session). This should trigger a JatosGuiException with a 403 HTTP code.
     */
    public void checkNotTheRightUserForStudy(Call call, Long studyId,
            String method) {
        User admin = testHelper.getAdmin();
        // We have to get the study from the database again because it's
        // detached (Hibernate)
        jpaApi.withTransaction(() -> {
            Study study = studyDao.findById(studyId);
            study.removeUser(admin);
            studyDao.update(study);
        });

        checkThatCallIsForbidden(call, method, admin, "", "isn't user of study");

        jpaApi.withTransaction(() -> {
            Study study = studyDao.findById(studyId);
            study.addUser(admin);
            studyDao.update(study);
        });
    }

    /**
     * Check that the given Call and HTTP method does lead to an
     * JatosGuiException with a HTTP status code 303 (See Other). Uses the given
     * user in the session for authentication.
     */
    public void checkThatCallLeadsToRedirect(Call call, String method) {
        User admin = testHelper.getAdmin();
        RequestBuilder request = new RequestBuilder()
                .method(method)
                .session(AuthenticationService.SESSION_USER_EMAIL, admin.getEmail())
                .uri(call.url());

        testHelper.assertJatosGuiException(request, Http.Status.SEE_OTHER, "");
    }

    /**
     * Check that the given Call and HTTP method does lead to an
     * JatosGuiException with a HTTP status code 403. Uses the given user in the
     * session for authentication.
     */
    public void checkThatCallIsForbidden(Call call, String method, User user, String bodyText, String errorMsg) {
        Http.Session session = testHelper.mockSessionCookieandCache(user);
        RequestBuilder request = new RequestBuilder()
                .method(method)
                .session(session)
                .remoteAddress(TestHelper.WWW_EXAMPLE_COM)
                .uri(call.url());
        if (bodyText != null) {
            request = request.bodyText(bodyText)
                    .header("Content-Type", "application/json");
        }
        testHelper.assertJatosGuiException(request, Http.Status.FORBIDDEN, errorMsg);
    }

}
