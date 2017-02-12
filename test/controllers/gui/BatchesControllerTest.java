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

import general.TestHelper;
import models.common.Study;
import models.common.User;
import play.Application;
import play.ApplicationLoader;
import play.Environment;
import play.inject.guice.GuiceApplicationBuilder;
import play.inject.guice.GuiceApplicationLoader;
import play.mvc.Http.RequestBuilder;
import play.mvc.Result;
import play.test.Helpers;
import utils.common.JsonUtils;

/**
 * Testing actions of controller.Batches.
 * 
 * @author Kristian Lange
 */
public class BatchesControllerTest {

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
		// Clean up
		testHelper.removeAllStudies();

		Helpers.stop(fakeApplication);
		testHelper.removeStudyAssetsRootDir();
	}

	@Test
	public void callCreatePersonalSingleRun() throws Exception {
		User admin = testHelper.getAdmin();
		Study study = testHelper
				.createAndPersistExampleStudyForAdmin(fakeApplication);

		JsonNode jsonNode = JsonUtils.OBJECTMAPPER
				.readTree("{\"comment\": \"test comment\",\"amount\": 10}");
		RequestBuilder request = new RequestBuilder().method("POST")
				.bodyJson(jsonNode).session(Users.SESSION_EMAIL,
						admin.getEmail())
				.uri(controllers.gui.routes.Batches.createPersonalSingleRun(
						study.getId(), study.getDefaultBatch().getId()).url());
		Result result = route(request);

		assertThat(result.status()).isEqualTo(OK);
		assertThat(result.contentType().get()).isEqualTo("application/json");
		jsonNode = JsonUtils.OBJECTMAPPER.readTree(contentAsString(result));
		assertThat(jsonNode.isArray()).isTrue();
		assertThat(jsonNode.size()).isEqualTo(10);
	}

	@Test
	public void callCreatePersonalMultipleRun() throws Exception {
		User admin = testHelper.getAdmin();
		Study study = testHelper
				.createAndPersistExampleStudyForAdmin(fakeApplication);

		JsonNode jsonNode = JsonUtils.OBJECTMAPPER
				.readTree("{\"comment\": \"test comment\",\"amount\": 10}");
		RequestBuilder request = new RequestBuilder().method("POST")
				.bodyJson(jsonNode)
				.session(Users.SESSION_EMAIL, admin.getEmail())
				.uri(controllers.gui.routes.Batches.createPersonalMultipleRun(
						study.getId(), study.getDefaultBatch().getId()).url());
		Result result = route(request);

		assertThat(result.status()).isEqualTo(OK);
		assertThat(result.contentType().get()).isEqualTo("application/json");
		jsonNode = JsonUtils.OBJECTMAPPER.readTree(contentAsString(result));
		assertThat(jsonNode.isArray()).isTrue();
		assertThat(jsonNode.size()).isEqualTo(10);
	}

}
