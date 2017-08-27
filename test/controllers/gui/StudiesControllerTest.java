package controllers.gui;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Guice;
import com.google.inject.Injector;
import daos.common.StudyDao;
import general.TestHelper;
import general.common.MessagesStrings;
import models.common.Study;
import models.common.User;
import models.gui.StudyProperties;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
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
import services.gui.BreadcrumbsService;
import services.gui.UserService;
import utils.common.JsonUtils;

import javax.inject.Inject;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static play.mvc.Http.Status.*;
import static play.test.Helpers.contentAsString;
import static play.test.Helpers.route;

/**
 * Testing actions of controller.Studies.
 * 
 * @author Kristian Lange
 */
public class StudiesControllerTest {

	private Injector injector;

	@Inject
	private static Application fakeApplication;

	@Inject
	private TestHelper testHelper;

	@Inject
	private JPAApi jpaApi;

	@Inject
	private StudyDao studyDao;

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
	 * Test Studies.study()
	 */
	@Test
	public void callStudy() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

		Http.Session session = testHelper
				.mockSessionCookieandCache(testHelper.getAdmin());
		RequestBuilder request = new RequestBuilder().method("GET")
				.session(session).remoteAddress(TestHelper.WWW_EXAMPLE_COM)
				.uri(controllers.gui.routes.Studies.study(study.getId()).url());
		Result result = route(request);
		assertThat(result.status()).isEqualTo(OK);
		assertThat(result.charset().get()).isEqualTo("utf-8");
		assertThat(result.contentType().get()).isEqualTo("text/html");
		assertThat(contentAsString(result)).contains("Components");
	}

	/**
	 * Test Studies.submitCreated()
	 */
	@Test
	public void callSubmitCreated() throws Exception {
		Map<String, String> formMap = new HashMap<>();
		formMap.put(StudyProperties.TITLE, "Title Test");
		formMap.put(StudyProperties.DESCRIPTION, "Description test.");
		formMap.put(StudyProperties.COMMENTS, "Comments test.");
		formMap.put(StudyProperties.DIRNAME, "dirName_submit");
		formMap.put(StudyProperties.JSON_DATA, "{}");

		User admin = testHelper.getAdmin();

		Http.Session session = testHelper.mockSessionCookieandCache(admin);
		RequestBuilder request = new RequestBuilder().method("POST")
				.session(session).remoteAddress(TestHelper.WWW_EXAMPLE_COM)
				.bodyForm(formMap)
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

	/**
	 * Test Studies.submitCreated()
	 */
	@Test
	public void callSubmitCreatedValidationError()
			throws IOException {
		// Fill with non-valid values
		Map<String, String> formMap = new HashMap<>();
		formMap.put(StudyProperties.TITLE, " ");
		formMap.put(StudyProperties.DESCRIPTION, "Description test <b>.");
		formMap.put(StudyProperties.COMMENTS, "Comments test <i>.");
		formMap.put(StudyProperties.DIRNAME, "%.test");
		formMap.put(StudyProperties.JSON_DATA, "{");

		User admin = testHelper.getAdmin();
		Http.Session session = testHelper.mockSessionCookieandCache(admin);
		RequestBuilder request = new RequestBuilder().method("POST")
				.session(session).remoteAddress(TestHelper.WWW_EXAMPLE_COM)
				.bodyForm(formMap)
				.uri(controllers.gui.routes.Studies.submitCreated().url());
		Result result = route(request);

		assertThat(result.contentType().get()).isEqualTo("application/json");
		JsonNode node = Json.mapper().readTree(contentAsString(result));
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

	/**
	 * Test Studies.submitCreated()
	 */
	@Test
	public void callSubmitCreatedStudyAssetsDirExists() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		Map<String, String> formMap = new HashMap<>();
		formMap.put(StudyProperties.TITLE, "Title Test");
		formMap.put(StudyProperties.DESCRIPTION, "Description test.");
		formMap.put(StudyProperties.COMMENTS, "Comments test.");
		formMap.put(StudyProperties.DIRNAME, study.getDirName());
		formMap.put(StudyProperties.JSON_DATA, "{}");

		Http.Session session = testHelper
				.mockSessionCookieandCache(testHelper.getAdmin());
		RequestBuilder request = new RequestBuilder().method("POST")
				.session(session).remoteAddress(TestHelper.WWW_EXAMPLE_COM)
				.bodyForm(formMap)
				.uri(controllers.gui.routes.Studies.submitCreated().url());
		Result result = route(request);

		assertThat(contentAsString(result)).contains(
				"{\"dirName\":[\"Study assets' directory (basic_example_study) couldn't be created because it already exists.\"]}");
	}

	/**
	 * Test Studies.properties()
	 */
	@Test
	public void callProperties() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

		Http.Session session = testHelper
				.mockSessionCookieandCache(testHelper.getAdmin());
		RequestBuilder request = new RequestBuilder().method("GET")
				.session(session).remoteAddress(TestHelper.WWW_EXAMPLE_COM)
				.uri(controllers.gui.routes.Studies.properties(study.getId())
						.url());
		Result result = route(request);

		assertThat(result.status()).isEqualTo(OK);
		assertThat(result.charset().get()).isEqualTo("UTF-8");
		assertThat(result.contentType().get()).isEqualTo("application/json");

		// Check properties in JSON
		JsonNode node = Json.mapper().readTree(contentAsString(result));
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

	/**
	 * Test Studies.submitEdited()
	 */
	@Test
	public void callSubmitEdited() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

		Map<String, String> formMap = new HashMap<>();
		formMap.put(StudyProperties.TITLE, "Title Test");
		formMap.put(StudyProperties.DESCRIPTION, "Description test.");
		formMap.put(StudyProperties.COMMENTS, "Comments test.");
		formMap.put(StudyProperties.DIRNAME, "dirName_submitEdited");
		formMap.put(StudyProperties.JSON_DATA, "{}");

		Http.Session session = testHelper
				.mockSessionCookieandCache(testHelper.getAdmin());
		RequestBuilder request = new RequestBuilder().method("POST")
				.session(session).remoteAddress(TestHelper.WWW_EXAMPLE_COM)
				.bodyForm(formMap).uri(controllers.gui.routes.Studies
						.submitEdited(study.getId()).url());
		Result result = route(request);

		assertEquals(OK, result.status());

		// Check that edited properties are stored
		Study editedStudy = jpaApi.withTransaction(() -> studyDao.findById(study.getId()));
		assertEquals("Title Test", editedStudy.getTitle());
		assertEquals("Description test.", editedStudy.getDescription());
		assertEquals("Comments test.", editedStudy.getComments());
		assertEquals("dirName_submitEdited", editedStudy.getDirName());
		assertEquals("{}", editedStudy.getJsonData());
		assertThat((!editedStudy.isLocked()));
	}

	/**
	 * Test Studies.toggleLock()
	 */
	@Test
	public void callToggleLock() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		Http.Session session = testHelper
				.mockSessionCookieandCache(testHelper.getAdmin());
		RequestBuilder request = new RequestBuilder().method("POST")
				.session(session).remoteAddress(TestHelper.WWW_EXAMPLE_COM)
				.uri(controllers.gui.routes.Studies.toggleLock(study.getId())
						.url());
		Result result = route(request);

		assertThat(result.status()).isEqualTo(OK);
		assertThat(contentAsString(result)).contains("true");
	}

	/**
	 * Test Studies.remove()
	 */
	@Test
	public void callRemove() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		Http.Session session = testHelper
				.mockSessionCookieandCache(testHelper.getAdmin());
		RequestBuilder request = new RequestBuilder().method("DELETE")
				.session(session).remoteAddress(TestHelper.WWW_EXAMPLE_COM)
				.uri(controllers.gui.routes.Studies.remove(study.getId())
						.url());
		Result result = route(request);
		assertThat(result.status()).isEqualTo(OK);
	}

	/**
	 * Test Studies.cloneStudy()
	 */
	@Test
	public void callCloneStudy() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		Http.Session session = testHelper
				.mockSessionCookieandCache(testHelper.getAdmin());
		RequestBuilder request = new RequestBuilder().method("GET")
				.session(session).remoteAddress(TestHelper.WWW_EXAMPLE_COM)
				.uri(controllers.gui.routes.Studies.cloneStudy(study.getId())
						.url());
		Result result = route(request);

		assertThat(result.status()).isEqualTo(OK);
	}

	/**
	 * Test Studies.memberUsers()
	 */
	@Test
	public void callMemberUsers() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		Http.Session session = testHelper
				.mockSessionCookieandCache(testHelper.getAdmin());
		RequestBuilder request = new RequestBuilder().method("GET")
				.session(session).remoteAddress(TestHelper.WWW_EXAMPLE_COM)
				.uri(controllers.gui.routes.Studies.memberUsers(study.getId())
						.url());
		Result result = route(request);

		assertThat(result.status()).isEqualTo(OK);
		assertThat(result.contentType().get()).isEqualTo("application/json");
		assertThat(contentAsString(result)).contains(UserService.ADMIN_NAME);
	}

	/**
	 * Tests Studies.toggleMemberUser(): test adding and removing a member user
	 * from a study
	 */
	@Test
	public void callToggleMemberUser() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		User admin = testHelper.getAdmin();
		User someUser = testHelper.createAndPersistUser(TestHelper.BLA_EMAIL, "Bla",
				"bla");

		// Add someUser as member to study
		Http.Session session = testHelper
				.mockSessionCookieandCache(testHelper.getAdmin());
		RequestBuilder request = new RequestBuilder().method("POST")
				.session(session).remoteAddress(TestHelper.WWW_EXAMPLE_COM)
				.uri(controllers.gui.routes.Studies.toggleMemberUser(
						study.getId(), someUser.getEmail(), true).url());
		Result result = route(request);

		assertThat(result.status()).isEqualTo(OK);
		assertThat(contentAsString(result)).contains("\"isMember\":true");

		// Remove admin as member from study
		request = new RequestBuilder().method("POST").session(session)
				.remoteAddress(TestHelper.WWW_EXAMPLE_COM)
				.uri(controllers.gui.routes.Studies.toggleMemberUser(
						study.getId(), admin.getEmail(), false).url());
		result = route(request);

		assertThat(result.status()).isEqualTo(OK);
		assertThat(contentAsString(result)).contains("\"isMember\":false");

		// Clean-up
		testHelper.removeUser(TestHelper.BLA_EMAIL);
	}

	/**
	 * Tests Studies.toggleMemberUser(): it's not allowed to remove the last
	 * user from a study - a study must always have at least one user.
	 */
	@Test
	public void callToggleMemberUserNotAllowedRemoveLast() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		User admin = testHelper.getAdmin();

		// Remove admin as last member from study must lead to HTTP status
		// FORBIDDEN
		Http.Session session = testHelper
				.mockSessionCookieandCache(testHelper.getAdmin());
		RequestBuilder request = new RequestBuilder().method("POST")
				.session(session).remoteAddress(TestHelper.WWW_EXAMPLE_COM)
				.uri(controllers.gui.routes.Studies.toggleMemberUser(
						study.getId(), admin.getEmail(), false).url());
		Result result = route(request);

		assertThat(result.status()).isEqualTo(FORBIDDEN);
	}

	/**
	 * Tests Studies.toggleMemberUser(): the new member must exist in the DB
	 */
	@Test
	public void callToggleMemberUserNotKnown() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

		// Adding a user that doesn't exist in th DB leads to HTTP status
		// NOT_FOUND
		Http.Session session = testHelper
				.mockSessionCookieandCache(testHelper.getAdmin());
		RequestBuilder request = new RequestBuilder().method("POST")
				.session(session).remoteAddress(TestHelper.WWW_EXAMPLE_COM)
				.bodyForm(ImmutableMap.of("bla", "blu"))
				.uri(controllers.gui.routes.Studies.toggleMemberUser(
						study.getId(), "non.existing@mail.com", true).url());
		Result result = route(request);

		assertThat(result.status()).isEqualTo(NOT_FOUND);
	}

	/**
	 * Tests Studies.changeComponentOrder()
	 */
	@Test
	public void callChangeComponentOrder() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

		// Move first component to second position
		Http.Session session = testHelper
				.mockSessionCookieandCache(testHelper.getAdmin());
		RequestBuilder request = new RequestBuilder().method("POST")
				.session(session).remoteAddress(TestHelper.WWW_EXAMPLE_COM)
				.bodyForm(ImmutableMap.of(Study.USERS, "admin"))
				.uri(controllers.gui.routes.Studies
						.changeComponentOrder(study.getId(),
								study.getComponentList().get(0).getId(), "2")
						.url());
		Result result = route(request);

		assertThat(result.status()).isEqualTo(OK);

		// Move second component to first position
		request = new RequestBuilder().method("POST").session(session)
				.remoteAddress(TestHelper.WWW_EXAMPLE_COM)
				.bodyForm(ImmutableMap.of(Study.USERS, "admin"))
				.uri(controllers.gui.routes.Studies
						.changeComponentOrder(study.getId(),
								study.getComponentList().get(1).getId(), "1")
						.url());
		result = route(request);

		assertThat(result.status()).isEqualTo(OK);
	}

	/**
	 * Tests Studies.runStudy()
	 */
	@Test
	public void callRunStudy() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

		Http.Session session = testHelper
				.mockSessionCookieandCache(testHelper.getAdmin());
		RequestBuilder request = new RequestBuilder().method("GET")
				.session(session).remoteAddress(TestHelper.WWW_EXAMPLE_COM)
				.bodyForm(ImmutableMap.of(Study.USERS, "admin"))
				.uri(controllers.gui.routes.Studies.runStudy(study.getId(), -1L)
						.url());
		Result result = route(request);

		assertEquals(SEE_OTHER, result.status());
	}

	/**
	 * Tests Studies.tableDataByStudy()
	 */
	@Test
	public void callTableDataByStudy() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

		Http.Session session = testHelper
				.mockSessionCookieandCache(testHelper.getAdmin());
		RequestBuilder request = new RequestBuilder().method("GET")
				.session(session).remoteAddress(TestHelper.WWW_EXAMPLE_COM)
				.uri(controllers.gui.routes.Studies
						.tableDataByStudy(study.getId()).url());
		Result result = route(request);

		assertEquals(OK, result.status());
		assertThat(result.contentType().get()).isEqualTo("application/json");
		assertThat(contentAsString(result)).contains(JsonUtils.DATA);
	}

	/**
	 * Tests Studies.workers()
	 */
	@Test
	public void callWorkers() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

		Http.Session session = testHelper
				.mockSessionCookieandCache(testHelper.getAdmin());
		RequestBuilder request = new RequestBuilder().method("GET")
				.session(session).remoteAddress(TestHelper.WWW_EXAMPLE_COM)
				.uri(controllers.gui.routes.Studies.workers(study.getId())
						.url());
		Result result = route(request);

		assertThat(result.status()).isEqualTo(OK);
		assertThat(contentAsString(result))
				.contains(BreadcrumbsService.WORKERS);
	}

}
