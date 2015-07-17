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

		// Check ID cookie
		Cookie idCookie = cookie(Publix.ID_COOKIE_NAME, result);
		assertThat(idCookie.value()).contains(
				"workerId=" + admin.getWorker().getId());
		assertThat(idCookie.value()).contains(
				"componentId=" + study.getFirstComponent().getId());
		assertThat(idCookie.value()).contains("componentPos=1");
		assertThat(idCookie.value()).contains("studyId=" + study.getId());
		assertThat(idCookie.value()).contains(
				"studyResultId=" + studyResult.getId());
		assertThat(idCookie.value()).contains("componentResultId");

		// And check a random line of the JS code
		assertThat(contentAsString(result)).contains(
				"jatos.onLoad(function() {");

		// Check ComponentResult and StudyResult
		assertThat(studyResult.getComponentResultList().size()).isEqualTo(1);
		assertThat(studyResult.getStudyState()).isEqualTo(StudyState.STARTED);
		ComponentResult componentResult = studyResult.getComponentResultList()
				.get(0);
		assertThat(componentResult.getComponent()).isEqualTo(
				study.getFirstComponent());
		assertThat(componentResult.getStudyResult()).isEqualTo(studyResult);
		assertThat(componentResult.getWorkerId()).isEqualTo(
				admin.getWorker().getId());
		assertThat(componentResult.getWorkerType()).isEqualTo(
				admin.getWorker().getWorkerType());
		assertThat(componentResult.getComponentState()).isEqualTo(
				ComponentState.STARTED);
		assertThat(componentResult.getStartDate()).isNotNull();

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
		assertThat(studyResult.getStudyState()).isEqualTo(
				StudyState.DATA_RETRIEVED);
		assertThat(componentResult.getComponentState()).isEqualTo(
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
		assertThat(studyResult.getStudyState()).isEqualTo(
				StudyState.DATA_RETRIEVED);
		assertThat(componentResult.getComponentState()).isEqualTo(
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
		fakeReq.withTextBody("That's session data.");
		result = routeAndCall(fakeReq, 10000);

		studyResultDao.refresh(studyResult);
		componentResultDao.refresh(componentResult);

		// Check response
		assertThat(status(result)).isEqualTo(OK);
		assertThat(studyResult.getStudyState()).isEqualTo(
				StudyState.DATA_RETRIEVED);
		assertThat(componentResult.getComponentState()).isEqualTo(
				ComponentState.RESULTDATA_POSTED);

		// Check componentResult
		assertThat(studyResult.getStudySessionData()).isEqualTo(
				"That's session data.");

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
		// Start 2. component, studyResult -> STARTED
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

		// Check ID cookie
		idCookie = cookie(Publix.ID_COOKIE_NAME, result);
		assertThat(idCookie.value()).contains(
				"workerId=" + admin.getWorker().getId());
		assertThat(idCookie.value()).contains(
				"componentId=" + study.getComponent(2).getId());
		assertThat(idCookie.value()).contains("componentPos=2");
		assertThat(idCookie.value()).contains("studyId=" + study.getId());
		assertThat(idCookie.value()).contains(
				"studyResultId=" + studyResult.getId());
		assertThat(idCookie.value()).contains("componentResultId");

		// And check a random line of the JS code
		assertThat(contentAsString(result)).contains(
				"jatos.onLoad(function() {");

		// Check ComponentResult and StudyResult
		assertThat(studyResult.getComponentResultList().size()).isEqualTo(2);
		assertThat(studyResult.getStudyState()).isEqualTo(StudyState.DATA_RETRIEVED);
		assertThat(componentResult.getComponentState()).isEqualTo(ComponentState.FINISHED);
		
		componentResult = studyResult.getComponentResultList()
				.get(1);
		assertThat(componentResult.getComponent()).isEqualTo(
				study.getComponent(2));
		assertThat(componentResult.getStudyResult()).isEqualTo(studyResult);
		assertThat(componentResult.getWorkerId()).isEqualTo(
				admin.getWorker().getId());
		assertThat(componentResult.getWorkerType()).isEqualTo(
				admin.getWorker().getWorkerType());
		assertThat(componentResult.getComponentState()).isEqualTo(
				ComponentState.STARTED);
		assertThat(componentResult.getStartDate()).isNotNull();

		// TODO start by Position
		// TODO logError
		// TODO end study

		// Clean-up
		removeStudy(study);
	}
}
