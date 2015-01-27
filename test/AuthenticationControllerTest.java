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

import java.io.IOException;

import models.UserModel;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import play.mvc.Result;
import services.UserService;

import com.google.common.collect.ImmutableMap;

import controllers.Users;

/**
 * Testing controller.Authentication
 * 
 * @author Kristian Lange
 */
public class AuthenticationControllerTest {

	private static ControllerTestUtils utils = new ControllerTestUtils();
	
	@BeforeClass
	public static void startApp() throws Exception {
		utils.startApp();
	}

	@AfterClass
	public static void stopApp() throws IOException {
		utils.stopApp();
	}
	
	@Test
	public void callLogin() throws Exception {
		Result result = callAction(controllers.routes.ref.Authentication
				.login());
		assertThat(status(result)).isEqualTo(OK);
		assertThat(charset(result)).isEqualTo("utf-8");
		assertThat(contentType(result)).isEqualTo("text/html");
		assertThat(contentAsString(result)).contains("login");
	}
	
	@Test
	public void callLogout() throws Exception {
		Result result = callAction(controllers.routes.ref.Authentication
				.logout());
		assertThat(status(result)).isEqualTo(SEE_OTHER);
		redirectLocation(result).contains("login");
		assertThat(!session(result).containsKey(Users.SESSION_EMAIL));
	}

	@Test
	public void authenticateSuccess() {
		Result result = callAction(
				controllers.routes.ref.Authentication.authenticate(),
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
				controllers.routes.ref.Authentication.authenticate(),
				fakeRequest().withFormUrlEncodedBody(
						ImmutableMap.of(UserModel.EMAIL,
								UserService.ADMIN_EMAIL, UserModel.PASSWORD,
								"bla")));
		assertEquals(400, status(result));
		assertNull(session(result).get(UserModel.EMAIL));
	}

}
