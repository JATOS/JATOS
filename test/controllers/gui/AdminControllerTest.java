package controllers.gui;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Guice;
import general.TestHelper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import play.Application;
import play.ApplicationLoader;
import play.Environment;
import play.inject.guice.GuiceApplicationBuilder;
import play.inject.guice.GuiceApplicationLoader;
import play.libs.Json;
import play.mvc.Http;
import play.mvc.Http.RequestBuilder;
import play.mvc.Result;
import play.test.Helpers;
import services.gui.BreadcrumbsService;

import javax.inject.Inject;

import static org.fest.assertions.Assertions.assertThat;
import static play.mvc.Http.Status.OK;
import static play.test.Helpers.contentAsString;
import static play.test.Helpers.route;

/**
 * Testing actions of controller.gui.Home.
 *
 * @author Kristian Lange
 */
@SuppressWarnings("OptionalGetWithoutIsPresent")
public class AdminControllerTest {

    @Inject
    private Application fakeApplication;

    @Inject
    private TestHelper testHelper;

    @Before
    public void startApp() throws Exception {
        fakeApplication = Helpers.fakeApplication();

        GuiceApplicationBuilder builder = new GuiceApplicationLoader()
                .builder(new ApplicationLoader.Context(Environment.simple()));
        Guice.createInjector(builder.applicationModule()).injectMembers(this);

        Helpers.start(fakeApplication);
    }

    @After
    public void stopApp() throws Exception {
        // Clean up
        testHelper.removeAllStudies();

        Helpers.stop(fakeApplication);
        testHelper.removeStudyAssetsRootDir();
        testHelper.removeAllStudyLogs();
    }

    @Test
    public void callAdministration() {
        Http.Session session = testHelper.mockSessionCookieAndCache(testHelper.getAdmin());
        RequestBuilder request = new RequestBuilder()
                .method("GET")
                .session(session)
                .remoteAddress(TestHelper.WWW_EXAMPLE_COM)
                .uri(routes.Admin.administration().url());
        Result result = route(fakeApplication, request);

        assertThat(result.status()).isEqualTo(OK);
        assertThat(result.charset().get()).isEqualTo("utf-8");
        assertThat(result.contentType().get()).isEqualTo("text/html");
        assertThat(contentAsString(result)).contains(BreadcrumbsService.ADMINISTRATION);
    }

    @Test
    public void callListLogs() {
        Http.Session session = testHelper.mockSessionCookieAndCache(testHelper.getAdmin());
        RequestBuilder request = new RequestBuilder()
                .method("GET")
                .session(session)
                .remoteAddress(TestHelper.WWW_EXAMPLE_COM)
                .uri(routes.Admin.listLogs().url());
        Result result = route(fakeApplication, request);

        assertThat(result.status()).isEqualTo(OK);
        assertThat(Json.parse(contentAsString(result)).toString()).isNotEmpty();
    }

    @Test
    public void callLogs() {
        Http.Session session = testHelper
                .mockSessionCookieAndCache(testHelper.getAdmin());
        RequestBuilder request = new RequestBuilder()
                .method("GET")
                .session(session)
                .remoteAddress(TestHelper.WWW_EXAMPLE_COM)
                .uri(routes.Admin.logs("application.log", 1000, true).url());
        Result result = route(fakeApplication, request);

        assertThat(result.status()).isEqualTo(OK);
        assertThat(result.body()).isNotNull();
    }

    @Test
    public void callStatus() {
        Http.Session session = testHelper.mockSessionCookieAndCache(testHelper.getAdmin());
        RequestBuilder request = new RequestBuilder()
                .method("GET")
                .session(session)
                .remoteAddress(TestHelper.WWW_EXAMPLE_COM)
                .uri(routes.Admin.status().url());
        Result result = route(fakeApplication, request);

        assertThat(result.status()).isEqualTo(OK);
        JsonNode json = Json.parse(contentAsString(result));
        assertThat(json.get("studyCount").toString()).isNotEmpty();
        assertThat(json.get("studyResultCount").toString()).isNotEmpty();
        assertThat(json.get("workerCount").toString()).isNotEmpty();
        assertThat(json.get("userCount").toString()).isNotEmpty();
        assertThat(json.get("serverTime").toString()).isNotEmpty();
        assertThat(json.get("latestUsers").toString()).isNotEmpty();
        assertThat(json.get("latestStudyRuns").toString()).isNotEmpty();
    }

    @Test
    public void callStudyAdmin() {
        Http.Session session = testHelper.mockSessionCookieAndCache(testHelper.getAdmin());
        RequestBuilder request = new RequestBuilder()
                .method("GET")
                .session(session)
                .remoteAddress(TestHelper.WWW_EXAMPLE_COM)
                .uri(routes.Admin.studyAdmin().url());
        Result result = route(fakeApplication, request);

        assertThat(result.status()).isEqualTo(OK);
        assertThat(result.charset().get()).isEqualTo("utf-8");
        assertThat(result.contentType().get()).isEqualTo("text/html");
        assertThat(contentAsString(result)).contains(BreadcrumbsService.ADMINISTRATION);
    }

    @Test
    public void callAllStudiesData() {
        Http.Session session = testHelper.mockSessionCookieAndCache(testHelper.getAdmin());
        RequestBuilder request = new RequestBuilder()
                .method("GET")
                .session(session)
                .remoteAddress(TestHelper.WWW_EXAMPLE_COM)
                .uri(routes.Admin.allStudiesData().url());
        Result result = route(fakeApplication, request);

        assertThat(result.status()).isEqualTo(OK);
        JsonNode json = Json.parse(contentAsString(result));
        assertThat(json.toString()).isNotEmpty();
    }

    @Test
    public void callStudiesDataByUser() {
        Http.Session session = testHelper.mockSessionCookieAndCache(testHelper.getAdmin());
        RequestBuilder request = new RequestBuilder()
                .method("GET")
                .session(session)
                .remoteAddress(TestHelper.WWW_EXAMPLE_COM)
                .uri(routes.Admin.studiesDataByUser("admin").url());
        Result result = route(fakeApplication, request);

        assertThat(result.status()).isEqualTo(OK);
        JsonNode json = Json.parse(contentAsString(result));
        assertThat(json.toString()).isNotEmpty();
    }

}
