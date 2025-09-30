package modules.gui.auth;

import auth.gui.AuthService;
import auth.gui.SigninLdap;
import daos.common.LoginAttemptDao;
import daos.common.UserDao;
import general.common.Common;
import models.common.User;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import play.mvc.Http;
import utils.common.HashUtils;

import javax.naming.NamingException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthService.
 */
@SuppressWarnings("deprecation")
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
    }

    private Http.Session newSession() {
        return new Http.Session(new HashMap<>());
    }

    @Test
    public void authenticate_DB_hashesPasswordAndDelegatesToUserDao() throws NamingException {
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
    public void authenticate_LDAP_delegatesToSigninLdap() throws NamingException {
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
    public void authenticate_nullArgs_returnsFalse() throws NamingException {
        assertThat(authService.authenticate(null, "pw")).isFalse();
        User user = new User();
        user.setAuthMethod(User.AuthMethod.DB);
        assertThat(authService.authenticate(user, null)).isFalse();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void authenticate_unsupportedAuth_throws() throws NamingException {
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
        Http.Session session = newSession();
        session.put(AuthService.SESSION_USERNAME, "charlie");
        User user = new User();
        when(userDao.findByUsername("charlie")).thenReturn(user);

        User result = authService.getSignedinUserBySessionCookie(session);

        assertThat(result).isSameAs(user);
    }

    @Test
    public void getSignedinUserBySessionCookie_returnsNullWhenMissing() {
        Http.Session session = newSession();
        assertThat(authService.getSignedinUserBySessionCookie(session)).isNull();
    }

    @Test
    public void writeSessionCookie_setsExpectedKeys_and_isSessionKeepSignedinReflectsAllowFlag() {
        Http.Session session = newSession();

        authService.writeSessionCookie(session, "dana", true);

        // Keys set
        assertThat(session.get(AuthService.SESSION_USERNAME)).isEqualTo("dana");
        assertThat(session.get(AuthService.SESSION_SIGNIN_TIME)).isNotNull();
        assertThat(session.get(AuthService.SESSION_LAST_ACTIVITY_TIME)).isNotNull();

        // Since the allow flag is false by default without app config
        assertThat(session.get(AuthService.SESSION_KEEP_SIGNEDIN)).isEqualTo("false");

        // Method should also honor the allow flag (default is false)
        boolean keep = authService.isSessionKeepSignedin(session);
        assertThat(keep).isEqualTo(false);
    }

    @Test
    public void writeSessionCookie_and_sessionAllowKeepSignedin() {
        Http.Session session = newSession();

        try (MockedStatic<Common> utilities = Mockito.mockStatic(Common.class)) {
            // Mock Common::getUserSessionAllowKeepSignedin to return true
            //noinspection ResultOfMethodCallIgnored
            utilities.when(Common::getUserSessionAllowKeepSignedin).thenReturn(true);

            authService.writeSessionCookie(session, "dana", true);

            assertThat(session.get(AuthService.SESSION_KEEP_SIGNEDIN)).isEqualTo("true");

            boolean keep = authService.isSessionKeepSignedin(session);
            assertThat(keep).isEqualTo(true);
        }
    }

    @Test
    public void isSessionTimeout_trueWhenExpiredOrOnError() {
        Http.Session session = newSession();
        // Missing value -> error path => true
        assertThat(authService.isSessionTimeout(session)).isTrue();

        // Expired timestamp
        session.put(AuthService.SESSION_SIGNIN_TIME, String.valueOf(Instant.now().minus(365, ChronoUnit.DAYS).toEpochMilli()));
        assertThat(authService.isSessionTimeout(session)).isTrue();
    }

    @Test
    public void isInactivityTimeout_trueWhenExpiredOrOnError() {
        Http.Session session = newSession();
        // Missing value -> error path => true
        assertThat(authService.isInactivityTimeout(session)).isTrue();

        // Expired timestamp
        session.put(AuthService.SESSION_LAST_ACTIVITY_TIME, String.valueOf(Instant.now().minus(365, ChronoUnit.DAYS).toEpochMilli()));
        assertThat(authService.isInactivityTimeout(session)).isTrue();
    }

    @Test
    public void getRedirectPageAfterSignin_homeWhenNoLastVisited() {
        User user = new User();
        user.setLastVisitedPageUrl("");
        String url = authService.getRedirectPageAfterSignin(user);
        assertThat(url).isEqualTo(controllers.gui.routes.Home.home().url());
    }
}
