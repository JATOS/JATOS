package models.gui;

import models.common.User;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The UserSession is a data object stored in the cache. Most importantly it
 * stores the session IDs for a user. The session ID is stored in two different
 * locations: 1) in Play's session cookie, and 2) in the cache with this object.
 *
 * @author Kristian Lange
 */
public class UserSession {

    /**
     * Identifies an user (normalized username)
     */
    private final String username;

    /**
     * This map maps the request's remote address (usually an IP) to its session
     * ID. This ID is used in Play's session cookie to add an additional layer
     * of security. It identifies the current session. If a user logs out, the
     * session ID becomes null. This way the session can't be reused after the
     * user logged-out. The ID is stored together with its remote address so
     * this ID can't be used from a different remote address (session
     * hijacking).
     */
    private final Map<String, String> sessionIdMap = new ConcurrentHashMap<>();

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
