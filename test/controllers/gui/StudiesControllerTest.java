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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Guice;

import controllers.ControllerTestHelper;
import daos.common.StudyDao;
import general.common.MessagesStrings;
import models.common.Study;
import models.common.User;
import models.gui.StudyProperties;
import play.Application;
import play.ApplicationLoader;
import play.Environment;
import play.db.jpa.JPAApi;
import play.inject.guice.GuiceApplicationBuilder;
import play.inject.guice.GuiceApplicationLoader;
import play.mvc.Http.RequestBuilder;
import play.mvc.Result;
import play.test.Helpers;
import services.gui.BreadcrumbsService;
import utils.common.JsonUtils;

/**
 * Testing actions of controller.Studies.
 * 
 * @author Kristian Lange
 */
public class StudiesControllerTest {

	@Inject
	private static Application fakeApplication;

	@Inject
	private ControllerTestHelper controllerTestHelper;

	@Inject
	private JPAApi jpaApi;

	@Inject
	private StudyDao studyDao;

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
		controllerTestHelper.removeAllStudies();

		Helpers.stop(fakeApplication);
		controllerTestHelper.removeStudyAssetsRootDir();
	}

	@Test
	public void callIndex() throws Exception {
		Study study = controllerTestHelper
				.createAndPersistExampleStudyForAdmin(fakeApplication);

		User admin = controllerTestHelper.getAdmin();
		RequestBuilder request = new RequestBuilder().method("GET")
				.session(Users.SESSION_EMAIL, admin.getEmail())
				.uri(controllers.gui.routes.Studies.study(study.getId()).url());
		Result result = route(request);
		assertThat(result.status()).isEqualTo(OK);
		assertThat(result.charset().get()).isEqualTo("utf-8");
		assertThat(result.contentType().get()).isEqualTo("text/html");
		assertThat(contentAsString(result)).contains("Components");
	}

	@Test
	public void callSubmitCreated() throws Exception {
		Map<String, String> formMap = new HashMap<String, String>();
		formMap.put(StudyProperties.TITLE, "Title Test");
		formMap.put(StudyProperties.DESCRIPTION, "Description test.");
		formMap.put(StudyProperties.COMMENTS, "Comments test.");
		formMap.put(StudyProperties.DIRNAME, "dirName_submit");
		formMap.put(StudyProperties.JSON_DATA, "{}");

		User admin = controllerTestHelper.getAdmin();
		RequestBuilder request = new RequestBuilder().method("POST")
				.bodyForm(formMap)
				.session(Users.SESSION_EMAIL, admin.getEmail())
				.uri(controllers.gui.routes.Studies.submitCreated().url());
		Result result = route(request);

		assertEquals(OK, result.status());

		// Get study ID of created study from response's header
		assertThat(contentAsString(result)).isNotEmpty();
		Long studyId = Long.valueOf(contentAsString(result));

		// It all has to be within the transaction because there are lazy
		// fetches in Study
		jpaApi.withTransaction(() -> {
			Study study = studyDao.findById(studyId);
			assertEquals("Title Test", study.getTitle());
			assertEquals("Description test.", study.getDescription());
			assertEquals("dirName_submit", study.getDirName());
			assertEquals("{}", study.getJsonData());
			assertThat((study.getComponentList().isEmpty()));
			assertThat((study.getUserList().contains(admin)));
			assertThat((!study.isLocked()));
		});
	}

	@Test
	public void callSubmitCreatedValidationError()
			throws JsonProcessingException, IOException {
		// Fill with non-valid values
		Map<String, String> formMap = new HashMap<String, String>();
		formMap.put(StudyProperties.TITLE, " ");
		formMap.put(StudyProperties.DESCRIPTION, "Description test <b>.");
		formMap.put(StudyProperties.COMMENTS, "Comments test <i>.");
		formMap.put(StudyProperties.DIRNAME, "%.test");
		formMap.put(StudyProperties.JSON_DATA, "{");
		User admin = controllerTestHelper.getAdmin();
		RequestBuilder request = new RequestBuilder().method("POST")
				.bodyForm(formMap)
				.session(Users.SESSION_EMAIL, admin.getEmail())
				.uri(controllers.gui.routes.Studies.submitCreated().url());
		Result result = route(request);

		assertThat(result.contentType().get()).isEqualTo("application/json");
		JsonNode node = JsonUtils.OBJECTMAPPER
				.readTree(contentAsString(result));
		assertThat(node.get(StudyProperties.TITLE).toString())
				.isEqualTo("[\"" + MessagesStrings.MISSING_TITLE + "\"]");
		assertThat(node.get(StudyProperties.DESCRIPTION).toString())
				.isEqualTo("[\"" + MessagesStrings.NO_HTML_ALLOWED + "\"]");
		assertThat(node.get(StudyProperties.COMMENTS).toString())
				.isEqualTo("[\"" + MessagesStrings.NO_HTML_ALLOWED + "\"]");
		assertThat(node.get(StudyProperties.DIRNAME).toString())
				.isEqualTo("[\"" + MessagesStrings.INVALID_DIR_NAME + "\"]");
		assertThat(node.get(StudyProperties.JSON_DATA).toString())
				.isEqualTo("[\"" + MessagesStrings.INVALID_JSON_FORMAT + "\"]");
	}

	@Test
	public void callSubmitCreatedStudyAssetsDirExists() throws Exception {
		Study study = controllerTestHelper
				.createAndPersistExampleStudyForAdmin(fakeApplication);
		Map<String, String> formMap = new HashMap<String, String>();
		formMap.put(StudyProperties.TITLE, "Title Test");
		formMap.put(StudyProperties.DESCRIPTION, "Description test.");
		formMap.put(StudyProperties.COMMENTS, "Comments test.");
		formMap.put(StudyProperties.DIRNAME, study.getDirName());
		formMap.put(StudyProperties.JSON_DATA, "{}");
		User admin = controllerTestHelper.getAdmin();
		RequestBuilder request = new RequestBuilder().method("POST")
				.bodyForm(formMap)
				.session(Users.SESSION_EMAIL, admin.getEmail())
				.uri(controllers.gui.routes.Studies.submitCreated().url());
		Result result = route(request);

		assertThat(contentAsString(result)).contains(
				"{\"dirName\":[\"Study assets' directory (basic_example_study) couldn't be created because it already exists.\"]}");
	}

	@Test
	public void callProperties() throws Exception {
		Study study = controllerTestHelper
				.createAndPersistExampleStudyForAdmin(fakeApplication);

		User admin = controllerTestHelper.getAdmin();
		RequestBuilder request = new RequestBuilder().method("GET")
				.session(Users.SESSION_EMAIL, admin.getEmail())
				.uri(controllers.gui.routes.Studies.properties(study.getId())
						.url());
		Result result = route(request);

		assertThat(result.status()).isEqualTo(OK);
		assertThat(result.charset().get()).isEqualTo("UTF-8");
		assertThat(result.contentType().get()).isEqualTo("application/json");

		// Check properties in JSON
		JsonNode node = JsonUtils.OBJECTMAPPER
				.readTree(contentAsString(result));
		assertThat(node.get(StudyProperties.TITLE).toString())
				.isEqualTo("\"" + study.getTitle() + "\"");
		assertThat(node.get(StudyProperties.COMMENTS).toString())
				.isEqualTo("null");
		assertThat(node.get(StudyProperties.DESCRIPTION).toString())
				.isEqualTo("\"" + study.getDescription() + "\"");
		assertThat(node.get(StudyProperties.DIRNAME).toString())
				.isEqualTo("\"" + study.getDirName() + "\"");
		assertThat(node.get(StudyProperties.UUID).toString())
				.isEqualTo("\"" + study.getUuid() + "\"");
		assertThat(node.get(StudyProperties.JSON_DATA).toString())
				.isEqualTo("\"{\\\"totalStudySlides\\\":17}\"");
		assertThat(node.get(StudyProperties.LOCKED).toString())
				.isEqualTo(String.valueOf(study.isLocked()));
		assertThat(node.get(StudyProperties.GROUP_STUDY).toString())
				.isEqualTo(String.valueOf(study.isGroupStudy()));
	}

	@Test
	public void callSubmitEdited() throws Exception {
		Study study = controllerTestHelper
				.createAndPersistExampleStudyForAdmin(fakeApplication);

		Map<String, String> formMap = new HashMap<String, String>();
		formMap.put(StudyProperties.TITLE, "Title Test");
		formMap.put(StudyProperties.DESCRIPTION, "Description test.");
		formMap.put(StudyProperties.COMMENTS, "Comments test.");
		formMap.put(StudyProperties.DIRNAME, "dirName_submitEdited");
		formMap.put(StudyProperties.JSON_DATA, "{}");
		User admin = controllerTestHelper.getAdmin();
		RequestBuilder request = new RequestBuilder().method("POST")
				.bodyForm(formMap)
				.session(Users.SESSION_EMAIL, admin.getEmail())
				.uri(controllers.gui.routes.Studies.submitEdited(study.getId())
						.url());
		Result result = route(request);

		assertEquals(OK, result.status());

		// Check that edited properties are stored
		Study editedStudy = jpaApi.withTransaction(() -> {
			return studyDao.findById(study.getId());
		});
		assertEquals("Title Test", editedStudy.getTitle());
		assertEquals("Description test.", editedStudy.getDescription());
		assertEquals("Comments test.", editedStudy.getComments());
		assertEquals("dirName_submitEdited", editedStudy.getDirName());
		assertEquals("{}", editedStudy.getJsonData());
		assertThat((!editedStudy.isLocked()));
	}

	@Test
	public void callSwapLock() throws Exception {
		Study study = controllerTestHelper
				.createAndPersistExampleStudyForAdmin(fakeApplication);
		User admin = controllerTestHelper.getAdmin();
		RequestBuilder request = new RequestBuilder().method("POST")
				.session(Users.SESSION_EMAIL, admin.getEmail())
				.uri(controllers.gui.routes.Studies.toggleLock(study.getId())
						.url());
		Result result = route(request);

		assertThat(result.status()).isEqualTo(OK);
		assertThat(contentAsString(result)).contains("true");
	}

	@Test
	public void callRemove() throws Exception {
		Study study = controllerTestHelper
				.createAndPersistExampleStudyForAdmin(fakeApplication);
		User admin = controllerTestHelper.getAdmin();
		RequestBuilder request = new RequestBuilder().method("DELETE")
				.session(Users.SESSION_EMAIL, admin.getEmail())
				.uri(controllers.gui.routes.Studies.remove(study.getId())
						.url());
		Result result = route(request);
		assertThat(result.status()).isEqualTo(OK);
	}

	@Test
	public void callCloneStudy() throws Exception {
		Study study = controllerTestHelper
				.createAndPersistExampleStudyForAdmin(fakeApplication);
		User admin = controllerTestHelper.getAdmin();
		RequestBuilder request = new RequestBuilder().method("GET")
				.session(Users.SESSION_EMAIL, admin.getEmail())
				.uri(controllers.gui.routes.Studies.cloneStudy(study.getId())
						.url());
		Result result = route(request);

		assertThat(result.status()).isEqualTo(OK);
	}

	@Test
	public void callChangeUser() throws Exception {
		Study study = controllerTestHelper
				.createAndPersistExampleStudyForAdmin(fakeApplication);
		User admin = controllerTestHelper.getAdmin();
		RequestBuilder request = new RequestBuilder().method("GET")
				.session(Users.SESSION_EMAIL, admin.getEmail())
				.uri(controllers.gui.routes.Studies
						.submitChangedUsers(study.getId()).url());
		Result result = route(request);

		assertThat(result.status()).isEqualTo(OK);
	}

	@Test
	public void callSubmitChangedUsers() throws Exception {
		Study study = controllerTestHelper
				.createAndPersistExampleStudyForAdmin(fakeApplication);
		User admin = controllerTestHelper.getAdmin();
		RequestBuilder request = new RequestBuilder().method("POST")
				.bodyForm(ImmutableMap.of(Study.USERS, "admin"))
				.session(Users.SESSION_EMAIL, admin.getEmail())
				.uri(controllers.gui.routes.Studies
						.submitChangedUsers(study.getId()).url());
		Result result = route(request);

		assertEquals(OK, result.status());
	}

	@Test
	public void callSubmitChangedUsersZeroUsers() throws Exception {
		Study study = controllerTestHelper
				.createAndPersistExampleStudyForAdmin(fakeApplication);
		User admin = controllerTestHelper.getAdmin();
		RequestBuilder request = new RequestBuilder().method("POST")
				.bodyForm(ImmutableMap.of("bla", "blu"))
				.session(Users.SESSION_EMAIL, admin.getEmail())
				.uri(controllers.gui.routes.Studies
						.submitChangedUsers(study.getId()).url());
		Result result = route(request);

		assertThat(contentAsString(result))
				.contains("An study should have at least one user.");
	}

	@Test
	public void callChangeComponentOrder() throws Exception {
		Study study = controllerTestHelper
				.createAndPersistExampleStudyForAdmin(fakeApplication);
		User admin = controllerTestHelper.getAdmin();
		// Move first component to second position
		RequestBuilder request = new RequestBuilder().method("POST")
				.bodyForm(ImmutableMap.of(Study.USERS, "admin"))
				.session(Users.SESSION_EMAIL, admin.getEmail())
				.uri(controllers.gui.routes.Studies
						.changeComponentOrder(study.getId(),
								study.getComponentList().get(0).getId(), "2")
						.url());
		Result result = route(request);

		assertThat(result.status()).isEqualTo(OK);

		// Move second component to first position
		request = new RequestBuilder().method("POST")
				.bodyForm(ImmutableMap.of(Study.USERS, "admin"))
				.session(Users.SESSION_EMAIL, admin.getEmail())
				.uri(controllers.gui.routes.Studies
						.changeComponentOrder(study.getId(),
								study.getComponentList().get(1).getId(), "1")
						.url());
		result = route(request);

		assertThat(result.status()).isEqualTo(OK);
	}

	@Test
	public void callShowStudy() throws Exception {
		Study study = controllerTestHelper
				.createAndPersistExampleStudyForAdmin(fakeApplication);
		User admin = controllerTestHelper.getAdmin();
		RequestBuilder request = new RequestBuilder().method("GET")
				.bodyForm(ImmutableMap.of(Study.USERS, "admin"))
				.session(Users.SESSION_EMAIL, admin.getEmail())
				.uri(controllers.gui.routes.Studies.runStudy(study.getId(), -1l)
						.url());
		Result result = route(request);

		assertEquals(SEE_OTHER, result.status());
	}

	@Test
	public void callWorkers() throws Exception {
		Study study = controllerTestHelper
				.createAndPersistExampleStudyForAdmin(fakeApplication);
		User admin = controllerTestHelper.getAdmin();
		RequestBuilder request = new RequestBuilder().method("GET")
				.session(Users.SESSION_EMAIL, admin.getEmail())
				.uri(controllers.gui.routes.Studies.workers(study.getId())
						.url());
		Result result = route(request);

		assertThat(result.status()).isEqualTo(OK);
		assertThat(contentAsString(result))
				.contains(BreadcrumbsService.WORKER_SETUP);
	}

}
