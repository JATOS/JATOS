package gui.controllers;

import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static play.mvc.Http.Status.OK;
import static play.mvc.Http.Status.SEE_OTHER;
import static play.test.Helpers.callAction;
import static play.test.Helpers.charset;
import static play.test.Helpers.contentAsString;
import static play.test.Helpers.contentType;
import static play.test.Helpers.fakeRequest;
import static play.test.Helpers.headers;
import static play.test.Helpers.status;

import java.util.HashMap;
import java.util.Map;

import models.Study;
import models.StudyProperties;
import models.workers.PersonalSingleWorker;

import org.apache.http.HttpHeaders;
import org.junit.Test;

import play.mvc.Result;
import play.test.FakeRequest;
import services.BreadcrumbsService;
import utils.IOUtils;
import utils.JsonUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import common.AbstractTest;

import controllers.Users;

/**
 * Testing actions of controller.Studies.
 * 
 * @author Kristian Lange
 */
public class StudiesControllerTest extends AbstractTest {

	private static Study studyTemplate;

	@Override
	public void before() throws Exception {
		studyTemplate = importExampleStudy();
	}

	@Override
	public void after() throws Exception {
		IOUtils.removeStudyAssetsDir(studyTemplate.getDirName());
	}

	@Test
	public void callIndex() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);

		Result result = callAction(
				controllers.routes.ref.Studies.index(studyClone.getId()),
				fakeRequest()
						.withSession(Users.SESSION_EMAIL, admin.getEmail()));
		assertThat(status(result)).isEqualTo(OK);
		assertThat(charset(result)).isEqualTo("utf-8");
		assertThat(contentType(result)).isEqualTo("text/html");
		assertThat(contentAsString(result)).contains("Components");

		// Clean up
		removeStudy(studyClone);
	}

	@Test
	public void callCreate() {
		Result result = callAction(controllers.routes.ref.Studies.create(),
				fakeRequest()
						.withSession(Users.SESSION_EMAIL, admin.getEmail()));
		assertThat(status(result)).isEqualTo(OK);
		assertThat(charset(result)).isEqualTo("utf-8");
		assertThat(contentType(result)).isEqualTo("text/html");
		assertThat(contentAsString(result)).contains(
				BreadcrumbsService.NEW_STUDY);
	}

	@Test
	public void callSubmit() throws Exception {
		Map<String, String> formMap = new HashMap<String, String>();
		formMap.put(StudyProperties.TITLE, "Title Test");
		formMap.put(StudyProperties.DESCRIPTION, "Description test.");
		formMap.put(StudyProperties.COMMENTS, "Comments test.");
		formMap.put(StudyProperties.DIRNAME, "dirName_submit");
		formMap.put(StudyProperties.GROUP_STUDY, "true");
		formMap.put(StudyProperties.MIN_ACTIVE_MEMBER_SIZE, "5");
		formMap.put(StudyProperties.MAX_ACTIVE_MEMBER_SIZE, "5");
		formMap.put(StudyProperties.JSON_DATA, "{}");
		formMap.put(StudyProperties.ALLOWED_WORKER_TYPE_LIST, "");
		FakeRequest request = fakeRequest().withSession(Users.SESSION_EMAIL,
				admin.getEmail()).withFormUrlEncodedBody(formMap);
		Result result = callAction(controllers.routes.ref.Studies.submit(),
				request);
		assertEquals(SEE_OTHER, status(result));

		// Get study ID of created study from response's header
		String[] locationArray = headers(result).get(HttpHeaders.LOCATION)
				.split("/");
		Long studyId = Long.valueOf(locationArray[locationArray.length - 1]);

		Study study = studyDao.findById(studyId);
		assertEquals("Title Test", study.getTitle());
		assertEquals("Description test.", study.getDescription());
		assertEquals("dirName_submit", study.getDirName());
		assertThat(study.isGroupStudy()).isTrue();
		assertThat(study.getGroup().getMinActiveMemberSize()).isEqualTo(5);
		assertThat(study.getGroup().getMaxActiveMemberSize()).isEqualTo(5);
		assertEquals("{}", study.getJsonData());
		assertThat((study.getComponentList().isEmpty()));
		assertThat((study.getUserList().contains(admin)));
		assertThat((!study.isLocked()));
		assertThat((study.getAllowedWorkerTypeList().isEmpty()));

		// Clean up
		removeStudy(study);
	}

	@Test
	public void callSubmitValidationError() {
		// Fill with non-valid values
		Map<String, String> formMap = new HashMap<String, String>();
		formMap.put(StudyProperties.TITLE, " ");
		formMap.put(StudyProperties.DESCRIPTION, "Description test <b>.");
		formMap.put(StudyProperties.COMMENTS, "Comments test <i>.");
		formMap.put(StudyProperties.DIRNAME, "%.test");
		formMap.put(StudyProperties.GROUP_STUDY, "true");
		formMap.put(StudyProperties.MIN_ACTIVE_MEMBER_SIZE, "5");
		formMap.put(StudyProperties.MAX_ACTIVE_MEMBER_SIZE, "4");
		formMap.put(StudyProperties.MAX_TOTAL_MEMBER_SIZE, "3");
		formMap.put(StudyProperties.JSON_DATA, "{");
		formMap.put(StudyProperties.ALLOWED_WORKER_TYPE_LIST, "WrongWorker");
		FakeRequest request = fakeRequest().withSession(Users.SESSION_EMAIL,
				admin.getEmail()).withFormUrlEncodedBody(formMap);

		Result result = callAction(controllers.routes.ref.Studies.submit(),
				request);
		assertThat(contentAsString(result)).contains(
				BreadcrumbsService.NEW_STUDY);
		assertThat(contentAsString(result)).contains(
				"Problems deserializing JSON data string: invalid JSON format");
	}

	@Test
	public void callSubmitStudyAssetsDirExists() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Map<String, String> formMap = new HashMap<String, String>();
		formMap.put(StudyProperties.TITLE, "Title Test");
		formMap.put(StudyProperties.DESCRIPTION, "Description test.");
		formMap.put(StudyProperties.COMMENTS, "Comments test.");
		formMap.put(StudyProperties.GROUP_STUDY, "true");
		formMap.put(StudyProperties.MIN_ACTIVE_MEMBER_SIZE, "3");
		formMap.put(StudyProperties.MAX_ACTIVE_MEMBER_SIZE, "4");
		formMap.put(StudyProperties.MAX_TOTAL_MEMBER_SIZE, "5");
		formMap.put(StudyProperties.DIRNAME, studyClone.getDirName());
		formMap.put(StudyProperties.JSON_DATA, "{}");
		formMap.put(StudyProperties.ALLOWED_WORKER_TYPE_LIST, "");
		FakeRequest request = fakeRequest().withSession(Users.SESSION_EMAIL,
				admin.getEmail()).withFormUrlEncodedBody(formMap);

		Result result = callAction(controllers.routes.ref.Studies.submit(),
				request);
		assertThat(contentAsString(result)).contains(
				BreadcrumbsService.NEW_STUDY);
		assertThat(contentAsString(result)).contains(
				"couldn&#x27;t be created because it already exists.");

		// Cleanup
		removeStudy(studyClone);
	}

	@Test
	public void callEdit() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);

		Result result = callAction(
				controllers.routes.ref.Studies.edit(studyClone.getId()),
				fakeRequest()
						.withSession(Users.SESSION_EMAIL, admin.getEmail()));
		assertThat(status(result)).isEqualTo(OK);
		assertThat(charset(result)).isEqualTo("utf-8");
		assertThat(contentType(result)).isEqualTo("text/html");
		assertThat(contentAsString(result)).contains(
				BreadcrumbsService.EDIT_PROPERTIES);

		// Clean up
		removeStudy(studyClone);
	}

	@Test
	public void callSubmitEdited() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);

		Map<String, String> formMap = new HashMap<String, String>();
		formMap.put(StudyProperties.TITLE, "Title Test");
		formMap.put(StudyProperties.DESCRIPTION, "Description test.");
		formMap.put(StudyProperties.COMMENTS, "Comments test.");
		formMap.put(StudyProperties.DIRNAME, "dirName_submitEdited");
		formMap.put(StudyProperties.GROUP_STUDY, "true");
		formMap.put(StudyProperties.MIN_ACTIVE_MEMBER_SIZE, "3");
		formMap.put(StudyProperties.MAX_ACTIVE_MEMBER_SIZE, "4");
		formMap.put(StudyProperties.MAX_TOTAL_MEMBER_SIZE, "5");
		formMap.put(StudyProperties.JSON_DATA, "{}");
		formMap.put(StudyProperties.ALLOWED_WORKER_TYPE_LIST, "");
		FakeRequest request = fakeRequest().withSession(Users.SESSION_EMAIL,
				admin.getEmail()).withFormUrlEncodedBody(formMap);
		Result result = callAction(
				controllers.routes.ref.Studies.submitEdited(studyClone.getId()),
				request);

		assertEquals(SEE_OTHER, status(result));

		// TODO It would be nice to test the edited study here

		// Clean up
		removeStudy(studyClone);
	}

	@Test
	public void callSwapLock() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);

		Result result = callAction(
				controllers.routes.ref.Studies.swapLock(studyClone.getId()),
				fakeRequest()
						.withSession(Users.SESSION_EMAIL, admin.getEmail()));
		assertThat(status(result)).isEqualTo(OK);
		assertThat(contentAsString(result)).contains("true");

		// Clean up
		removeStudy(studyClone);
	}

	@Test
	public void callRemove() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);

		Result result = callAction(
				controllers.routes.ref.Studies.remove(studyClone.getId()),
				fakeRequest()
						.withSession(Users.SESSION_EMAIL, admin.getEmail()));
		assertThat(status(result)).isEqualTo(OK);
	}

	@Test
	public void callCloneStudy() throws Exception {
		Study study = cloneAndPersistStudy(studyTemplate);

		Result result = callAction(
				controllers.routes.ref.Studies.cloneStudy(study.getId()),
				fakeRequest()
						.withSession(Users.SESSION_EMAIL, admin.getEmail()));
		assertThat(status(result)).isEqualTo(OK);

		// Clean up
		IOUtils.removeStudyAssetsDir(study.getDirName() + "_clone");
		removeStudy(study);
	}

	@Test
	public void callChangeUser() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);

		Result result = callAction(
				controllers.routes.ref.Studies.changeUsers(studyClone.getId()),
				fakeRequest()
						.withSession(Users.SESSION_EMAIL, admin.getEmail()));
		assertThat(status(result)).isEqualTo(OK);

		// Clean up
		removeStudy(studyClone);
	}

	@Test
	public void callSubmitChangedUsers() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);

		Result result = callAction(
				controllers.routes.ref.Studies.submitChangedUsers(studyClone
						.getId()),
				fakeRequest().withFormUrlEncodedBody(
						ImmutableMap.of(Study.USERS, "admin")).withSession(
						Users.SESSION_EMAIL, admin.getEmail()));
		assertEquals(SEE_OTHER, status(result));

		// Clean up
		removeStudy(studyClone);
	}

	@Test
	public void callSubmitChangedUsersZeroUsers() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);

		Result result = callAction(
				controllers.routes.ref.Studies.submitChangedUsers(studyClone
						.getId()),
				fakeRequest().withFormUrlEncodedBody(
				// Just put some gibberish in the map
						ImmutableMap.of("bla", "blu")).withSession(
						Users.SESSION_EMAIL, admin.getEmail()));
		assertThat(contentAsString(result)).contains(
				"An study should have at least one user.");

		removeStudy(studyClone);
	}

	@Test
	public void callChangeComponentOrder() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);

		// Move first component to second position
		Result result = callAction(
				controllers.routes.ref.Studies.changeComponentOrder(
						studyClone.getId(), studyClone.getComponentList()
								.get(0).getId(), "2"),
				fakeRequest().withFormUrlEncodedBody(
						ImmutableMap.of(Study.USERS, "admin")).withSession(
						Users.SESSION_EMAIL, admin.getEmail()));
		assertThat(status(result)).isEqualTo(OK);

		// Move second component to first position
		result = callAction(
				controllers.routes.ref.Studies.changeComponentOrder(
						studyClone.getId(), studyClone.getComponentList()
								.get(1).getId(), "1"),
				fakeRequest().withFormUrlEncodedBody(
						ImmutableMap.of(Study.USERS, "admin")).withSession(
						Users.SESSION_EMAIL, admin.getEmail()));
		assertThat(status(result)).isEqualTo(OK);

		// Clean up
		removeStudy(studyClone);
	}

	@Test
	public void callShowStudy() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);

		Result result = callAction(
				controllers.routes.ref.Studies.showStudy(studyClone.getId()),
				fakeRequest()
						.withSession(Users.SESSION_EMAIL, admin.getEmail()));
		assertEquals(SEE_OTHER, status(result));

		// Clean up
		removeStudy(studyClone);
	}

	@Test
	public void callCreatePersonalSingleRun() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);

		JsonNode jsonNode = JsonUtils.OBJECTMAPPER.readTree("{ \""
				+ PersonalSingleWorker.COMMENT + "\": \"testcomment\" }");
		Result result = callAction(
				controllers.routes.ref.Studies.createPersonalSingleRun(studyClone
						.getId()), fakeRequest().withJsonBody(jsonNode)
						.withSession(Users.SESSION_EMAIL, admin.getEmail()));
		assertThat(status(result)).isEqualTo(OK);

		// Clean up
		removeStudy(studyClone);
	}

	@Test
	public void callCreatePersonalMultipleRun() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);

		JsonNode jsonNode = JsonUtils.OBJECTMAPPER.readTree("{ \""
				+ PersonalSingleWorker.COMMENT + "\": \"testcomment\" }");
		Result result = callAction(
				controllers.routes.ref.Studies.createPersonalMultipleRun(studyClone
						.getId()), fakeRequest().withJsonBody(jsonNode)
						.withSession(Users.SESSION_EMAIL, admin.getEmail()));
		assertThat(status(result)).isEqualTo(OK);

		// Clean up
		removeStudy(studyClone);
	}

	@Test
	public void callShowMTurkSourceCode() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);

		Result result = callAction(
				controllers.routes.ref.Studies.showMTurkSourceCode(studyClone
						.getId()),
				fakeRequest().withHeader("Referer",
						"http://www.example.com:9000").withSession(
						Users.SESSION_EMAIL, admin.getEmail()));
		assertThat(status(result)).isEqualTo(OK);
		assertThat(contentAsString(result)).contains(
				BreadcrumbsService.MECHANICAL_TURK_HIT_LAYOUT_SOURCE_CODE);

		// Clean up
		removeStudy(studyClone);
	}

	@Test
	public void callWorkers() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);

		Result result = callAction(
				controllers.routes.ref.Studies.workers(studyClone.getId()),
				fakeRequest()
						.withSession(Users.SESSION_EMAIL, admin.getEmail()));
		assertThat(status(result)).isEqualTo(OK);
		assertThat(contentAsString(result))
				.contains(BreadcrumbsService.WORKERS);

		// Clean up
		removeStudy(studyClone);
	}

}
