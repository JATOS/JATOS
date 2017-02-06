package services.publix.workers;

import javax.inject.Singleton;

import controllers.publix.Publix;
import controllers.publix.workers.GeneralSinglePublix;
import models.common.Study;
import models.common.workers.Worker;
import play.mvc.Http.Cookie;

/**
 * Manages the GeneralSingle cookie. This cookie exists only with GeneralSingle
 * workers. In this cookie is stored which studies where done in the browser
 * where the cookie is stored. This provides an easy way to check whether a
 * GeneralSingle worker tries to run the same study a second time (which is not
 * allowed). Note, that it is easy to get around this mechanism by deleting the
 * cookie in the browser.
 * 
 * A GeneralSingle cookie consists of a list of tuples storing the study ID and
 * worker ID. With the cookie's data it is possible to determine whether in this
 * browser this study was done already with a GeneralSingle worker and by which
 * worker it was done.
 * 
 * @author Kristian Lange (2016)
 */
@Singleton
public class GeneralSingleCookieService {

	public static final String COOKIE_NAME = "JATOS_GENERALSINGLE_UUIDS";

	/**
	 * Delimiter in cookie's string used to separate study's UUID from worker ID
	 */
	public static final String COOKIE_TUPLE_DELIMITER = "=";

	/**
	 * Delimiter in cookie's string used to separate entries (tuples of study
	 * UUID and worker ID)
	 */
	public static final String COOKIE_LIST_DELIMITER = "&";

	/**
	 * Returns the GeneralSingleWorker that belongs to the given study - or
	 * returns null if it doen't exists. If the study was run before the study
	 * UUID has been stored together with the worker ID in the cookie.
	 */
	public Long retrieveWorkerByStudy(Study study) {
		Cookie generalSingleCookie = Publix.request().cookies()
				.get(COOKIE_NAME);
		if (generalSingleCookie == null) {
			return null;
		}
		String[] studiesArray = generalSingleCookie.value()
				.split(COOKIE_LIST_DELIMITER);
		for (String studyStr : studiesArray) {
			Long workerId = retrieveWorkerIdFromCookieStudyString(study,
					studyStr);
			if (workerId != null) {
				return workerId;
			}
		}
		return null;
	}

	private Long retrieveWorkerIdFromCookieStudyString(Study study,
			String studyStr) {
		String[] uuidAndWorker = studyStr.split(COOKIE_TUPLE_DELIMITER);
		if (uuidAndWorker.length != 2) {
			// Ignore malformed cookie values
			return null;
		}
		String studyUuid = uuidAndWorker[0];
		try {
			Long workerId = Long.valueOf(uuidAndWorker[1]);
			if (study.getUuid().equals(studyUuid)) {
				// Only return the workerId if its UUID is the one of the given
				// study
				return workerId;
			}
		} catch (NumberFormatException e) {
			// Ignore malformed cookie values
			return null;
		}
		return null;
	}

	/**
	 * Sets the cookie in the response. The cookie will contain all
	 * GeneralSingle studies done by this worker so far and adds the given study
	 * (and worker). This cookie is HTTP only and has an expire date in the far
	 * future.
	 */
	public void set(Study study, Worker worker) {
		Cookie oldCookie = GeneralSinglePublix.request().cookie(COOKIE_NAME);
		String newCookieValue = addStudy(study, worker, oldCookie);
		Cookie newCookie = new Cookie(COOKIE_NAME, newCookieValue,
				Integer.MAX_VALUE, "/", null, false, true);
		Publix.response().setCookie(newCookie);
	}

	/**
	 * If the cookie is not null it adds a new tuple (study's UUID and worker
	 * ID) to the cookie's value and returns it. If the cookie is null (this
	 * client never did a general single run) just return the new cookie's value
	 * which is just the tuple.
	 */
	public String addStudy(Study study, Worker worker,
			Cookie generalSingleCookie) {
		if (generalSingleCookie != null) {
			return generalSingleCookie.value() + COOKIE_LIST_DELIMITER
					+ study.getUuid() + COOKIE_TUPLE_DELIMITER + worker.getId();
		} else {
			return study.getUuid() + COOKIE_TUPLE_DELIMITER + worker.getId();
		}
	}

}
