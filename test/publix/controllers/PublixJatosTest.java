package publix.controllers;

import static org.fest.assertions.Assertions.assertThat;
import static play.api.test.Helpers.testServerPort;
import static play.mvc.Http.Status.OK;
import static play.mvc.Http.Status.SEE_OTHER;
import static play.test.Helpers.GET;
import static play.test.Helpers.POST;
import static play.test.Helpers.contentAsString;
import static play.test.Helpers.route;

import java.io.IOException;

import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;

import controllers.gui.Users;
import controllers.publix.Publix;
import controllers.publix.workers.JatosPublix;
import general.AbstractTest;
import models.common.Component;
import models.common.ComponentResult;
import models.common.ComponentResult.ComponentState;
import models.common.Study;
import models.common.StudyResult;
import models.common.StudyResult.StudyState;
import models.common.workers.Worker;
import play.mvc.Http.Cookie;
import play.mvc.Http.Cookies;
import play.mvc.Http.HeaderNames;
import play.mvc.Http.RequestBuilder;
import play.mvc.Result;
import services.publix.idcookie.IdCookie;
import utils.common.JsonUtils;

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

	/**
	 * Functional test: runs a whole study successfully. Includes start/end
	 * study, start component, start next component by ID or position, log error
	 * message, get init data, send session data, submit result data
	 */
	@Test
	public void runWholeStudy() throws IOException {
		Study study = importExampleStudy();
		addStudy(study);

		// ***
		// Start study:
		// studyResult -> STARTED
		Result result = startStudy(study);

		// Check HTTP status is redirect
		assertThat(result.status()).isEqualTo(SEE_OTHER);

		// Check redirect URL
		assertThat(result.header("Location"))
				.isEqualTo("/publix/" + study.getId() + "/"
						+ study.getFirstComponent().getId() + "/start");

		// Check that worker ID is in session
		assertThat(result.session().get(Publix.WORKER_ID))
				.isEqualTo(admin.getWorker().getId().toString());

		// Check JATOS_RUN still in session
		assertThat(result.session().get(JatosPublix.JATOS_RUN))
				.isEqualTo(JatosPublix.RUN_STUDY);

		// Check study result
		assertThat(admin.getWorker().getStudyResultList().size()).isEqualTo(1);
		StudyResult studyResult = admin.getWorker().getLastStudyResult();
		assertThat(studyResult.getStudy()).isEqualTo(study);
		assertThat(studyResult.getWorker()).isEqualTo(admin.getWorker());
		assertThat(studyResult.getWorkerId())
				.isEqualTo(admin.getWorker().getId());
		assertThat(studyResult.getWorkerType())
				.isEqualTo(admin.getWorker().getWorkerType());
		assertThat(studyResult.getStudyState()).isEqualTo(StudyState.STARTED);
		assertThat(studyResult.getStartDate()).isNotNull();

		// ***
		// Start first component
		// studyResult -> STARTED, componentResult -> STARTED
		result = startComponent(study, study.getFirstComponent());
		Cookies cookies = result.cookies();
		studyResultDao.refresh(studyResult);

		assertThat(result.status()).isEqualTo(OK);
		checkIdCookie(result, admin.getWorker(), study, studyResult, 1);

		// And check a random line of the JS code
		assertThat(contentAsString(result))
				.contains("jatos.onLoad(function() {");

		// Check ComponentResult and StudyResult
		assertThat(studyResult.getComponentResultList().size()).isEqualTo(1);
		ComponentResult firstComponentResult = studyResult
				.getComponentResultList().get(0);
		checkStates(studyResult, StudyState.STARTED, firstComponentResult,
				ComponentState.STARTED);
		checkComponentResultAfterStart(study, studyResult, 1, 1);

		// ***
		// Send request to get InitData:
		// studyResult -> DATA_RETRIEVED, componentResult -> DATA_RETRIEVED
		result = initData(study, study.getFirstComponent());

		studyResultDao.refresh(studyResult);
		componentResultDao.refresh(firstComponentResult);

		// Check InitData in response
		assertThat(result.status()).isEqualTo(OK);
		assertThat(JsonUtils.isValidJSON(contentAsString(result))).isTrue();
		JsonNode json = JsonUtils.OBJECTMAPPER
				.readTree(contentAsString(result));
		assertThat(json.get("studySessionData")).isNotNull();
		assertThat(json.get("studyProperties")).isNotNull();
		assertThat(json.get("componentList")).isNotNull();
		assertThat(json.get("componentProperties")).isNotNull();

		// Check studyResult and componentResult
		checkStates(studyResult, StudyState.DATA_RETRIEVED,
				firstComponentResult, ComponentState.DATA_RETRIEVED);

		// ***
		// Send request submitResultData:
		// studyResult -> DATA_RETRIEVED, componentResult -> RESULTDATA_POSTED
		result = submitResultData(study);

		studyResultDao.refresh(studyResult);
		componentResultDao.refresh(firstComponentResult);

		// Check response
		assertThat(result.status()).isEqualTo(OK);
		checkStates(studyResult, StudyState.DATA_RETRIEVED,
				firstComponentResult, ComponentState.RESULTDATA_POSTED);

		// Check componentResult
		assertThat(firstComponentResult.getData())
				.isEqualTo("That's a test result data.");

		// ***
		// Send request setStudySessionData:
		// studyResult -> DATA_RETRIEVED, componentResult -> RESULTDATA_POSTED
		result = setStudySessionData(study);

		studyResultDao.refresh(studyResult);
		componentResultDao.refresh(firstComponentResult);

		// Check response
		assertThat(result.status()).isEqualTo(OK);
		checkStates(studyResult, StudyState.DATA_RETRIEVED,
				firstComponentResult, ComponentState.RESULTDATA_POSTED);

		// Check componentResult
		assertThat(studyResult.getStudySessionData())
				.isEqualTo("That's our session data.");

		// ***
		// Send request startNextComponent: studyResult -> DATA_RETRIEVED,
		// old componentResult -> FINISHED, new componentResult -> STARTED
		result = startNextComponent(study);

		studyResultDao.refresh(studyResult);
		componentResultDao.refresh(firstComponentResult);

		// Check response
		assertThat(result.status()).isEqualTo(SEE_OTHER);

		// Check redirect URL
		assertThat(result.header("Location"))
				.endsWith("/publix/" + study.getId() + "/"
						+ study.getComponent(2).getId() + "/start");

		// ***
		// Start 2. component by ID, studyResult -> DATA_RETRIEVED
		// old componentResult -> FINISHED, new componentResult -> STARTED
		result = startComponent(study, study.getComponent(2));

		studyResultDao.refresh(studyResult);
		componentResultDao.refresh(firstComponentResult);

		assertThat(result.status()).isEqualTo(OK);
		checkIdCookie(result, admin.getWorker(), study, studyResult, 2);

		// And check a random line of the JS code
		assertThat(contentAsString(result))
				.contains("jatos.onLoad(function() {");

		// Check old and new ComponentResult and StudyResult
		assertThat(studyResult.getComponentResultList().size()).isEqualTo(2);
		ComponentResult secondComponentResult = studyResult
				.getComponentResultList().get(1);
		checkStates(studyResult, StudyState.DATA_RETRIEVED,
				secondComponentResult, ComponentState.STARTED);
		checkStates(studyResult, StudyState.DATA_RETRIEVED,
				firstComponentResult, ComponentState.FINISHED);
		checkComponentResultAfterStart(study, studyResult, 2, 2);

		// ***
		// Start 3. component by position, studyResult -> DATA_RETRIEVED
		// old componentResult -> FINISHED, new componentResult -> STARTED
		result = startComponentByPosition(study, 3);

		studyResultDao.refresh(studyResult);
		componentResultDao.refresh(firstComponentResult);

		assertThat(result.status()).isEqualTo(OK);
		checkIdCookie(result, admin.getWorker(), study, studyResult, 3);

		// And check a random line of the JS code
		assertThat(contentAsString(result))
				.contains("jatos.onLoad(function() {");

		// Check old and new ComponentResult and StudyResult
		assertThat(firstComponentResult.getEndDate()).isNotNull();
		checkStates(studyResult, StudyState.DATA_RETRIEVED,
				firstComponentResult, ComponentState.FINISHED);
		checkComponentResultAfterStart(study, studyResult, 3, 3);

		// ***
		// Log error
		result = logError(study, "This is an error message.");

		assertThat(result.status()).isEqualTo(OK);
		// TODO check that error msg appears in log - how?

		// ***
		// Send request to get InitData: prior session data should be there
		// studyResult -> DATA_RETRIEVED, componentResult -> DATA_RETRIEVED
		result = initData(study, study.getComponent(3));

		studyResultDao.refresh(studyResult);
		componentResultDao.refresh(firstComponentResult);

		// Check InitData in response: is session data still there?
		assertThat(result.status()).isEqualTo(OK);
		assertThat(JsonUtils.isValidJSON(contentAsString(result))).isTrue();
		json = JsonUtils.OBJECTMAPPER.readTree(contentAsString(result));
		assertThat(json.get("studySessionData").asText())
				.isEqualTo("That's our session data.");
		assertThat(json.get("studyProperties")).isNotNull();
		assertThat(json.get("componentList")).isNotNull();
		assertThat(json.get("componentProperties")).isNotNull();

		// ***
		// Send request to end study not successfully with error message
		// studyResult -> FAIL, componentResult -> FINISHED
		result = endStudy(study, cookies, false,
				"This%20is%20an%20error%20message.");

		studyResultDao.refresh(studyResult);
		componentResultDao.refresh(firstComponentResult);

		// Check response: HTTP status is redirect (it's a JATOS worker run)
		assertThat(result.status()).isEqualTo(SEE_OTHER);

		// Check redirect URL
		assertThat(result.header("Location"))
				.isEqualTo("/jatos/" + study.getId());

		// Check that 'jatos_run' is removed from Play's session
		assertThat(result.session().get(JatosPublix.JATOS_RUN)).isNull();

		// Check that ID cookie is removed
		assertThat(result.cookie(IdCookie.ID_COOKIE_NAME).value()).isEmpty();

		// Check results
		assertThat(studyResult.getStudyState()).isEqualTo(StudyState.FAIL);
		assertThat(firstComponentResult.getComponentState())
				.isEqualTo(ComponentState.FINISHED);

		// Check the abort message is in flash storage
		assertThat(result.flash().get("info")).isEqualTo(
				"Study finished with message: This is an error message.");

		// Clean-up
		removeStudy(study);
	}

	/**
	 * Functional test: start a study and then abort it.
	 */
	@Test
	public void startAndAbortStudy() throws IOException {
		Study study = importExampleStudy();
		addStudy(study);

		// ***
		// Start study:
		// studyResult -> STARTED
		Result result = startStudy(study);

		// Check HTTP status is redirect
		assertThat(result.status()).isEqualTo(SEE_OTHER);

		// ***
		// Start first component
		// studyResult -> STARTED, componentResult -> STARTED
		result = startComponent(study, study.getFirstComponent());

		Cookies cookies = result.cookies();
		StudyResult studyResult = admin.getWorker().getLastStudyResult();
		studyResultDao.refresh(studyResult);

		assertThat(result.status()).isEqualTo(OK);

		ComponentResult firstComponentResult = studyResult
				.getComponentResultList().get(0);

		// ***
		// Send request to end study
		// studyResult -> ABORTED, componentResult -> ABORTED
		result = abortStudy(study, cookies,
				"This%20is%20an%20abort%20message.");

		studyResultDao.refresh(studyResult);
		componentResultDao.refresh(firstComponentResult);

		// Check response: HTTP status is redirect (it's a JATOS worker run)
		assertThat(result.status()).isEqualTo(SEE_OTHER);

		// Check redirect URL
		assertThat(result.header("Location"))
				.isEqualTo("/jatos/" + study.getId());

		// Check that 'jatos_run' is removed from Play's session
		assertThat(result.session().get(JatosPublix.JATOS_RUN)).isNull();

		// Check that ID cookie is removed
		assertThat(result.cookie(IdCookie.ID_COOKIE_NAME).value()).isEmpty();

		// Check results
		assertThat(studyResult.getStudyState()).isEqualTo(StudyState.ABORTED);
		assertThat(firstComponentResult.getComponentState())
				.isEqualTo(ComponentState.ABORTED);

		// Check the abort message is in flash storage
		assertThat(result.flash().get("info")).isEqualTo(
				"Study finished with message: This is an abort message.");

		// Clean-up
		removeStudy(study);

	}

	private Result startStudy(Study study) {
		String url = "/publix/" + study.getId() + "/start?"
				+ JatosPublix.JATOS_WORKER_ID + "=" + admin.getWorker().getId();
		RequestBuilder request = new RequestBuilder().method(GET).uri(url)
				.session(Users.SESSION_EMAIL, admin.getEmail())
				.session(JatosPublix.JATOS_RUN, JatosPublix.RUN_STUDY);
		return route(request);
	}

	private Result startComponentByPosition(Study study, int position) {
		Result result;
		String url = "/publix/" + study.getId() + "/component/start?position="
				+ position;
		RequestBuilder request = new RequestBuilder().method(GET).uri(url)
				.session("email", admin.getEmail())
				.session(JatosPublix.JATOS_RUN, JatosPublix.RUN_STUDY)
				.session(Publix.WORKER_ID, admin.getWorker().getId().toString())
				// .session(PublixInterceptor.WORKER_TYPE,
				// JatosWorker.WORKER_TYPE)
				.session(Publix.BATCH_ID, "1")
				.session(Publix.STUDY_ASSETS, study.getDirName())
				.header(HeaderNames.HOST, "localhost:" + testServerPort());
		result = route(request, 10000);
		return result;
	}

	private Result startComponent(Study study, Component component) {
		String url = "/publix/" + study.getId() + "/" + component.getId()
				+ "/start";
		RequestBuilder request = new RequestBuilder().method(GET).uri(url)
				.session(Users.SESSION_EMAIL, admin.getEmail())
				.session(JatosPublix.JATOS_RUN, JatosPublix.RUN_STUDY)
				.session(Publix.WORKER_ID, admin.getWorker().getId().toString())
				// .session(PublixInterceptor.WORKER_TYPE,
				// JatosWorker.WORKER_TYPE)
				.session(Publix.BATCH_ID, "1")
				.session(Publix.STUDY_ASSETS, study.getDirName())
				.header(HeaderNames.HOST, "localhost:" + testServerPort());
		return route(request, 10000);
	}

	private Result startNextComponent(Study study) {
		Result result;
		String url = "/publix/" + study.getId() + "/nextComponent/start";
		RequestBuilder request = new RequestBuilder().method(GET).uri(url)
				.session("email", admin.getEmail())
				.session(JatosPublix.JATOS_RUN, JatosPublix.RUN_STUDY)
				.session(Publix.WORKER_ID, admin.getWorker().getId().toString())
				// .session(PublixInterceptor.WORKER_TYPE,
				// JatosWorker.WORKER_TYPE)
				.session(Publix.BATCH_ID, "1")
				.session(Publix.STUDY_ASSETS, study.getDirName())
				.header(HeaderNames.HOST, "localhost:" + testServerPort())
				.bodyText("That's session data.");
		result = route(request, 10000);
		return result;
	}

	private Result initData(Study study, Component component) {
		Result result;
		String url = "/publix/" + study.getId() + "/" + component.getId()
				+ "/initData";
		RequestBuilder request = new RequestBuilder().method(GET).uri(url)
				.session("email", admin.getEmail())
				.session(JatosPublix.JATOS_RUN, JatosPublix.RUN_STUDY)
				.session(Publix.WORKER_ID, admin.getWorker().getId().toString())
				// .session(PublixInterceptor.WORKER_TYPE,
				// JatosWorker.WORKER_TYPE)
				.session(Publix.BATCH_ID, "1")
				.session(Publix.STUDY_ASSETS, study.getDirName())
				.header(HeaderNames.HOST, "localhost:" + testServerPort());
		result = route(request, 10000);
		return result;
	}

	private Result logError(Study study, String msg) {
		Result result;
		String url = "/publix/" + study.getId() + "/"
				+ study.getComponent(3).getId() + "/log";
		RequestBuilder request = new RequestBuilder().method(POST).uri(url)
				.session("email", admin.getEmail())
				.session(JatosPublix.JATOS_RUN, JatosPublix.RUN_STUDY)
				.session(Publix.WORKER_ID, admin.getWorker().getId().toString())
				// .session(PublixInterceptor.WORKER_TYPE,
				// JatosWorker.WORKER_TYPE)
				.session(Publix.BATCH_ID, "1")
				.session(Publix.STUDY_ASSETS, study.getDirName())
				.header(HeaderNames.HOST, "localhost:" + testServerPort())
				.bodyText(msg);
		result = route(request, 10000);
		return result;
	}

	private Result setStudySessionData(Study study) {
		Result result;
		String url = "/publix/" + study.getId() + "/studySessionData";
		RequestBuilder request = new RequestBuilder().method(POST).uri(url)
				.session("email", admin.getEmail())
				.session(JatosPublix.JATOS_RUN, JatosPublix.RUN_STUDY)
				.session(Publix.WORKER_ID, admin.getWorker().getId().toString())
				// .session(PublixInterceptor.WORKER_TYPE,
				// JatosWorker.WORKER_TYPE)
				.session(Publix.BATCH_ID, "1")
				.session(Publix.STUDY_ASSETS, study.getDirName())
				.header(HeaderNames.HOST, "localhost:" + testServerPort())
				.bodyText("That's our session data.");
		result = route(request, 10000);
		return result;
	}

	private Result submitResultData(Study study) {
		Result result;
		String url = "/publix/" + study.getId() + "/"
				+ study.getFirstComponent().getId() + "/resultData";
		RequestBuilder request = new RequestBuilder().method(POST).uri(url)
				.session("email", admin.getEmail())
				.session(JatosPublix.JATOS_RUN, JatosPublix.RUN_STUDY)
				.session(Publix.WORKER_ID, admin.getWorker().getId().toString())
				// .session(PublixInterceptor.WORKER_TYPE,
				// JatosWorker.WORKER_TYPE)
				.session(Publix.BATCH_ID, "1")
				.session(Publix.STUDY_ASSETS, study.getDirName())
				.header(HeaderNames.HOST, "localhost:" + testServerPort())
				.bodyText("That's a test result data.");
		result = route(request, 10000);
		return result;
	}

	private Result endStudy(Study study, Cookies cookies, boolean successful,
			String errorMsg) {
		Result result;
		String url = "/publix/" + study.getId() + "/end?successful="
				+ successful + "&errorMsg=" + errorMsg;
		RequestBuilder request = new RequestBuilder().method(GET).uri(url)
				.session("email", admin.getEmail())
				.session(JatosPublix.JATOS_RUN, JatosPublix.RUN_STUDY)
				.session(Publix.WORKER_ID, admin.getWorker().getId().toString())
				// .session(PublixInterceptor.WORKER_TYPE,
				// JatosWorker.WORKER_TYPE)
				.session(Publix.BATCH_ID, "1")
				.session(Publix.STUDY_ASSETS, study.getDirName())
				.header(HeaderNames.HOST, "localhost:" + testServerPort())
				.cookie(cookies.get(IdCookie.ID_COOKIE_NAME));
		result = route(request, 10000);
		return result;
	}

	private Result abortStudy(Study study, Cookies cookies, String abortMsg) {
		Result result;
		String url = "/publix/" + study.getId() + "/abort?message=" + abortMsg;
		RequestBuilder request = new RequestBuilder().method(GET).uri(url)
				.session("email", admin.getEmail())
				.session(JatosPublix.JATOS_RUN, JatosPublix.RUN_STUDY)
				.session(Publix.WORKER_ID, admin.getWorker().getId().toString())
				// .session(PublixInterceptor.WORKER_TYPE,
				// JatosWorker.WORKER_TYPE)
				.session(Publix.BATCH_ID, "1")
				.session(Publix.STUDY_ASSETS, study.getDirName())
				.header(HeaderNames.HOST, "localhost:" + testServerPort())
				.cookie(cookies.get(IdCookie.ID_COOKIE_NAME));
		result = route(request, 10000);
		return result;
	}

	private void checkIdCookie(Result result, Worker worker, Study study,
			StudyResult studyResult, int componentPosition) {
		Cookie idCookie = result.cookie(IdCookie.ID_COOKIE_NAME);
		assertThat(idCookie.value()).contains("workerId=" + worker.getId());
		assertThat(idCookie.value()).contains(
				"componentId=" + study.getComponent(componentPosition).getId());
		assertThat(idCookie.value())
				.contains("componentPos=" + componentPosition);
		assertThat(idCookie.value()).contains("studyId=" + study.getId());
		assertThat(idCookie.value())
				.contains("studyResultId=" + studyResult.getId());
		assertThat(idCookie.value()).contains("componentResultId");
	}

	private void checkStates(StudyResult studyResult, StudyState studyState,
			ComponentResult componentResult, ComponentState componentState) {
		assertThat(studyResult.getStudyState()).isEqualTo(studyState);
		assertThat(componentResult.getComponentState())
				.isEqualTo(componentState);
	}

	private void checkComponentResultAfterStart(Study study,
			StudyResult studyResult, int componentPosition,
			int componentResultListSize) {
		assertThat(studyResult.getComponentResultList().size())
				.isEqualTo(componentResultListSize);

		// Get the last component result
		ComponentResult componentResult = studyResult.getComponentResultList()
				.get(componentResultListSize - 1);

		assertThat(componentResult.getComponent())
				.isEqualTo(study.getComponent(componentPosition));
		assertThat(componentResult.getStudyResult()).isEqualTo(studyResult);
		assertThat(componentResult.getWorkerId())
				.isEqualTo(admin.getWorker().getId());
		assertThat(componentResult.getWorkerType())
				.isEqualTo(admin.getWorker().getWorkerType());
		assertThat(componentResult.getComponentState())
				.isEqualTo(ComponentState.STARTED);
		assertThat(componentResult.getStartDate()).isNotNull();
	}
}
