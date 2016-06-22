package services.publix;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import controllers.publix.Publix;
import exceptions.publix.BadRequestPublixException;
import models.common.Batch;
import models.common.Component;
import models.common.ComponentResult;
import models.common.GroupResult;
import models.common.Study;
import models.common.StudyResult;
import models.common.workers.Worker;
import play.mvc.Http.Cookie;
import play.mvc.Http.Cookies;

/**
 * Service class that generates, extracts and discards JATOS' ID cookies. An ID
 * cookie is used by the JATOS server to tell jatos.js about several IDs the
 * current component run is having (e.g. worker ID, study ID, study result ID).
 * This cookie is created when the study run is started and discarded when it's
 * done.
 * 
 * @author Kristian Lange
 */
public class IdCookie {

	public static final String ID_COOKIE_NAME = "JATOS_IDS";
	public static final String WORKER_ID = "workerId";
	public static final String BATCH_ID = "batchId";
	public static final String GROUP_RESULT_ID = "groupResultId";
	public static final String STUDY_ID = "studyId";
	public static final String STUDY_RESULT_ID = "studyResultId";
	public static final String COMPONENT_ID = "componentId";
	public static final String COMPONENT_RESULT_ID = "componentResultId";
	public static final String COMPONENT_POSITION = "componentPos";

	private static final String COOKIE_EQUALS = "=";
	private static final String COOKIE_AND = "&";

	private Cookie idCookie;
	private Map<String, String> idCookieMap;

	public IdCookie() throws BadRequestPublixException {
		extractIdCookie(Publix.request().cookies());
	}

	/**
	 * Extracts the ID cookie from all the given cookies. Stores it and it's
	 * key-value Map in global variables.
	 */
	private void extractIdCookie(Cookies cookies) {
		for (Cookie cookie : cookies) {
			if (cookie.name().startsWith(ID_COOKIE_NAME)) {
				this.idCookie = cookie;
				this.idCookieMap = getCookiesKeyValuePairs(cookie);
			}
		}
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

	/**
	 * Returns true if this IdCookieManager has found an ID cookie.
	 */
	public boolean exists() {
		return idCookie != null;
	}

	/**
	 * Get study result from ID cookie. Throws a BadRequestPublixException if
	 * the cookie is malformed.
	 */
	public Long getStudyResultId() throws BadRequestPublixException {
		return getCookieContentValue(STUDY_RESULT_ID);
	}

	/**
	 * Searches the ID cookie for the given key and returns the corresponding
	 * value. Throws a BadRequestPublixException if the cookie is malformed.
	 */
	private Long getCookieContentValue(String key)
			throws BadRequestPublixException {
		String valueStr = idCookieMap.get(key);
		try {
			return Long.valueOf(valueStr);
		} catch (NumberFormatException e) {
			throw new BadRequestPublixException("JATOS cookie malformed.");
		}
	}

	/**
	 * Generates an ID cookie from the given parameters and sets it in the
	 * response object.
	 */
	public void writeToResponse(Batch batch, StudyResult studyResult,
			ComponentResult componentResult, Worker worker) {
		String value = generateIdCookieValue(batch, studyResult,
				componentResult, worker);
		if (idCookie != null) {
			Publix.response().discardCookie(idCookie.name());
		}
		Publix.response().setCookie(ID_COOKIE_NAME, value);
	}

	/**
	 * Generates the value that will be put in the ID cookie. An ID cookie has a
	 * worker ID, study ID, study result ID, group result ID (if not exist:
	 * null), component ID, component result ID and component position.
	 */
	public String generateIdCookieValue(Batch batch, StudyResult studyResult,
			ComponentResult componentResult, Worker worker) {
		Study study = studyResult.getStudy();
		GroupResult groupResult = studyResult.getActiveGroupResult();
		Component component = componentResult.getComponent();
		Map<String, String> cookieMap = new HashMap<>();
		cookieMap.put(WORKER_ID, String.valueOf(worker.getId()));
		cookieMap.put(STUDY_ID, String.valueOf(study.getId()));
		cookieMap.put(STUDY_RESULT_ID, String.valueOf(studyResult.getId()));
		String batchId = String.valueOf(batch.getId());
		cookieMap.put(BATCH_ID, batchId);
		String groupResultId = (groupResult != null)
				? String.valueOf(groupResult.getId()) : "null";
		cookieMap.put(GROUP_RESULT_ID, groupResultId);
		cookieMap.put(COMPONENT_ID, String.valueOf(component.getId()));
		cookieMap.put(COMPONENT_RESULT_ID,
				String.valueOf(componentResult.getId()));
		cookieMap.put(COMPONENT_POSITION,
				String.valueOf(study.getComponentPosition(component)));
		return generateCookieString(cookieMap);
	}

	/**
	 * Takes a map and put its key-value-pairs into a string like in
	 * key=value&key=value&... (similar to a URL query).
	 */
	private String generateCookieString(Map<String, String> cookieMap) {
		StringBuilder sb = new StringBuilder();
		Iterator<Entry<String, String>> iterator = cookieMap.entrySet()
				.iterator();
		while (iterator.hasNext()) {
			Entry<String, String> entry = iterator.next();
			sb.append(entry.getKey());
			sb.append(COOKIE_EQUALS);
			sb.append(entry.getValue());
			if (iterator.hasNext()) {
				sb.append(COOKIE_AND);
			}
		}
		return sb.toString();
	}

	/**
	 * Discards the ID cookie if the given study result ID is equal to the one
	 * in the cookie. Throws a BadRequestPublixException if the cookie is
	 * malformed.
	 */
	public void discard(long studyResultId)
			throws BadRequestPublixException {
		if (getStudyResultId().equals(studyResultId)) {
			Publix.response().discardCookie(idCookie.name());
		}
	}

}
