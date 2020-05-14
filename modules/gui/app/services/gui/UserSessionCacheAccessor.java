package services.gui;

import models.gui.UserSession;
import play.cache.NamedCache;
import play.cache.SyncCacheApi;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * This class stores and retrieves user session data from a cache.
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

    public String getUserSessionId(String normalizedUsername, String remoteAddress) {
        UserSession userSession = cache.get(normalizedUsername);
        if (userSession != null) {
            return userSession.getSessionId(remoteAddress);
        } else {
            return null;
        }
    }

    public void setUserSessionId(String normalizedUsername, String remoteAddress, String sessionId) {
        UserSession userSession = findOrCreateByUsername(normalizedUsername);
        userSession.addSessionId(remoteAddress, sessionId);
    }

    public boolean removeUserSessionId(String normalizedUsername, String remoteAddress) {
        UserSession userSession = cache.get(normalizedUsername);
        if (userSession != null) {
            // Only remove the session ID - leave the UserSession in the Cache
            String sessionId = userSession.removeSessionId(remoteAddress);
            return sessionId != null;
        } else {
            return false;
        }
    }

    public boolean isRepeatedLoginAttempt(String normalizedUsername) {
        UserSession userSession = findOrCreateByUsername(normalizedUsername);
        Instant oldest = userSession.getOldestLoginTime();
        return !oldest.equals(Instant.MIN) && oldest.plus(1, ChronoUnit.MINUTES).isAfter(Instant.now());
    }

    public void addLoginAttempt(String normalizedUsername) {
        UserSession userSession = findOrCreateByUsername(normalizedUsername);
        userSession.overwriteOldestLoginTime(Instant.now());
    }

    private UserSession findOrCreateByUsername(String normalizedUsername) {
        UserSession userSession = cache.get(normalizedUsername);
        if (userSession == null) {
            userSession = new UserSession(normalizedUsername);
            save(userSession);
        }
        return userSession;
    }

    private void save(UserSession userSession) {
        cache.set(userSession.getUsername(), userSession);
    }

}
