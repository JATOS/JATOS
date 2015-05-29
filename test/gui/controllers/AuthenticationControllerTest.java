package gui.controllers;

import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static play.mvc.Http.Status.OK;
import static play.mvc.Http.Status.SEE_OTHER;
import static play.test.Helpers.callAction;
import static play.test.Helpers.charset;
import static play.test.Helpers.contentAsString;
import static play.test.Helpers.contentType;
import static play.test.Helpers.fakeRequest;
import static play.test.Helpers.redirectLocation;
import static play.test.Helpers.session;
import static play.test.Helpers.status;
import gui.AbstractTest;
import models.UserModel;

import org.junit.Test;

import play.mvc.Result;
import services.gui.UserService;

import com.google.common.collect.ImmutableMap;

import controllers.gui.Users;

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
		Result result = callAction(controllers.gui.routes.ref.Authentication
				.login());
		assertThat(status(result)).isEqualTo(OK);
		assertThat(charset(result)).isEqualTo("utf-8");
		assertThat(contentType(result)).isEqualTo("text/html");
		assertThat(contentAsString(result)).contains("login");
	}

	@Test
	public void callLogout() throws Exception {
		Result result = callAction(controllers.gui.routes.ref.Authentication
				.logout());
		assertThat(status(result)).isEqualTo(SEE_OTHER);
		assertThat(redirectLocation(result)).contains("login");
		assertThat(!session(result).containsKey(Users.SESSION_EMAIL));
	}

	@Test
	public void authenticateSuccess() {
		Result result = callAction(
				controllers.gui.routes.ref.Authentication.authenticate(),
				fakeRequest().withFormUrlEncodedBody(
						ImmutableMap.of(UserModel.EMAIL,
								UserService.ADMIN_EMAIL, UserModel.PASSWORD,
								UserService.ADMIN_PASSWORD)));
		assertEquals(303, status(result));
		assertEquals(UserService.ADMIN_EMAIL,
				session(result).get(UserModel.EMAIL));
	}

	@Test
	public void authenticateFailure() {
		Result result = callAction(
				controllers.gui.routes.ref.Authentication.authenticate(),
				fakeRequest().withFormUrlEncodedBody(
						ImmutableMap.of(UserModel.EMAIL,
								UserService.ADMIN_EMAIL, UserModel.PASSWORD,
								"bla")));
		assertEquals(400, status(result));
		assertNull(session(result).get(UserModel.EMAIL));
	}

}
