package auth.gui;

import daos.common.LoginAttemptDao;
import daos.common.UserDao;
import exceptions.common.AuthException;
import general.common.Common;
import http.common.Http.Context;
import models.common.User;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import play.mvc.Http;
import play.test.Helpers;
import utils.common.HashUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import static auth.gui.AuthService.*;
import static auth.gui.AuthService.SESSION_SIGNIN_TIME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthService.
 */
public class AuthServiceTest {

    private UserDao userDao;
    private LoginAttemptDao loginAttemptDao;
    private SigninLdap signinLdap;
    private AuthService authService;

    @Before
    public void setUp() {
        userDao = Mockito.mock(UserDao.class);
        loginAttemptDao = Mockito.mock(LoginAttemptDao.class);
        signinLdap = Mockito.mock(SigninLdap.class);
        authService = new AuthService(userDao, loginAttemptDao, signinLdap);

        Context.setCurrent(new Context(Helpers.fakeRequest().build()));
    }

    @After
    public void tearDown() {
        Context.clear();
    }

    @Test
    public void authenticate_DB_hashesPasswordAndDelegatesToUserDao() {
        User user = new User();
        user.setUsername("alice");
        user.setAuthMethod(User.AuthMethod.DB);
        String password = "secret";
        String expectedHash = HashUtils.getHashMD5(password);
        when(userDao.authenticate("alice", expectedHash)).thenReturn(true);

        boolean result = authService.authenticate(user, password);

        assertThat(result).isTrue();
        verify(userDao).authenticate("alice", expectedHash);
        verifyNoInteractions(signinLdap);
    }

    @Test
    public void authenticate_LDAP_delegatesToSigninLdap() {
        User user = new User();
        user.setUsername("bob");
        user.setAuthMethod(User.AuthMethod.LDAP);
        when(signinLdap.authenticate("bob", "pw")).thenReturn(true);

        boolean result = authService.authenticate(user, "pw");

        assertThat(result).isTrue();
        verify(signinLdap).authenticate("bob", "pw");
        verifyNoInteractions(userDao);
    }

    @Test
    public void authenticate_nullArgs_returnsFalse() {
        assertThat(authService.authenticate(null, "pw")).isFalse();
        User user = new User();
        user.setAuthMethod(User.AuthMethod.DB);
        assertThat(authService.authenticate(user, null)).isFalse();
    }

    @Test(expected = AuthException.class)
    public void authenticate_unsupportedAuth_throws() {
        User user = new User();
        user.setAuthMethod(User.AuthMethod.OAUTH_GOOGLE); // any non-DB/LDAP
        authService.authenticate(user, "pw");
    }

    @Test
    public void isRepeatedSigninAttempt_trueWhenAtLeastThree() {
        when(loginAttemptDao.countLoginAttemptsOfLastMin("user", "1.2.3.4")).thenReturn(3);
        assertThat(authService.isRepeatedSigninAttempt("user", "1.2.3.4")).isTrue();
    }

    @Test
    public void isRepeatedSigninAttempt_falseWhenLessThanThree() {
        when(loginAttemptDao.countLoginAttemptsOfLastMin("user", "1.2.3.4")).thenReturn(2);
        assertThat(authService.isRepeatedSigninAttempt("user", "1.2.3.4")).isFalse();
    }

    @Test
    public void getSignedinUserBySessionCookie_returnsUserWhenPresent() {
        Map<String, String> map = new HashMap<>();
        map.put(SESSION_USERNAME, "charlie");
        Context.current().response().putSession(map);

        User user = new User();
        when(userDao.findByUsername("charlie")).thenReturn(user);

        User result = authService.getSignedinUserBySessionCookie();

        assertThat(result).isSameAs(user);
    }

    @Test
    public void getSignedinUserBySessionCookie_returnsNullWhenMissing() {
        assertThat(authService.getSignedinUserBySessionCookie()).isNull();
    }

    @Test
    public void writeSessionCookie_setsExpectedKeys_and_isSessionKeepSignedinReflectsAllowFlag() {
        authService.writeSessionCookie("dana", true);

        // Keys set
        Http.Session session = Context.current().response().session();
        assertThat(Context.current().response().getSession(SESSION_USERNAME).orElse(null)).isEqualTo("dana");
        assertThat(Context.current().response().getSession(SESSION_SIGNIN_TIME)).isNotNull();
        assertThat(Context.current().response().getSession(SESSION_LAST_ACTIVITY_TIME)).isNotNull();

        // Since the allow flag is false by default without app config
        assertThat(session.get(SESSION_KEEP_SIGNEDIN).orElse(null)).isEqualTo("false");

        // Method should also honor the allow flag (default is false)
        boolean keep = authService.isSessionKeepSignedin();
        assertThat(keep).isEqualTo(false);
    }

    @Test
    public void writeSessionCookie_and_sessionAllowKeepSignedin() {
        try (MockedStatic<Common> utilities = Mockito.mockStatic(Common.class)) {
            // Mock Common::getUserSessionAllowKeepSignedin to return true
            //noinspection ResultOfMethodCallIgnored
            utilities.when(Common::getUserSessionAllowKeepSignedin).thenReturn(true);

            authService.writeSessionCookie("dana", true);

            Http.Session session = Context.current().response().session();
            assertThat(session.get(SESSION_KEEP_SIGNEDIN).orElse(null)).isEqualTo("true");

            boolean keep = authService.isSessionKeepSignedin();
            assertThat(keep).isEqualTo(true);
        }
    }

    @Test
    public void isSessionTimeout_trueWhenExpiredOrOnError() {
        // Missing value -> error path => true
        assertThat(authService.isSessionTimeout()).isTrue();

        // Expired timestamp
        Context.current().response().putSession(SESSION_SIGNIN_TIME,
                String.valueOf(Instant.now().minus(365, ChronoUnit.DAYS).toEpochMilli()));
        assertThat(authService.isSessionTimeout()).isTrue();
    }

    @Test
    public void isInactivityTimeout_trueWhenExpiredOrOnError() {
        // Missing value -> error path => true
        assertThat(authService.isInactivityTimeout()).isTrue();

        // Expired timestamp
        Context.current().response().putSession(SESSION_SIGNIN_TIME,
                String.valueOf(Instant.now().minus(365, ChronoUnit.DAYS).toEpochMilli()));
        assertThat(authService.isInactivityTimeout()).isTrue();
    }

    @Test
    public void getRedirectPageAfterSignin_homeWhenNoLastVisited() {
        User user = new User();
        user.setLastVisitedPageUrl("");
        String url = authService.getRedirectPageAfterSignin(user);
        assertThat(url).isEqualTo(controllers.gui.routes.Home.home(Http.Status.OK).url());
    }
}
