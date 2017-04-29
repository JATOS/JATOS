package services.gui;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import models.gui.UserSession;
import play.cache.CacheApi;
import play.cache.NamedCache;

/**
 * This class stores and retrieves user session data from a cache.
 * 
 * @author Kristian Lange (2017)
 */
@Singleton
public class UserSessionCacheAccessor {

	private final CacheApi cache;

	@Inject
	UserSessionCacheAccessor(@NamedCache("user-session-cache") CacheApi cache) {
		this.cache = cache;
	}

	public String getUserSessionId(String email, String remoteAddress) {
		UserSession userSession = cache.get(email);
		if (userSession != null) {
			return userSession.getSessionId(remoteAddress);
		} else {
			return null;
		}
	}

	public void setUserSessionId(String email, String remoteAddress,
			String sessionId) {
		UserSession userSession = findOrCreateByEmail(email);
		userSession.addSessionId(remoteAddress, sessionId);
	}

	public boolean removeUserSessionId(String email, String remoteAddress) {
		UserSession userSession = cache.get(email);
		if (userSession != null) {
			// Only remove the session ID - leave the UserSession in the Cache
			String sessionId = userSession.removeSessionId(remoteAddress);
			return sessionId != null;
		} else {
			return false;
		}
	}

	public boolean isRepeatedLoginAttempt(String email) {
		UserSession userSession = findOrCreateByEmail(email);
		Instant oldest = userSession.getOldestLoginTime();
		return !oldest.equals(Instant.MIN)
				&& oldest.plus(1, ChronoUnit.MINUTES).isAfter(Instant.now());
	}

	public void addLoginAttempt(String email) {
		UserSession userSession = findOrCreateByEmail(email);
		userSession.overwriteOldestLoginTime(Instant.now());
	}

	private UserSession findOrCreateByEmail(String email) {
		UserSession userSession = cache.get(email);
		if (userSession == null) {
			userSession = new UserSession(email);
			save(userSession);
		}
		return userSession;
	}

	private void save(UserSession userSession) {
		cache.set(userSession.getEmail(), userSession);
	}

}
