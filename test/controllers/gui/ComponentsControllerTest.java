package controllers.gui;

import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static play.mvc.Http.Status.OK;
import static play.mvc.Http.Status.SEE_OTHER;
import static play.test.Helpers.contentAsString;
import static play.test.Helpers.route;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import org.apache.http.HttpHeaders;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Guice;
import com.google.inject.Injector;

import daos.common.ComponentDao;
import general.TestHelper;
import general.common.MessagesStrings;
import models.common.Study;
import models.common.User;
import models.gui.ComponentProperties;
import play.Application;
import play.ApplicationLoader;
import play.Environment;
import play.db.jpa.JPAApi;
import play.inject.guice.GuiceApplicationBuilder;
import play.inject.guice.GuiceApplicationLoader;
import play.libs.Json;
import play.mvc.Http;
import play.mvc.Http.RequestBuilder;
import play.mvc.Result;
import play.test.Helpers;
import services.gui.AuthenticationService;

/**
 * Testing actions of controller.Components.
 * 
 * @author Kristian Lange
 */
public class ComponentsControllerTest {

	private Injector injector;

	@Inject
	private static Application fakeApplication;

	@Inject
	private TestHelper testHelper;

	@Inject
	private JPAApi jpaApi;

	@Inject
	private ComponentDao componentDao;

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

	/**
	 * Checks action with route Components.showComponent.
	 */
	@Test
	public void callRunComponent() throws Exception {
		User admin = testHelper.getAdmin();
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

		RequestBuilder request = new RequestBuilder().method("GET")
				.session(
						AuthenticationService.SESSION_USER_EMAIL, admin
								.getEmail())
				.uri(controllers.gui.routes.Components
						.runComponent(study.getId(),
								study.getComponent(1).getId(), -1l)
						.url());
		Result result = route(request);

		assertEquals(SEE_OTHER, result.status());
		assertThat(result.session().containsKey("jatos_run"));
		assertThat(result.session().containsValue("single_component_start"));
		assertThat(result.session().containsKey("run_component_id"));
		assertThat(result.session().containsValue(study.getId().toString()));
		assertThat(result.headers().get(HttpHeaders.LOCATION)
				.contains("jatosWorkerId"));
	}

	/**
	 * Checks action with route Components.showComponent if no html file is set
	 * within the Component.
	 */
	@Test
	public void callRunComponentNoHtml() throws Exception {
		User admin = testHelper.getAdmin();
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

		jpaApi.withTransaction(() -> {
			study.getComponent(1).setHtmlFilePath(null);
			componentDao.update(study.getComponent(1));
		});

		RequestBuilder request = new RequestBuilder().method("GET")
				.session(
						AuthenticationService.SESSION_USER_EMAIL, admin
								.getEmail())
				.uri(controllers.gui.routes.Components
						.runComponent(study.getId(),
								study.getComponent(1).getId(), -1l)
						.url());
		// Empty html path must lead to an JatosGuiException with a HTTP status
		// of 400
		testHelper.assertJatosGuiException(request, Http.Status.BAD_REQUEST);
	}

	/**
	 * Checks action with route Components.create.
	 */
	@Test
	public void callProperties() throws IOException {
		User admin = testHelper.getAdmin();
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

		RequestBuilder request = new RequestBuilder().method("GET")
				.session(
						AuthenticationService.SESSION_USER_EMAIL, admin
								.getEmail())
				.uri(controllers.gui.routes.Components.properties(study.getId(),
						study.getFirstComponent().getId()).url());
		Result result = route(request);

		assertThat(result.status()).isEqualTo(OK);
		assertThat(result.charset().get()).isEqualTo("UTF-8");
		assertThat(result.contentType().get()).isEqualTo("application/json");

		// Check properties in JSON
		JsonNode node = Json.mapper().readTree(contentAsString(result));
		assertThat(node.get(ComponentProperties.TITLE).toString())
				.isEqualTo("\"" + study.getFirstComponent().getTitle() + "\"");
		assertThat(node.get(ComponentProperties.ACTIVE).toString()).isEqualTo(
				String.valueOf(study.getFirstComponent().isActive()));
		assertThat(node.get(ComponentProperties.COMMENTS).toString()).isEqualTo(
				"\"" + study.getFirstComponent().getComments() + "\"");
		assertThat(node.get(ComponentProperties.HTML_FILE_PATH).toString())
				.isEqualTo("\"" + study.getFirstComponent().getHtmlFilePath()
						+ "\"");
		assertThat(node.get(ComponentProperties.JSON_DATA).toString()).contains(
				"This component displays text and reacts to key presses.");
		assertThat(node.get(ComponentProperties.RELOADABLE).toString())
				.isEqualTo(String
						.valueOf(study.getFirstComponent().isReloadable()));
		assertThat(node.get(ComponentProperties.UUID).toString())
				.isEqualTo("\"" + study.getFirstComponent().getUuid() + "\"");
		assertThat(node.get(ComponentProperties.ID).toString())
				.isEqualTo(String.valueOf(study.getFirstComponent().getId()));
		assertThat(node.get("studyId").toString()).isEqualTo(
				String.valueOf(study.getFirstComponent().getStudy().getId()));
		assertThat(node.get("date").toString())
				.isEqualTo(String.valueOf(study.getFirstComponent().getDate()));
	}

	/**
	 * Checks action with route Components.submit. After the call the study's
	 * page should be shown.
	 */
	@Test
	public void callSubmitCreated() throws Exception {
		User admin = testHelper.getAdmin();
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

		Map<String, String> form = new HashMap<String, String>();
		form.put(ComponentProperties.TITLE, "Title Test");
		form.put(ComponentProperties.RELOADABLE, "true");
		form.put(ComponentProperties.HTML_FILE_PATH,
				"html_file_path_test.html");
		form.put(ComponentProperties.COMMENTS, "Comments test test.");
		form.put(ComponentProperties.JSON_DATA, "{}");
		RequestBuilder request = new RequestBuilder().method("POST")
				.bodyForm(form)
				.session(AuthenticationService.SESSION_USER_EMAIL,
						admin.getEmail())
				.uri(controllers.gui.routes.Components
						.submitCreated(study.getId()).url());
		Result result = route(request);

		assertEquals(OK, result.status());
	}

	/**
	 * Checks action with route Components.submit. After the call the component
	 * itself should be shown.
	 */
	@Test
	public void callSubmitEdited() throws Exception {
		User admin = testHelper.getAdmin();
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

		Map<String, String> form = new HashMap<String, String>();
		form.put(ComponentProperties.TITLE, "Title Test");
		form.put(ComponentProperties.RELOADABLE, "true");
		form.put(ComponentProperties.HTML_FILE_PATH,
				"html_file_path_test.html");
		form.put(ComponentProperties.COMMENTS, "Comments test test.");
		form.put(ComponentProperties.JSON_DATA, "{}");
		RequestBuilder request = new RequestBuilder().method("POST")
				.bodyForm(form)
				.session(
						AuthenticationService.SESSION_USER_EMAIL, admin
								.getEmail())
				.uri(controllers.gui.routes.Components
						.submitEdited(study.getId(),
								study.getFirstComponent().getId())
						.url());
		Result result = route(request);

		assertEquals(OK, result.status());
	}

	/**
	 * Checks action with route Components.submit with validation error.
	 */
	@Test
	public void callSubmitValidationError() throws Exception {
		User admin = testHelper.getAdmin();
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

		Map<String, String> form = new HashMap<String, String>();
		form.put(ComponentProperties.TITLE, "");
		form.put(ComponentProperties.RELOADABLE, "true");
		form.put(ComponentProperties.HTML_FILE_PATH, "%.test");
		form.put(ComponentProperties.COMMENTS, "Comments test <i>.");
		form.put(ComponentProperties.JSON_DATA, "{");
		form.put(Components.EDIT_SUBMIT_NAME, Components.EDIT_SAVE_AND_RUN);
		RequestBuilder request = new RequestBuilder().method("POST")
				.bodyForm(form)
				.session(AuthenticationService.SESSION_USER_EMAIL,
						admin.getEmail())
				.uri(controllers.gui.routes.Components
						.submitCreated(study.getId()).url());
		Result result = route(request);

		assertThat(result.contentType().get()).isEqualTo("application/json");
		JsonNode node = Json.mapper().readTree(contentAsString(result));
		assertThat(node.get(ComponentProperties.TITLE).toString())
				.isEqualTo("[\"" + MessagesStrings.MISSING_TITLE + "\"]");
		assertThat(node.get(ComponentProperties.HTML_FILE_PATH).toString())
				.isEqualTo(
						"[\"" + MessagesStrings.NOT_A_VALID_PATH_YOU_CAN_LEAVE_IT_EMPTY
								+ "\"]");
		assertThat(node.get(ComponentProperties.COMMENTS).toString())
				.isEqualTo("[\"" + MessagesStrings.NO_HTML_ALLOWED + "\"]");
		assertThat(node.get(ComponentProperties.JSON_DATA).toString())
				.isEqualTo("[\"" + MessagesStrings.INVALID_JSON_FORMAT + "\"]");
	}

	@Test
	public void callChangeProperty() throws Exception {
		User admin = testHelper.getAdmin();
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		RequestBuilder request = new RequestBuilder().method("POST")
				.session(
						AuthenticationService.SESSION_USER_EMAIL, admin
								.getEmail())
				.uri(controllers.gui.routes.Components
						.toggleActive(study.getId(),
								study.getComponent(1).getId(), true)
						.url());
		Result result = route(request);

		assertThat(result.status()).isEqualTo(OK);
	}

	@Test
	public void callCloneComponent() throws Exception {
		User admin = testHelper.getAdmin();
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		RequestBuilder request = new RequestBuilder().method("GET")
				.session(
						AuthenticationService.SESSION_USER_EMAIL, admin
								.getEmail())
				.uri(controllers.gui.routes.Components.cloneComponent(
						study.getId(), study.getComponent(1).getId()).url());
		Result result = route(request);

		assertThat(result.status()).isEqualTo(OK);
	}

	@Test
	public void callRemove() throws Exception {
		User admin = testHelper.getAdmin();
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		RequestBuilder request = new RequestBuilder().method("DELETE")
				.session(AuthenticationService.SESSION_USER_EMAIL,
						admin.getEmail())
				.uri(controllers.gui.routes.Components
						.remove(study.getId(), study.getComponent(1).getId())
						.url());
		Result result = route(request);

		assertThat(result.status()).isEqualTo(OK);
	}

}
