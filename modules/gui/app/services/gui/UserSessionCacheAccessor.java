package services.gui;

import models.gui.UserSession;
import play.cache.NamedCache;
import play.cache.SyncCacheApi;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * This class stores and retrieves user session data from a cache.
 *
 * @author Kristian Lange (2017)
 */
@Singleton
public class UserSessionCacheAccessor {

    private final SyncCacheApi cache;

    @Inject
    UserSessionCacheAccessor(
            @NamedCache("user-session-cache")
                    SyncCacheApi cache) {
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

    /**
     * Gets the time of the last activity of the given user. If the user doesn't exist in the cache returns
     * Instant.EPOCH.
     */
    public Instant getLastSeen(String normalizedUsername) {
        Optional<Object> cacheOptional = cache.getOptional(normalizedUsername);
        // This is weird! Getting the Optional<UserSession> directly without instanceof sometimes leads to
        // 'java.lang.ClassCastException: java.lang.String cannot be cast to models.gui.UserSession'
        if (cacheOptional.isPresent() && cacheOptional.get() instanceof UserSession) {
            UserSession userSession = (UserSession) cacheOptional.get();
            return userSession.getLastSeen();
        } else {
            return Instant.EPOCH;
        }
    }

    public void setLastSeen(String normalizedUsername) {
        Optional<UserSession> userSession = cache.getOptional(normalizedUsername);
        userSession.ifPresent(session -> session.setLastSeen(Instant.now()));
    }

}
