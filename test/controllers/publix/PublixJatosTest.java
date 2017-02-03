package controllers.publix;

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
import controllers.publix.workers.JatosPublix;
import controllers.publix.workers.JatosPublix.JatosRun;
import general.AbstractTest;
import models.common.Component;
import models.common.ComponentResult;
import models.common.ComponentResult.ComponentState;
import models.common.Study;
import models.common.StudyResult;
import models.common.StudyResult.StudyState;
import play.mvc.Http.Cookie;
import play.mvc.Http.HeaderNames;
import play.mvc.Http.RequestBuilder;
import play.mvc.Result;
import utils.common.JsonUtils;

/**
 * Integration test that does a whole run with the JatosWorker, calls all the
 * endpoints of a run with a JatosWorker
 * 
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
		Cookie idCookie = result.cookie("JATOS_IDS_0");
		StudyResult studyResult = admin.getWorker().getLastStudyResult();

		// Check HTTP status is redirect
		assertThat(result.status()).isEqualTo(SEE_OTHER);

		// Check redirect URL
		assertThat(result.header("Location").get())
				.startsWith("/publix/" + study.getId() + "/"
						+ study.getFirstComponent().getId() + "/start?srid=");

		// Check that ID cookie exists
		assertThat(idCookie.value()).isNotEmpty();

		// Check JATOS_RUN is removed from session
		assertThat(result.session().get(JatosPublix.SESSION_JATOS_RUN))
				.isEqualTo(null);

		// Check study result
		assertThat(admin.getWorker().getStudyResultList().size()).isEqualTo(1);
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
		result = startComponent(studyResult, study.getFirstComponent(),
				idCookie);
		idCookie = result.cookie("JATOS_IDS_0");
		studyResultDao.refresh(studyResult);

		assertThat(result.status()).isEqualTo(OK);
		assertThat(idCookie.value()).isNotEmpty();

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
		result = initData(studyResult, study.getFirstComponent(), idCookie);

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
		result = submitResultData(studyResult, idCookie);

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
		result = setStudySessionData(studyResult, idCookie);

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
		result = startNextComponent(studyResult, idCookie);
		idCookie = result.cookie("JATOS_IDS_0");

		studyResultDao.refresh(studyResult);
		componentResultDao.refresh(firstComponentResult);

		// Check response
		assertThat(result.status()).isEqualTo(SEE_OTHER);

		// Check redirect URL
		assertThat(result.header("Location").get()).endsWith(
				"/publix/" + study.getId() + "/" + study.getComponent(2).getId()
						+ "/start?srid=" + studyResult.getId());

		// ***
		// Start 2. component by ID, studyResult -> DATA_RETRIEVED
		// old componentResult -> FINISHED, new componentResult -> STARTED
		result = startComponent(studyResult, study.getComponent(2), idCookie);
		idCookie = result.cookie("JATOS_IDS_0");

		studyResultDao.refresh(studyResult);
		componentResultDao.refresh(firstComponentResult);

		assertThat(result.status()).isEqualTo(OK);

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
		result = startComponentByPosition(studyResult, 3, idCookie);
		idCookie = result.cookie("JATOS_IDS_0");

		studyResultDao.refresh(studyResult);
		componentResultDao.refresh(firstComponentResult);

		assertThat(result.status()).isEqualTo(OK);

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
		result = logError(studyResult, "This is an error message.", idCookie);

		assertThat(result.status()).isEqualTo(OK);
		// TODO check that error msg appears in log - how?

		// ***
		// Send request to get InitData: prior session data should be there
		// studyResult -> DATA_RETRIEVED, componentResult -> DATA_RETRIEVED
		result = initData(studyResult, study.getComponent(3), idCookie);

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
		result = endStudy(studyResult, idCookie, false,
				"This%20is%20an%20error%20message.");
		idCookie = result.cookie("JATOS_IDS_0");

		studyResultDao.refresh(studyResult);
		componentResultDao.refresh(firstComponentResult);

		// Check response: HTTP status is redirect (it's a JATOS worker run)
		assertThat(result.status()).isEqualTo(SEE_OTHER);

		// Check redirect URL
		assertThat(result.header("Location"))
				.isEqualTo("/jatos/" + study.getId());

		// Check that ID cookie is removed
		assertThat(idCookie.value()).isEmpty();

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
		Cookie idCookie = result.cookie("JATOS_IDS_0");
		StudyResult studyResult = admin.getWorker().getLastStudyResult();
		studyResultDao.refresh(studyResult);

		assertThat(result.header("Location").get()).contains("srid=");

		// Check HTTP status is redirect
		assertThat(result.status()).isEqualTo(SEE_OTHER);

		// ***
		// Start first component
		// studyResult -> STARTED, componentResult -> STARTED
		// result.
		result = startComponent(studyResult, study.getFirstComponent(),
				idCookie);
		idCookie = result.cookie("JATOS_IDS_0");
		studyResult = admin.getWorker().getLastStudyResult();
		studyResultDao.refresh(studyResult);

		assertThat(result.status()).isEqualTo(OK);

		ComponentResult firstComponentResult = studyResult
				.getComponentResultList().get(0);

		// ***
		// Send request to end study
		// studyResult -> ABORTED, componentResult -> ABORTED
		result = abortStudy(studyResult, idCookie,
				"This%20is%20an%20abort%20message.");
		idCookie = result.cookie("JATOS_IDS_0");
		studyResultDao.refresh(studyResult);
		componentResultDao.refresh(firstComponentResult);

		// Check response: HTTP status is redirect (it's a JATOS worker run)
		assertThat(result.status()).isEqualTo(SEE_OTHER);

		// Check redirect URL
		assertThat(result.header("Location"))
				.isEqualTo("/jatos/" + study.getId());

		// Check that ID cookie is removed
		assertThat(idCookie.value()).isEmpty();

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
				.session(JatosPublix.SESSION_JATOS_RUN,
						JatosRun.RUN_STUDY.name());
		return route(request);
	}

	private Result startComponentByPosition(StudyResult studyResult,
			int position, Cookie idCookie) {
		Result result;
		String url = "/publix/" + studyResult.getStudy().getId()
				+ "/component/start?position=" + position + "&srid="
				+ studyResult.getId();
		RequestBuilder request = new RequestBuilder().method(GET).uri(url)
				.session("email", admin.getEmail()).cookie(idCookie)
				.header(HeaderNames.HOST, "localhost:" + testServerPort());
		result = route(request, 10000);
		return result;
	}

	private Result startComponent(StudyResult studyResult, Component component,
			Cookie idCookie) {
		String url = "/publix/" + studyResult.getStudy().getId() + "/"
				+ component.getId() + "/start?srid=" + studyResult.getId();
		RequestBuilder request = new RequestBuilder().method(GET).uri(url)
				.session(Users.SESSION_EMAIL, admin.getEmail()).cookie(idCookie)
				.header(HeaderNames.HOST, "localhost:" + testServerPort());
		return route(request, 10000);
	}

	private Result startNextComponent(StudyResult studyResult,
			Cookie idCookie) {
		Result result;
		String url = "/publix/" + studyResult.getStudy().getId()
				+ "/nextComponent/start?srid=" + studyResult.getId();
		RequestBuilder request = new RequestBuilder().method(GET).uri(url)
				.session("email", admin.getEmail()).cookie(idCookie)
				.header(HeaderNames.HOST, "localhost:" + testServerPort())
				.bodyText("That's session data.");
		result = route(request, 10000);
		return result;
	}

	private Result initData(StudyResult studyResult, Component component,
			Cookie idCookie) {
		Result result;
		String url = "/publix/" + studyResult.getStudy().getId() + "/"
				+ component.getId() + "/initData?srid=" + studyResult.getId();
		RequestBuilder request = new RequestBuilder().method(GET).uri(url)
				.session("email", admin.getEmail()).cookie(idCookie)
				.header(HeaderNames.HOST, "localhost:" + testServerPort());
		result = route(request, 10000);
		return result;
	}

	private Result logError(StudyResult studyResult, String msg,
			Cookie idCookie) {
		Result result;
		String url = "/publix/" + studyResult.getStudy().getId() + "/"
				+ studyResult.getStudy().getComponent(3).getId() + "/log?srid="
				+ studyResult.getId();
		RequestBuilder request = new RequestBuilder().method(POST).uri(url)
				.session("email", admin.getEmail()).cookie(idCookie)
				.header(HeaderNames.HOST, "localhost:" + testServerPort())
				.bodyText(msg);
		result = route(request, 10000);
		return result;
	}

	private Result setStudySessionData(StudyResult studyResult,
			Cookie idCookie) {
		Result result;
		String url = "/publix/" + studyResult.getStudy().getId()
				+ "/studySessionData?srid=" + studyResult.getId();
		RequestBuilder request = new RequestBuilder().method(POST).uri(url)
				.session("email", admin.getEmail()).cookie(idCookie)
				.header(HeaderNames.HOST, "localhost:" + testServerPort())
				.bodyText("That's our session data.");
		result = route(request, 10000);
		return result;
	}

	private Result submitResultData(StudyResult studyResult, Cookie idCookie) {
		Result result;
		String url = "/publix/" + studyResult.getStudy().getId() + "/"
				+ studyResult.getStudy().getFirstComponent().getId()
				+ "/resultData?srid=" + studyResult.getId();
		RequestBuilder request = new RequestBuilder().method(POST).uri(url)
				.session("email", admin.getEmail()).cookie(idCookie)
				.header(HeaderNames.HOST, "localhost:" + testServerPort())
				.bodyText("That's a test result data.");
		result = route(request, 10000);
		return result;
	}

	private Result endStudy(StudyResult studyResult, Cookie idCookie,
			boolean successful, String errorMsg) {
		Result result;
		String url = "/publix/" + studyResult.getStudy().getId() + "/end?srid="
				+ studyResult.getId() + "&successful=" + successful
				+ "&errorMsg=" + errorMsg;
		RequestBuilder request = new RequestBuilder().method(GET).uri(url)
				.session("email", admin.getEmail())
				.header(HeaderNames.HOST, "localhost:" + testServerPort())
				.cookie(idCookie);
		result = route(request, 10000);
		return result;
	}

	private Result abortStudy(StudyResult studyResult, Cookie idCookie,
			String abortMsg) {
		Result result;
		String url = "/publix/" + studyResult.getStudy().getId()
				+ "/abort?srid=" + studyResult.getId() + "&message=" + abortMsg;
		RequestBuilder request = new RequestBuilder().method(GET).uri(url)
				.session("email", admin.getEmail())
				.header(HeaderNames.HOST, "localhost:" + testServerPort())
				.cookie(idCookie);
		result = route(request, 10000);
		return result;
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
