package services.gui;

import com.google.inject.Guice;
import com.google.inject.Injector;
import general.TestHelper;
import general.common.Common;
import general.common.RequestScope;
import models.common.User;
import org.fest.assertions.Fail;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import play.ApplicationLoader;
import play.Environment;
import play.db.jpa.JPAApi;
import play.inject.guice.GuiceApplicationBuilder;
import play.inject.guice.GuiceApplicationLoader;
import play.mvc.Http;

import javax.inject.Inject;
import javax.naming.CommunicationException;
import javax.naming.InvalidNameException;
import javax.naming.NamingException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import static org.fest.assertions.Assertions.assertThat;

/**
 * Tests AuthenticationService
 *
 * @author Kristian Lange
 */
@SuppressWarnings("deprecation")
public class AuthenticationServiceTest {

    @Inject
    private TestHelper testHelper;

    @Inject
    private JPAApi jpaApi;

    @Inject
    private AuthenticationService authenticationService;

    @Inject
    private UserSessionCacheAccessor userSessionCacheAccessor;

    @Before
    public void startApp() throws Exception {
        GuiceApplicationBuilder builder = new GuiceApplicationLoader()
                .builder(new ApplicationLoader.Context(Environment.simple()));
        Injector injector = Guice.createInjector(builder.applicationModule());
        injector.injectMembers(this);
    }

    @After
    public void stopApp() throws Exception {
        testHelper.removeUser(TestHelper.BLA_EMAIL);
    }

    @Test
    public void simpleCheck() {
        int a = 1 + 1;
        assertThat(a).isEqualTo(2);
    }

    /**
     * Test AuthenticationService.authenticate(): check with admin user
     */
    @Test
    public void checkAuthenticate() {
        jpaApi.withTransaction(() -> {
            try {
                assertThat(authenticationService.authenticate(UserService.ADMIN_USERNAME, UserService.ADMIN_PASSWORD))
                        .isTrue();
                assertThat(authenticationService.authenticate(UserService.ADMIN_USERNAME, "wrongPassword"))
                        .isFalse();
            } catch (NamingException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Test AuthenticationService.authenticate(): check with a different user
     * than the admin user (shouldn't be necessary - just to be sure)
     */
    @Test
    public void checkAuthenticateNotAdminUser() {
        testHelper.createAndPersistUser("oliver.zumba@gmail.com", "Bla Bla", "bla");

        jpaApi.withTransaction(() -> {
            try {
                assertThat(authenticationService.authenticate("oliver.zumba@gmail.com", "bla")).isTrue();
                assertThat(authenticationService.authenticate("oliver.zumba@gmail.com", "wrongPassword")).isFalse();
            } catch (NamingException e) {
                throw new RuntimeException(e);
            }
        });

        testHelper.removeUser("oliver.zumba@gmail.com");
    }

    /**
     * Test AuthenticationService.authenticate(): check LDAP successful authentication with external service
     */
    @Test
    public void checkAuthenticateUserLdap() {
        testHelper.setupLdap("ldap://ldap.forumsys.com:389", "dc=example,dc=com");
        testHelper.createAndPersistUserLdap("einstein", "Albert Einstein", "password", false);

        jpaApi.withTransaction(() -> {
            try {
                assertThat(authenticationService.authenticate("einstein", "password")).isTrue();
                assertThat(authenticationService.authenticate("einstein", "wrongPassword")).isFalse();
            } catch (NamingException e) {
                throw new RuntimeException(e);
            }
        });

        testHelper.removeUser("einstein");
    }

    /**
     * Test AuthenticationService.authenticate(): check LDAP fail due to wrong URL in config
     */
    @Test
    public void checkAuthenticateUserLdapWrongLdapUrl() {
        // Set up LDAP in Common with wrong URL (wrong port)
        testHelper.setupLdap("ldap://ldap.forumsys.com:666", "dc=example,dc=com");
        testHelper.createAndPersistUserLdap("einstein", "Albert Einstein", "password", false);

        jpaApi.withTransaction(() -> {
            try {
                authenticationService.authenticate("einstein", "password");
                Fail.fail();
            } catch (NamingException e) {
                assertThat(e).isInstanceOf(CommunicationException.class);
            }
        });

        testHelper.removeUser("einstein");
    }

    /**
     * Test AuthenticationService.authenticate(): check LDAP fail due to wrong ldapBase in config
     */
    @Test
    public void checkAuthenticateUserLdapWrongLdapBase() {
        // Set up LDAP in Common with wrong ldapBase
        testHelper.setupLdap("ldap://ldap.forumsys.com:389", "foo=example,bar=org");
        testHelper.createAndPersistUserLdap("einstein", "Albert Einstein", "password", false);

        jpaApi.withTransaction(() -> {
            try {
                authenticationService.authenticate("einstein", "password");
                Fail.fail();
            } catch (NamingException e) {
                assertThat(e).isInstanceOf(InvalidNameException.class);
            }
        });

        testHelper.removeUser("einstein");
    }

    /**
     * Test AuthenticationService.authenticate(): check LDAP authentication fail due to missing config
     */
    @Test
    public void checkAuthenticateUserLdapNoConfig() {
        // By default no LDAP is configured
        testHelper.createAndPersistUserLdap("einstein", "Albert Einstein", "password", false);

        jpaApi.withTransaction(() -> {
            try {
                authenticationService.authenticate("einstein", "password");
                Fail.fail();
            } catch (NamingException e) {
                assertThat(e).isInstanceOf(NamingException.class);
            }
        });

        testHelper.removeUser("einstein");
    }

    /**
     * Test AuthenticationService.isRepeatedLoginAttempt(): The 4th call for the
     * same email within the same minute returns true
     */
    @Test
    public void checkIsRepeatedLoginAttempt() {
        testHelper.createAndPersistUser(TestHelper.BLA_EMAIL, "Bla Bla", "bla");

        assertThat(authenticationService.isRepeatedLoginAttempt(TestHelper.BLA_EMAIL)).isFalse();
        assertThat(authenticationService.isRepeatedLoginAttempt(TestHelper.BLA_EMAIL)).isFalse();
        assertThat(authenticationService.isRepeatedLoginAttempt(TestHelper.BLA_EMAIL)).isFalse();
        assertThat(authenticationService.isRepeatedLoginAttempt(TestHelper.BLA_EMAIL)).isTrue();

        // Try a different email - should still return true
        assertThat(authenticationService.isRepeatedLoginAttempt("foo@foo.org")).isFalse();
    }

    /**
     * Test AuthenticationService.getLoggedInUserBySession()
     */
    @Test
    public void checkGetLoggedInUserBySession() {
        jpaApi.withTransaction(() -> {
            Map<String, String> data = new HashMap<>();
            data.put(AuthenticationService.SESSION_USERNAME, UserService.ADMIN_USERNAME);
            Http.Session session = new Http.Session(data);
            assertThat(authenticationService.getLoggedInUserBySessionCookie(session)).isEqualTo(testHelper.getAdmin());
        });

        // Try again with another non-admin user
        User userBla = testHelper.createAndPersistUser(TestHelper.BLA_EMAIL, "Bla Bla", "bla");
        jpaApi.withTransaction(() -> {
            Map<String, String> data = new HashMap<>();
            data.put(AuthenticationService.SESSION_USERNAME, userBla.getUsername());
            Http.Session session = new Http.Session(data);
            assertThat(authenticationService.getLoggedInUserBySessionCookie(session)).isEqualTo(userBla);
        });
    }

    /**
     * Test AuthenticationService.getLoggedInUserBySession(): Returns null if
     * user doesn't exists in the database
     */
    @Test
    public void checkGetLoggedInUserBySessionNotFound() {
        jpaApi.withTransaction(() -> {
            Map<String, String> data = new HashMap<>();
            data.put(AuthenticationService.SESSION_USERNAME, "user-not-exist@bla.org");
            Http.Session session = new Http.Session(data);
            assertThat(authenticationService.getLoggedInUserBySessionCookie(session)).isNull();
        });
    }

    /**
     * Test AuthenticationService.getLoggedInUser(): Gets the user that was put
     * into RequestScope by AuthenticationAction
     */
    @Test
    public void checkGetLoggedInUser() {
        testHelper.mockContext();

        User userBla = testHelper.createAndPersistUser(TestHelper.BLA_EMAIL, "Bla Bla", "bla");
        RequestScope.put(AuthenticationService.LOGGED_IN_USER, userBla);

        assertThat(authenticationService.getLoggedInUser()).isEqualTo(userBla);
    }

    /**
     * Test AuthenticationService.getLoggedInUser(): Returns null if no user is
     * in RequestScope
     */
    @Test
    public void checkGetLoggedInUserNotFound() {
        testHelper.mockContext();
        assertThat(authenticationService.getLoggedInUser()).isNull();
    }

    /**
     * Test AuthenticationService.writeSessionCookieAndSessionCache()
     */
    @Test
    public void checkWriteSessionCookieAndSessionCache() {
        User userBla = testHelper.createAndPersistUser(TestHelper.BLA_EMAIL, "Bla Bla", "bla");
        Map<String, String> data = new HashMap<>();
        Http.Session session = new Http.Session(data);

        jpaApi.withTransaction(() -> authenticationService.writeSessionCookieAndSessionCache(session,
                userBla.getUsername(), TestHelper.WWW_EXAMPLE_COM));

        // Check session cookie
        assertThat(session.get(AuthenticationService.SESSION_ID)).isNotEmpty();
        assertThat(session.get(AuthenticationService.SESSION_USERNAME)).isEqualTo(userBla.getUsername());
        assertThat(session.get(AuthenticationService.SESSION_LOGIN_TIME)).isNotEmpty();
        assertThat(session.get(AuthenticationService.SESSION_LAST_ACTIVITY_TIME)).isNotEmpty();

        // Check that user session cache and that the session ID is the same as in the session cookie
        String cachedUserSessionId = userSessionCacheAccessor.getUserSessionId(userBla.getUsername(),
                TestHelper.WWW_EXAMPLE_COM);
        assertThat(cachedUserSessionId).isEqualTo(session.get(AuthenticationService.SESSION_ID));
    }

    /**
     * Test AuthenticationService.refreshSessionCookie(): writes a new last
     * activity time into the session cookie
     */
    @Test
    public void checkRefreshSessionCookie() {
        Map<String, String> data = new HashMap<>();
        data.put(AuthenticationService.SESSION_LAST_ACTIVITY_TIME, "blafasel");
        Http.Session session = new Http.Session(data);
        authenticationService.refreshSessionCookie(session);

        assertThat(session.get(AuthenticationService.SESSION_LAST_ACTIVITY_TIME)).isNotEqualTo("blafasel");
    }

    /**
     * Test AuthenticationService.clearSessionCookie(): removes all entries from
     * the session cookie
     */
    @Test
    public void checkClearSessionCookie() {
        Map<String, String> data = new HashMap<>();
        data.put(AuthenticationService.SESSION_LAST_ACTIVITY_TIME, "blafasel");
        data.put(AuthenticationService.SESSION_ID, "blafasel");
        data.put(AuthenticationService.SESSION_LOGIN_TIME, "blafasel");
        data.put(AuthenticationService.SESSION_USERNAME, "blafasel");
        Http.Session session = new Http.Session(data);
        authenticationService.clearSessionCookie(session);

        // Check that session is cleared
        assertThat(session.get(AuthenticationService.SESSION_LAST_ACTIVITY_TIME)).isNull();
        assertThat(session.get(AuthenticationService.SESSION_ID)).isNull();
        assertThat(session.get(AuthenticationService.SESSION_LOGIN_TIME)).isNull();
        assertThat(session.get(AuthenticationService.SESSION_USERNAME)).isNull();
    }

    /**
     * Test AuthenticationService.clearSessionCookieAndSessionCache():
     * additional to removing all entries from the session it also removes the
     * session ID from the cached user session
     */
    @Test
    public void checkClearSessionCookieAndSessionCache() {
        User userBla = testHelper.createAndPersistUser(TestHelper.BLA_EMAIL, "Bla Bla", "bla");
        Map<String, String> data = new HashMap<>();
        data.put(AuthenticationService.SESSION_LAST_ACTIVITY_TIME, "blafasel");
        data.put(AuthenticationService.SESSION_ID, "blafasel");
        data.put(AuthenticationService.SESSION_LOGIN_TIME, "blafasel");
        data.put(AuthenticationService.SESSION_USERNAME, "blafasel");
        Http.Session session = new Http.Session(data);
        jpaApi.withTransaction(() -> authenticationService.clearSessionCookieAndSessionCache(session,
                userBla.getUsername(), TestHelper.WWW_EXAMPLE_COM));

        // Check that session is cleared
        assertThat(session.get(AuthenticationService.SESSION_LAST_ACTIVITY_TIME)).isNull();
        assertThat(session.get(AuthenticationService.SESSION_ID)).isNull();
        assertThat(session.get(AuthenticationService.SESSION_LOGIN_TIME)).isNull();
        assertThat(session.get(AuthenticationService.SESSION_USERNAME)).isNull();

        // Check that the session ID that was stored in cached user session is
        // removed
        String cachedUserSessionId = userSessionCacheAccessor.getUserSessionId(userBla.getUsername(),
                TestHelper.WWW_EXAMPLE_COM);
        assertThat(cachedUserSessionId).isNull();
    }

    /**
     * Test AuthenticationService.isValidSessionId(): returns true if both
     * session IDs (user's and in the session cookie) are the same
     */
    @Test
    public void checkIsValidSessionIdIsValid() {
        userSessionCacheAccessor.setUserSessionId(TestHelper.BLA_EMAIL, TestHelper.WWW_EXAMPLE_COM,
                "this-is-a-session-id");

        Map<String, String> data = new HashMap<>();
        data.put(AuthenticationService.SESSION_ID, "this-is-a-session-id");
        Http.Session session = new Http.Session(data);

        assertThat(authenticationService.isValidSessionId(session, TestHelper.BLA_EMAIL, TestHelper.WWW_EXAMPLE_COM))
                .isTrue();
    }

    /**
     * Test AuthenticationService.isValidSessionId(): returns false if both
     * session IDs aren't the same
     */
    @Test
    public void checkIsValidSessionIdReturnsFalse() {
        userSessionCacheAccessor.setUserSessionId(TestHelper.BLA_EMAIL, TestHelper.WWW_EXAMPLE_COM,
                "this-is-a-session-id");

        Map<String, String> data = new HashMap<>();
        data.put(AuthenticationService.SESSION_ID, "this-is-a-complete-different-session-id");
        Http.Session session = new Http.Session(data);

        assertThat(authenticationService.isValidSessionId(session, TestHelper.BLA_EMAIL, TestHelper.WWW_EXAMPLE_COM))
                .isFalse();
        // Emails are case-insensitive
        assertThat(authenticationService.isValidSessionId(session,
                TestHelper.BLA_UPPER_CASE_EMAIL, TestHelper.WWW_EXAMPLE_COM)).isFalse();
    }

    /**
     * Test AuthenticationService.isValidSessionId(): returns false if cached
     * user session ID was never set
     */
    @Test
    public void checkIsValidSessionIdUsersSessionIdNull() {
        Map<String, String> data = new HashMap<>();
        data.put(AuthenticationService.SESSION_ID, "this-is-a-session-id");
        Http.Session session = new Http.Session(data);

        assertThat(authenticationService.isValidSessionId(session,
                TestHelper.BLA_EMAIL, TestHelper.WWW_EXAMPLE_COM)).isFalse();
    }

    /**
     * Test AuthenticationService.isValidSessionId(): returns false if session
     * ID in session cookie is not set
     */
    @Test
    public void checkIsValidSessionIdNullInSession() {
        userSessionCacheAccessor.setUserSessionId(TestHelper.BLA_EMAIL, TestHelper.WWW_EXAMPLE_COM,
                "this-is-a-session-id");

        Map<String, String> data = new HashMap<>();
        Http.Session session = new Http.Session(data);

        assertThat(authenticationService.isValidSessionId(session,
                TestHelper.BLA_EMAIL, TestHelper.WWW_EXAMPLE_COM)).isFalse();
    }

    /**
     * Test AuthenticationService.isSessionTimeout(): returns false if the
     * session timeout didn't happen yet - or in other words if the session
     * login time is not older than specified in application.conf in
     * 'jatos.session.timeout'
     */
    @Test
    public void checkIsSessionTimeout() {
        Map<String, String> data = new HashMap<>();
        Instant loginTime = Instant.now();
        data.put(AuthenticationService.SESSION_LOGIN_TIME, String.valueOf(loginTime.toEpochMilli()));
        Http.Session session = new Http.Session(data);

        assertThat(authenticationService.isSessionTimeout(session)).isFalse();
    }

    /**
     * Test AuthenticationService.isSessionTimeout(): returns true if the
     * session timeout happened - or in other words if the session login time is
     * older than specified in application.conf in 'jatos.session.timeout'
     */
    @Test
    public void checkIsSessionTimeoutFail() {
        Map<String, String> data = new HashMap<>();
        // Make sure login time is older than what is allowed in the configured
        // session timeout
        Instant loginTime = Instant.now().minus(Common.getUserSessionTimeout() + 1, ChronoUnit.MINUTES);
        data.put(AuthenticationService.SESSION_LOGIN_TIME, String.valueOf(loginTime.toEpochMilli()));
        Http.Session session = new Http.Session(data);

        assertThat(authenticationService.isSessionTimeout(session)).isTrue();
    }

    /**
     * Test AuthenticationService.isSessionTimeout(): returns false if the
     * inactivity timeout didn't happen yet - or in other words if the session's
     * last activity time is not older than specified in application.conf in
     * 'jatos.session.inactivity'
     */
    @Test
    public void checkIsSessionInactivity() {
        Map<String, String> data = new HashMap<>();
        Instant lastActivityTime = Instant.now();
        data.put(AuthenticationService.SESSION_LAST_ACTIVITY_TIME, String.valueOf(lastActivityTime.toEpochMilli()));
        Http.Session session = new Http.Session(data);

        assertThat(authenticationService.isInactivityTimeout(session)).isFalse();
    }

    /**
     * Test AuthenticationService.isSessionTimeout(): returns true if the
     * inactivity timeout happened - or in other words if the session's last
     * activity time is older than specified in application.conf in
     * 'jatos.session.inactivity'
     */
    @Test
    public void checkIsSessionInactivityFail() {
        Map<String, String> data = new HashMap<>();
        Instant lastActivityTime = Instant.now().minus(Common.getUserSessionInactivity() + 1, ChronoUnit.MINUTES);
        data.put(AuthenticationService.SESSION_LAST_ACTIVITY_TIME, String.valueOf(lastActivityTime.toEpochMilli()));
        Http.Session session = new Http.Session(data);

        assertThat(authenticationService.isInactivityTimeout(session)).isTrue();
    }

}
