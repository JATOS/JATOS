package auth.gui;

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

/**
 * Service class around authentication and the session cookie. It works together with the
 * {@link Signin} controller and the @{@link auth.gui.AuthAction.Auth} annotation defined in {@link AuthAction}.
 *
 * @author Kristian Lange
 */
@SuppressWarnings("deprecation")
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
                throw new UnsupportedOperationException("Unsupported auth method " + user.getAuthMethod().name());
        }
    }

    private boolean authenticateViaDb(String normalizedUsername, String password) {
        String passwordHash = HashUtils.getHashMD5(password);
        return userDao.authenticate(normalizedUsername, passwordHash);
    }

    /**
     * Returns true if there were already 3 sign-in attempts within the last minute with this username
     */
    public boolean isRepeatedSigninAttempt(String normalizedUsername) {
        return loginAttemptDao.countLoginAttemptsOfLastMin(normalizedUsername) >= 3;
    }

    /**
     * Retrieves the signed-in user from Play's session. If a user is signed-in their username is stored in Play's
     * session cookie. With the username, a user can be retrieved from the database. Returns null if the session doesn't
     * contain a username or if the user doesn't exist in the database.
     * <p>
     * In most cases, getSignedinUser() is faster since it doesn't have to query the database.
     */
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
    public void writeSessionCookie(Http.Session session, String normalizedUsername) {
        session.put(SESSION_USERNAME, normalizedUsername);
        session.put(SESSION_SIGNIN_TIME, String.valueOf(Instant.now().toEpochMilli()));
        session.put(SESSION_LAST_ACTIVITY_TIME, String.valueOf(Instant.now().toEpochMilli()));
    }

    /**
     * Returns true if the session sign-in time as saved in Play's session cookie
     * is older than allowed.
     */
    public boolean isSessionTimeout(Http.Session session) {
        try {
            Instant signinTime = Instant.ofEpochMilli(Long.parseLong(session.get(SESSION_SIGNIN_TIME)));
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
            Instant lastActivityTime = Instant.ofEpochMilli(Long.parseLong(session.get(SESSION_LAST_ACTIVITY_TIME)));
            Instant now = Instant.now();
            Instant allowedUntil = lastActivityTime.plus(Common.getUserSessionInactivity(), ChronoUnit.MINUTES);
            return allowedUntil.isBefore(now);
        } catch (Exception e) {
            LOGGER.error(".isInactivityTimeout: " + e.getMessage());
            // In case of any exception: timeout
            return true;
        }
    }

}
