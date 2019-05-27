package models.gui;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The UserSession is a data object to be stored in a in-memory cache. It is part of the authentication, especially the
 * ability to be authenticated from different computers at the same time. It stores session IDs and login attempt
 * timestamps.
 *
 * Each UserSession can store multiple pairs of remote address and session IDs. Each remote address (usually IP or host)
 * has its own session ID. The session ID is additionally stored in Play's session cookie and with each request it can
 * be checked whether Play's session cookie session ID is the same as stored in the UserSession that belongs to the
 * remote address. If a user logs out, the entry belonging the the remote address where the log-out came from is deleted
 * - but not the whole UserSession, since it can have other entries for other remote addresses. This way the user
 * session can't be reused after the user logged-out. This makes session hijacking very difficult.
 *
 * Then a UserSession stores timestamps of login attempts. Those are used to limit login attempts in a certain time
 * frame.
 *
 * @author Kristian Lange (2017, 2019)
 */
public class UserSession {

    /**
     * Map: request's remote address -> session ID
     */
    private final Map<String, String> sessionIdMap = new ConcurrentHashMap<>();

    /**
     * Container for the last 4 login attempt times (no matter of failed or successful). It is sorted so the oldest in
     * the first position and the youngest in the last.
     */
    private final Instant[] loginAttemptTimes = { Instant.MIN, Instant.MIN, Instant.MIN, Instant.MIN };

    public String getSessionId(String remoteAddress) {
        return sessionIdMap.get(remoteAddress);
    }

    public void addSessionId(String remoteAddress, String sessionId) {
        sessionIdMap.put(remoteAddress, sessionId);
    }

    public String removeSessionId(String remoteAddress) {
        return sessionIdMap.remove(remoteAddress);
    }

    public Instant getOldestLoginTime() {
        return loginAttemptTimes[0];
    }

    public void overwriteOldestLoginTime(Instant loginTime) {
        this.loginAttemptTimes[0] = loginTime;
        // Sort again so the oldest in the first position
        Arrays.sort(loginAttemptTimes);
    }

}
