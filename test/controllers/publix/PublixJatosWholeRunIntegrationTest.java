package controllers.publix;

import akka.stream.Materializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Guice;
import com.google.inject.Injector;
import controllers.publix.workers.JatosPublix;
import controllers.publix.workers.JatosPublix.JatosRun;
import daos.common.StudyResultDao;
import daos.common.UserDao;
import general.TestHelper;
import general.common.Common;
import models.common.*;
import models.common.ComponentResult.ComponentState;
import models.common.StudyResult.StudyState;
import models.common.workers.JatosWorker;
import models.common.workers.Worker;
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
import play.mvc.Http.Cookie;
import play.mvc.Http.HeaderNames;
import play.mvc.Http.RequestBuilder;
import play.mvc.Result;
import play.test.Helpers;
import services.gui.AuthenticationService;
import utils.common.JsonUtils;

import javax.inject.Inject;
import java.io.IOException;
import java.util.List;

import static org.fest.assertions.Assertions.assertThat;
import static play.api.test.Helpers.testServerPort;
import static play.mvc.Http.Status.OK;
import static play.mvc.Http.Status.SEE_OTHER;
import static play.test.Helpers.*;

/**
 * Integration test that does a whole run with the JatosWorker (calls all the
 * endpoints of a run with a JatosWorker)
 *
 * @author Kristian Lange
 */
public class PublixJatosWholeRunIntegrationTest {

    private Injector injector;

    @Inject
    private static Application fakeApplication;

    @Inject
    private TestHelper testHelper;

    @Inject
    private JPAApi jpaApi;

    @Inject
    private JsonUtils jsonUtils;

    @Inject
    private StudyResultDao studyResultDao;

    @Inject
    private UserDao userDao;

    @Inject
    private Materializer materializer;

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
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        User admin = testHelper.getAdmin();

        // *************************************************************
        // Start study:
        // studyResult -> STARTED
        Result result = startStudy(study, admin);
        Cookie idCookie = result.cookie("JATOS_IDS_0");
        List<StudyResult> studyResultList = retrieveStudyResultList(admin);
        StudyResult studyResult = getLastStudyResult(studyResultList);

        // Check HTTP status is redirect
        assertThat(result.status()).isEqualTo(SEE_OTHER);

        // Check redirect URL
        assertThat(result.header("Location").get())
                .startsWith(Common.getPlayHttpContext() + "publix/" + study.getId() + "/"
                        + study.getFirstComponent().getId() + "/start?srid=");

        // Check that ID cookie exists
        assertThat(idCookie.value()).isNotEmpty();

        // Check JATOS_RUN is removed from session
        assertThat(result.session().get(JatosPublix.SESSION_JATOS_RUN))
                .isEqualTo(null);

        // Check study result
        assertThat(studyResultList.size()).isEqualTo(1);
        assertThat(studyResult.getStudy()).isEqualTo(study);
        assertThat(studyResult.getWorker()).isEqualTo(admin.getWorker());
        assertThat(studyResult.getWorkerId())
                .isEqualTo(admin.getWorker().getId());
        assertThat(studyResult.getWorkerType())
                .isEqualTo(admin.getWorker().getWorkerType());
        assertThat(studyResult.getStudyState()).isEqualTo(StudyState.STARTED);
        assertThat(studyResult.getStartDate()).isNotNull();

        // *************************************************************
        // Start first component
        // studyResult -> STARTED, componentResult -> STARTED
        result = startComponent(studyResult, study.getFirstComponent(), admin,
                idCookie);
        idCookie = result.cookie("JATOS_IDS_0");
        studyResult = retrieveStudyResult(studyResult.getId());

        assertThat(result.status()).isEqualTo(OK);
        assertThat(idCookie.value()).isNotEmpty();

        // And check a random line of the JS code
        assertThat(contentAsString(result, materializer))
                .contains("jatos.onLoad(function() {");

        // Check ComponentResult and StudyResult
        assertThat(studyResult.getComponentResultList().size()).isEqualTo(1);
        ComponentResult firstComponentResult = retrieveComponentResult(
                studyResult.getId(), 0);
        checkStates(studyResult, StudyState.STARTED, firstComponentResult,
                ComponentState.STARTED);
        checkComponentResultAfterStart(study, studyResult, admin, 1, 1);

        // *************************************************************
        // Send request to get InitData:
        // studyResult -> DATA_RETRIEVED, componentResult -> DATA_RETRIEVED
        result = initData(studyResult, study.getFirstComponent(), admin,
                idCookie);

        studyResult = retrieveStudyResult(studyResult.getId());
        firstComponentResult = retrieveComponentResult(studyResult.getId(), 0);

        // Check InitData in response
        assertThat(result.status()).isEqualTo(OK);
        assertThat(JsonUtils.isValid(contentAsString(result))).isTrue();
        JsonNode json = Json.mapper().readTree(contentAsString(result));
        assertThat(json.get("studySessionData")).isNotNull();
        assertThat(json.get("studyProperties")).isNotNull();
        assertThat(json.get("componentList")).isNotNull();
        assertThat(json.get("componentProperties")).isNotNull();

        // Check studyResult and componentResult
        checkStates(studyResult, StudyState.DATA_RETRIEVED,
                firstComponentResult, ComponentState.DATA_RETRIEVED);

        // *************************************************************
        // Send request submitResultData:
        // studyResult -> DATA_RETRIEVED, componentResult -> RESULTDATA_POSTED
        result = submitResultData(studyResult, admin, idCookie);

        studyResult = retrieveStudyResult(studyResult.getId());
        firstComponentResult = retrieveComponentResult(studyResult.getId(), 0);

        // Check response
        assertThat(result.status()).isEqualTo(OK);
        checkStates(studyResult, StudyState.DATA_RETRIEVED,
                firstComponentResult, ComponentState.RESULTDATA_POSTED);

        // Check componentResult
        assertThat(firstComponentResult.getData())
                .isEqualTo("That's a test result data.");

        // *************************************************************
        // Send request appendResultData:
        // studyResult -> DATA_RETRIEVED, componentResult -> RESULTDATA_POSTED
        result = appendResultData(studyResult, admin, idCookie);

        studyResult = retrieveStudyResult(studyResult.getId());
        firstComponentResult = retrieveComponentResult(studyResult.getId(), 0);

        // Check response
        assertThat(result.status()).isEqualTo(OK);
        checkStates(studyResult, StudyState.DATA_RETRIEVED,
                firstComponentResult, ComponentState.RESULTDATA_POSTED);

        // Check componentResult
        assertThat(firstComponentResult.getData())
                .isEqualTo(
                        "That's a test result data. And here are appended data.");

        // *************************************************************
        // Send request setStudySessionData:
        // studyResult -> DATA_RETRIEVED, componentResult -> RESULTDATA_POSTED
        result = setStudySessionData(studyResult, admin, idCookie);

        studyResult = retrieveStudyResult(studyResult.getId());
        firstComponentResult = retrieveComponentResult(studyResult.getId(), 0);

        // Check response
        assertThat(result.status()).isEqualTo(OK);
        checkStates(studyResult, StudyState.DATA_RETRIEVED,
                firstComponentResult, ComponentState.RESULTDATA_POSTED);

        // Check componentResult
        assertThat(studyResult.getStudySessionData())
                .isEqualTo("That's our session data.");

        // *************************************************************
        // Send request startNextComponent: studyResult -> DATA_RETRIEVED,
        // old componentResult -> FINISHED, new componentResult -> STARTED
        result = startNextComponent(studyResult, admin, idCookie);
        idCookie = result.cookie("JATOS_IDS_0");

        studyResult = retrieveStudyResult(studyResult.getId());
        firstComponentResult = retrieveComponentResult(studyResult.getId(), 0);

        // Check response
        assertThat(result.status()).isEqualTo(SEE_OTHER);

        // Check redirect URL
        assertThat(result.header("Location").get()).endsWith(Common.getPlayHttpContext() +
                "publix/" + study.getId() + "/" + study.getComponent(2).getId()
                        + "/start?srid=" + studyResult.getId());

        // *************************************************************
        // Start 2. component by ID, studyResult -> DATA_RETRIEVED
        // old componentResult -> FINISHED, new componentResult -> STARTED
        result = startComponent(studyResult, study.getComponent(2), admin,
                idCookie);
        idCookie = result.cookie("JATOS_IDS_0");

        studyResult = retrieveStudyResult(studyResult.getId());
        firstComponentResult = retrieveComponentResult(studyResult.getId(), 0);

        assertThat(result.status()).isEqualTo(OK);

        // And check a random line of the JS code
        assertThat(contentAsString(result, materializer))
                .contains("jatos.onLoad(function() {");

        // Check old and new ComponentResult and StudyResult
        assertThat(studyResult.getComponentResultList().size()).isEqualTo(2);
        ComponentResult secondComponentResult = studyResult
                .getComponentResultList().get(1);
        checkStates(studyResult, StudyState.DATA_RETRIEVED,
                secondComponentResult, ComponentState.STARTED);
        checkStates(studyResult, StudyState.DATA_RETRIEVED,
                firstComponentResult, ComponentState.FINISHED);
        checkComponentResultAfterStart(study, studyResult, admin, 2, 2);

        // *************************************************************
        // Start 3. component by position, studyResult -> DATA_RETRIEVED
        // old componentResult -> FINISHED, new componentResult -> STARTED
        result = startComponentByPosition(studyResult, admin, 3, idCookie);
        idCookie = result.cookie("JATOS_IDS_0");

        studyResult = retrieveStudyResult(studyResult.getId());
        firstComponentResult = retrieveComponentResult(studyResult.getId(), 0);

        assertThat(result.status()).isEqualTo(OK);

        // And check a random line of the JS code
        assertThat(contentAsString(result, materializer))
                .contains("jatos.onLoad(function() {");

        // Check old and new ComponentResult and StudyResult
        assertThat(firstComponentResult.getEndDate()).isNotNull();
        checkStates(studyResult, StudyState.DATA_RETRIEVED,
                firstComponentResult, ComponentState.FINISHED);
        checkComponentResultAfterStart(study, studyResult, admin, 3, 3);

        // *************************************************************
        // Log error
        result = logError(studyResult, admin, "This is an error message.",
                idCookie);

        assertThat(result.status()).isEqualTo(OK);
        // TODO check that error msg appears in log - how?

        // *************************************************************
        // Send request to get InitData: prior session data should be there
        // studyResult -> DATA_RETRIEVED, componentResult -> DATA_RETRIEVED
        result = initData(studyResult, study.getComponent(3), admin, idCookie);

        studyResult = retrieveStudyResult(studyResult.getId());
        firstComponentResult = retrieveComponentResult(studyResult.getId(), 0);

        // Check InitData in response: is session data still there?
        assertThat(result.status()).isEqualTo(OK);
        assertThat(JsonUtils.isValid(contentAsString(result))).isTrue();
        json = Json.mapper().readTree(contentAsString(result));
        assertThat(json.get("studySessionData").asText())
                .isEqualTo("That's our session data.");
        assertThat(json.get("studyProperties")).isNotNull();
        assertThat(json.get("componentList")).isNotNull();
        assertThat(json.get("componentProperties")).isNotNull();

        // *************************************************************
        // Send request to end study not successfully with error message
        // studyResult -> FAIL, componentResult -> FINISHED
        result = endStudy(studyResult, admin, idCookie, false,
                "This%20is%20an%20error%20message.");
        idCookie = result.cookie("JATOS_IDS_0");

        studyResult = retrieveStudyResult(studyResult.getId());
        firstComponentResult = retrieveComponentResult(studyResult.getId(), 0);

        // Check response: HTTP status is redirect (it's a JATOS worker run)
        assertThat(result.status()).isEqualTo(SEE_OTHER);

        // Check redirect URL
        assertThat(result.header("Location").get())
                .isEqualTo(Common.getPlayHttpContext() + "jatos/" + study.getId());

        // Check that ID cookie is removed
        assertThat(idCookie.value()).isEmpty();

        // Check results
        assertThat(studyResult.getStudyState()).isEqualTo(StudyState.FAIL);
        assertThat(firstComponentResult.getComponentState())
                .isEqualTo(ComponentState.FINISHED);

        // Check the abort message is in flash storage
        assertThat(result.flash().get("info")).isEqualTo(
                "Study finished with message: This is an error message.");
    }

    /**
     * Functional test: start a study and then abort it.
     */
    @Test
    public void startAndAbortStudy() throws IOException {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        User admin = testHelper.getAdmin();

        // *************************************************************
        // Start study:
        // studyResult -> STARTED
        Result result = startStudy(study, admin);
        Cookie idCookie = result.cookie("JATOS_IDS_0");
        List<StudyResult> studyResultList = retrieveStudyResultList(admin);
        StudyResult studyResult = getLastStudyResult(studyResultList);

        assertThat(result.header("Location").get()).contains("srid=");

        // Check HTTP status is redirect
        assertThat(result.status()).isEqualTo(SEE_OTHER);

        // *************************************************************
        // Start first component
        // studyResult -> STARTED, componentResult -> STARTED
        result = startComponent(studyResult, study.getFirstComponent(), admin,
                idCookie);
        idCookie = result.cookie("JATOS_IDS_0");
        studyResult = retrieveStudyResult(studyResult.getId());
        ComponentResult firstComponentResult = retrieveComponentResult(
                studyResult.getId(), 0);

        assertThat(result.status()).isEqualTo(OK);

        // *************************************************************
        // Send request to end study
        // studyResult -> ABORTED, componentResult -> ABORTED
        result = abortStudy(studyResult, admin, idCookie,
                "This%20is%20an%20abort%20message.");
        idCookie = result.cookie("JATOS_IDS_0");
        studyResult = retrieveStudyResult(studyResult.getId());
        firstComponentResult = retrieveComponentResult(studyResult.getId(), 0);

        // Check response: HTTP status is redirect (it's a JATOS worker run)
        assertThat(result.status()).isEqualTo(SEE_OTHER);

        // Check redirect URL
        assertThat(result.header("Location").get())
                .isEqualTo(Common.getPlayHttpContext() + "jatos/" + study.getId());

        // Check that ID cookie is removed
        assertThat(idCookie.value()).isEmpty();

        // Check results
        assertThat(studyResult.getStudyState()).isEqualTo(StudyState.ABORTED);
        assertThat(firstComponentResult.getComponentState())
                .isEqualTo(ComponentState.ABORTED);

        // Check the abort message is in flash storage
        assertThat(result.flash().get("info")).isEqualTo(
                "Study finished with message: This is an abort message.");
    }

    private Result startStudy(Study study, User admin) {
        String url = Common.getPlayHttpContext() + "publix/" + study.getId() + "/start?"
                + JatosPublix.JATOS_WORKER_ID + "=" + admin.getWorker().getId();
        RequestBuilder request = new RequestBuilder().method(GET).uri(url)
                .session(AuthenticationService.SESSION_USER_EMAIL,
                        admin.getEmail())
                .session(JatosPublix.SESSION_JATOS_RUN,
                        JatosRun.RUN_STUDY.name());
        return route(request);
    }

    private Result startComponentByPosition(StudyResult studyResult, User admin,
            int position, Cookie idCookie) {
        Result result;
        String url = Common.getPlayHttpContext() + "publix/" + studyResult.getStudy().getId()
                + "/component/start?position=" + position + "&srid="
                + studyResult.getId();
        RequestBuilder request = new RequestBuilder().method(GET).uri(url)
                .session(AuthenticationService.SESSION_USER_EMAIL,
                        admin.getEmail())
                .cookie(idCookie)
                .header(HeaderNames.HOST, "localhost:" + testServerPort());
        result = route(request, 10000);
        return result;
    }

    private Result startComponent(StudyResult studyResult, Component component,
            User admin, Cookie idCookie) {
        String url = Common.getPlayHttpContext()
                + "publix/" + studyResult.getStudy().getId() + "/"
                + component.getId() + "/start?srid=" + studyResult.getId();
        RequestBuilder request = new RequestBuilder().method(GET).uri(url)
                .session(AuthenticationService.SESSION_USER_EMAIL,
                        admin.getEmail())
                .cookie(idCookie)
                .header(HeaderNames.HOST, "localhost:" + testServerPort());
        return route(request, 10000);
    }

    private Result startNextComponent(StudyResult studyResult, User admin,
            Cookie idCookie) {
        Result result;
        String url = Common.getPlayHttpContext()
                + "publix/" + studyResult.getStudy().getId()
                + "/nextComponent/start?srid=" + studyResult.getId();
        RequestBuilder request = new RequestBuilder().method(GET).uri(url)
                .session(AuthenticationService.SESSION_USER_EMAIL,
                        admin.getEmail())
                .cookie(idCookie)
                .header(HeaderNames.HOST, "localhost:" + testServerPort())
                .bodyText("That's session data.");
        result = route(request, 10000);
        return result;
    }

    private Result initData(StudyResult studyResult, Component component,
            User admin, Cookie idCookie) {
        Result result;
        String url = Common.getPlayHttpContext()
                + "publix/" + studyResult.getStudy().getId() + "/"
                + component.getId() + "/initData?srid=" + studyResult.getId();
        RequestBuilder request = new RequestBuilder().method(GET).uri(url)
                .session(AuthenticationService.SESSION_USER_EMAIL,
                        admin.getEmail())
                .cookie(idCookie)
                .header(HeaderNames.HOST, "localhost:" + testServerPort());
        result = route(request, 10000);
        return result;
    }

    private Result logError(StudyResult studyResult, User admin, String msg,
            Cookie idCookie) {
        Result result;
        String url = Common.getPlayHttpContext()
                + "publix/" + studyResult.getStudy().getId() + "/"
                + studyResult.getStudy().getComponent(3).getId() + "/log?srid="
                + studyResult.getId();
        RequestBuilder request = new RequestBuilder().method(POST).uri(url)
                .session(AuthenticationService.SESSION_USER_EMAIL,
                        admin.getEmail())
                .cookie(idCookie)
                .header(HeaderNames.HOST, "localhost:" + testServerPort())
                .bodyText(msg);
        result = route(request, 10000);
        return result;
    }

    private Result setStudySessionData(StudyResult studyResult, User admin,
            Cookie idCookie) {
        Result result;
        String url = Common.getPlayHttpContext()
                + "publix/" + studyResult.getStudy().getId()
                + "/studySessionData?srid=" + studyResult.getId();
        RequestBuilder request = new RequestBuilder().method(POST).uri(url)
                .session(AuthenticationService.SESSION_USER_EMAIL,
                        admin.getEmail())
                .cookie(idCookie)
                .header(HeaderNames.HOST, "localhost:" + testServerPort())
                .bodyText("That's our session data.");
        result = route(request, 10000);
        return result;
    }

    private Result submitResultData(StudyResult studyResult, User admin,
            Cookie idCookie) {
        Result result;
        String url = Common.getPlayHttpContext()
                + "publix/" + studyResult.getStudy().getId() + "/"
                + studyResult.getStudy().getFirstComponent().getId()
                + "/resultData?srid=" + studyResult.getId();
        RequestBuilder request = new RequestBuilder().method(PUT).uri(url)
                .session(AuthenticationService.SESSION_USER_EMAIL,
                        admin.getEmail())
                .cookie(idCookie)
                .header(HeaderNames.HOST, "localhost:" + testServerPort())
                .bodyText("That's a test result data.");
        result = route(request, 10000);
        return result;
    }

    private Result appendResultData(StudyResult studyResult, User admin,
            Cookie idCookie) {
        Result result;
        String url = Common.getPlayHttpContext()
                + "publix/" + studyResult.getStudy().getId() + "/"
                + studyResult.getStudy().getFirstComponent().getId()
                + "/resultData?srid=" + studyResult.getId();
        RequestBuilder request = new RequestBuilder().method(POST).uri(url)
                .session(AuthenticationService.SESSION_USER_EMAIL,
                        admin.getEmail())
                .cookie(idCookie)
                .header(HeaderNames.HOST, "localhost:" + testServerPort())
                .bodyText(" And here are appended data.");
        result = route(request, 10000);
        return result;
    }

    private Result endStudy(StudyResult studyResult, User admin,
            Cookie idCookie, boolean successful, String errorMsg) {
        Result result;
        String url = Common.getPlayHttpContext()
                + "publix/" + studyResult.getStudy().getId() + "/end?srid="
                + studyResult.getId() + "&successful=" + successful
                + "&errorMsg=" + errorMsg;
        RequestBuilder request = new RequestBuilder().method(GET).uri(url)
                .session(AuthenticationService.SESSION_USER_EMAIL,
                        admin.getEmail())
                .header(HeaderNames.HOST, "localhost:" + testServerPort())
                .cookie(idCookie);
        result = route(request, 10000);
        return result;
    }

    private Result abortStudy(StudyResult studyResult, User admin,
            Cookie idCookie, String abortMsg) {
        Result result;
        String url = Common.getPlayHttpContext()
                + "publix/" + studyResult.getStudy().getId()
                + "/abort?srid=" + studyResult.getId() + "&message=" + abortMsg;
        RequestBuilder request = new RequestBuilder().method(GET).uri(url)
                .session(AuthenticationService.SESSION_USER_EMAIL,
                        admin.getEmail())
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
            StudyResult studyResult, User admin, int componentPosition,
            int componentResultListSize) {
        assertThat(studyResult.getComponentResultList().size())
                .isEqualTo(componentResultListSize);

        // Get the last component result
        ComponentResult componentResult = retrieveComponentResult(
                studyResult.getId(), componentResultListSize - 1);

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

    private List<StudyResult> retrieveStudyResultList(User user) {
        return jpaApi.withTransaction(() -> {
            User admin = userDao.findByEmail(user.getEmail());
            JatosWorker worker = admin.getWorker();
            List<StudyResult> studyResultList = worker.getStudyResultList();
            testHelper.fetchTheLazyOnes(studyResultList);
            return worker.getStudyResultList();
        });
    }

    private StudyResult retrieveStudyResult(long studyResultId) {
        return jpaApi.withTransaction(() -> {
            StudyResult studyResult = studyResultDao.findById(studyResultId);
            testHelper.fetchTheLazyOnes(studyResult.getStudy());
            testHelper.fetchTheLazyOnes(
                    studyResult.getStudy().getFirstComponent());
            testHelper.fetchTheLazyOnes(studyResult.getWorker());
            testHelper.fetchTheLazyOnes(studyResult.getBatch());
            testHelper.fetchTheLazyOnes(studyResult.getComponentResultList());
            return studyResult;
        });
    }

    private ComponentResult retrieveComponentResult(long studyResultId,
            int index) {
        return jpaApi.withTransaction(() -> {
            StudyResult studyResult = studyResultDao.findById(studyResultId);
            ComponentResult componentResult = studyResult
                    .getComponentResultList().get(index);
            testHelper.fetchTheLazyOnes(componentResult);
            StudyResult componentResultsStudyResult = componentResult
                    .getStudyResult();
            testHelper.fetchTheLazyOnes(componentResultsStudyResult);
            Worker worker = componentResultsStudyResult.getWorker();
            testHelper.fetchTheLazyOnes(worker);
            Component component = componentResult.getComponent();
            testHelper.fetchTheLazyOnes(component);
            return componentResult;
        });
    }

    public StudyResult getLastStudyResult(List<StudyResult> studyResultList) {
        if (!studyResultList.isEmpty()) {
            StudyResult unfetchedStudyResult = studyResultList
                    .get(studyResultList.size() - 1);
            return retrieveStudyResult(unfetchedStudyResult.getId());
        } else {
            return null;
        }
    }
}
