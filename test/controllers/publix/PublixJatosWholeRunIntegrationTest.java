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
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
    private Application fakeApplication;

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
    public void runWholeStudy() throws Exception {
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
                        + study.getFirstComponent().get().getId() + "/start?srid=");

        // Check that ID cookie exists
        assertThat(idCookie.value()).isNotEmpty();

        // Check JATOS_RUN is removed from session
        assertThat(result.session().get(JatosPublix.SESSION_JATOS_RUN)).isEqualTo(null);

        // Check study result
        assertThat(studyResultList.size()).isEqualTo(1);
        assertThat(studyResult.getStudy()).isEqualTo(study);
        assertThat(studyResult.getWorker()).isEqualTo(admin.getWorker());
        assertThat(studyResult.getWorkerId()).isEqualTo(admin.getWorker().getId());
        assertThat(studyResult.getWorkerType()).isEqualTo(admin.getWorker().getWorkerType());
        assertThat(studyResult.getStudyState()).isEqualTo(StudyState.STARTED);
        assertThat(studyResult.getStartDate()).isNotNull();

        // *************************************************************
        // Start first component
        // studyResult -> STARTED, componentResult -> STARTED
        result = startComponent(studyResult, study.getFirstComponent().get(), admin, idCookie);
        idCookie = result.cookie("JATOS_IDS_0");
        studyResult = retrieveStudyResult(studyResult.getId());

        assertThat(result.status()).isEqualTo(OK);
        assertThat(idCookie.value()).isNotEmpty();

        // And check a random line of the JS code
        assertThat(contentAsString(result, materializer)).contains("jatos.onLoad(function() {");

        // Check ComponentResult and StudyResult
        assertThat(studyResult.getComponentResultList().size()).isEqualTo(1);
        ComponentResult firstComponentResult = retrieveComponentResult(studyResult.getId(), 0);
        checkStates(studyResult, StudyState.STARTED, firstComponentResult, ComponentState.STARTED);
        checkComponentResultAfterStart(study, studyResult, admin, 1, 1);

        // *************************************************************
        // Send request to get InitData:
        // studyResult -> DATA_RETRIEVED, componentResult -> DATA_RETRIEVED
        result = initData(studyResult, study.getFirstComponent().get(), admin, idCookie);

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
        checkStates(studyResult, StudyState.DATA_RETRIEVED, firstComponentResult, ComponentState.DATA_RETRIEVED);

        // *************************************************************
        // Send request submitResultData:
        // studyResult -> DATA_RETRIEVED, componentResult -> RESULTDATA_POSTED
        result = submitResultData(studyResult, admin, idCookie);

        studyResult = retrieveStudyResult(studyResult.getId());
        firstComponentResult = retrieveComponentResult(studyResult.getId(), 0);

        // Check response
        assertThat(result.status()).isEqualTo(OK);
        checkStates(studyResult, StudyState.DATA_RETRIEVED, firstComponentResult, ComponentState.RESULTDATA_POSTED);

        // Check componentResult
        assertThat(firstComponentResult.getData()).isEqualTo("That's a test result data.");

        // *************************************************************
        // Send request appendResultData:
        // studyResult -> DATA_RETRIEVED, componentResult -> RESULTDATA_POSTED
        result = appendResultData(studyResult, admin, idCookie);

        studyResult = retrieveStudyResult(studyResult.getId());
        firstComponentResult = retrieveComponentResult(studyResult.getId(), 0);

        // Check response
        assertThat(result.status()).isEqualTo(OK);
        checkStates(studyResult, StudyState.DATA_RETRIEVED, firstComponentResult, ComponentState.RESULTDATA_POSTED);

        // Check componentResult
        assertThat(firstComponentResult.getData()).isEqualTo(
                "That's a test result data. And here are appended data.");

        // *************************************************************
        // Send request setStudySessionData:
        // studyResult -> DATA_RETRIEVED, componentResult -> RESULTDATA_POSTED
        result = setStudySessionData(studyResult, admin, idCookie);

        studyResult = retrieveStudyResult(studyResult.getId());
        firstComponentResult = retrieveComponentResult(studyResult.getId(), 0);

        // Check response
        assertThat(result.status()).isEqualTo(OK);
        checkStates(studyResult, StudyState.DATA_RETRIEVED, firstComponentResult, ComponentState.RESULTDATA_POSTED);

        // Check componentResult
        assertThat(studyResult.getStudySessionData()).isEqualTo("That's our session data.");

        // *************************************************************
        // Start 2nd startComponent: studyResult -> DATA_RETRIEVED,
        // old componentResult -> FINISHED, new componentResult -> STARTED
        result = startComponent(studyResult, study.getComponent(2), admin, idCookie);
        idCookie = result.cookie("JATOS_IDS_0");

        studyResult = retrieveStudyResult(studyResult.getId());
        firstComponentResult = retrieveComponentResult(studyResult.getId(), 0);
        ComponentResult secondComponentResult = studyResult.getComponentResultList().get(1);

        // Check response
        assertThat(result.status()).isEqualTo(OK);

        // And check a random line of the JS code
        assertThat(contentAsString(result, materializer)).contains("jatos.onLoad(function() {");

        // Check old and new ComponentResult and StudyResult
        assertThat(studyResult.getComponentResultList().size()).isEqualTo(2);
        checkStates(studyResult, StudyState.DATA_RETRIEVED, firstComponentResult, ComponentState.FINISHED);
        checkStates(studyResult, StudyState.DATA_RETRIEVED, secondComponentResult, ComponentState.STARTED);
        checkComponentResultAfterStart(study, studyResult, admin, 2, 2);

        // *************************************************************
        // Start 3rd component, studyResult -> DATA_RETRIEVED
        // old componentResult -> FINISHED, new componentResult -> STARTED
        result = startComponent(studyResult, study.getComponent(3), admin, idCookie);
        idCookie = result.cookie("JATOS_IDS_0");

        studyResult = retrieveStudyResult(studyResult.getId());
        secondComponentResult = retrieveComponentResult(studyResult.getId(), 1);
        ComponentResult thirdComponentResult = studyResult.getComponentResultList().get(2);

        // Check response
        assertThat(result.status()).isEqualTo(OK);

        // And check a random line of the JS code
        assertThat(contentAsString(result, materializer)).contains("jatos.onLoad(function() {");

        // Check old and new ComponentResult and StudyResult
        assertThat(studyResult.getComponentResultList().size()).isEqualTo(3);
        checkStates(studyResult, StudyState.DATA_RETRIEVED, secondComponentResult, ComponentState.FINISHED);
        checkStates(studyResult, StudyState.DATA_RETRIEVED, thirdComponentResult, ComponentState.STARTED);
        checkComponentResultAfterStart(study, studyResult, admin, 3, 3);

        // *************************************************************
        // Start 4th component, studyResult -> DATA_RETRIEVED
        // old componentResult -> FINISHED, new componentResult -> STARTED
        result = startComponent(studyResult, study.getComponent(4), admin, idCookie);
        idCookie = result.cookie("JATOS_IDS_0");

        studyResult = retrieveStudyResult(studyResult.getId());
        thirdComponentResult = retrieveComponentResult(studyResult.getId(), 2);
        ComponentResult forthComponentResult = studyResult.getComponentResultList().get(3);

        // Check response
        assertThat(result.status()).isEqualTo(OK);

        // And check a random line of the JS code
        assertThat(contentAsString(result, materializer)).contains("jatos.onLoad(function() {");

        // Check old and new ComponentResult and StudyResult
        assertThat(studyResult.getComponentResultList().size()).isEqualTo(4);
        checkStates(studyResult, StudyState.DATA_RETRIEVED, thirdComponentResult, ComponentState.FINISHED);
        checkStates(studyResult, StudyState.DATA_RETRIEVED, forthComponentResult, ComponentState.STARTED);
        checkComponentResultAfterStart(study, studyResult, admin, 4, 4);

        // *************************************************************
        // Log error
        result = logError(studyResult, admin, "This is an error message.", idCookie);

        assertThat(result.status()).isEqualTo(OK);
        // TODO check that error msg appears in log - how?

        // *************************************************************
        // Get InitData: prior session data should be there
        // studyResult -> DATA_RETRIEVED, componentResult -> DATA_RETRIEVED
        result = initData(studyResult, study.getComponent(3), admin, idCookie);

        studyResult = retrieveStudyResult(studyResult.getId());
        retrieveComponentResult(studyResult.getId(), 0);

        // Check InitData in response: is session data still there?
        assertThat(result.status()).isEqualTo(OK);
        assertThat(JsonUtils.isValid(contentAsString(result))).isTrue();
        json = Json.mapper().readTree(contentAsString(result));
        assertThat(json.get("studySessionData").asText()).isEqualTo("That's our session data.");
        assertThat(json.get("studyProperties")).isNotNull();
        assertThat(json.get("componentList")).isNotNull();
        assertThat(json.get("componentProperties")).isNotNull();

        // *************************************************************
        // End study not successfully with error message
        // studyResult -> FAIL, componentResult -> FINISHED
        result = endStudy(studyResult, admin, idCookie, false, "This is an error message.");
        idCookie = result.cookie("JATOS_IDS_0");

        studyResult = retrieveStudyResult(studyResult.getId());
        firstComponentResult = retrieveComponentResult(studyResult.getId(), 0);

        // Check response: HTTP status is redirect (to end page)
        assertThat(result.status()).isEqualTo(SEE_OTHER);

        // Check redirect URL
        assertThat(result.header("Location").get()).isEqualTo(Common.getPlayHttpContext() + "jatos/" + study.getId());

        // Check that ID cookie is removed
        assertThat(idCookie.value()).isEmpty();

        // Check results
        assertThat(studyResult.getStudyState()).isEqualTo(StudyState.FAIL);
        assertThat(firstComponentResult.getComponentState()).isEqualTo(ComponentState.FINISHED);

        // Check the abort message is in flash storage
        assertThat(result.flash().get("info")).isEqualTo("Study finished with message: This is an error message.");
    }

    /**
     * Functional test: start a study and then abort it.
     */
    @Test
    public void startAndAbortStudy() throws Exception {
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
        result = startComponent(studyResult, study.getFirstComponent().get(), admin, idCookie);
        idCookie = result.cookie("JATOS_IDS_0");
        studyResult = retrieveStudyResult(studyResult.getId());

        assertThat(result.status()).isEqualTo(OK);

        // *************************************************************
        // Send request to end study
        // studyResult -> ABORTED, componentResult -> ABORTED
        result = abortStudy(studyResult, admin, idCookie, "This is an abort message.");
        idCookie = result.cookie("JATOS_IDS_0");
        studyResult = retrieveStudyResult(studyResult.getId());
        ComponentResult firstComponentResult = retrieveComponentResult(studyResult.getId(), 0);

        // Check response: HTTP status is redirect (it's a JATOS worker run)
        assertThat(result.status()).isEqualTo(SEE_OTHER);

        // Check redirect URL
        assertThat(result.header("Location").get()).isEqualTo(Common.getPlayHttpContext() + "jatos/" + study.getId());

        // Check that ID cookie is removed
        assertThat(idCookie.value()).isEmpty();

        // Check results
        assertThat(studyResult.getStudyState()).isEqualTo(StudyState.ABORTED);
        assertThat(firstComponentResult.getComponentState()).isEqualTo(ComponentState.ABORTED);

        // Check the abort message is in flash storage
        assertThat(result.flash().get("info")).isEqualTo(
                "Study finished with message: This is an abort message.");
    }

    private Result startStudy(Study study, User admin) {
        String url = Common.getPlayHttpContext() + "publix/" + study.getId() + "/start?"
                + JatosPublix.JATOS_WORKER_ID + "=" + admin.getWorker().getId();
        RequestBuilder request = new RequestBuilder().method(GET).uri(url)
                .session(AuthenticationService.SESSION_USERNAME, admin.getUsername())
                .session(JatosPublix.SESSION_JATOS_RUN, JatosRun.RUN_STUDY.name());
        return route(fakeApplication, request);
    }

    private Result startComponent(StudyResult studyResult, Component component, User admin,
            Cookie idCookie) {
        String url = Common.getPlayHttpContext()
                + "publix/" + studyResult.getStudy().getId() + "/"
                + component.getId() + "/start?srid=" + studyResult.getId();
        RequestBuilder request = new RequestBuilder().method(GET).uri(url)
                .session(AuthenticationService.SESSION_USERNAME, admin.getUsername())
                .cookie(idCookie)
                .bodyText("That's session data.")
                .header(HeaderNames.HOST, "localhost:" + testServerPort());
        return route(fakeApplication, request, 10000);
    }

    private Result initData(StudyResult studyResult, Component component, User admin,
            Cookie idCookie) {
        Result result;
        String url = Common.getPlayHttpContext() + "publix/" + studyResult.getStudy().getId() + "/"
                + component.getId() + "/initData?srid=" + studyResult.getId();
        RequestBuilder request = new RequestBuilder().method(GET).uri(url)
                .session(AuthenticationService.SESSION_USERNAME, admin.getUsername())
                .cookie(idCookie)
                .header(HeaderNames.HOST, "localhost:" + testServerPort());
        result = route(fakeApplication, request, 10000);
        return result;
    }

    private Result logError(StudyResult studyResult, User admin, String msg, Cookie idCookie) {
        Result result;
        String url = Common.getPlayHttpContext() + "publix/" + studyResult.getStudy().getId() + "/"
                + studyResult.getStudy().getComponent(3).getId() + "/log?srid=" +
                studyResult.getId();
        RequestBuilder request = new RequestBuilder().method(POST).uri(url)
                .session(AuthenticationService.SESSION_USERNAME, admin.getUsername())
                .cookie(idCookie)
                .header(HeaderNames.HOST, "localhost:" + testServerPort())
                .bodyText(msg);
        result = route(fakeApplication, request, 10000);
        return result;
    }

    private Result setStudySessionData(StudyResult studyResult, User admin, Cookie idCookie) {
        Result result;
        String url = Common.getPlayHttpContext() + "publix/" + studyResult.getStudy().getId()
                + "/studySessionData?srid=" + studyResult.getId();
        RequestBuilder request = new RequestBuilder().method(POST).uri(url)
                .session(AuthenticationService.SESSION_USERNAME, admin.getUsername())
                .cookie(idCookie)
                .header(HeaderNames.HOST, "localhost:" + testServerPort())
                .bodyText("That's our session data.");
        result = route(fakeApplication, request, 10000);
        return result;
    }

    private Result submitResultData(StudyResult studyResult, User admin, Cookie idCookie) {
        Result result;
        String url = Common.getPlayHttpContext()
                + "publix/" + studyResult.getStudy().getId() + "/"
                + studyResult.getStudy().getFirstComponent().get().getId()
                + "/resultData?srid=" + studyResult.getId();
        RequestBuilder request = new RequestBuilder().method(PUT).uri(url)
                .session(AuthenticationService.SESSION_USERNAME, admin.getUsername())
                .cookie(idCookie)
                .header(HeaderNames.HOST, "localhost:" + testServerPort())
                .bodyText("That's a test result data.");
        result = route(fakeApplication, request, 10000);
        return result;
    }

    private Result appendResultData(StudyResult studyResult, User admin, Cookie idCookie) {
        Result result;
        String url = Common.getPlayHttpContext()
                + "publix/" + studyResult.getStudy().getId() + "/"
                + studyResult.getStudy().getFirstComponent().get().getId()
                + "/resultData?srid=" + studyResult.getId();
        RequestBuilder request = new RequestBuilder().method(POST).uri(url)
                .session(AuthenticationService.SESSION_USERNAME, admin.getUsername())
                .cookie(idCookie)
                .header(HeaderNames.HOST, "localhost:" + testServerPort())
                .bodyText(" And here are appended data.");
        result = route(fakeApplication, request, 10000);
        return result;
    }

    private Result endStudy(StudyResult studyResult, User admin, Cookie idCookie,
            boolean successful, String message) throws UnsupportedEncodingException {
        Result result;
        String url = Common.getPlayHttpContext() + "publix/" + studyResult.getStudy().getId()
                + "/end?srid=" + studyResult.getId() + "&successful=" + successful
                + "&message=" + URLEncoder.encode(message, StandardCharsets.UTF_8.toString());
        RequestBuilder request = new RequestBuilder().method(GET).uri(url)
                .session(AuthenticationService.SESSION_USERNAME, admin.getUsername())
                .header(HeaderNames.HOST, "localhost:" + testServerPort())
                .cookie(idCookie);
        result = route(fakeApplication, request, 10000);
        return result;
    }

    private Result abortStudy(StudyResult studyResult, User admin, Cookie idCookie,
            String message) throws UnsupportedEncodingException {
        Result result;
        String url = Common.getPlayHttpContext() + "publix/" + studyResult.getStudy().getId()
                + "/abort?srid=" + studyResult.getId()
                + "&message=" + URLEncoder.encode(message, StandardCharsets.UTF_8.toString());
        RequestBuilder request = new RequestBuilder().method(GET).uri(url)
                .session(AuthenticationService.SESSION_USERNAME, admin.getUsername())
                .header(HeaderNames.HOST, "localhost:" + testServerPort())
                .cookie(idCookie);
        result = route(fakeApplication, request, 10000);
        return result;
    }

    private void checkStates(StudyResult studyResult, StudyState studyState,
            ComponentResult componentResult, ComponentState componentState) {
        assertThat(studyResult.getStudyState()).isEqualTo(studyState);
        assertThat(componentResult.getComponentState()).isEqualTo(componentState);
    }

    private void checkComponentResultAfterStart(Study study, StudyResult studyResult, User admin,
            int componentPosition, int componentResultListSize) {
        assertThat(studyResult.getComponentResultList().size()).isEqualTo(componentResultListSize);

        // Get the last component result
        ComponentResult componentResult =
                retrieveComponentResult(studyResult.getId(), componentResultListSize - 1);

        assertThat(componentResult.getComponent()).isEqualTo(study.getComponent(componentPosition));
        assertThat(componentResult.getStudyResult()).isEqualTo(studyResult);
        assertThat(componentResult.getWorkerId()).isEqualTo(admin.getWorker().getId());
        assertThat(componentResult.getWorkerType()).isEqualTo(admin.getWorker().getWorkerType());
        assertThat(componentResult.getComponentState()).isEqualTo(ComponentState.STARTED);
        assertThat(componentResult.getStartDate()).isNotNull();
    }

    private List<StudyResult> retrieveStudyResultList(User user) {
        return jpaApi.withTransaction(() -> {
            User admin = userDao.findByUsername(user.getUsername());
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
            testHelper.fetchTheLazyOnes(studyResult.getStudy().getFirstComponent());
            testHelper.fetchTheLazyOnes(studyResult.getWorker());
            testHelper.fetchTheLazyOnes(studyResult.getBatch());
            testHelper.fetchTheLazyOnes(studyResult.getComponentResultList());
            return studyResult;
        });
    }

    private ComponentResult retrieveComponentResult(long studyResultId, int index) {
        return jpaApi.withTransaction(() -> {
            StudyResult studyResult = studyResultDao.findById(studyResultId);
            ComponentResult componentResult = studyResult.getComponentResultList().get(index);
            testHelper.fetchTheLazyOnes(componentResult);
            StudyResult componentResultsStudyResult = componentResult.getStudyResult();
            testHelper.fetchTheLazyOnes(componentResultsStudyResult);
            Worker worker = componentResultsStudyResult.getWorker();
            testHelper.fetchTheLazyOnes(worker);
            Component component = componentResult.getComponent();
            testHelper.fetchTheLazyOnes(component);
            return componentResult;
        });
    }

    private StudyResult getLastStudyResult(List<StudyResult> studyResultList) {
        if (!studyResultList.isEmpty()) {
            StudyResult unfetchedStudyResult = studyResultList.get(studyResultList.size() - 1);
            return retrieveStudyResult(unfetchedStudyResult.getId());
        } else {
            return null;
        }
    }
}
