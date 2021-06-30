package controllers.publix;

import akka.stream.Materializer;
import com.google.inject.Guice;
import com.google.inject.Injector;
import controllers.publix.workers.JatosPublix;
import controllers.publix.workers.JatosPublix.JatosRun;
import daos.common.UserDao;
import exceptions.publix.NotFoundPublixException;
import general.TestHelper;
import general.common.Common;
import general.common.MessagesStrings;
import models.common.Study;
import models.common.User;
import org.fest.assertions.Fail;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import play.Application;
import play.ApplicationLoader;
import play.Environment;
import play.db.jpa.JPAApi;
import play.inject.guice.GuiceApplicationBuilder;
import play.inject.guice.GuiceApplicationLoader;
import play.mvc.Call;
import play.mvc.Http.Cookie;
import play.mvc.Http.RequestBuilder;
import play.mvc.Result;
import play.test.Helpers;
import services.gui.AuthenticationService;
import services.gui.StudyService;
import services.gui.UserService;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;

import static org.fest.assertions.Assertions.assertThat;
import static play.mvc.Http.Status.*;
import static play.test.Helpers.*;

/**
 * Testing controller.publix.StudyAssets
 *
 * @author Kristian Lange
 */
public class StudyAssetsTest {

    private Injector injector;

    @Inject
    private Application fakeApplication;

    @Inject
    private TestHelper testHelper;

    @Inject
    private JPAApi jpaApi;

    @Inject
    private StudyService studyService;

    @Inject
    private UserDao userDao;

    @Inject
    private StudyAssets studyAssets;

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

    @Test
    public void testStudyAssetsRootPath() {
        File studyAssetsRoot = new File(Common.getStudyAssetsRootPath());
        assertThat(studyAssetsRoot.exists()).isTrue();
        assertThat(studyAssetsRoot.isDirectory()).isTrue();
        assertThat(studyAssetsRoot.isAbsolute()).isTrue();
    }

    @Test
    public void testViaStudyPath() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        Result startStudyResult = startStudy(study);
        Cookie idCookie = startStudyResult.cookie("JATOS_IDS_0");

        Call call = controllers.publix.routes.StudyAssets.viaStudyPath(study.getId(), "bla",
                study.getFirstComponent().get().getHtmlFilePath());
        RequestBuilder request =
                new RequestBuilder().method(Helpers.GET).uri(call.url()).cookie(idCookie);
        Result result = route(fakeApplication, request);
        assertThat(result.status()).isEqualTo(OK);
    }

    @Test
    public void testViaStudyPathNotFound() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Result startStudyResult = startStudy(study);
        Cookie idCookie = startStudyResult.cookie("JATOS_IDS_0");

        Call call = controllers.publix.routes.StudyAssets
                .viaStudyPath(study.getId(), "bla", "non_existend_file");
        RequestBuilder request =
                new RequestBuilder().method(Helpers.GET).uri(call.url()).cookie(idCookie);
        Result result = route(fakeApplication, request);
        assertThat(result.status()).isEqualTo(NOT_FOUND);
    }

    @Test
    public void testViaAssetsPath() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        Result startStudyResult = startStudy(study);
        Cookie idCookie = startStudyResult.cookie("JATOS_IDS_0");

        Call call = controllers.publix.routes.StudyAssets.viaAssetsPath(study.getDirName() + "/"
                + study.getFirstComponent().get().getHtmlFilePath());
        RequestBuilder request =
                new RequestBuilder().method(Helpers.GET).uri(call.url()).cookie(idCookie);
        Result result = route(fakeApplication, request);
        assertThat(result.status()).isEqualTo(OK);
    }

    @Test
    public void testViaAssetsPathNotFound() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Result startStudyResult = startStudy(study);
        Cookie idCookie = startStudyResult.cookie("JATOS_IDS_0");

        Call call = controllers.publix.routes.StudyAssets
                .viaAssetsPath(study.getDirName() + "/non_existend_file");
        RequestBuilder request =
                new RequestBuilder().method(Helpers.GET).uri(call.url()).cookie(idCookie);
        Result result = route(fakeApplication, request);
        assertThat(result.status()).isEqualTo(NOT_FOUND);
    }

    @Test
    public void testViaAssetsPathWrongStudyDir() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Result startStudyResult = startStudy(study);
        Cookie idCookie = startStudyResult.cookie("JATOS_IDS_0");

        Call call = controllers.publix.routes.StudyAssets.viaAssetsPath("wrong_study_dir/"
                + study.getFirstComponent().get().getHtmlFilePath());
        RequestBuilder request = new RequestBuilder().method(Helpers.GET)
                .uri(call.url()).cookie(idCookie);
        Result result = route(fakeApplication, request);
        assertThat(result.status()).isEqualTo(FORBIDDEN);
    }

    @Test
    public void testViaAssetsPathWrongAssets() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Study otherStudy = cloneStudy(study);
        Result startStudyResult = startStudy(study);
        Cookie idCookie = startStudyResult.cookie("JATOS_IDS_0");

        // Started study but ask for asset of otherStudy
        Call call = controllers.publix.routes.StudyAssets.viaAssetsPath(otherStudy.getDirName()
                + "/" + otherStudy.getFirstComponent().get().getHtmlFilePath());
        RequestBuilder request =
                new RequestBuilder().method(Helpers.GET).uri(call.url()).cookie(idCookie);
        Result result = route(fakeApplication, request);
        assertThat(result.status()).isEqualTo(FORBIDDEN);
    }

    private Study cloneStudy(Study study) {
        return jpaApi.withTransaction(() -> {
            try {
                User admin = userDao.findByUsername(UserService.ADMIN_USERNAME);
                Study clone = studyService.clone(study);
                studyService.createAndPersistStudy(admin, clone);
                return clone;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    @Test
    public void testViaAssetsPathPathTraversalAttack() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Result startStudyResult = startStudy(study);
        Cookie idCookie = startStudyResult.cookie("JATOS_IDS_0");

        // Although this file exists, it shouldn't be found since all '/..' are removed
        Call call = controllers.publix.routes.StudyAssets
                .viaAssetsPath(study.getDirName() + "/../../conf/application.conf");
        RequestBuilder request =
                new RequestBuilder().method(Helpers.GET).uri(call.url()).cookie(idCookie);
        Result result = route(fakeApplication, request);
        assertThat(result.status()).isEqualTo(NOT_FOUND);
    }

    @Test
    public void testRetrieveComponentHtmlFile()
            throws NotFoundPublixException {
        testHelper.mockContext();

        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        Result result = studyAssets.retrieveComponentHtmlFile(study.getDirName(),
                study.getFirstComponent().get().getHtmlFilePath()).asJava();

        assertThat(result.status()).isEqualTo(OK);
        assertThat(result.charset().get()).isEqualTo("utf-8");
        assertThat(result.contentType().get()).isEqualTo("text/html");
        // And check a random line of the JS code
        assertThat(contentAsString(result, materializer)).contains("jatos.onLoad(function() {");
    }

    @Test
    public void testRetrieveComponentHtmlFileNotFound() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        try {
            studyAssets.retrieveComponentHtmlFile(study.getDirName(), "/someNotExistingPath");
            Fail.fail();
        } catch (NotFoundPublixException e) {
            assertThat(e.getMessage()).isEqualTo(MessagesStrings
                    .htmlFilePathNotExist(study.getDirName(), "/someNotExistingPath"));
        }
    }

    private Result startStudy(Study study) {
        User admin = testHelper.getAdmin();
        String url = Common.getPlayHttpContext() + "publix/" + study.getId() + "/start?"
                + JatosPublix.JATOS_WORKER_ID + "=" + admin.getWorker().getId();
        RequestBuilder request = new RequestBuilder().method(GET).uri(url)
                .session(AuthenticationService.SESSION_USERNAME, admin.getUsername())
                .session(JatosPublix.SESSION_JATOS_RUN, JatosRun.RUN_STUDY.name());
        return route(fakeApplication, request);
    }

    @Test
    public void testEnhanceQueryParametersInEndRedirectUrl() {
        assertThat(studyAssets.enhanceQueryStringInEndRedirectUrl(
                "{\"foo\":\"bar\", \"SONA_ID\":\"abc123\", \"foo-bar\":\"1a3-4b6-7c9\"}",
                "https://end.redirect-url.com.com/study?id=123&survey_code=[SONA_ID]"))
                .isEqualTo("https://end.redirect-url.com.com/study?id=123&survey_code=abc123");

        // Multiple parameters
        assertThat(studyAssets.enhanceQueryStringInEndRedirectUrl(
                "{\"foo\":\"bar\", \"SONA_ID\":\"abc123\", \"foo-bar\":\"1a3\", \"uid\":\"123456\"}",
                "https://end.redirect-url.com.com/study?id=123&survey_code=[SONA_ID]&another=[foo-bar]&uid=[uid]"))
                .isEqualTo("https://end.redirect-url.com.com/study?id=123&survey_code=abc123&another=1a3&uid=123456");

        // With URL encoding
        // Problem here: space can be encoded as + or %20 (https://stackoverflow.com/questions/1634271)
        assertThat(studyAssets.enhanceQueryStringInEndRedirectUrl(
                "{\"foo\":\"bar\", \"[ID]\":\"abc / 123\", \"foo-bar\":\"1a3-4b6-7c9\"}",
                "https://end.redirect-url.com.com/study?id=123&survey_code=[%5BID%5D]"))
                .isEqualTo("https://end.redirect-url.com.com/study?id=123&survey_code=abc+%2F+123");

        // Empty query parameter
        assertThat(studyAssets.enhanceQueryStringInEndRedirectUrl(
                "{\"foo\":\"bar\", \"SONA_ID\":\"\", \"foo-bar\":\"1a3-4b6-7c9\"}",
                "https://end.redirect-url.com.com/study?id=123&survey_code=[SONA_ID]"))
                .isEqualTo("https://end.redirect-url.com.com/study?id=123&survey_code=");

        // Missing query parameter
        assertThat(studyAssets.enhanceQueryStringInEndRedirectUrl(
                "{\"foo\":\"bar\", \"foo-bar\":\"1a3-4b6-7c9\"}",
                "https://end.redirect-url.com.com/study?id=123&survey_code=[SONA_ID]"))
                .isEqualTo("https://end.redirect-url.com.com/study?id=123&survey_code=undefined");

        // Wrong parameter in endRedirectURL
        assertThat(studyAssets.enhanceQueryStringInEndRedirectUrl(
                "{\"foo\":\"bar\", \"SONA_ID\":\"abc123\", \"foo-bar\":\"1a3-4b6-7c9\"}",
                "https://end.redirect-url.com.com/study?id=123&survey_code=[WRONG_ID]"))
                .isEqualTo("https://end.redirect-url.com.com/study?id=123&survey_code=undefined");
    }

}
