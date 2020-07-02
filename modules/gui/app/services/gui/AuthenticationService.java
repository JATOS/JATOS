package services.gui;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import controllers.gui.Authentication;
import controllers.gui.actionannotations.AuthenticationAction;
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
import javax.naming.AuthenticationException;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Hashtable;

/**
 * Service class around authentication, session cookie and session cache handling. It works together with the
 * {@link Authentication} controller and the @Authenticated annotation defined in {@link AuthenticationAction}.
 * <p>
 * If a user is authenticated (same password as stored in the database) a user session ID is generated and stored in
 * Play's session cookie and in the the cache. With each subsequent request this session is checked in the
 * AuthenticationAction.
 *
 * @author Kristian Lange
 */
@Singleton
public class AuthenticationService {

    private static final ALogger LOGGER = Logger.of(AuthenticationService.class);

    /**
     * Parameter name in Play's session cookie: It contains the username of the logged in user
     */
    public static final String SESSION_ID = "sessionID";

    /**
     * Parameter name in Play's session cookie: It contains the username of the logged in user
     */
    public static final String SESSION_USERNAME = "username";

    /**
     * Parameter name in Play's session cookie: It contains the timestamp of the login time
     */
    public static final String SESSION_LOGIN_TIME = "loginTime";

    /**
     * Parameter name in Play's session cookie: It contains a timestamp of the
     * time of the last HTTP request done by the browser with this cookie
     */
    public static final String SESSION_LAST_ACTIVITY_TIME = "lastActivityTime";

    /**
     * Key name used in RequestScope to store the logged-in User
     */
    public static final String LOGGED_IN_USER = "loggedInUser";

    private static final SecureRandom random = new SecureRandom();

    private final UserDao userDao;
    private final UserSessionCacheAccessor userSessionCacheAccessor;

    @Inject
    AuthenticationService(UserDao userDao, UserSessionCacheAccessor userSessionCacheAccessor) {
        this.userDao = userDao;
        this.userSessionCacheAccessor = userSessionCacheAccessor;
    }

    /**
     * Authenticates the user specified by the username with the given password.
     */
    public boolean authenticate(String normalizedUsername, String password) throws NamingException {
        if (password == null) return false;

        User user = userDao.findByUsername(normalizedUsername);
        if (user == null) return false;

        switch (user.getAuthMethod()) {
            case LDAP:
                return authenticateViaLdap(normalizedUsername, password);
            case DB:
                return authenticateViaDb(normalizedUsername, password);
            default:
                throw new UnsupportedOperationException("Unsupported auth method " + user.getAuthMethod().name());
        }
    }

    private boolean authenticateViaDb(String normalizedUsername, String password) {
        String passwordHash = HashUtils.getHashMD5(password);
        return userDao.authenticate(normalizedUsername, passwordHash);
    }

    /**
     * Authenticated via an external LDAP server and throws an NamingException if the LDAP server can't be reached or
     * the the LDAP URL or Base DN is wrong.
     */
    private boolean authenticateViaLdap(String normalizedUsername, String password) throws NamingException {
        Hashtable<String, String> props = new Hashtable<>();
        String principalName = "uid=" + normalizedUsername + "," + Common.getLdapBasedn();
        props.put(Context.SECURITY_PRINCIPAL, principalName);
        props.put(Context.SECURITY_CREDENTIALS, password);
        props.put(Context.PROVIDER_URL, Common.getLdapUrl());
        props.put("com.sun.jndi.ldap.read.timeout", String.valueOf(Common.getLdapTimeout()));
        props.put("com.sun.jndi.ldap.connect.timeout", String.valueOf(Common.getLdapTimeout()));
        DirContext context;
        try {
            context = new InitialDirContext(props);
            context.close();
            return true;
        } catch (AuthenticationException e) {
            return false;
        }
    }

    /**
     * Verifies and fetches an ID token from Google OAuth by sending an HTTP POST to Google. The actual authentication
     * happens in the frontend with Google's gapi library.
     */
    public GoogleIdToken fetchOAuthGoogleIdToken(String idTokenString) throws GeneralSecurityException, IOException {
        HttpTransport transport = new NetHttpTransport();
        JacksonFactory jacksonFactory = new JacksonFactory();
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(transport, jacksonFactory)
                .setAudience(Collections.singletonList(Common.getOauthGoogleClientId())).build();
        return verifier.verify(idTokenString);
    }

    /**
     * Checks the user session cache whether this user tries to login repeatedly
     */
    public boolean isRepeatedLoginAttempt(String normalizedUsername) {
        userSessionCacheAccessor.addLoginAttempt(normalizedUsername);
        return userSessionCacheAccessor.isRepeatedLoginAttempt(normalizedUsername);
    }

    /**
     * Retrieves the logged-in user from Play's session. If a user is logged-in their username is stored in Play's
     * session cookie. With the username a user can be retrieved from the database. Returns null if the session doesn't
     * contains an username or if the user doesn't exists in the database.
     * <p>
     * In most cases getLoggedInUser() is faster since it doesn't has to query the database.
     */
    public User getLoggedInUserBySessionCookie(Http.Session session) {
        String normalizedUsername = session.get(AuthenticationService.SESSION_USERNAME);
        User loggedInUser = null;
        if (normalizedUsername != null) {
            loggedInUser = userDao.findByUsername(normalizedUsername);
        }
        return loggedInUser;
    }

    /**
     * Gets the logged-in user from the RequestScope. It was put into the
     * RequestScope by the AuthenticationAction. Therefore this method works
     * only if you use the @Authenticated annotation at your action.
     */
    public User getLoggedInUser() {
        return (User) RequestScope.get(LOGGED_IN_USER);
    }

    /**
     * Prepares Play's session cookie and the user session cache for the user
     * with the given username to be logged-in. Does not authenticate the user (use
     * authenticate() for this).
     */
    public void writeSessionCookieAndSessionCache(Http.Session session, String normalizedUsername, String remoteAddress) {
        String sessionId = generateSessionId();
        userSessionCacheAccessor.setUserSessionId(normalizedUsername, remoteAddress, sessionId);
        session.put(SESSION_ID, sessionId);
        session.put(SESSION_USERNAME, normalizedUsername);
        session.put(SESSION_LOGIN_TIME, String.valueOf(Instant.now().toEpochMilli()));
        session.put(SESSION_LAST_ACTIVITY_TIME, String.valueOf(Instant.now().toEpochMilli()));
    }

    /**
     * Used StackOverflow for this session ID generator: "This works by choosing
     * 130 bits from a cryptographically secure random bit generator, and
     * encoding them in base-32" (http://stackoverflow.com/questions/41107)
     */
    private String generateSessionId() {
        return new BigInteger(130, random).toString(32);
    }

    /**
     * Refreshes the last activity timestamp in Play's session cookie. This is
     * usually done with each HTTP call of the user.
     */
    public void refreshSessionCookie(Http.Session session) {
        session.put(SESSION_LAST_ACTIVITY_TIME, String.valueOf(Instant.now().toEpochMilli()));
    }

    /**
     * Deletes the session cookie. This is usual done during a user logout.
     */
    public void clearSessionCookie(Http.Session session) {
        session.clear();
    }

    /**
     * Deletes the session cookie and removes the cache entry. This is usual
     * done during a user logout.
     */
    public void clearSessionCookieAndSessionCache(Http.Session session, String normalizedUsername, String remoteAddress) {
        userSessionCacheAccessor.removeUserSessionId(normalizedUsername, remoteAddress);
        session.clear();
    }

    /**
     * Checks the session ID stored in Play's session cookie whether it is the
     * same as stored in the cache during the last login.
     */
    public boolean isValidSessionId(Http.Session session, String normalizedUsername, String remoteAddress) {
        String cookieSessionId = session.get(SESSION_ID);
        String cachedSessionId = userSessionCacheAccessor.getUserSessionId(normalizedUsername, remoteAddress);
        return cookieSessionId != null && cookieSessionId.equals(cachedSessionId);
    }

    /**
     * Returns true if the session login time as saved in Play's session cookie
     * is older than allowed.
     */
    public boolean isSessionTimeout(Http.Session session) {
        try {
            Instant loginTime = Instant.ofEpochMilli(Long.parseLong(session.get(SESSION_LOGIN_TIME)));
            Instant now = Instant.now();
            Instant allowedUntil = loginTime.plus(Common.getUserSessionTimeout(), ChronoUnit.MINUTES);
            return allowedUntil.isBefore(now);
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
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
            LOGGER.error(e.getMessage());
            // In case of any exception: timeout
            return true;
        }
    }

}
