package auth.gui;

import models.common.User;
import play.cache.NamedCache;
import play.cache.SyncCacheApi;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Optional;

/**
 * This class stores and retrieves user session data from a cache.
 *
 * @author Kristian Lange
 */
@Singleton
public class UserSessionCacheAccessor {

    private final SyncCacheApi cache;

    @Inject
    UserSessionCacheAccessor(@NamedCache("user-session-cache") SyncCacheApi cache) {
        this.cache = cache;
    }

    /**
     * The UserSession is a data object stored in the cache. It's used to detect repeated login attempts and to get the last
     * seen time (the last time when a user did a request).
     */
    private static class UserSession {

        /**
         * Identifies a user (normalized username)
         */
        private final String username;

        /**
         * Container for the last 4 login times (no matter of failed or successful).
         * It is sorted so the oldest in the first position and the youngest in the
         * last.
         */
        private final Instant[] loginTimes = { Instant.MIN, Instant.MIN, Instant.MIN, Instant.MIN };

        /**
         * Time when the user did the last action
         */
        private Instant lastSeen = Instant.EPOCH;

        public UserSession(String username) {
            this.username = User.normalizeUsername(username);
        }

        public String getUsername() {
            return username;
        }

        public Instant getOldestLoginTime() {
            return loginTimes[0];
        }

        public void overwriteOldestLoginTime(Instant loginTime) {
            this.loginTimes[0] = loginTime;
            // Sort again so the oldest in the first position
            Arrays.sort(loginTimes);
        }

        public Instant getLastSeen() {
            return lastSeen;
        }

        public void setLastSeen(Instant lastSeen) {
            this.lastSeen = lastSeen;
        }

    }

    public boolean isRepeatedLoginAttempt(String normalizedUsername) {
        UserSession userSession = findOrAdd(normalizedUsername);
        Instant oldest = userSession.getOldestLoginTime();
        return !oldest.equals(Instant.MIN) && oldest.plus(1, ChronoUnit.MINUTES).isAfter(Instant.now());
    }

    public void addLoginAttempt(String normalizedUsername) {
        UserSession userSession = findOrAdd(normalizedUsername);
        userSession.overwriteOldestLoginTime(Instant.now());
    }

    public void add(String normalizedUsername) {
        findOrAdd(normalizedUsername);
    }

    public void update(String normalizedUsername) throws IllegalAccessException {
        Optional<UserSession> userSession = cache.getOptional(normalizedUsername);
        if (userSession.isPresent()) {
            save(userSession.get());
        } else {
            throw new IllegalAccessException("User not in user session cache");
        }
    }

    private UserSession findOrAdd(String normalizedUsername) {
        Optional<UserSession> userSession = cache.getOptional(normalizedUsername);
        if (!userSession.isPresent()) {
            userSession = Optional.of(new UserSession(normalizedUsername));
            save(userSession.get());
        }
        return userSession.get();
    }

    private void save(UserSession userSession) {
        cache.set(userSession.getUsername(), userSession);
    }

    /**
     * Gets the time of the last activity of the given user.
     */
    public Optional<Instant> getLastSeen(String normalizedUsername) {
        Optional<Object> cacheOptional = cache.getOptional(normalizedUsername);
        // This is weird! Getting the Optional<UserSession> directly without instanceof sometimes leads to
        // 'java.lang.ClassCastException: java.lang.String cannot be cast to models.gui.UserSession'
        if (cacheOptional.isPresent() && cacheOptional.get() instanceof UserSession) {
            UserSession userSession = (UserSession) cacheOptional.get();
            return Optional.of(userSession.getLastSeen());
        } else {
            return Optional.empty();
        }
    }

    public void setLastSeen(String normalizedUsername) {
        Optional<UserSession> userSession = cache.getOptional(normalizedUsername);
        userSession.ifPresent(session -> session.setLastSeen(Instant.now()));
    }

}
