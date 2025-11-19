package auth.gui;

import com.google.common.base.Strings;
import daos.common.LoginAttemptDao;
import daos.common.UserDao;
import general.common.Common;
import general.common.RequestScope;
import models.common.User;
import play.Logger;
import play.Logger.ALogger;
import play.mvc.Http;
import utils.common.HashUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.naming.NamingException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Service class around authentication and the session cookie. It works together with the
 * {@link Signin} controller and the @{@link auth.gui.AuthAction.Auth} annotation defined in {@link AuthAction}.
 *
 * @author Kristian Lange
 */
@Singleton
public class AuthService {

    private static final ALogger LOGGER = Logger.of(AuthService.class);

    /**
     * Parameter name in Play's session cookie: It contains the username of the signed-in user
     */
    public static final String SESSION_USERNAME = "username";

    /**
     * Parameter name in Play's session cookie: It contains the timestamp of the sign-in time
     */
    public static final String SESSION_SIGNIN_TIME = "signinTime";

    /**
     * Parameter name in Play's session cookie: It contains a timestamp of the last HTTP request done by the browser
     * with this cookie
     */
    public static final String SESSION_LAST_ACTIVITY_TIME = "lastActivityTime";

    /**
     * Parameter name in Play's session cookie: true if the user wants to be kept signed in.
     * This means the session does not time out.
     */
    public static final String SESSION_KEEP_SIGNEDIN = "keepSignedin";

    /**
     * Key name used in RequestScope to store the signed-in User
     */
    public static final String SIGNEDIN_USER = "signedinUser";

    private final UserDao userDao;
    private final LoginAttemptDao loginAttemptDao;
    private final SigninLdap signinLdap;

    @Inject
    AuthService(UserDao userDao, LoginAttemptDao loginAttemptDao, SigninLdap signinLdap) {
        this.userDao = userDao;
        this.loginAttemptDao = loginAttemptDao;
        this.signinLdap = signinLdap;
    }

    /**
     * Authenticates the user with the given password.
     */
    public boolean authenticate(User user, String password) throws NamingException {
        if (user == null || password == null) return false;

        switch (user.getAuthMethod()) {
            case LDAP:
                return signinLdap.authenticate(user.getUsername(), password);
            case DB:
                return authenticateViaDb(user.getUsername(), password);
            default:
                throw new IllegalArgumentException("Unsupported auth method " + user.getAuthMethod().name());
        }
    }

    private boolean authenticateViaDb(String normalizedUsername, String password) {
        String passwordHash = HashUtils.getHashMD5(password);
        return userDao.authenticate(normalizedUsername, passwordHash);
    }

    /**
     * Returns true if there were already 3 sign-in attempts within the last minute with this username from this
     * remoteAddress
     */
    public boolean isRepeatedSigninAttempt(String normalizedUsername, String remoteAddress) {
        return loginAttemptDao.countLoginAttemptsOfLastMin(normalizedUsername, remoteAddress) >= 3;
    }

    /**
     * Retrieves the signed-in user from Play's session. If a user is signed-in their username is stored in Play's
     * session cookie. With the username, a user can be retrieved from the database. Returns null if the session doesn't
     * contain a username or if the user doesn't exist in the database.
     * <p>
     * In most cases, getSignedinUser() is faster since it doesn't have to query the database.
     */
    @SuppressWarnings("deprecation")
    public User getSignedinUserBySessionCookie(Http.Session session) {
        String normalizedUsername = session.get(AuthService.SESSION_USERNAME);
        User signedinUser = null;
        if (normalizedUsername != null) {
            signedinUser = userDao.findByUsername(normalizedUsername);
        }
        return signedinUser;
    }

    /**
     * Gets the signed-in user from the RequestScope. It was put into the
     * RequestScope by the AuthenticationAction. Therefore, this method works
     * only if you use the @Authenticated annotation at your action.
     */
    public User getSignedinUser() {
        return (User) RequestScope.get(SIGNEDIN_USER);
    }

    /**
     * Prepares Play's session cookie for the user with the given username to be signed-in. Does not authenticate the
     * user (use authenticate() for this).
     */
    @SuppressWarnings("deprecation")
    public void writeSessionCookie(Http.Session session, String normalizedUsername, boolean keepSignedin) {
        session.put(SESSION_USERNAME, normalizedUsername);
        session.put(SESSION_SIGNIN_TIME, String.valueOf(Instant.now().toEpochMilli()));
        session.put(SESSION_LAST_ACTIVITY_TIME, String.valueOf(Instant.now().toEpochMilli()));
        session.put(SESSION_KEEP_SIGNEDIN, String.valueOf(Common.getUserSessionAllowKeepSignedin() && keepSignedin));
    }

    /**
     * Returns true if the user decided to be kept signed (checkbox on the sign-in page) AND if it is allowed to be kept
     * signed in.
     */
    public boolean isSessionKeepSignedin(Http.Session session) {
        Optional<String> keepSignedin = session.getOptional(SESSION_KEEP_SIGNEDIN);
        boolean allowKeepSignedin = Common.getUserSessionAllowKeepSignedin();
        return allowKeepSignedin && keepSignedin.isPresent() && keepSignedin.get().equals("true");
    }

    /**
     * Returns true if the session sign-in time as saved in Play's session cookie
     * is older than allowed.
     */
    @SuppressWarnings("deprecation")
    public boolean isSessionTimeout(Http.Session session) {
        try {
            String signinTimeStr = session.get(SESSION_SIGNIN_TIME);
            Instant signinTime = Instant.ofEpochMilli(Long.parseLong(signinTimeStr));
            Instant now = Instant.now();
            Instant allowedUntil = signinTime.plus(Common.getUserSessionTimeout(), ChronoUnit.MINUTES);
            return allowedUntil.isBefore(now);
        } catch (Exception e) {
            LOGGER.error(".isSessionTimeout: " + e.getMessage());
            // In case of any exception: timeout
            return true;
        }
    }

    /**
     * Returns true if the session inactivity time as saved in Play's session
     * cookie is older than allowed.
     */
    public boolean isInactivityTimeout(Http.Session session) {
        try {
            String lastActivityTimeStr = session.getOptional(SESSION_LAST_ACTIVITY_TIME).orElseThrow(IllegalArgumentException::new);
            Instant lastActivityTime = Instant.ofEpochMilli(Long.parseLong(lastActivityTimeStr));
            Instant now = Instant.now();
            Instant allowedUntil = lastActivityTime.plus(Common.getUserSessionInactivity(), ChronoUnit.MINUTES);
            return allowedUntil.isBefore(now);
        } catch (Exception e) {
            LOGGER.error(".isInactivityTimeout: " + e.getMessage());
            // In case of any exception: timeout
            return true;
        }
    }

    /**
     * Returns the URL of the page the user visited last - or the URL of the home page.
     */
    public String getRedirectPageAfterSignin(User user) {
        return !Strings.isNullOrEmpty(user.getLastVisitedPageUrl())
                ? Common.getJatosUrlBasePath() + user.getLastVisitedPageUrl()
                : controllers.gui.routes.Home.home().url();
    }

}
