package services.gui;

import models.gui.UserSession;
import play.cache.NamedCache;
import play.cache.SyncCacheApi;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * This class stores and retrieves UserSessions from a cache (in memory). It uses the user's email as key.
 *
 * @author Kristian Lange (2017)
 */
@Singleton
public class UserSessionCacheAccessor {

    private final SyncCacheApi cache;

    @Inject
    UserSessionCacheAccessor(@NamedCache("user-session-cache") SyncCacheApi cache) {
        this.cache = cache;
    }

    public String getUserSessionId(String email, String remoteAddress) {
        email = email.toLowerCase();
        UserSession userSession = cache.get(email);
        if (userSession != null) {
            return userSession.getSessionId(remoteAddress);
        } else {
            return null;
        }
    }

    public void setUserSessionId(String email, String remoteAddress, String sessionId) {
        email = email.toLowerCase();
        UserSession userSession = findOrCreateByEmail(email);
        userSession.addSessionId(remoteAddress, sessionId);
    }

    /**
     * Removes the session ID belonging to the given remoteAddress from the UserSession belonging to the given email
     *
     */
    public boolean removeUserSessionId(String email, String remoteAddress) {
        email = email.toLowerCase();
        UserSession userSession = cache.get(email);
        if (userSession != null) {
            // Only remove the session ID belonging to this remoteAddress - leave the UserSession in the Cache
            String sessionId = userSession.removeSessionId(remoteAddress);
            return sessionId != null;
        } else {
            return false;
        }
    }

    public boolean isRepeatedLoginAttempt(String email) {
        email = email.toLowerCase();
        UserSession userSession = findOrCreateByEmail(email);
        Instant oldest = userSession.getOldestLoginTime();
        return !oldest.equals(Instant.MIN) && oldest.plus(1, ChronoUnit.MINUTES).isAfter(Instant.now());
    }

    public void addLoginAttempt(String email) {
        email = email.toLowerCase();
        UserSession userSession = findOrCreateByEmail(email);
        userSession.overwriteOldestLoginTime(Instant.now());
    }

    private UserSession findOrCreateByEmail(String email) {
        email = email.toLowerCase();
        UserSession userSession = cache.get(email);
        if (userSession == null) {
            userSession = new UserSession();
            cache.set(email, userSession);
        }
        return userSession;
    }

}
