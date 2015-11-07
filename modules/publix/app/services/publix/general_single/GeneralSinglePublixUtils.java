package services.publix.general_single;

import javax.inject.Inject;
import javax.inject.Singleton;

import models.common.Study;
import models.common.workers.GeneralSingleWorker;
import models.common.workers.Worker;
import play.mvc.Http.Cookie;
import services.publix.PublixErrorMessages;
import services.publix.PublixUtils;
import daos.ComponentDao;
import daos.ComponentResultDao;
import daos.StudyDao;
import daos.StudyResultDao;
import daos.workers.WorkerDao;
import exceptions.publix.ForbiddenPublixException;

/**
 * GeneralSinglePublix' implementation of PublixUtils
 * 
 * @author Kristian Lange
 */
@Singleton
public class GeneralSinglePublixUtils extends PublixUtils<GeneralSingleWorker> {

	@Inject
	GeneralSinglePublixUtils(GeneralSingleErrorMessages errorMessages,
			StudyDao studyDao, StudyResultDao studyResultDao,
			ComponentDao componentDao, ComponentResultDao componentResultDao,
			WorkerDao workerDao) {
		super(errorMessages, studyDao, studyResultDao, componentDao,
				componentResultDao, workerDao);
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
	 * Check if study was done before. Throws an ForbiddenPublixException if
	 * this study's UUID is in the cookie.
	 */
	public void checkStudyInCookie(Study study, Cookie cookie)
			throws ForbiddenPublixException {
		if (cookie != null) {
			String[] studyUuidArray = cookie.value().split(",");
			for (String uuidStr : studyUuidArray) {
				if (study.getUuid().equals(uuidStr)) {
					throw new ForbiddenPublixException(
							PublixErrorMessages.STUDY_CAN_BE_DONE_ONLY_ONCE);
				}
			}
		}
	}

	/**
	 * Adds this study's UUID to this cookie's value and returns the value.
	 */
	public String addStudyToCookie(Study study, Cookie cookie) {
		String value;
		if (cookie != null) {
			value = cookie.value() + "," + study.getUuid();
		} else {
			value = study.getUuid();
		}
		return value;
	}

}
