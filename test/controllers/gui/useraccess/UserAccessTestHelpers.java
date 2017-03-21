package controllers.gui.useraccess;

import static org.fest.assertions.Assertions.assertThat;
import static play.mvc.Http.Status.*;
import static play.test.Helpers.route;

import javax.inject.Inject;
import javax.inject.Singleton;

import controllers.gui.Authentication;
import daos.common.StudyDao;
import general.TestHelper;
import models.common.Study;
import models.common.User;
import play.api.mvc.Call;
import play.db.jpa.JPAApi;
import play.mvc.Http;
import play.mvc.Http.RequestBuilder;
import play.mvc.Result;

/**
 * Helper methods for testing user access to controller actions
 * 
 * @author Kristian Lange (2015 - 2017)
 */
@Singleton
public class UserAccessTestHelpers {

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
		Result result = route(call);
		assertThat(result.status()).isEqualTo(SEE_OTHER);
		assertThat(result.redirectLocation().get()).contains("/jatos/login");
	}

	/**
	 * Calls the given action/call with the given method (GET, POST, ...) with
	 * an User that does not have the ADMIN role. This must lead to an HTTP
	 * FORBIDDEN.
	 */
	public void checkDeniedAccessDueToAuthorization(Call call,
			String method) {
		// Persist User without ADMIN role
		testHelper.createAndPersistUser("bla@bla.org", "Bla Bla", "bla");
		RequestBuilder request = new RequestBuilder().method(method)
				.session(Authentication.SESSION_USER_EMAIL, "bla@bla.org")
				.uri(call.url());
		Result result = route(request);

		assertThat(result.status()).isEqualTo(FORBIDDEN);
	}

	/**
	 * Removes the admin user (!) from the users who have permission in this
	 * study. Then calls the action with the admin user logged-in (in the
	 * session). This should trigger a JatosGuiException with a 403 HTTP code.
	 */
	public void checkNotTheRightUser(Call call, Long studyId, String method) {
		User admin = testHelper.getAdmin();
		// We have to get the study from the database again because it's
		// detached (Hibernate)
		jpaApi.withTransaction(() -> {
			Study study = studyDao.findById(studyId);
			study.removeUser(admin);
		});
		checkThatCallIsForbidden(call, method, admin);
	}

	/**
	 * Check that the given Call and HTTP method does lead to an
	 * JatosGuiException with a HTTP status code 303 (See Other). Uses the given
	 * user in the session for authentication.
	 */
	public void checkThatCallLeadsToRedirect(Call call, String method) {
		User admin = testHelper.getAdmin();
		RequestBuilder request = new RequestBuilder().method(method)
				.session(Authentication.SESSION_USER_EMAIL, admin.getEmail())
				.uri(call.url());

		testHelper.assertJatosGuiException(request, Http.Status.SEE_OTHER);
	}

	/**
	 * Check that the given Call and HTTP method does lead to an
	 * JatosGuiException with a HTTP status code 403. Uses the given user in the
	 * session for authentication.
	 */
	public void checkThatCallIsForbidden(Call call, String method, User user) {
		RequestBuilder request = new RequestBuilder().method(method)
				.session(Authentication.SESSION_USER_EMAIL, user.getEmail())
				.uri(call.url());
		testHelper.assertJatosGuiException(request, Http.Status.FORBIDDEN);
	}

}
