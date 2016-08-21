package controllers.gui;

import static org.fest.assertions.Assertions.assertThat;
import static play.mvc.Http.Status.OK;
import static play.test.Helpers.contentAsString;
import static play.test.Helpers.route;

import models.common.User;

import org.junit.Test;

import play.mvc.Http;
import play.mvc.Result;
import play.mvc.Http.RequestBuilder;
import services.gui.BreadcrumbsService;
import controllers.gui.Users;
import general.AbstractTest;

/**
 * Testing actions of controller.gui.Home.
 * 
 * @author Kristian Lange
 */
public class HomeControllerTest extends AbstractTest {

	@Override
	public void before() throws Exception {
		// Nothing additional to AbstractGuiTest to to do before test
	}

	@Override
	public void after() throws Exception {
		// Nothing additional to AbstractGuiTest to to do after test
	}

	@Test
	public void callHome() throws Exception {
		RequestBuilder request = new RequestBuilder().method("GET")
				.session(Users.SESSION_EMAIL, admin.getEmail())
				.uri(controllers.gui.routes.Home.home().url());
		Result result = route(request);

		assertThat(result.status()).isEqualTo(OK);
		assertThat(result.charset()).isEqualTo("utf-8");
		assertThat(result.contentType()).isEqualTo("text/html");
		assertThat(contentAsString(result)).contains(BreadcrumbsService.HOME);
	}

	@Test
	public void callLog() throws Exception {
		RequestBuilder request = new RequestBuilder().method("GET")
				.session(Users.SESSION_EMAIL, admin.getEmail())
				.uri(controllers.gui.routes.Home.log(1000).url());
		Result result = route(request);

		assertThat(result.status()).isEqualTo(OK);
		assertThat(result.charset()).isEqualTo("utf-8");
		assertThat(result.contentType()).isEqualTo("text/plain");
		assertThat(contentAsString(result))
				.contains("JATOS has started");

		User testUser = createAndPersistUser("bla@bla.com", "Bla", "bla");
		request = new RequestBuilder().method("GET")
				.session(Users.SESSION_EMAIL, testUser.getEmail())
				.uri(controllers.gui.routes.Home.log(1000).url());
		result = route(request);

		assertThat(result.status()).isEqualTo(Http.Status.FORBIDDEN);
	}

}
