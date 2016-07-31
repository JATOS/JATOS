package services.publix;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import javax.inject.Singleton;

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
@Singleton
public class IdCookieService {

	/**
	 * Extracts the ID cookie from all the given cookies. Stores it and it's
	 * key-value Map in global variables.
	 * 
	 * @return
	 * @throws BadRequestPublixException
	 */
	public IdCookieContainer extractIdCookieList(Cookies cookies) {
		IdCookieContainer idCookieContainer = new IdCookieContainer();
		for (Cookie cookie : cookies) {
			if (cookie.name().startsWith(IdCookie2.ID_COOKIE_NAME)) {
				idCookieContainer.add(new IdCookie2(cookie));
			}
		}
		return idCookieContainer;
	}

	/**
	 * Generates an ID cookie from the given parameters and sets it in the
	 * response object. Use Integer.MAX_VALUE as Max-Age of the cookie so it
	 * never expires.
	 */
	public void writeToResponse(IdCookieContainer idCookieContainer,
			Batch batch, StudyResult studyResult,
			ComponentResult componentResult, Worker worker) {
		IdCookie2 oldestIdCookie = getOldestIdCookie(idCookieContainer);
		String cookieValue = generateIdCookieValue(batch, studyResult,
				componentResult, worker);
		Publix.response().setCookie(oldestIdCookie.getName(), cookieValue,
				Integer.MAX_VALUE);
	}

	private IdCookie2 getOldestIdCookie(IdCookieContainer idCookieContainer) {
		Long oldest = Long.MAX_VALUE;
		IdCookie2 oldestIdCookie = null;
		for (IdCookie2 idCookie : idCookieContainer) {
			try {
				Long creationTime = idCookie.getCreationTime();
				if (creationTime < oldest) {
					oldest = creationTime;
					oldestIdCookie = idCookie;
				}
			} catch (BadRequestPublixException e) {
				// Don't care here
			}
		}
		return oldestIdCookie;
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
		cookieMap.put(IdCookie2.WORKER_ID, String.valueOf(worker.getId()));
		cookieMap.put(IdCookie2.STUDY_ID, String.valueOf(study.getId()));
		cookieMap.put(IdCookie2.STUDY_RESULT_ID,
				String.valueOf(studyResult.getId()));
		String batchId = String.valueOf(batch.getId());
		cookieMap.put(IdCookie2.BATCH_ID, batchId);
		String groupResultId = (groupResult != null)
				? String.valueOf(groupResult.getId()) : "null";
		cookieMap.put(IdCookie2.GROUP_RESULT_ID, groupResultId);
		cookieMap.put(IdCookie2.COMPONENT_ID,
				String.valueOf(component.getId()));
		cookieMap.put(IdCookie2.COMPONENT_RESULT_ID,
				String.valueOf(componentResult.getId()));
		cookieMap.put(IdCookie2.COMPONENT_POSITION,
				String.valueOf(study.getComponentPosition(component)));
		cookieMap.put(IdCookie2.CREATION_TIME,
				String.valueOf(System.currentTimeMillis()));
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
	 * Discards the ID cookie if the given study result ID is equal to the one
	 * in the cookie. Throws a BadRequestPublixException if the cookie is
	 * malformed.
	 */
	public void discard(IdCookieContainer idCookieContainer, long studyResultId)
			throws BadRequestPublixException {
		IdCookie2 idCookie = idCookieContainer
				.getWithStudyResultId(studyResultId);
		if (idCookie != null) {
			Publix.response().discardCookie(idCookie.getName());
		}
	}

}
