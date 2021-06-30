package controllers.gui;

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
public class HomeControllerTest {

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
    public void callHome() {
        Http.Session session = testHelper.mockSessionCookieandCache(testHelper.getAdmin());
        RequestBuilder request = new RequestBuilder()
                .method("GET")
                .session(session)
                .remoteAddress(TestHelper.WWW_EXAMPLE_COM)
                .uri(routes.Home.home().url());
        Result result = route(fakeApplication, request);

        assertThat(result.status()).isEqualTo(OK);
        assertThat(result.charset().get()).isEqualTo("utf-8");
        assertThat(result.contentType().get()).isEqualTo("text/html");
        assertThat(contentAsString(result)).contains(BreadcrumbsService.HOME);
    }

    @Test
    public void callSidebarStudyList() {
        Http.Session session = testHelper.mockSessionCookieandCache(testHelper.getAdmin());
        RequestBuilder request = new RequestBuilder()
                .method("GET")
                .session(session)
                .remoteAddress(TestHelper.WWW_EXAMPLE_COM)
                .uri(routes.Home.sidebarStudyList().url());
        Result result = route(fakeApplication, request);

        assertThat(result.status()).isEqualTo(OK);
    }

}
