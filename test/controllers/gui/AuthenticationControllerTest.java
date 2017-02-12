package controllers.gui;

import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static play.mvc.Http.Status.OK;
import static play.mvc.Http.Status.SEE_OTHER;
import static play.test.Helpers.contentAsString;
import static play.test.Helpers.route;

import javax.inject.Inject;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Guice;

import general.TestHelper;
import models.common.User;
import play.Application;
import play.ApplicationLoader;
import play.Environment;
import play.inject.guice.GuiceApplicationBuilder;
import play.inject.guice.GuiceApplicationLoader;
import play.mvc.Http.RequestBuilder;
import play.mvc.Result;
import play.test.Helpers;
import services.gui.UserService;

/**
 * Testing controller.Authentication
 * 
 * @author Kristian Lange
 */
public class AuthenticationControllerTest {

	@Inject
	private static Application fakeApplication;

	@Inject
	private TestHelper testHelper;

	@Before
	public void startApp() throws Exception {
		fakeApplication = Helpers.fakeApplication();

		GuiceApplicationBuilder builder = new GuiceApplicationLoader()
				.builder(new ApplicationLoader.Context(Environment.simple()));
		Guice.createInjector(builder.applicationModule()).injectMembers(this);

		Helpers.start(fakeApplication);
	}

	@After
	public void stopApp() throws Exception {
		Helpers.stop(fakeApplication);
		testHelper.removeStudyAssetsRootDir();
	}

	/**
	 * Test Authentication.login()
	 */
	@Test
	public void callLogin() {
		User admin = testHelper.getAdmin();
		RequestBuilder request = new RequestBuilder().method("GET")
				.session(Users.SESSION_EMAIL, admin.getEmail())
				.uri(controllers.gui.routes.Authentication.login().url());
		Result result = route(request);

		assertThat(result.status()).isEqualTo(OK);
		assertThat(result.charset().get()).isEqualTo("utf-8");
		assertThat(result.contentType().get()).isEqualTo("text/html");
		assertThat(contentAsString(result)).contains("login");
	}

	/**
	 * Test Authentication.logout()
	 */
	@Test
	public void callLogout() throws Exception {
		User admin = testHelper.getAdmin();
		RequestBuilder request = new RequestBuilder().method("GET")
				.session(Users.SESSION_EMAIL, admin.getEmail())
				.uri(controllers.gui.routes.Authentication.logout().url());
		Result result = route(request);

		// Check that it redirects to the login page
		assertThat(result.status()).isEqualTo(SEE_OTHER);
		assertThat(result.redirectLocation().get()).contains("login");
		assertThat(!result.session().containsKey(Users.SESSION_EMAIL));
	}

	/**
	 * Test Authentication.authenticate()
	 */
	@Test
	public void authenticateSuccess() {
		RequestBuilder request = new RequestBuilder().method("POST")
				.bodyForm(ImmutableMap.of(User.EMAIL, UserService.ADMIN_EMAIL,
						User.PASSWORD, UserService.ADMIN_PASSWORD))
				.uri(controllers.gui.routes.Authentication.authenticate()
						.url());
		Result result = route(request);

		// Successful login leads to a redirect and the user's email is in the
		// session
		assertEquals(303, result.status());
		assertEquals(UserService.ADMIN_EMAIL, result.session().get(User.EMAIL));
	}

	/**
	 * Test Authentication.authenticate()
	 */
	@Test
	public void authenticateFailure() {
		RequestBuilder request = new RequestBuilder().method("POST")
				.bodyForm(ImmutableMap.of(User.EMAIL, UserService.ADMIN_EMAIL,
						User.PASSWORD, "bla"))
				.uri(controllers.gui.routes.Authentication.authenticate()
						.url());
		Result result = route(request);

		// Fail to login leads to a Bad Request (400)
		assertEquals(400, result.status());
		assertNull(result.session().get(User.EMAIL));
	}

}
