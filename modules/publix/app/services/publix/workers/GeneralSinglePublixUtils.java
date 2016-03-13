package services.publix.workers;

import javax.inject.Inject;
import javax.inject.Singleton;

import controllers.publix.Publix;
import controllers.publix.workers.GeneralSinglePublix;
import daos.common.BatchDao;
import daos.common.ComponentDao;
import daos.common.ComponentResultDao;
import daos.common.StudyDao;
import daos.common.StudyResultDao;
import daos.common.worker.WorkerDao;
import exceptions.publix.ForbiddenPublixException;
import models.common.Study;
import models.common.workers.GeneralSingleWorker;
import models.common.workers.Worker;
import play.mvc.Http.Cookie;
import services.publix.PublixErrorMessages;
import services.publix.PublixUtils;
import services.publix.ResultCreator;

/**
 * GeneralSinglePublix' implementation of PublixUtils
 * 
 * @author Kristian Lange
 */
@Singleton
public class GeneralSinglePublixUtils extends PublixUtils<GeneralSingleWorker> {

	private static final String GENERAL_SINGLE_COOKIE_DELIMITER = ":";

	@Inject
	GeneralSinglePublixUtils(ResultCreator resultCreator,
			GeneralSingleErrorMessages errorMessages, StudyDao studyDao,
			StudyResultDao studyResultDao, ComponentDao componentDao,
			ComponentResultDao componentResultDao, WorkerDao workerDao,
			BatchDao batchDao) {
		super(resultCreator, errorMessages, studyDao, studyResultDao,
				componentDao, componentResultDao, workerDao, batchDao);
	}

	@Override
	public GeneralSingleWorker retrieveTypedWorker(String workerIdStr)
			throws ForbiddenPublixException {
		Worker worker = retrieveWorker(workerIdStr);
		if (!(worker instanceof GeneralSingleWorker)) {
			throw new ForbiddenPublixException(
					errorMessages.workerNotCorrectType(worker.getId()));
		}
		return (GeneralSingleWorker) worker;
	}

	/**
	 * Check if the given study was done before. Throws an
	 * ForbiddenPublixException if this study's UUID is in the cookie. This
	 * cookie is used only in General Single runs to determine whether in a
	 * browser a study was done already. This is a (simple) way of preventing
	 * workers doing the same study twice.
	 */
	public void checkStudyInGeneralSingleCookie(Study study)
			throws ForbiddenPublixException {
		Cookie generalSingleCookie = Publix.request()
				.cookie(GeneralSinglePublix.COOKIE);
		if (generalSingleCookie != null) {
			String[] studyUuidArray = generalSingleCookie.value()
					.split(GENERAL_SINGLE_COOKIE_DELIMITER);
			for (String uuidStr : studyUuidArray) {
				if (study.getUuid().equals(uuidStr)) {
					throw new ForbiddenPublixException(
							PublixErrorMessages.STUDY_CAN_BE_DONE_ONLY_ONCE);
				}
			}
		}
	}

	/**
	 * Adds this study's UUID to the cookie's value and sets the new cookie
	 * value in the response object. This cookie is used only in General Single
	 * runs to determine whether in a browser a study was done already. This is
	 * a (simple) way of preventing workers doing the same study twice.
	 */
	public void addStudyUuidToGeneralSingleCookie(Study study) {
		Cookie generalSingleCookie = Publix.request()
				.cookie(GeneralSinglePublix.COOKIE);
		String value;
		if (generalSingleCookie != null) {
			value = generalSingleCookie.value()
					+ GENERAL_SINGLE_COOKIE_DELIMITER + study.getUuid();
		} else {
			value = study.getUuid();
		}
		Publix.response().setCookie(GeneralSinglePublix.COOKIE, value);
	}

}
