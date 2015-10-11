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

import models.StudyModel;
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

	private static StudyModel studyTemplate;

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
		StudyModel studyClone = cloneAndPersistStudy(studyTemplate);

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
		formMap.put(StudyModel.TITLE, "Title Test");
		formMap.put(StudyModel.DESCRIPTION, "Description test.");
		formMap.put(StudyModel.COMMENTS, "Comments test.");
		formMap.put(StudyModel.DIRNAME, "dirName_submit");
		formMap.put(StudyModel.GROUP_STUDY, "true");
		formMap.put(StudyModel.MIN_GROUP_SIZE, "5");
		formMap.put(StudyModel.MAX_GROUP_SIZE, "5");
		formMap.put(StudyModel.JSON_DATA, "{}");
		formMap.put(StudyModel.ALLOWED_WORKER_LIST, "");
		FakeRequest request = fakeRequest().withSession(Users.SESSION_EMAIL,
				admin.getEmail()).withFormUrlEncodedBody(formMap);
		Result result = callAction(controllers.routes.ref.Studies.submit(),
				request);
		assertEquals(SEE_OTHER, status(result));

		// Get study ID of created study from response's header
		String[] locationArray = headers(result).get(HttpHeaders.LOCATION)
				.split("/");
		Long studyId = Long.valueOf(locationArray[locationArray.length - 1]);

		StudyModel study = studyDao.findById(studyId);
		assertEquals("Title Test", study.getTitle());
		assertEquals("Description test.", study.getDescription());
		assertEquals("dirName_submit", study.getDirName());
		assertThat(study.isGroupStudy()).isTrue();
		assertThat(study.getMinGroupSize()).isEqualTo(5);
		assertThat(study.getMaxGroupSize()).isEqualTo(5);
		assertEquals("{}", study.getJsonData());
		assertThat((study.getComponentList().isEmpty()));
		assertThat((study.getMemberList().contains(admin)));
		assertThat((!study.isLocked()));
		assertThat((study.getAllowedWorkerList().isEmpty()));

		// Clean up
		removeStudy(study);
	}

	@Test
	public void callSubmitValidationError() {
		// Fill with non-valid values
		Map<String, String> formMap = new HashMap<String, String>();
		formMap.put(StudyModel.TITLE, " ");
		formMap.put(StudyModel.DESCRIPTION, "Description test <b>.");
		formMap.put(StudyModel.COMMENTS, "Comments test <i>.");
		formMap.put(StudyModel.DIRNAME, "%.test");
		formMap.put(StudyModel.GROUP_STUDY, "true");
		formMap.put(StudyModel.MIN_GROUP_SIZE, "5");
		formMap.put(StudyModel.MAX_GROUP_SIZE, "5");
		formMap.put(StudyModel.JSON_DATA, "{");
		formMap.put(StudyModel.ALLOWED_WORKER_LIST, "WrongWorker");
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
		StudyModel studyClone = cloneAndPersistStudy(studyTemplate);
		Map<String, String> formMap = new HashMap<String, String>();
		formMap.put(StudyModel.TITLE, "Title Test");
		formMap.put(StudyModel.DESCRIPTION, "Description test.");
		formMap.put(StudyModel.COMMENTS, "Comments test.");
		formMap.put(StudyModel.GROUP_STUDY, "true");
		formMap.put(StudyModel.MIN_GROUP_SIZE, "5");
		formMap.put(StudyModel.MAX_GROUP_SIZE, "5");
		formMap.put(StudyModel.DIRNAME, studyClone.getDirName());
		formMap.put(StudyModel.JSON_DATA, "{}");
		formMap.put(StudyModel.ALLOWED_WORKER_LIST, "");
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
		StudyModel studyClone = cloneAndPersistStudy(studyTemplate);

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
		StudyModel studyClone = cloneAndPersistStudy(studyTemplate);

		Map<String, String> formMap = new HashMap<String, String>();
		formMap.put(StudyModel.TITLE, "Title Test");
		formMap.put(StudyModel.DESCRIPTION, "Description test.");
		formMap.put(StudyModel.COMMENTS, "Comments test.");
		formMap.put(StudyModel.DIRNAME, "dirName_submitEdited");
		formMap.put(StudyModel.GROUP_STUDY, "true");
		formMap.put(StudyModel.MIN_GROUP_SIZE, "5");
		formMap.put(StudyModel.MAX_GROUP_SIZE, "5");
		formMap.put(StudyModel.JSON_DATA, "{}");
		formMap.put(StudyModel.ALLOWED_WORKER_LIST, "");
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
		StudyModel studyClone = cloneAndPersistStudy(studyTemplate);

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
		StudyModel studyClone = cloneAndPersistStudy(studyTemplate);

		Result result = callAction(
				controllers.routes.ref.Studies.remove(studyClone.getId()),
				fakeRequest()
						.withSession(Users.SESSION_EMAIL, admin.getEmail()));
		assertThat(status(result)).isEqualTo(OK);
	}

	@Test
	public void callCloneStudy() throws Exception {
		StudyModel study = cloneAndPersistStudy(studyTemplate);

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
	public void callChangeMember() throws Exception {
		StudyModel studyClone = cloneAndPersistStudy(studyTemplate);

		Result result = callAction(
				controllers.routes.ref.Studies
						.changeMembers(studyClone.getId()),
				fakeRequest()
						.withSession(Users.SESSION_EMAIL, admin.getEmail()));
		assertThat(status(result)).isEqualTo(OK);

		// Clean up
		removeStudy(studyClone);
	}

	@Test
	public void callSubmitChangedMembers() throws Exception {
		StudyModel studyClone = cloneAndPersistStudy(studyTemplate);

		Result result = callAction(
				controllers.routes.ref.Studies.submitChangedMembers(studyClone
						.getId()),
				fakeRequest().withFormUrlEncodedBody(
						ImmutableMap.of(StudyModel.MEMBERS, "admin"))
						.withSession(Users.SESSION_EMAIL, admin.getEmail()));
		assertEquals(SEE_OTHER, status(result));

		// Clean up
		removeStudy(studyClone);
	}

	@Test
	public void callSubmitChangedMembersZeroMembers() throws Exception {
		StudyModel studyClone = cloneAndPersistStudy(studyTemplate);

		Result result = callAction(
				controllers.routes.ref.Studies.submitChangedMembers(studyClone
						.getId()),
				fakeRequest().withFormUrlEncodedBody(
				// Just put some gibberish in the map
						ImmutableMap.of("bla", "blu")).withSession(
						Users.SESSION_EMAIL, admin.getEmail()));
		assertThat(contentAsString(result)).contains(
				"An study should have at least one member.");

		removeStudy(studyClone);
	}

	@Test
	public void callChangeComponentOrder() throws Exception {
		StudyModel studyClone = cloneAndPersistStudy(studyTemplate);

		// Move first component to second position
		Result result = callAction(
				controllers.routes.ref.Studies.changeComponentOrder(
						studyClone.getId(), studyClone.getComponentList()
								.get(0).getId(), "2"),
				fakeRequest().withFormUrlEncodedBody(
						ImmutableMap.of(StudyModel.MEMBERS, "admin"))
						.withSession(Users.SESSION_EMAIL, admin.getEmail()));
		assertThat(status(result)).isEqualTo(OK);

		// Move second component to first position
		result = callAction(
				controllers.routes.ref.Studies.changeComponentOrder(
						studyClone.getId(), studyClone.getComponentList()
								.get(1).getId(), "1"),
				fakeRequest().withFormUrlEncodedBody(
						ImmutableMap.of(StudyModel.MEMBERS, "admin"))
						.withSession(Users.SESSION_EMAIL, admin.getEmail()));
		assertThat(status(result)).isEqualTo(OK);

		// Clean up
		removeStudy(studyClone);
	}

	@Test
	public void callShowStudy() throws Exception {
		StudyModel studyClone = cloneAndPersistStudy(studyTemplate);

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
		StudyModel studyClone = cloneAndPersistStudy(studyTemplate);

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
		StudyModel studyClone = cloneAndPersistStudy(studyTemplate);

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
		StudyModel studyClone = cloneAndPersistStudy(studyTemplate);

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
		StudyModel studyClone = cloneAndPersistStudy(studyTemplate);

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
