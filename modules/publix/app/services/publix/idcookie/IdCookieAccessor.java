package services.publix.idcookie;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Singleton;

import controllers.publix.Publix;
import exceptions.publix.IdCookieContainerFullException;
import exceptions.publix.IdCookieMalformedException;
import models.common.Batch;
import models.common.Component;
import models.common.ComponentResult;
import models.common.GroupResult;
import models.common.Study;
import models.common.StudyResult;
import models.common.workers.Worker;
import play.Logger;
import play.Logger.ALogger;
import play.mvc.Http.Cookie;
import play.mvc.Http.Cookies;
import services.publix.PublixErrorMessages;

/**
 * This class provides methods to retrieve IdCookies from the HTTP Request and
 * writes them into the HTTP Response. It stores the extracted IdCookies in a
 * CookieContainer.
 * 
 * Each browser can run up to 10 studies at the same time. This means that there
 * are up to 10 ID cookies stored in the browser. The ID cookies are
 * distinguished by the suffix which is a '_' and a number 0-9.
 * 
 * @author Kristian Lange (2016)
 */
@Singleton
public class IdCookieAccessor {

	private static final ALogger LOGGER = Logger.of(IdCookieAccessor.class);

	public static final String COOKIE_EQUALS = "=";
	public static final String COOKIE_AND = "&";

	/**
	 * Extracts the ID cookie from all the Request's cookies. Stores them into
	 * an {@link IdCookieContainer}. If a cookie is malformed it is discarded
	 * right away (removed from the Response.
	 */
	public IdCookieContainer extract() {
		Cookies cookies = Publix.request().cookies();
		IdCookieContainer idCookieContainer = new IdCookieContainer();
		for (Cookie cookie : cookies) {
			if (cookie.name().startsWith(IdCookie.ID_COOKIE_NAME)) {
				try {
					IdCookie idCookie = buildIdCookie(cookie);
					idCookieContainer.add(idCookie);
				} catch (IdCookieMalformedException e) {
					LOGGER.warn(e.getMessage());
					Publix.response().discardCookie(cookie.name());
					LOGGER.warn("Deleted malformed JATOS ID cookie.");
				}
			}
		}
		return idCookieContainer;
	}

	private IdCookie buildIdCookie(Cookie cookie)
			throws IdCookieMalformedException {
		IdCookie idCookie = new IdCookie();
		Map<String, String> cookieMap = getCookiesKeyValuePairs(cookie);
		idCookie.setName(cookie.name());
		idCookie.setIndex(getCookieIndex(cookie.name()));
		idCookie.setWorkerId(getValueAsLong(cookieMap, IdCookie.WORKER_ID, true,
				cookie.name()));
		idCookie.setWorkerType(getValueAsString(cookieMap, IdCookie.WORKER_TYPE,
				cookie.name()));
		idCookie.setBatchId(getValueAsLong(cookieMap, IdCookie.BATCH_ID, true,
				cookie.name()));
		idCookie.setGroupResultId(getValueAsLong(cookieMap,
				IdCookie.GROUP_RESULT_ID, false, cookie.name()));
		idCookie.setStudyId(getValueAsLong(cookieMap, IdCookie.STUDY_ID, true,
				cookie.name()));
		idCookie.setStudyResultId(getValueAsLong(cookieMap,
				IdCookie.STUDY_RESULT_ID, true, cookie.name()));
		idCookie.setComponentId(getValueAsLong(cookieMap, IdCookie.COMPONENT_ID,
				false, cookie.name()));
		idCookie.setComponentResultId(getValueAsLong(cookieMap,
				IdCookie.COMPONENT_RESULT_ID, false, cookie.name()));
		idCookie.setComponentPosition(getValueAsInt(cookieMap,
				IdCookie.COMPONENT_POSITION, false, cookie.name()));
		idCookie.setCreationTime(getValueAsLong(cookieMap,
				IdCookie.CREATION_TIME, true, cookie.name()));
		return idCookie;
	}

	/**
	 * Returns the index of the ID cookie which is in the last char of it's
	 * name. If the last char is not a number than an IdCookieMalformedException
	 * is thrown.
	 */
	private int getCookieIndex(String name)
			throws IdCookieMalformedException {
		char lastChar = name.charAt(name.length() - 1);
		int index = Character.getNumericValue(lastChar);
		if (index < 0) {
			throw new IdCookieMalformedException(PublixErrorMessages
					.couldntExtractIndexFromIdCookieName(name));

		}
		return Character.getNumericValue(lastChar);

	}
	
	/**
	 * Extract and returns a Map with the given Cookie's key-value pairs.
	 */
	private Map<String, String> getCookiesKeyValuePairs(Cookie cookie) {
		Map<String, String> cookieKeyValuePairs = new HashMap<>();
		for (String pair : cookie.value().split(COOKIE_AND)) {
			String[] pairArray = pair.split(COOKIE_EQUALS);
			cookieKeyValuePairs.put(pairArray[0], pairArray[1]);
		}
		return cookieKeyValuePairs;
	}

	/**
	 * Searches the given map the given key and returns the corresponding value
	 * as String. Throws a MalformedIdCookieException if the key doesn't exist.
	 */
	private String getValueAsString(Map<String, String> cookieMap, String key,
			String cookieName) throws IdCookieMalformedException {
		String value = cookieMap.get(key);
		if (value == null) {
			throw new IdCookieMalformedException(PublixErrorMessages
					.couldntExtractFromIdCookie(cookieName, key));
		}
		return value;
	}

	/**
	 * Searches the given map the given key and returns the corresponding value
	 * as Long. Does some simple validation.
	 * 
	 * @param cookieMap
	 *            Map with cookie's key-value pairs
	 * @param key
	 *            Key to extract the value from
	 * @param strict
	 *            If true it doesn't accept null values and throws an
	 *            MalformedIdCookieException. If false it just returns null.
	 * @param cookieName
	 *            Name of the cookie
	 * @return
	 * @throws MalformedIdCookieException
	 *             Throws a MalformedIdCookieException if the cookie is
	 *             malformed.
	 */
	private Long getValueAsLong(Map<String, String> cookieMap, String key,
			boolean strict, String cookieName)
			throws IdCookieMalformedException {
		String valueStr = cookieMap.get(key);
		if ((valueStr == null || valueStr.equals("null")) && !strict) {
			return null;
		}
		try {
			return Long.valueOf(valueStr);
		} catch (Exception e) {
			throw new IdCookieMalformedException(PublixErrorMessages
					.couldntExtractFromIdCookie(cookieName, key));
		}
	}

	/**
	 * Searches the given map the given key and returns the corresponding value
	 * as Integer. Does some simple validation.
	 * 
	 * @param cookieMap
	 *            Map with cookie's key-value pairs
	 * @param key
	 *            Key to extract the value from
	 * @param strict
	 *            If true it doesn't accept null values and throws an
	 *            MalformedIdCookieException. If false it just returns null.
	 * @param cookieName
	 *            Name of the cookie
	 * @return
	 * @throws MalformedIdCookieException
	 *             Throws a MalformedIdCookieException if the cookie is
	 *             malformed.
	 */
	private Integer getValueAsInt(Map<String, String> cookieMap, String key,
			boolean strict, String cookieName)
			throws IdCookieMalformedException {
		String valueStr = cookieMap.get(key);
		if ((valueStr == null || valueStr.equals("null")) && !strict) {
			return null;
		}
		try {
			return Integer.valueOf(valueStr);
		} catch (Exception e) {
			throw new IdCookieMalformedException(PublixErrorMessages
					.couldntExtractFromIdCookie(cookieName, key));
		}
	}

	/**
	 * Discards the ID cookie that corresponds to the given study result ID. If
	 * there is no such ID cookie it does nothing.
	 */
	public void discard(IdCookieContainer idCookieContainer,
			long studyResultId) {
		IdCookie idCookie = idCookieContainer
				.findWithStudyResultId(studyResultId);
		if (idCookie != null) {
			Publix.response().discardCookie(idCookie.getName());
		}
	}

	/**
	 * Generates an ID cookie from the given parameters and sets it in the
	 * Response object. Uses Integer.MAX_VALUE as Max-Age for the cookie so it
	 * never expires. Checks if there is an existing ID cookie with the same
	 * study result ID and if so overwrites it. If there isn't it checks if the
	 * max number of ID cookies is reached and if so overwrites the oldest one -
	 * or if not writes a new one.
	 * 
	 * @throws IdCookieContainerFullException
	 *             if the IdCookieContainer is full.
	 */
	public void write(IdCookieContainer idCookieContainer, Batch batch,
			StudyResult studyResult, ComponentResult componentResult,
			Worker worker) throws IdCookieContainerFullException {
		String newIdCookieName = null;
		String existingIdCookieName = getExistingIdCookieName(idCookieContainer,
				studyResult);
		if (existingIdCookieName != null) {
			newIdCookieName = existingIdCookieName;
		} else {
			newIdCookieName = getNewIdCookieName(idCookieContainer);
		}

		IdCookie newIdCookie = buildIdCookie(newIdCookieName, batch,
				studyResult, componentResult, worker);
		String cookieValue = asCookieString(newIdCookie);
		Publix.response().setCookie(newIdCookieName, cookieValue,
				Integer.MAX_VALUE, "/");
	}

	/**
	 * Checks if there is an IdCookie in the container with the given
	 * StudyResult's ID. Returns the IdCookie or null otherwise.
	 */
	private String getExistingIdCookieName(IdCookieContainer idCookieContainer,
			StudyResult studyResult) {
		String existingIdCookieName = null;
		IdCookie exitingIdCookie = idCookieContainer
				.findWithStudyResultId(studyResult.getId());
		if (exitingIdCookie != null) {
			existingIdCookieName = exitingIdCookie.getName();
		}
		return existingIdCookieName;
	}

	/**
	 * Generates the name for a new IdCookie: If the max number of IdCookies is
	 * reached it reuses the name of the oldest IdCookie. If not it creates a
	 * new name.
	 * 
	 * @throws IdCookieContainerFullException
	 */
	private String getNewIdCookieName(IdCookieContainer idCookieContainer)
			throws IdCookieContainerFullException {
		if (idCookieContainer.isFull()) {
			throw new IdCookieContainerFullException(
					PublixErrorMessages.IDCOOKIE_CONTAINER_FULL);
		}
		int newIndex = idCookieContainer.getNextAvailableIdCookieIndex();
		return IdCookie.ID_COOKIE_NAME + "_" + newIndex;
	}

	/**
	 * Builds an IdCookie from the given parameters. It accepts null values for
	 * ComponentResult and GroupResult (stored in StudyResult). All others must
	 * not be null.
	 */
	private IdCookie buildIdCookie(String name, Batch batch,
			StudyResult studyResult, ComponentResult componentResult,
			Worker worker) {
		IdCookie idCookie = new IdCookie();
		Study study = studyResult.getStudy();

		// ComponentResult might not yet be created
		if (componentResult != null) {
			Component component = componentResult.getComponent();
			idCookie.setComponentId(component.getId());
			idCookie.setComponentResultId(componentResult.getId());
			idCookie.setComponentPosition(
					study.getComponentPosition(component));
		}

		// Might not have a GroupResult because it's not a group study
		GroupResult groupResult = studyResult.getActiveGroupResult();
		if (groupResult != null) {
			idCookie.setGroupResultId(groupResult.getId());
		}

		idCookie.setBatchId(batch.getId());
		idCookie.setCreationTime(System.currentTimeMillis());
		idCookie.setName(name);
		idCookie.setStudyId(study.getId());
		idCookie.setStudyResultId(studyResult.getId());
		idCookie.setWorkerId(worker.getId());
		idCookie.setWorkerType(worker.getWorkerType());
		return idCookie;
	}

	/**
	 * Takes an IdCookie and put its fields into a String for an cookie value:
	 * key=value&key=value&... (similar to a URL query).
	 */
	private String asCookieString(IdCookie idCookie) {
		StringBuilder sb = new StringBuilder();
		appendCookieEntry(sb, IdCookie.BATCH_ID, idCookie.getBatchId(), true);
		appendCookieEntry(sb, IdCookie.COMPONENT_ID, idCookie.getComponentId(),
				true);
		appendCookieEntry(sb, IdCookie.COMPONENT_POSITION,
				idCookie.getComponentPosition(), true);
		appendCookieEntry(sb, IdCookie.COMPONENT_RESULT_ID,
				idCookie.getComponentResultId(), true);
		appendCookieEntry(sb, IdCookie.CREATION_TIME,
				idCookie.getCreationTime(), true);
		appendCookieEntry(sb, IdCookie.GROUP_RESULT_ID,
				idCookie.getGroupResultId(), true);
		appendCookieEntry(sb, IdCookie.STUDY_ID, idCookie.getStudyId(), true);
		appendCookieEntry(sb, IdCookie.STUDY_RESULT_ID,
				idCookie.getStudyResultId(), true);
		appendCookieEntry(sb, IdCookie.WORKER_ID, idCookie.getWorkerId(), true);
		appendCookieEntry(sb, IdCookie.WORKER_TYPE, idCookie.getWorkerType(),
				false);
		return sb.toString();
	}

	private StringBuilder appendCookieEntry(StringBuilder sb, String key,
			Object value, boolean cookieAnd) {
		sb.append(key);
		sb.append(COOKIE_EQUALS);
		sb.append(value);
		if (cookieAnd) {
			sb.append(COOKIE_AND);
		}
		return sb;
	}

}
