package services.publix;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import javax.inject.Singleton;

import controllers.publix.Publix;
import exceptions.publix.BadRequestPublixException;
import exceptions.publix.MalformedIdCookieException;
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

/**
 * Service class that generates, extracts and discards JATOS' ID cookies. An ID
 * cookie is used by the JATOS server to tell jatos.js about several IDs the
 * current component run is having (e.g. worker ID, study ID, study result ID).
 * This cookie is created when the study run is started and discarded when it's
 * done.
 * 
 * @author Kristian Lange
 */
@Singleton
public class IdCookieAccessor {

	private static final ALogger LOGGER = Logger.of(IdCookieAccessor.class);

	/**
	 * Extracts the ID cookie from all the given cookies. Stores it and it's
	 * key-value Map in global variables.
	 * 
	 * @return
	 * @throws MalformedIdCookieException
	 * @throws BadRequestPublixException
	 */
	public IdCookieContainer extractIdCookies() {
		Cookies cookies = Publix.request().cookies();
		IdCookieContainer idCookieContainer = new IdCookieContainer();
		for (Cookie cookie : cookies) {
			if (cookie.name().startsWith(IdCookie2.ID_COOKIE_NAME)) {
				try {
					IdCookie2 idCookie = new IdCookie2(cookie);
					idCookieContainer.add(idCookie);
				} catch (MalformedIdCookieException e) {
					LOGGER.warn(e.getMessage());
					Publix.response().discardCookie(cookie.name());
					LOGGER.warn("Deleted malformed JATOS ID cookie.");
				}
			}
		}
		return idCookieContainer;
	}

	/**
	 * Generates an ID cookie from the given parameters and sets it in the
	 * response object. Use Integer.MAX_VALUE as Max-Age of the cookie so it
	 * never expires.
	 */
	public void writeCookieToResponse(IdCookieContainer idCookieContainer,
			Batch batch, StudyResult studyResult,
			ComponentResult componentResult, Worker worker) {
		String cookieName = null;
		IdCookie2 cookie = idCookieContainer
				.findWithStudyResultId(studyResult.getId());
		if (cookie != null) {
			cookieName = cookie.getName();
		} else {
			cookieName = getNewCookieName(idCookieContainer);
		}

		IdCookie2 idCookie = buildIdCookie(cookieName, batch, studyResult,
				componentResult, worker);
		String cookieValue = asString(idCookie);
		Publix.response().setCookie(cookieName, cookieValue, Integer.MAX_VALUE);
	}

	private String getNewCookieName(IdCookieContainer idCookieContainer) {
		String cookieName;
		if (!idCookieContainer.isFull()) {
			// Write new cookie
			int newIndex = idCookieContainer.getNextCookieIndex();
			cookieName = IdCookie2.ID_COOKIE_NAME + "_" + newIndex;
		} else {
			cookieName = getOldestIdCookie(idCookieContainer).getName();
		}
		return cookieName;
	}

	public IdCookie2 getOldestIdCookie(IdCookieContainer idCookieContainer) {
		Long oldest = Long.MAX_VALUE;
		IdCookie2 oldestIdCookie = null;
		for (IdCookie2 idCookie : idCookieContainer) {
			Long creationTime = idCookie.getCreationTime();
			if (creationTime != null && creationTime < oldest) {
				oldest = creationTime;
				oldestIdCookie = idCookie;
			}
		}
		return oldestIdCookie;
	}

	public long getOldestIdCookieStudyResultId(
			IdCookieContainer idCookieContainer)
			throws MalformedIdCookieException {
		IdCookie2 oldest = getOldestIdCookie(idCookieContainer);
		return (oldest != null) ? oldest.getStudyResultId() : null;
	}

	private IdCookie2 buildIdCookie(String name, Batch batch,
			StudyResult studyResult, ComponentResult componentResult,
			Worker worker) {
		Study study = studyResult.getStudy();
		GroupResult groupResult = studyResult.getActiveGroupResult();
		IdCookie2 idCookie = new IdCookie2();
		idCookie.setBatchId(batch.getId());
		// ComponentResult might not yet be created
		if (componentResult != null) {
			Component component = componentResult.getComponent();
			idCookie.setComponentId(component.getId());
			idCookie.setComponentResultId(componentResult.getId());
			idCookie.setComponentPosition(
					study.getComponentPosition(component));
		}
		idCookie.setCreationTime(System.currentTimeMillis());
		idCookie.setGroupResultId(groupResult.getId());
		idCookie.setName(name);
		idCookie.setStudyId(study.getId());
		idCookie.setStudyResultId(studyResult.getId());
		idCookie.setWorkerId(worker.getId());
		idCookie.setWorkerType(worker.getWorkerType());
		return idCookie;
	}

	/**
	 * Generates the value that will be put in the ID cookie. An ID cookie has a
	 * worker ID, study ID, study result ID, group result ID (if not exist:
	 * null), component ID, component result ID and component position.
	 */
	private String generateIdCookieValue(Batch batch, StudyResult studyResult,
			ComponentResult componentResult, Worker worker) {
		Study study = studyResult.getStudy();
		GroupResult groupResult = studyResult.getActiveGroupResult();
		Map<String, String> cookieMap = new HashMap<>();
		cookieMap.put(IdCookie2.WORKER_ID, String.valueOf(worker.getId()));
		cookieMap.put(IdCookie2.WORKER_TYPE, worker.getWorkerType());
		cookieMap.put(IdCookie2.STUDY_ID, String.valueOf(study.getId()));
		cookieMap.put(IdCookie2.STUDY_RESULT_ID,
				String.valueOf(studyResult.getId()));
		String batchId = String.valueOf(batch.getId());
		cookieMap.put(IdCookie2.BATCH_ID, batchId);
		String groupResultId = (groupResult != null)
				? String.valueOf(groupResult.getId()) : "null";
		cookieMap.put(IdCookie2.GROUP_RESULT_ID, groupResultId);
		cookieMap.put(IdCookie2.CREATION_TIME,
				String.valueOf(System.currentTimeMillis()));
		if (componentResult != null) {
			Component component = componentResult.getComponent();
			cookieMap.put(IdCookie2.COMPONENT_RESULT_ID,
					String.valueOf(componentResult.getId()));
			cookieMap.put(IdCookie2.COMPONENT_POSITION,
					String.valueOf(study.getComponentPosition(component)));
			cookieMap.put(IdCookie2.COMPONENT_ID,
					String.valueOf(component.getId()));
		} else {

		}
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
			sb.append(IdCookie2.COOKIE_EQUALS);
			sb.append(entry.getValue());
			if (iterator.hasNext()) {
				sb.append(IdCookie2.COOKIE_AND);
			}
		}
		return sb.toString();
	}

	/**
	 * Takes a map and put its key-value-pairs into a string like in
	 * key=value&key=value&... (similar to a URL query).
	 */
	private String asString(IdCookie2 idCookie) {
		StringBuilder sb = new StringBuilder();
		appendCookieEntry(sb, IdCookie2.BATCH_ID, idCookie.getBatchId(), true);
		appendCookieEntry(sb, IdCookie2.COMPONENT_ID, idCookie.getComponentId(),
				true);
		appendCookieEntry(sb, IdCookie2.COMPONENT_POSITION,
				idCookie.getComponentPosition(), true);
		appendCookieEntry(sb, IdCookie2.COMPONENT_RESULT_ID,
				idCookie.getComponentResultId(), true);
		appendCookieEntry(sb, IdCookie2.CREATION_TIME,
				idCookie.getCreationTime(), true);
		appendCookieEntry(sb, IdCookie2.GROUP_RESULT_ID,
				idCookie.getGroupResultId(), true);
		appendCookieEntry(sb, IdCookie2.STUDY_ID, idCookie.getStudyId(), true);
		appendCookieEntry(sb, IdCookie2.STUDY_RESULT_ID,
				idCookie.getStudyResultId(), true);
		appendCookieEntry(sb, IdCookie2.WORKER_ID, idCookie.getWorkerId(),
				true);
		appendCookieEntry(sb, IdCookie2.WORKER_TYPE, idCookie.getWorkerType(),
				false);
		return sb.toString();
	}

	private StringBuilder appendCookieEntry(StringBuilder sb, String key,
			Object value, boolean cookieAnd) {
		sb.append(key);
		sb.append(IdCookie2.COOKIE_EQUALS);
		sb.append(value);
		if (cookieAnd) {
			sb.append(IdCookie2.COOKIE_AND);
		}
		return sb;
	}

	/**
	 * Discards the ID cookie if the given study result ID is equal to the one
	 * in the cookie. Throws a BadRequestPublixException if the cookie is
	 * malformed.
	 */
	public void discard(IdCookieContainer idCookieContainer,
			long studyResultId) {
		IdCookie2 idCookie = idCookieContainer
				.findWithStudyResultId(studyResultId);
		if (idCookie != null) {
			Publix.response().discardCookie(idCookie.getName());
		}
	}

}
