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

	public static final String WORKER_ID = "workerId";
	public static final String WORKER_TYPE = "workerType";
	public static final String BATCH_ID = "batchId";
	public static final String GROUP_RESULT_ID = "groupResultId";
	public static final String STUDY_ID = "studyId";
	public static final String STUDY_RESULT_ID = "studyResultId";
	public static final String COMPONENT_ID = "componentId";
	public static final String COMPONENT_RESULT_ID = "componentResultId";
	public static final String COMPONENT_POSITION = "componentPos";
	public static final String CREATION_TIME = "creationTime";

	public static final String ID_COOKIE_NAME = "JATOS_IDS";
	public static final String COOKIE_EQUALS = "=";
	public static final String COOKIE_AND = "&";

	private String name;
	private Long workerId;
	private String workerType;
	private Long batchId;
	private Long groupResultId;
	private Long studyId;
	private Long studyResultId;
	private Long componentId;
	private Long componentResultId;
	private Integer componentPosition;
	private Long creationTime;

	private Cookie idCookie;

	public IdCookie2(Cookie cookie) throws MalformedIdCookieException {
		Map<String, String> cookieMap = getCookiesKeyValuePairs(cookie);
		this.name = cookie.name();
		this.workerId = getValueAsLong(cookieMap, WORKER_ID);
		this.workerType = getValueAsString(cookieMap, WORKER_TYPE);
		this.batchId = getValueAsLong(cookieMap, BATCH_ID);
		this.groupResultId = getValueAsLong(cookieMap, GROUP_RESULT_ID);
		this.studyId = getValueAsLong(cookieMap, STUDY_ID);
		this.studyResultId = getValueAsLong(cookieMap, STUDY_RESULT_ID);
		this.componentId = getValueAsLong(cookieMap, COMPONENT_ID);
		this.componentResultId = getValueAsLong(cookieMap, COMPONENT_RESULT_ID);
		this.componentPosition = getValueAsInt(cookieMap, COMPONENT_POSITION);
		this.creationTime = getValueAsLong(cookieMap, CREATION_TIME);
	}

	public IdCookie2() {
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
	 * Searches the ID cookie for the given key and returns the corresponding
	 * value. Throws a BadRequestPublixException if the key does not exist.
	 */
	private String getValueAsString(Map<String, String> cookieMap, String key)
			throws MalformedIdCookieException {
		String value = cookieMap.get(key);
		if (value == null) {
			throw new MalformedIdCookieException(PublixErrorMessages
					.couldntExtractFromIdCookie(idCookie.name(), key));
		}
		return value;
	}

	/**
	 * Searches the ID cookie for the given key and returns the corresponding
	 * value. Throws a BadRequestPublixException if the cookie is malformed.
	 */
	private long getValueAsLong(Map<String, String> cookieMap, String key)
			throws MalformedIdCookieException {
		String valueStr = cookieMap.get(key);
		try {
			return Long.valueOf(valueStr);
		} catch (Exception e) {
			throw new MalformedIdCookieException(PublixErrorMessages
					.couldntExtractFromIdCookie(idCookie.name(), key));
		}
	}
	
	/**
	 * Searches the ID cookie for the given key and returns the corresponding
	 * value. Throws a BadRequestPublixException if the cookie is malformed.
	 */
	private int getValueAsInt(Map<String, String> cookieMap, String key)
			throws MalformedIdCookieException {
		String valueStr = cookieMap.get(key);
		try {
			return Integer.valueOf(valueStr);
		} catch (Exception e) {
			throw new MalformedIdCookieException(PublixErrorMessages
					.couldntExtractFromIdCookie(idCookie.name(), key));
		}
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Long getWorkerId() {
		return workerId;
	}

	public void setWorkerId(Long workerId) {
		this.workerId = workerId;
	}

	public String getWorkerType() {
		return workerType;
	}

	public void setWorkerType(String workerType) {
		this.workerType = workerType;
	}

	public Long getBatchId() {
		return batchId;
	}

	public void setBatchId(Long batchId) {
		this.batchId = batchId;
	}

	public Long getGroupResultId() {
		return groupResultId;
	}

	public void setGroupResultId(Long groupResultId) {
		this.groupResultId = groupResultId;
	}

	public Long getStudyId() {
		return studyId;
	}

	public void setStudyId(Long studyId) {
		this.studyId = studyId;
	}

	public Long getStudyResultId() {
		return studyResultId;
	}

	public void setStudyResultId(Long studyResultId) {
		this.studyResultId = studyResultId;
	}

	public Long getComponentId() {
		return componentId;
	}

	public void setComponentId(Long componentId) {
		this.componentId = componentId;
	}

	public Long getComponentResultId() {
		return componentResultId;
	}

	public void setComponentResultId(Long componentResultId) {
		this.componentResultId = componentResultId;
	}

	public Integer getComponentPosition() {
		return componentPosition;
	}

	public void setComponentPosition(Integer componentPosition) {
		this.componentPosition = componentPosition;
	}

	public Long getCreationTime() {
		return creationTime;
	}

	public void setCreationTime(Long creationTime) {
		this.creationTime = creationTime;
	}

}
