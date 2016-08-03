package services.publix;

import java.util.HashMap;
import java.util.Map;

import exceptions.publix.MalformedIdCookieException;
import play.mvc.Http.Cookie;

/**
 * Service class that generates, extracts and discards JATOS' ID cookies. An ID
 * cookie is used by the JATOS server to tell jatos.js about several IDs the
 * current component run is having (e.g. worker ID, study ID, study result ID).
 * This cookie is created when the study run is started and discarded when it's
 * done.
 * 
 * @author Kristian Lange
 */
public class IdCookie2 {

	public static final String ID_COOKIE_NAME = "JATOS_IDS";
	public static final String WORKER_ID = "workerId";
	public static final String BATCH_ID = "batchId";
	public static final String GROUP_RESULT_ID = "groupResultId";
	public static final String STUDY_ID = "studyId";
	public static final String STUDY_RESULT_ID = "studyResultId";
	public static final String COMPONENT_ID = "componentId";
	public static final String COMPONENT_RESULT_ID = "componentResultId";
	public static final String COMPONENT_POSITION = "componentPos";
	public static final String CREATION_TIME = "creationTime";

	public static final String COOKIE_EQUALS = "=";
	public static final String COOKIE_AND = "&";

	private Cookie idCookie;
	private Map<String, String> idCookieMap;

	public IdCookie2(Cookie cookie) {
		this.idCookie = cookie;
		this.idCookieMap = getCookiesKeyValuePairs(cookie);
	}

	/**
	 * Extract and returns a Map with the given cookie's key-value pairs.
	 */
	private Map<String, String> getCookiesKeyValuePairs(Cookie cookie) {
		Map<String, String> cookieKeyValuePairs = new HashMap<>();
		for (String pair : cookie.value().split(COOKIE_AND)) {
			String[] pairArray = pair.split(COOKIE_EQUALS);
			cookieKeyValuePairs.put(pairArray[0], pairArray[1]);
		}
		return cookieKeyValuePairs;
	}

	public String getName() {
		return idCookie.name();
	}

	public Long getCreationTime() throws MalformedIdCookieException {
		return getCookieContentValue(CREATION_TIME);
	}

	/**
	 * Get study result from ID cookie. Throws a BadRequestPublixException if
	 * the cookie is malformed.
	 */
	public Long getStudyResultId() throws MalformedIdCookieException {
		return getCookieContentValue(STUDY_RESULT_ID);
	}

	/**
	 * Searches the ID cookie for the given key and returns the corresponding
	 * value. Throws a BadRequestPublixException if the cookie is malformed.
	 */
	private Long getCookieContentValue(String key)
			throws MalformedIdCookieException {
		String valueStr = idCookieMap.get(key);
		try {
			return Long.valueOf(valueStr);
		} catch (NumberFormatException e) {
			throw new MalformedIdCookieException(
					"Couldn't extract " + key + " from JATOS ID cookie "
							+ idCookie.name() + " as a Long.");
		}
	}

}
