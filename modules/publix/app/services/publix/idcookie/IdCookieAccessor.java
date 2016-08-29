package services.publix.idcookie;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Singleton;

import controllers.publix.Publix;
import controllers.publix.workers.JatosPublix.JatosRun;
import general.common.RequestScope;
import play.Logger;
import play.Logger.ALogger;
import play.mvc.Http.Cookie;
import play.mvc.Http.Cookies;
import services.publix.HttpHelpers;
import services.publix.PublixErrorMessages;
import services.publix.idcookie.exception.IdCookieAlreadyExistsException;
import services.publix.idcookie.exception.IdCookieCollectionFullException;
import services.publix.idcookie.exception.IdCookieMalformedException;

/**
 * This class offers a simple interface to extract, write and discard IdCookies.
 * 
 * Internally this class accesses JATOS' ID cookies in the HTTP Request or
 * Response. It stores the extracted {@link IdCookies} in a
 * {@link IdCookieCollection}. Additionally it puts the
 * {@link IdCookieCollection} in the {@link RequestScope} for easier retrieval
 * in subsequent calls within the same Request.
 * 
 * Each browser can run up to IdCookieCollection.MAX_ID_COOKIES ID studies at
 * the same time. This means that there are the same number of ID cookies stored
 * in the browser as studies are currently running (although part of them might
 * be abandoned).
 * 
 * @author Kristian Lange (2016)
 */
@Singleton
public class IdCookieAccessor {

	private static final ALogger LOGGER = Logger.of(IdCookieAccessor.class);

	protected static final String COOKIE_EQUALS = "=";
	protected static final String COOKIE_AND = "&";

	/**
	 * Returns the IdCookieCollection containing all IdCookies of this Request.
	 * Additionally it stores this idCookieCollection in the RequestScope. All
	 * subsequent calls of this method will get the IdCookieCollection from the
	 * RequestScope.
	 */
	protected IdCookieCollection extract()
			throws IdCookieAlreadyExistsException {
		String requestScopeName = IdCookieCollection.class.getSimpleName();
		if (RequestScope.has(requestScopeName)) {
			return (IdCookieCollection) RequestScope.get(requestScopeName);
		} else {
			IdCookieCollection idCookieCollection = extractFromCookies(
					Publix.request().cookies());
			RequestScope.put(requestScopeName, idCookieCollection);
			return idCookieCollection;
		}
	}

	/**
	 * Extracts all ID cookies from all the HTTP cookies and stores them into an
	 * {@link IdCookieCollection}. If a cookie is malformed it is discarded
	 * right away (removed from the Response).
	 */
	private IdCookieCollection extractFromCookies(Cookies cookies)
			throws IdCookieAlreadyExistsException {
		IdCookieCollection idCookieCollection = new IdCookieCollection();
		for (Cookie cookie : cookies) {
			// Cookie names are case insensitive
			if (cookie.name().toLowerCase()
					.startsWith(IdCookie.ID_COOKIE_NAME.toLowerCase())) {
				try {
					IdCookie idCookie = buildIdCookie(cookie);
					idCookieCollection.add(idCookie);
				} catch (IdCookieMalformedException e) {
					LOGGER.warn(e.getMessage());
					Publix.response().discardCookie(cookie.name());
					LOGGER.warn("Deleted malformed JATOS ID cookie.");
				}
			}
		}
		return idCookieCollection;
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
				true, cookie.name()));
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
		idCookie.setStudyAssets(getValueAsString(cookieMap,
				IdCookie.STUDY_ASSETS, true, cookie.name()));
		idCookie.setJatosRun(valueOfJatosRun(cookieMap, cookie));
		idCookie.setCreationTime(getValueAsLong(cookieMap,
				IdCookie.CREATION_TIME, true, cookie.name()));
		return idCookie;
	}

	/**
	 * Maps the IdCookie value for a JATOS run to the enum {@link JatosRun}. If
	 * the value can't be matched to an instance of JatosRun then null is
	 * returned.
	 */
	private JatosRun valueOfJatosRun(Map<String, String> cookieMap,
			Cookie cookie) throws IdCookieMalformedException {
		try {
			return JatosRun.valueOf(getValueAsString(cookieMap,
					IdCookie.JATOS_RUN, false, cookie.name()));
		} catch (IllegalArgumentException | NullPointerException e) {
			return null;
		}
	}

	/**
	 * Returns the index of the ID cookie which is in the last char of it's
	 * name. If the last char is not a number than an IdCookieMalformedException
	 * is thrown.
	 */
	private int getCookieIndex(String name) throws IdCookieMalformedException {
		String lastChar = name.substring(name.length() - 1);
		try {
			return Integer.valueOf(lastChar);
		} catch (NumberFormatException e) {
			throw new IdCookieMalformedException(PublixErrorMessages
					.couldntExtractIndexFromIdCookieName(name));

		}
	}

	/**
	 * Extract and returns a Map with the given Cookie's key-value pairs.
	 * 
	 * @throws IdCookieMalformedException
	 */
	private Map<String, String> getCookiesKeyValuePairs(Cookie cookie)
			throws IdCookieMalformedException {
		Map<String, String> cookieKeyValuePairs = new HashMap<>();
		for (String pairStr : cookie.value().split(COOKIE_AND)) {
			addKeyValuePair(cookieKeyValuePairs, pairStr);
		}
		return cookieKeyValuePairs;
	}

	private void addKeyValuePair(Map<String, String> cookieKeyValuePairs,
			String pairStr) throws IdCookieMalformedException {
		String[] pairArray = pairStr.split(COOKIE_EQUALS);
		String key;
		String value;
		// Every pair should have 2 elements (a key and a value)
		if (pairArray.length == 0) {
			throw new IdCookieMalformedException(
					"Couldn't extract key from ID cookie.");
		} else if (pairArray.length == 1) {
			key = pairArray[0];
			value = "";
		} else if (pairArray.length == 2) {
			key = pairArray[0];
			value = pairArray[1];
		} else {
			throw new IdCookieMalformedException(
					"Wrong number of '&' in ID cookie.");
		}
		cookieKeyValuePairs.put(key, value);
	}

	/**
	 * Searches the given map the given key and returns the corresponding value
	 * as String. Throws a MalformedIdCookieException if the key doesn't exist.
	 * If strict is true and the value is "" it throws an
	 * MalformedIdCookieException.
	 */
	private String getValueAsString(Map<String, String> cookieMap, String key,
			boolean strict, String cookieName)
			throws IdCookieMalformedException {
		String valueStr;
		try {
			valueStr = HttpHelpers.urlDecode(cookieMap.get(key));
		} catch (Exception e) {
			throw new IdCookieMalformedException(PublixErrorMessages
					.couldntExtractFromIdCookie(cookieName, key));
		}
		if (strict && valueStr.trim() == "") {
			throw new IdCookieMalformedException(PublixErrorMessages
					.couldntExtractFromIdCookie(cookieName, key));
		}
		return valueStr;
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
	 *             Throws a MalformedIdCookieException if the a cookie value is
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
	 *             Throws a MalformedIdCookieException if the cookie value is
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
	protected void discard(long studyResultId)
			throws IdCookieAlreadyExistsException {
		IdCookieCollection idCookieCollection = extract();
		IdCookie idCookie = idCookieCollection
				.findWithStudyResultId(studyResultId);
		if (idCookie != null) {
			idCookieCollection.remove(idCookie);
			RequestScope.put(IdCookieCollection.class.getSimpleName(),
					idCookieCollection);
			Publix.response().discardCookie(idCookie.getName());
		}
	}

	/**
	 * Puts the given IdCookie in the Response. Additionally it stores the
	 * IdCookie in the RequestScope. Uses Integer.MAX_VALUE as Max-Age for the
	 * cookie so it never expires.
	 */
	protected void write(IdCookie newIdCookie)
			throws IdCookieAlreadyExistsException,
			IdCookieCollectionFullException {
		IdCookieCollection idCookieCollection = extract();

		// Put new IdCookie into Response
		String cookieValue = asCookieString(newIdCookie);
		Publix.response().setCookie(newIdCookie.getName(), cookieValue,
				Integer.MAX_VALUE, "/");

		idCookieCollection.put(newIdCookie);

		// Put changed idCookieCollection into RequestScope
		RequestScope.put(IdCookieCollection.class.getSimpleName(),
				idCookieCollection);
	}

	/**
	 * Takes an IdCookie and put its fields into a String for an cookie value:
	 * key=value&key=value&... (similar to a URL query).
	 */
	protected String asCookieString(IdCookie idCookie) {
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
		appendCookieEntry(sb, IdCookie.STUDY_ASSETS, idCookie.getStudyAssets(),
				true);
		appendCookieEntry(sb, IdCookie.JATOS_RUN, idCookie.getJatosRun(), true);
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
