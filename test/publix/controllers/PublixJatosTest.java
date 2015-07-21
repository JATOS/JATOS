package publix.controllers;

import static org.fest.assertions.Assertions.assertThat;
import static play.api.test.Helpers.testServerPort;
import static play.mvc.Http.Status.OK;
import static play.mvc.Http.Status.SEE_OTHER;
import static play.test.Helpers.GET;
import static play.test.Helpers.POST;
import static play.test.Helpers.contentAsString;
import static play.test.Helpers.cookie;
import static play.test.Helpers.header;
import static play.test.Helpers.routeAndCall;
import static play.test.Helpers.session;
import static play.test.Helpers.status;

import java.io.IOException;

import models.ComponentResult;
import models.ComponentResult.ComponentState;
import models.StudyModel;
import models.StudyResult;
import models.StudyResult.StudyState;
import models.workers.JatosWorker;
import models.workers.Worker;

import org.junit.Test;

import play.mvc.Http.Cookie;
import play.mvc.Http.HeaderNames;
import play.mvc.Result;
import play.test.FakeRequest;
import publix.controllers.jatos.JatosPublix;
import utils.JsonUtils;

import com.fasterxml.jackson.databind.JsonNode;

import common.AbstractTest;
import controllers.Users;

/**
 * @author Kristian Lange
 */
public class PublixJatosTest extends AbstractTest {

	@Override
	public void before() throws Exception {
	}

	@Override
	public void after() throws Exception {
	}

	@Test
	public void simpleCheck() {
		int a = 1 + 1;
		assertThat(a).isEqualTo(2);
	}

	@Test
	public void startStudy() throws IOException {
		StudyModel study = importExampleStudy();
		addStudy(study);

		// ***
		// Start study:
		// studyResult -> STARTED
		FakeRequest fakeReq = new FakeRequest(GET, "/publix/" + study.getId()
				+ "/start?" + JatosPublix.JATOS_WORKER_ID + "="
				+ admin.getWorker().getId());
		fakeReq.withSession(Users.SESSION_EMAIL, admin.getEmail());
		fakeReq.withSession(JatosPublix.JATOS_RUN, JatosPublix.RUN_STUDY);
		Result result = routeAndCall(fakeReq, 10000);

		// Check HTTP status is redirect
		assertThat(status(result)).isEqualTo(SEE_OTHER);

		// Check redirect URL
		assertThat(header("Location", result)).isEqualTo(
				"/publix/" + study.getId() + "/"
						+ study.getFirstComponent().getId() + "/start");

		// Check that worker ID is in session
		assertThat(session(result).get(Publix.WORKER_ID)).isEqualTo(
				admin.getWorker().getId().toString());

		// Check JATOS_RUN still in session
		assertThat(session(result).get(JatosPublix.JATOS_RUN)).isEqualTo(
				JatosPublix.RUN_STUDY);

		// Check study result
		assertThat(admin.getWorker().getStudyResultList().size()).isEqualTo(1);
		StudyResult studyResult = admin.getWorker().getStudyResultList().get(0);
		assertThat(studyResult.getStudy()).isEqualTo(study);
		assertThat(studyResult.getWorker()).isEqualTo(admin.getWorker());
		assertThat(studyResult.getWorkerId()).isEqualTo(
				admin.getWorker().getId());
		assertThat(studyResult.getWorkerType()).isEqualTo(
				admin.getWorker().getWorkerType());
		assertThat(studyResult.getStudyState()).isEqualTo(StudyState.STARTED);
		assertThat(studyResult.getStartDate()).isNotNull();

		// ***
		// Start first component
		// studyResult -> STARTED, componentResult -> STARTED
		fakeReq = new FakeRequest(GET, "/publix/" + study.getId() + "/"
				+ study.getFirstComponent().getId() + "/start");
		fakeReq.withSession(Users.SESSION_EMAIL, admin.getEmail());
		fakeReq.withSession(JatosPublix.JATOS_RUN, JatosPublix.RUN_STUDY);
		fakeReq.withSession(Publix.WORKER_ID, admin.getWorker().getId()
				.toString());
		fakeReq.withSession(PublixInterceptor.WORKER_TYPE,
				JatosWorker.WORKER_TYPE);
		fakeReq.withHeader(HeaderNames.HOST, "localhost:" + testServerPort());
		result = routeAndCall(fakeReq, 10000);
		studyResultDao.refresh(studyResult);

		assertThat(status(result)).isEqualTo(OK);
		checkIdCookie(result, admin.getWorker(), study, studyResult, 1);

		// And check a random line of the JS code
		assertThat(contentAsString(result)).contains(
				"jatos.onLoad(function() {");

		// Check ComponentResult and StudyResult
		assertThat(studyResult.getComponentResultList().size()).isEqualTo(1);
		ComponentResult componentResult = studyResult.getComponentResultList()
				.get(0);
		checkStates(studyResult, StudyState.STARTED, componentResult,
				ComponentState.STARTED);
		checkComponentResultAfterStart(study, studyResult, 1, 1);

		// ***
		// Send request to get InitData:
		// studyResult -> DATA_RETRIEVED, componentResult -> DATA_RETRIEVED
		fakeReq = new FakeRequest(GET, "/publix/" + study.getId() + "/"
				+ study.getFirstComponent().getId() + "/initData");
		fakeReq.withSession(Users.SESSION_EMAIL, admin.getEmail());
		fakeReq.withSession(JatosPublix.JATOS_RUN, JatosPublix.RUN_STUDY);
		fakeReq.withSession(Publix.WORKER_ID, admin.getWorker().getId()
				.toString());
		fakeReq.withSession(PublixInterceptor.WORKER_TYPE,
				JatosWorker.WORKER_TYPE);
		fakeReq.withHeader(HeaderNames.HOST, "localhost:" + testServerPort());
		result = routeAndCall(fakeReq, 10000);

		studyResultDao.refresh(studyResult);
		componentResultDao.refresh(componentResult);

		// Check InitData in response
		assertThat(status(result)).isEqualTo(OK);
		assertThat(JsonUtils.isValidJSON(contentAsString(result))).isTrue();
		JsonNode json = JsonUtils.OBJECTMAPPER
				.readTree(contentAsString(result));
		assertThat(json.get("studySession")).isNotNull();
		assertThat(json.get("studyProperties")).isNotNull();
		assertThat(json.get("componentList")).isNotNull();
		assertThat(json.get("componentProperties")).isNotNull();

		// Check studyResult and componentResult
		checkStates(studyResult, StudyState.DATA_RETRIEVED, componentResult,
				ComponentState.DATA_RETRIEVED);

		// ***
		// Send request submitResultData:
		// studyResult -> DATA_RETRIEVED, componentResult -> RESULTDATA_POSTED
		fakeReq = new FakeRequest(POST, "/publix/" + study.getId() + "/"
				+ study.getFirstComponent().getId() + "/submitResultData");
		fakeReq.withSession(Users.SESSION_EMAIL, admin.getEmail());
		fakeReq.withSession(JatosPublix.JATOS_RUN, JatosPublix.RUN_STUDY);
		fakeReq.withSession(Publix.WORKER_ID, admin.getWorker().getId()
				.toString());
		fakeReq.withSession(PublixInterceptor.WORKER_TYPE,
				JatosWorker.WORKER_TYPE);
		fakeReq.withHeader(HeaderNames.HOST, "localhost:" + testServerPort());
		fakeReq.withTextBody("That's a test result data.");
		result = routeAndCall(fakeReq, 10000);

		studyResultDao.refresh(studyResult);
		componentResultDao.refresh(componentResult);

		// Check response
		assertThat(status(result)).isEqualTo(OK);
		checkStates(studyResult, StudyState.DATA_RETRIEVED, componentResult,
				ComponentState.RESULTDATA_POSTED);

		// Check componentResult
		assertThat(componentResult.getData()).isEqualTo(
				"That's a test result data.");

		// ***
		// Send request setStudySessionData:
		// studyResult -> DATA_RETRIEVED, componentResult -> RESULTDATA_POSTED
		fakeReq = new FakeRequest(POST, "/publix/" + study.getId()
				+ "/setSessionData");
		fakeReq.withSession(Users.SESSION_EMAIL, admin.getEmail());
		fakeReq.withSession(JatosPublix.JATOS_RUN, JatosPublix.RUN_STUDY);
		fakeReq.withSession(Publix.WORKER_ID, admin.getWorker().getId()
				.toString());
		fakeReq.withSession(PublixInterceptor.WORKER_TYPE,
				JatosWorker.WORKER_TYPE);
		fakeReq.withHeader(HeaderNames.HOST, "localhost:" + testServerPort());
		fakeReq.withTextBody("That's our session data.");
		result = routeAndCall(fakeReq, 10000);

		studyResultDao.refresh(studyResult);
		componentResultDao.refresh(componentResult);

		// Check response
		assertThat(status(result)).isEqualTo(OK);
		checkStates(studyResult, StudyState.DATA_RETRIEVED, componentResult,
				ComponentState.RESULTDATA_POSTED);

		// Check componentResult
		assertThat(studyResult.getStudySessionData()).isEqualTo(
				"That's our session data.");

		// ***
		// Send request startNextComponent: studyResult -> DATA_RETRIEVED,
		// old componentResult -> FINISHED, new componentResult -> STARTED
		fakeReq = new FakeRequest(GET, "/publix/" + study.getId()
				+ "/startNextComponent");
		fakeReq.withSession(Users.SESSION_EMAIL, admin.getEmail());
		fakeReq.withSession(JatosPublix.JATOS_RUN, JatosPublix.RUN_STUDY);
		fakeReq.withSession(Publix.WORKER_ID, admin.getWorker().getId()
				.toString());
		fakeReq.withSession(PublixInterceptor.WORKER_TYPE,
				JatosWorker.WORKER_TYPE);
		fakeReq.withHeader(HeaderNames.HOST, "localhost:" + testServerPort());
		fakeReq.withTextBody("That's session data.");
		result = routeAndCall(fakeReq, 10000);

		studyResultDao.refresh(studyResult);
		componentResultDao.refresh(componentResult);

		// Check response
		assertThat(status(result)).isEqualTo(SEE_OTHER);

		// Check redirect URL
		assertThat(header("Location", result)).isEqualTo(
				"http://localhost:" + testServerPort() + "/publix/"
						+ study.getId() + "/" + study.getComponent(2).getId()
						+ "/start");

		// ***
		// Start 2. component by ID, studyResult -> DATA_RETRIEVED
		// old componentResult -> FINISHED, new componentResult -> STARTED
		fakeReq = new FakeRequest(GET, "/publix/" + study.getId() + "/"
				+ study.getComponent(2).getId() + "/start");
		fakeReq.withSession(Users.SESSION_EMAIL, admin.getEmail());
		fakeReq.withSession(JatosPublix.JATOS_RUN, JatosPublix.RUN_STUDY);
		fakeReq.withSession(Publix.WORKER_ID, admin.getWorker().getId()
				.toString());
		fakeReq.withSession(PublixInterceptor.WORKER_TYPE,
				JatosWorker.WORKER_TYPE);
		fakeReq.withHeader(HeaderNames.HOST, "localhost:" + testServerPort());
		result = routeAndCall(fakeReq, 10000);

		studyResultDao.refresh(studyResult);
		componentResultDao.refresh(componentResult);

		assertThat(status(result)).isEqualTo(OK);
		checkIdCookie(result, admin.getWorker(), study, studyResult, 2);

		// And check a random line of the JS code
		assertThat(contentAsString(result)).contains(
				"jatos.onLoad(function() {");

		// Check old and new ComponentResult and StudyResult
		checkStates(studyResult, StudyState.DATA_RETRIEVED, componentResult,
				ComponentState.FINISHED);
		checkComponentResultAfterStart(study, studyResult, 2, 2);

		// ***
		// Start 3. component by position, studyResult -> DATA_RETRIEVED
		// old componentResult -> FINISHED, new componentResult -> STARTED
		fakeReq = new FakeRequest(GET, "/publix/" + study.getId()
				+ "/startComponent?position=3");
		fakeReq.withSession(Users.SESSION_EMAIL, admin.getEmail());
		fakeReq.withSession(JatosPublix.JATOS_RUN, JatosPublix.RUN_STUDY);
		fakeReq.withSession(Publix.WORKER_ID, admin.getWorker().getId()
				.toString());
		fakeReq.withSession(PublixInterceptor.WORKER_TYPE,
				JatosWorker.WORKER_TYPE);
		fakeReq.withHeader(HeaderNames.HOST, "localhost:" + testServerPort());
		result = routeAndCall(fakeReq, 10000);

		studyResultDao.refresh(studyResult);
		componentResultDao.refresh(componentResult);

		assertThat(status(result)).isEqualTo(OK);
		checkIdCookie(result, admin.getWorker(), study, studyResult, 3);

		// And check a random line of the JS code
		assertThat(contentAsString(result)).contains(
				"jatos.onLoad(function() {");

		// Check old and new ComponentResult and StudyResult
		assertThat(componentResult.getEndDate()).isNotNull();
		checkStates(studyResult, StudyState.DATA_RETRIEVED, componentResult,
				ComponentState.FINISHED);
		checkComponentResultAfterStart(study, studyResult, 3, 3);

		// ***
		// Log error
		fakeReq = new FakeRequest(POST, "/publix/" + study.getId() + "/"
				+ study.getComponent(3).getId() + "/logError");
		fakeReq.withSession(Users.SESSION_EMAIL, admin.getEmail());
		fakeReq.withSession(JatosPublix.JATOS_RUN, JatosPublix.RUN_STUDY);
		fakeReq.withSession(Publix.WORKER_ID, admin.getWorker().getId()
				.toString());
		fakeReq.withSession(PublixInterceptor.WORKER_TYPE,
				JatosWorker.WORKER_TYPE);
		fakeReq.withHeader(HeaderNames.HOST, "localhost:" + testServerPort());
		fakeReq.withTextBody("This is an error message.");
		result = routeAndCall(fakeReq, 10000);

		assertThat(status(result)).isEqualTo(OK);
		// TODO check that error msg appears in log

		// ***
		// Send request to get InitData: prior session data should be there
		// studyResult -> DATA_RETRIEVED, componentResult -> DATA_RETRIEVED
		fakeReq = new FakeRequest(GET, "/publix/" + study.getId() + "/"
				+ study.getComponent(3).getId() + "/initData");
		fakeReq.withSession(Users.SESSION_EMAIL, admin.getEmail());
		fakeReq.withSession(JatosPublix.JATOS_RUN, JatosPublix.RUN_STUDY);
		fakeReq.withSession(Publix.WORKER_ID, admin.getWorker().getId()
				.toString());
		fakeReq.withSession(PublixInterceptor.WORKER_TYPE,
				JatosWorker.WORKER_TYPE);
		fakeReq.withHeader(HeaderNames.HOST, "localhost:" + testServerPort());
		result = routeAndCall(fakeReq, 10000);

		studyResultDao.refresh(studyResult);
		componentResultDao.refresh(componentResult);

		// Check InitData in response: is session data still there?
		assertThat(status(result)).isEqualTo(OK);
		assertThat(JsonUtils.isValidJSON(contentAsString(result))).isTrue();
		json = JsonUtils.OBJECTMAPPER.readTree(contentAsString(result));
		assertThat(json.get("studySession").asText()).isEqualTo(
				"That's our session data.");
		assertThat(json.get("studyProperties")).isNotNull();
		assertThat(json.get("componentList")).isNotNull();
		assertThat(json.get("componentProperties")).isNotNull();

		// TODO end study

		// Clean-up
		removeStudy(study);
	}

	private void checkIdCookie(Result result, Worker worker, StudyModel study,
			StudyResult studyResult, int componentPosition) {
		Cookie idCookie = cookie(Publix.ID_COOKIE_NAME, result);
		assertThat(idCookie.value()).contains("workerId=" + worker.getId());
		assertThat(idCookie.value()).contains(
				"componentId=" + study.getComponent(componentPosition).getId());
		assertThat(idCookie.value()).contains(
				"componentPos=" + componentPosition);
		assertThat(idCookie.value()).contains("studyId=" + study.getId());
		assertThat(idCookie.value()).contains(
				"studyResultId=" + studyResult.getId());
		assertThat(idCookie.value()).contains("componentResultId");
	}

	private void checkStates(StudyResult studyResult, StudyState studyState,
			ComponentResult componentResult, ComponentState componentState) {
		assertThat(studyResult.getStudyState()).isEqualTo(studyState);
		assertThat(componentResult.getComponentState()).isEqualTo(
				componentState);
	}

	private void checkComponentResultAfterStart(StudyModel study,
			StudyResult studyResult, int componentPosition,
			int componentResultListSize) {
		assertThat(studyResult.getComponentResultList().size()).isEqualTo(
				componentResultListSize);

		// Get the last component result
		ComponentResult componentResult = studyResult.getComponentResultList()
				.get(componentResultListSize - 1);

		assertThat(componentResult.getComponent()).isEqualTo(
				study.getComponent(componentPosition));
		assertThat(componentResult.getStudyResult()).isEqualTo(studyResult);
		assertThat(componentResult.getWorkerId()).isEqualTo(
				admin.getWorker().getId());
		assertThat(componentResult.getWorkerType()).isEqualTo(
				admin.getWorker().getWorkerType());
		assertThat(componentResult.getComponentState()).isEqualTo(
				ComponentState.STARTED);
		assertThat(componentResult.getStartDate()).isNotNull();
	}
}
