package controllers.gui;

import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static play.mvc.Http.Status.OK;
import static play.mvc.Http.Status.SEE_OTHER;
import static play.test.Helpers.contentAsString;
import static play.test.Helpers.route;

import javax.inject.Inject;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Guice;

import controllers.gui.Authentication.Login;
import general.TestHelper;
import play.Application;
import play.ApplicationLoader;
import play.Environment;
import play.inject.guice.GuiceApplicationBuilder;
import play.inject.guice.GuiceApplicationLoader;
import play.mvc.Http;
import play.mvc.Http.RequestBuilder;
import play.mvc.Result;
import play.test.Helpers;
import services.gui.AuthenticationService;
import services.gui.UserService;

/**
 * Testing controller.Authentication
 *
 * @author Kristian Lange
 */
public class AuthenticationControllerTest {

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
        Helpers.stop(fakeApplication);
        testHelper.removeStudyAssetsRootDir();
        testHelper.removeAllStudyLogs();
    }

    /**
     * Test Authentication.login()
     */
    @Test
    public void callLogin() {
        Http.Session session = testHelper.mockSessionCookieandCache(testHelper.getAdmin());
        RequestBuilder request = new RequestBuilder()
                .method("GET")
                .session(session)
                .remoteAddress(TestHelper.WWW_EXAMPLE_COM)
                .uri(controllers.gui.routes.Authentication.login().url());
        Result result = route(fakeApplication, request);

        assertThat(result.status()).isEqualTo(OK);
        assertThat(result.charset().get()).isEqualTo("utf-8");
        assertThat(result.contentType().get()).isEqualTo("text/html");
        assertThat(contentAsString(result)).contains("login");
    }

    /**
     * Test Authentication.logout()
     */
    @Test
    public void callLogout() {
        Http.Session session = testHelper.mockSessionCookieandCache(testHelper.getAdmin());
        RequestBuilder request = new RequestBuilder()
                .method("GET")
                .session(session)
                .remoteAddress(TestHelper.WWW_EXAMPLE_COM)
                .uri(controllers.gui.routes.Authentication.logout().url());
        Result result = route(fakeApplication, request);

        // Check that it redirects to the login page
        assertThat(result.status()).isEqualTo(SEE_OTHER);
        assertThat(result.redirectLocation().get()).contains("login");
        assertThat(!result.session().containsKey(AuthenticationService.SESSION_USERNAME));
    }

    /**
     * Test Authentication.authenticate()
     */
    @Test
    public void authenticateSuccess() {
        RequestBuilder request = new RequestBuilder()
                .method("POST")
                .remoteAddress(TestHelper.WWW_EXAMPLE_COM)
                .bodyForm(ImmutableMap.of(Login.USERNAME, UserService.ADMIN_USERNAME, Login.PASSWORD, UserService.ADMIN_PASSWORD))
                .uri(controllers.gui.routes.Authentication.authenticate().url());
        Result result = route(fakeApplication, request);

        // Successful login leads to a redirect and the user's email is in the session
        assertEquals(303, result.status());
        assertEquals(UserService.ADMIN_USERNAME, result.session().get(AuthenticationService.SESSION_USERNAME));
    }

    /**
     * Test Authentication.authenticate()
     */
    @Test
    public void authenticateFailure() {
        RequestBuilder request = new RequestBuilder()
                .method("POST")
                .remoteAddress(TestHelper.WWW_EXAMPLE_COM)
                .bodyForm(ImmutableMap.of(Login.USERNAME, UserService.ADMIN_USERNAME, Login.PASSWORD, "bla"))
                .uri(controllers.gui.routes.Authentication.authenticate().url());
        Result result = route(fakeApplication, request);

        // Fail to login leads to a Bad Request (400)
        assertEquals(401, result.status());
        assertNull(result.session());
    }

    /**
     * Test Authentication.authenticate() with LDAP
     */
    @Test
    public void authenticateLdapSuccess() {
        testHelper.setupLdap("ldap://ldap.forumsys.com:389", "dc=example,dc=com");
        testHelper.createAndPersistUserLdap("einstein", "Albert Einstein", "password", false);

        RequestBuilder request = new RequestBuilder()
                .method("POST")
                .remoteAddress(TestHelper.WWW_EXAMPLE_COM)
                .bodyForm(ImmutableMap.of(Login.USERNAME, "einstein", Login.PASSWORD, "password"))
                .uri(controllers.gui.routes.Authentication.authenticate().url());
        Result result = route(fakeApplication, request);

        // Successful login leads to a redirect and the user's email is in the session
        assertEquals(303, result.status());
        assertEquals("einstein", result.session().get(AuthenticationService.SESSION_USERNAME));
    }

    /**
     * Test Authentication.authenticate() with LDAP
     */
    @Test
    public void authenticateLdapFail() {
        testHelper.setupLdap("ldap://ldap.forumsys.com:389", "dc=example,dc=com");
        testHelper.createAndPersistUserLdap("einstein", "Albert Einstein", "password", false);

        RequestBuilder request = new RequestBuilder()
                .method("POST")
                .remoteAddress(TestHelper.WWW_EXAMPLE_COM)
                .bodyForm(ImmutableMap.of(Login.USERNAME, "einstein", Login.PASSWORD, "wrongpassword"))
                .uri(controllers.gui.routes.Authentication.authenticate().url());
        Result result = route(fakeApplication, request);

        // Fail to login leads to a Bad Request (400)
        assertEquals(401, result.status());
        assertNull(result.session());
    }

}
