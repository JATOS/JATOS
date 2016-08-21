package controllers.gui;

import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static play.mvc.Http.Status.OK;
import static play.mvc.Http.Status.SEE_OTHER;
import static play.test.Helpers.contentAsString;
import static play.test.Helpers.route;

import models.common.User;

import org.junit.Test;

import play.mvc.Result;
import play.mvc.Http.RequestBuilder;
import services.gui.UserService;

import com.google.common.collect.ImmutableMap;

import controllers.gui.Users;
import general.AbstractTest;

/**
 * Testing controller.Authentication
 * 
 * @author Kristian Lange
 */
public class AuthenticationControllerTest extends AbstractTest {

	@Override
	public void before() throws Exception {
		// Nothing additional to AbstractGuiTest to to do before test
	}

	@Override
	public void after() throws Exception {
		// Nothing additional to AbstractGuiTest to to do after test
	}

	@Test
	public void callLogin() throws Exception {
		RequestBuilder request = new RequestBuilder().method("GET")
				.session(Users.SESSION_EMAIL, admin.getEmail())
				.uri(controllers.gui.routes.Authentication.login().url());
		Result result = route(request);

		assertThat(result.status()).isEqualTo(OK);
		assertThat(result.charset()).isEqualTo("utf-8");
		assertThat(result.contentType()).isEqualTo("text/html");
		assertThat(contentAsString(result)).contains("login");
	}

	@Test
	public void callLogout() throws Exception {
		RequestBuilder request = new RequestBuilder().method("GET")
				.session(Users.SESSION_EMAIL, admin.getEmail())
				.uri(controllers.gui.routes.Authentication.logout().url());
		Result result = route(request);
		assertThat(result.status()).isEqualTo(SEE_OTHER);
		assertThat(result.redirectLocation()).contains("login");
		assertThat(!result.session().containsKey(Users.SESSION_EMAIL));
	}

	@Test
	public void authenticateSuccess() {
		RequestBuilder request = new RequestBuilder().method("POST")
				.bodyForm(ImmutableMap.of(User.EMAIL, UserService.ADMIN_EMAIL,
						User.PASSWORD, UserService.ADMIN_PASSWORD))
				.uri(controllers.gui.routes.Authentication.authenticate()
						.url());
		Result result = route(request);
		assertEquals(303, result.status());
		assertEquals(UserService.ADMIN_EMAIL, result.session().get(User.EMAIL));
	}

	@Test
	public void authenticateFailure() {
		RequestBuilder request = new RequestBuilder().method("POST")
				.bodyForm(ImmutableMap.of(User.EMAIL, UserService.ADMIN_EMAIL,
						User.PASSWORD, "bla"))
				.uri(controllers.gui.routes.Authentication.authenticate()
						.url());
		Result result = route(request);
		assertEquals(400, result.status());
		assertNull(result.session().get(User.EMAIL));
	}

}
