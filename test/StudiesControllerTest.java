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

import java.io.IOException;

import models.StudyModel;
import models.workers.ClosedStandaloneWorker;

import org.apache.http.HttpHeaders;
import org.hamcrest.core.IsInstanceOf;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import play.mvc.Result;
import play.test.FakeRequest;
import services.Breadcrumbs;
import services.IOUtils;
import services.JsonUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;

import controllers.Studies;
import controllers.Users;
import exceptions.ResultException;

/**
 * Testing actions of controller.Studies.
 * 
 * @author Kristian Lange
 */
public class StudiesControllerTest {

	private static ControllerTestUtils utils = new ControllerTestUtils();
	private static StudyModel studyTemplate;

	@BeforeClass
	public static void startApp() throws Exception {
		utils.startApp();
		studyTemplate = utils.importExampleStudy();
	}

	@AfterClass
	public static void stopApp() throws IOException {
		IOUtils.removeStudyAssetsDir(studyTemplate.getDirName());
		utils.stopApp();
	}

	@Test
	public void callIndex() throws Exception {
		StudyModel studyClone = utils.cloneAndPersistStudy(studyTemplate);

		Result result = callAction(
				controllers.routes.ref.Studies.index(studyClone.getId(), null),
				fakeRequest().withSession(Users.SESSION_EMAIL,
						utils.admin.getEmail()));
		assertThat(status(result)).isEqualTo(OK);
		assertThat(charset(result)).isEqualTo("utf-8");
		assertThat(contentType(result)).isEqualTo("text/html");
		assertThat(contentAsString(result)).contains("Components");

		// Clean up
		utils.removeStudy(studyClone);
	}

	@Test
	public void callCreate() {
		Result result = callAction(
				controllers.routes.ref.Studies.create(),
				fakeRequest().withSession(Users.SESSION_EMAIL,
						utils.admin.getEmail()));
		assertThat(status(result)).isEqualTo(OK);
		assertThat(charset(result)).isEqualTo("utf-8");
		assertThat(contentType(result)).isEqualTo("text/html");
		assertThat(contentAsString(result)).contains(Breadcrumbs.NEW_STUDY);
	}

	@Test
	public void callSubmit() throws Exception {
		FakeRequest request = fakeRequest().withSession(Users.SESSION_EMAIL,
				utils.admin.getEmail()).withFormUrlEncodedBody(
				ImmutableMap.of(StudyModel.TITLE, "Title Test",
						StudyModel.DESCRIPTION, "Description test.",
						StudyModel.DIRNAME, "dirName_submit",
						StudyModel.JSON_DATA, "{}",
						StudyModel.ALLOWED_WORKER_LIST, ""));
		Result result = callAction(controllers.routes.ref.Studies.submit(),
				request);
		assertEquals(SEE_OTHER, status(result));

		// Get study ID of created study from response's header
		String[] locationArray = headers(result).get(HttpHeaders.LOCATION)
				.split("/");
		Long studyId = Long.valueOf(locationArray[locationArray.length - 1]);

		StudyModel study = StudyModel.findById(studyId);
		assertEquals("Title Test", study.getTitle());
		assertEquals("Description test.", study.getDescription());
		assertEquals("dirName_submit", study.getDirName());
		assertEquals("{ }", study.getJsonData());
		assertThat((study.getComponentList().isEmpty()));
		assertThat((study.getMemberList().contains(utils.admin)));
		assertThat((!study.isLocked()));
		assertThat((study.getAllowedWorkerList().isEmpty()));

		// Clean up
		utils.removeStudy(study);
	}

	@Test
	public void callSubmitValidationError() {
		FakeRequest request = fakeRequest().withSession(Users.SESSION_EMAIL,
				utils.admin.getEmail()).withFormUrlEncodedBody(
				ImmutableMap.of(StudyModel.TITLE, " ", StudyModel.DESCRIPTION,
						"Description test.", StudyModel.DIRNAME, "%.test",
						StudyModel.JSON_DATA, "{",
						StudyModel.ALLOWED_WORKER_LIST, "WrongWorker"));

		utils.thrown.expect(RuntimeException.class);
		utils.thrown.expectCause(IsInstanceOf
				.<Throwable> instanceOf(ResultException.class));
		callAction(controllers.routes.ref.Studies.submit(), request);
	}

	@Test
	public void callSubmitStudyAssetsDirExists() throws Exception {
		StudyModel studyClone = utils.cloneAndPersistStudy(studyTemplate);

		FakeRequest request = fakeRequest().withSession(Users.SESSION_EMAIL,
				utils.admin.getEmail()).withFormUrlEncodedBody(
				ImmutableMap.of(StudyModel.TITLE, "Title Test",
						StudyModel.DESCRIPTION, "Description test.",
						StudyModel.DIRNAME, studyClone.getDirName(),
						StudyModel.JSON_DATA, "{}",
						StudyModel.ALLOWED_WORKER_LIST, ""));

		try {
			callAction(controllers.routes.ref.Studies.submit(), request);
		} catch (RuntimeException e) {
			assertThat(e.getCause() instanceof ResultException);
		} finally {
			utils.removeStudy(studyClone);
		}
	}

	@Test
	public void callEdit() throws Exception {
		StudyModel studyClone = utils.cloneAndPersistStudy(studyTemplate);

		Result result = callAction(
				controllers.routes.ref.Studies.edit(studyClone.getId()),
				fakeRequest().withSession(Users.SESSION_EMAIL,
						utils.admin.getEmail()));
		assertThat(status(result)).isEqualTo(OK);
		assertThat(charset(result)).isEqualTo("utf-8");
		assertThat(contentType(result)).isEqualTo("text/html");
		assertThat(contentAsString(result)).contains(
				Breadcrumbs.EDIT_PROPERTIES);

		// Clean up
		utils.removeStudy(studyClone);
	}

	@Test
	public void callSubmitEdited() throws Exception {
		StudyModel studyClone = utils.cloneAndPersistStudy(studyTemplate);

		FakeRequest request = fakeRequest().withSession(Users.SESSION_EMAIL,
				utils.admin.getEmail()).withFormUrlEncodedBody(
				ImmutableMap.of(StudyModel.TITLE, "Title Test",
						StudyModel.DESCRIPTION, "Description test.",
						StudyModel.DIRNAME, "dirName_submitEdited",
						StudyModel.JSON_DATA, "{}",
						StudyModel.ALLOWED_WORKER_LIST, ""));
		Result result = callAction(
				controllers.routes.ref.Studies.submitEdited(studyClone.getId()),
				request);
		assertEquals(SEE_OTHER, status(result));

		// It would be nice to test the edited study here
		// Clean up
		studyClone.setDirName("dirName_submitEdited");
		utils.removeStudy(studyClone);
	}

	@Test
	public void callSwapLock() throws Exception {
		StudyModel studyClone = utils.cloneAndPersistStudy(studyTemplate);

		Result result = callAction(
				controllers.routes.ref.Studies.swapLock(studyClone.getId()),
				fakeRequest().withSession(Users.SESSION_EMAIL,
						utils.admin.getEmail()));
		assertThat(status(result)).isEqualTo(OK);
		assertThat(contentAsString(result)).contains("true");

		// Clean up
		utils.removeStudy(studyClone);
	}

	@Test
	public void callRemove() throws Exception {
		StudyModel studyClone = utils.cloneAndPersistStudy(studyTemplate);

		Result result = callAction(
				controllers.routes.ref.Studies.remove(studyClone.getId()),
				fakeRequest().withSession(Users.SESSION_EMAIL,
						utils.admin.getEmail()));
		assertThat(status(result)).isEqualTo(OK);
	}

	@Test
	public void callCloneStudy() throws Exception {
		StudyModel study = utils.cloneAndPersistStudy(studyTemplate);

		Result result = callAction(
				controllers.routes.ref.Studies.cloneStudy(study.getId()),
				fakeRequest().withSession(Users.SESSION_EMAIL,
						utils.admin.getEmail()));
		assertThat(status(result)).isEqualTo(OK);

		// Clean up
		IOUtils.removeStudyAssetsDir(study.getDirName() + "_clone");
		utils.removeStudy(study);
	}

	@Test
	public void callChangeMember() throws Exception {
		StudyModel studyClone = utils.cloneAndPersistStudy(studyTemplate);

		Result result = callAction(
				controllers.routes.ref.Studies
						.changeMembers(studyClone.getId()),
				fakeRequest().withSession(Users.SESSION_EMAIL,
						utils.admin.getEmail()));
		assertThat(status(result)).isEqualTo(OK);

		// Clean up
		utils.removeStudy(studyClone);
	}

	@Test
	public void callSubmitChangedMembers() throws Exception {
		StudyModel studyClone = utils.cloneAndPersistStudy(studyTemplate);

		Result result = callAction(
				controllers.routes.ref.Studies.submitChangedMembers(studyClone
						.getId()),
				fakeRequest().withFormUrlEncodedBody(
						ImmutableMap.of(StudyModel.MEMBERS, "admin"))
						.withSession(Users.SESSION_EMAIL,
								utils.admin.getEmail()));
		assertEquals(SEE_OTHER, status(result));

		// Clean up
		utils.removeStudy(studyClone);
	}

	@Test
	public void callSubmitChangedMembersZeroMembers() throws Exception {
		StudyModel studyClone = utils.cloneAndPersistStudy(studyTemplate);

		try {
			callAction(
					controllers.routes.ref.Studies.submitChangedMembers(studyClone
							.getId()),
					fakeRequest().withFormUrlEncodedBody(
					// Just put some gibberish in the map
							ImmutableMap.of("bla", "blu")).withSession(
							Users.SESSION_EMAIL, utils.admin.getEmail()));
		} catch (RuntimeException e) {
			assertThat(e.getMessage()
					.contains("An study should have at least one member."));
			assertThat(e.getCause() instanceof ResultException);
		} finally {
			utils.removeStudy(studyClone);
		}
	}

	@Test
	public void callChangeComponentOrder() throws Exception {
		StudyModel studyClone = utils.cloneAndPersistStudy(studyTemplate);

		// Move first component one down
		Result result = callAction(
				controllers.routes.ref.Studies.changeComponentOrder(
						studyClone.getId(), studyClone.getComponentList()
								.get(0).getId(), Studies.COMPONENT_ORDER_DOWN),
				fakeRequest().withFormUrlEncodedBody(
						ImmutableMap.of(StudyModel.MEMBERS, "admin"))
						.withSession(Users.SESSION_EMAIL,
								utils.admin.getEmail()));
		assertThat(status(result)).isEqualTo(OK);

		// Move second component one up
		result = callAction(
				controllers.routes.ref.Studies.changeComponentOrder(
						studyClone.getId(), studyClone.getComponentList()
								.get(1).getId(), Studies.COMPONENT_ORDER_UP),
				fakeRequest().withFormUrlEncodedBody(
						ImmutableMap.of(StudyModel.MEMBERS, "admin"))
						.withSession(Users.SESSION_EMAIL,
								utils.admin.getEmail()));
		assertThat(status(result)).isEqualTo(OK);

		// Clean up
		utils.removeStudy(studyClone);
	}

	@Test
	public void callShowStudy() throws Exception {
		StudyModel studyClone = utils.cloneAndPersistStudy(studyTemplate);

		Result result = callAction(
				controllers.routes.ref.Studies.showStudy(studyClone.getId()),
				fakeRequest().withSession(Users.SESSION_EMAIL,
						utils.admin.getEmail()));
		assertEquals(SEE_OTHER, status(result));

		// Clean up
		utils.removeStudy(studyClone);
	}

	@Test
	public void callCreateClosedStandaloneRun() throws Exception {
		StudyModel studyClone = utils.cloneAndPersistStudy(studyTemplate);

		JsonNode jsonNode = JsonUtils.OBJECTMAPPER.readTree("{ \""
				+ ClosedStandaloneWorker.COMMENT + "\": \"testcomment\" }");
		Result result = callAction(
				controllers.routes.ref.Studies.createClosedStandaloneRun(studyClone
						.getId()),
				fakeRequest().withJsonBody(jsonNode).withSession(
						Users.SESSION_EMAIL, utils.admin.getEmail()));
		assertThat(status(result)).isEqualTo(OK);

		// Clean up
		utils.removeStudy(studyClone);
	}

	@Test
	public void callCreateTesterRun() throws Exception {
		StudyModel studyClone = utils.cloneAndPersistStudy(studyTemplate);

		JsonNode jsonNode = JsonUtils.OBJECTMAPPER.readTree("{ \""
				+ ClosedStandaloneWorker.COMMENT + "\": \"testcomment\" }");
		Result result = callAction(
				controllers.routes.ref.Studies.createTesterRun(studyClone
						.getId()),
				fakeRequest().withJsonBody(jsonNode).withSession(
						Users.SESSION_EMAIL, utils.admin.getEmail()));
		assertThat(status(result)).isEqualTo(OK);

		// Clean up
		utils.removeStudy(studyClone);
	}

	@Test
	public void callShowMTurkSourceCode() throws Exception {
		StudyModel studyClone = utils.cloneAndPersistStudy(studyTemplate);

		Result result = callAction(
				controllers.routes.ref.Studies.showMTurkSourceCode(studyClone
						.getId()),
				fakeRequest().withHeader("Referer",
						"http://www.example.com:9000").withSession(
						Users.SESSION_EMAIL, utils.admin.getEmail()));
		assertThat(status(result)).isEqualTo(OK);
		assertThat(contentAsString(result)).contains(
				Breadcrumbs.MECHANICAL_TURK_HIT_LAYOUT_SOURCE_CODE);

		// Clean up
		utils.removeStudy(studyClone);
	}

	@Test
	public void callWorkers() throws Exception {
		StudyModel studyClone = utils.cloneAndPersistStudy(studyTemplate);

		Result result = callAction(
				controllers.routes.ref.Studies.workers(studyClone.getId()),
				fakeRequest().withSession(Users.SESSION_EMAIL,
						utils.admin.getEmail()));
		assertThat(status(result)).isEqualTo(OK);
		assertThat(contentAsString(result)).contains(Breadcrumbs.WORKERS);

		// Clean up
		utils.removeStudy(studyClone);
	}

}
