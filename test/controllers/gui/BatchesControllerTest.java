package controllers.gui;

import static org.fest.assertions.Assertions.assertThat;
import static play.mvc.Http.Status.OK;
import static play.test.Helpers.contentAsString;
import static play.test.Helpers.route;

import javax.inject.Inject;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Guice;
import com.google.inject.Injector;

import general.TestHelper;
import models.common.Study;
import models.common.User;
import play.Application;
import play.ApplicationLoader;
import play.Environment;
import play.inject.guice.GuiceApplicationBuilder;
import play.inject.guice.GuiceApplicationLoader;
import play.libs.Json;
import play.mvc.Http.RequestBuilder;
import play.mvc.Result;
import play.test.Helpers;

/**
 * Testing actions of controller.Batches.
 * 
 * @author Kristian Lange
 */
public class BatchesControllerTest {

	private Injector injector;

	@Inject
	private static Application fakeApplication;

	@Inject
	private TestHelper testHelper;

	@Before
	public void startApp() throws Exception {
		fakeApplication = Helpers.fakeApplication();

		GuiceApplicationBuilder builder = new GuiceApplicationLoader()
				.builder(new ApplicationLoader.Context(Environment.simple()));
		injector = Guice.createInjector(builder.applicationModule());
		injector.injectMembers(this);

		Helpers.start(fakeApplication);
	}

	@After
	public void stopApp() throws Exception {
		// Clean up
		testHelper.removeAllStudies();

		Helpers.stop(fakeApplication);
		testHelper.removeStudyAssetsRootDir();
	}

	@Test
	public void callCreatePersonalSingleRun() throws Exception {
		User admin = testHelper.getAdmin();
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

		JsonNode jsonNode = Json.mapper()
				.readTree("{\"comment\": \"test comment\",\"amount\": 10}");
		RequestBuilder request = new RequestBuilder().method("POST")
				.bodyJson(jsonNode).session(Authentication.SESSION_USER_EMAIL,
						admin.getEmail())
				.uri(controllers.gui.routes.Batches.createPersonalSingleRun(
						study.getId(), study.getDefaultBatch().getId()).url());
		Result result = route(request);

		assertThat(result.status()).isEqualTo(OK);
		assertThat(result.contentType().get()).isEqualTo("application/json");
		jsonNode = Json.mapper().readTree(contentAsString(result));
		assertThat(jsonNode.isArray()).isTrue();
		assertThat(jsonNode.size()).isEqualTo(10);
	}

	@Test
	public void callCreatePersonalMultipleRun() throws Exception {
		User admin = testHelper.getAdmin();
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

		JsonNode jsonNode = Json.mapper()
				.readTree("{\"comment\": \"test comment\",\"amount\": 10}");
		RequestBuilder request = new RequestBuilder().method("POST")
				.bodyJson(jsonNode)
				.session(Authentication.SESSION_USER_EMAIL, admin.getEmail())
				.uri(controllers.gui.routes.Batches.createPersonalMultipleRun(
						study.getId(), study.getDefaultBatch().getId()).url());
		Result result = route(request);

		assertThat(result.status()).isEqualTo(OK);
		assertThat(result.contentType().get()).isEqualTo("application/json");
		jsonNode = Json.mapper().readTree(contentAsString(result));
		assertThat(jsonNode.isArray()).isTrue();
		assertThat(jsonNode.size()).isEqualTo(10);
	}

}
