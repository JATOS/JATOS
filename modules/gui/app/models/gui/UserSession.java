package models.gui;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The UserSession is a data object stored in the cache. Most importantly it
 * stores the session IDs for a user. The session ID is stored in two different
 * locations: 1) in Play's session cookie, and 2) in the cache with this object.
 * 
 * @author Kristian Lange (2017)
 */
public class UserSession {

	/**
	 * Identifies an user
	 */
	private String email;

	/**
	 * This map maps the request's host to its session ID. This ID is used in
	 * Play's session cookie to add an additional layer of security. It
	 * identifies the current session. If a user logs out, the session ID
	 * becomes null. This way the session can't be reused after the user
	 * logged-out. The ID is stored together with its host so this ID can't be
	 * used from a different host (session hijacking).
	 */
	private Map<String, String> sessionIdMap = new ConcurrentHashMap<String, String>();

	/**
	 * Container for the last 4 login times (no matter of failed or successful).
	 * It is sorted so the oldest in the first position and the youngest in the
	 * last.
	 */
	private Instant[] loginTimes = { Instant.MIN, Instant.MIN, Instant.MIN,
			Instant.MIN };

	public UserSession(String email) {
		this.email = email;
	}

	public String getEmail() {
		return email;
	}

	public String getSessionId(String host) {
		return sessionIdMap.get(host);
	}

	public void addSessionId(String host, String sessionId) {
		sessionIdMap.put(host, sessionId);
	}

	public String removeSessionId(String host) {
		return sessionIdMap.remove(host);
	}

	public Instant getOldestLoginTime() {
		return loginTimes[0];
	}

	public void overwriteOldestLoginTime(Instant loginTime) {
		this.loginTimes[0] = loginTime;
		// Sort again so the oldest in the first position
		Arrays.sort(loginTimes);
	}

}
