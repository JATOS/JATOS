package publix.controllers;

import static org.fest.assertions.Assertions.assertThat;
import static play.mvc.Http.Status.OK;
import static play.mvc.Http.Status.SEE_OTHER;
import static play.test.Helpers.GET;
import static play.test.Helpers.POST;
import static play.test.Helpers.contentAsString;
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

	// @Test
	public void simpleCheck() {
		int a = 1 + 1;
		assertThat(a).isEqualTo(2);
	}

//	@Test
	public void startStudy() throws IOException {
		StudyModel study = importExampleStudy();
		addStudy(study);

		// Start study
		FakeRequest fakeReq = new FakeRequest(GET, "/publix/" + study.getId()
				+ "/start?" + JatosPublix.JATOS_WORKER_ID + "="
				+ admin.getWorker().getId());
		fakeReq.withSession(Users.SESSION_EMAIL, admin.getEmail());
		fakeReq.withSession(JatosPublix.JATOS_RUN, JatosPublix.RUN_STUDY);
		Result result = routeAndCall(fakeReq, 10000);

		// Check HTTP status is redirect
		assertThat(status(result)).isEqualTo(SEE_OTHER);

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

		// Start first component
		fakeReq = new FakeRequest(GET, "/publix/" + study.getId() + "/"
				+ study.getFirstComponent().getId() + "/start");
		fakeReq.withSession(Users.SESSION_EMAIL, admin.getEmail());
		fakeReq.withSession(JatosPublix.JATOS_RUN, JatosPublix.RUN_STUDY);
		fakeReq.withSession(Publix.WORKER_ID, admin.getWorker().getId()
				.toString());
		fakeReq.withSession(PublixInterceptor.WORKER_TYPE,
				JatosWorker.WORKER_TYPE);
		fakeReq.withHeader(HeaderNames.HOST, "localhost:"
				+ play.api.test.Helpers.testServerPort());
		result = routeAndCall(fakeReq, 10000);

		assertThat(status(result)).isEqualTo(OK);
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

		// Send request to get InitData
		fakeReq = new FakeRequest(GET, "/publix/" + study.getId() + "/"
				+ study.getFirstComponent().getId() + "/initData");
		fakeReq.withSession(Users.SESSION_EMAIL, admin.getEmail());
		fakeReq.withSession(JatosPublix.JATOS_RUN, JatosPublix.RUN_STUDY);
		fakeReq.withSession(Publix.WORKER_ID, admin.getWorker().getId()
				.toString());
		fakeReq.withSession(PublixInterceptor.WORKER_TYPE,
				JatosWorker.WORKER_TYPE);
		fakeReq.withHeader(HeaderNames.HOST, "localhost:"
				+ play.api.test.Helpers.testServerPort());
		result = routeAndCall(fakeReq, 10000);

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
//		assertThat(studyResult.getStudyState()).isEqualTo(
//				StudyState.DATA_RETRIEVED);
//		assertThat(componentResult.getComponentState()).isEqualTo(
//				ComponentState.DATA_RETRIEVED);

		// Send request submitResultData
		fakeReq = new FakeRequest(POST, "/publix/" + study.getId() + "/"
				+ study.getFirstComponent().getId() + "/submitResultData");
		fakeReq.withSession(Users.SESSION_EMAIL, admin.getEmail());
		fakeReq.withSession(JatosPublix.JATOS_RUN, JatosPublix.RUN_STUDY);
		fakeReq.withSession(Publix.WORKER_ID, admin.getWorker().getId()
				.toString());
		fakeReq.withSession(PublixInterceptor.WORKER_TYPE,
				JatosWorker.WORKER_TYPE);
		fakeReq.withHeader(HeaderNames.HOST, "localhost:"
				+ play.api.test.Helpers.testServerPort());
		fakeReq.withTextBody("That's a test result data.");
		result = routeAndCall(fakeReq, 10000);

		// Check response
		assertThat(status(result)).isEqualTo(OK);
//		assertThat(studyResult.getStudyState()).isEqualTo(
//				StudyState.DATA_RETRIEVED);
//		assertThat(componentResult.getComponentState()).isEqualTo(
//				ComponentState.RESULTDATA_POSTED);

		// TODO setStudySessionData
		// TODO start next component
		// TODO end study

		// Clean-up
		removeStudy(study);
		testServer.stop();
	}
}
