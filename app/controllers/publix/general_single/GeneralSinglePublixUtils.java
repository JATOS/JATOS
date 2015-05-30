package controllers.publix.general_single;

import models.StudyModel;
import models.workers.GeneralSingleWorker;
import models.workers.Worker;
import persistance.ComponentDao;
import persistance.ComponentResultDao;
import persistance.StudyDao;
import persistance.StudyResultDao;
import persistance.workers.WorkerDao;
import play.mvc.Http.Cookie;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import controllers.publix.PublixErrorMessages;
import controllers.publix.PublixUtils;
import exceptions.publix.ForbiddenPublixException;
import exceptions.publix.PublixException;

/**
 * Special PublixUtils for GeneralSinglePublix
 * 
 * @author Kristian Lange
 */
@Singleton
public class GeneralSinglePublixUtils extends PublixUtils<GeneralSingleWorker> {

	private GeneralSingleErrorMessages errorMessages;

	@Inject
	GeneralSinglePublixUtils(GeneralSingleErrorMessages errorMessages,
			StudyDao studyDao, StudyResultDao studyResultDao,
			ComponentDao componentDao, ComponentResultDao componentResultDao,
			WorkerDao workerDao) {
		super(errorMessages, studyDao, studyResultDao, componentDao,
				componentResultDao, workerDao);
		this.errorMessages = errorMessages;
	}

	@Override
	public GeneralSingleWorker retrieveTypedWorker(String workerIdStr)
			throws PublixException {
		Worker worker = retrieveWorker(workerIdStr);
		if (!(worker instanceof GeneralSingleWorker)) {
			throw new ForbiddenPublixException(
					errorMessages.workerNotCorrectType(worker.getId()));
		}
		return (GeneralSingleWorker) worker;
	}

	@Override
	public void checkWorkerAllowedToStartStudy(GeneralSingleWorker worker,
			StudyModel study) throws ForbiddenPublixException {
		checkWorkerAllowedToDoStudy(worker, study);
	}

	@Override
	public void checkWorkerAllowedToDoStudy(GeneralSingleWorker worker,
			StudyModel study) throws ForbiddenPublixException {
		if (!study.hasAllowedWorker(worker.getWorkerType())) {
			throw new ForbiddenPublixException(
					errorMessages.workerTypeNotAllowed(worker.getUIWorkerType()));
		}
		// General single workers can't repeat the same study
		if (finishedStudyAlready(worker, study)) {
			throw new ForbiddenPublixException(
					PublixErrorMessages.STUDY_CAN_BE_DONE_ONLY_ONCE);
		}
	}

	/**
	 * Check if study was done before. Throws an ForbiddenPublixException if
	 * this study's UUID is in the cookie. 
	 */
	public void checkStudyInCookie(StudyModel study, Cookie cookie)
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
	public String addStudyToCookie(StudyModel study, Cookie cookie) {
		String value;
		if (cookie != null) {
			value = cookie.value() + "," + study.getUuid();
		} else {
			value = study.getUuid();
		}
		return value;
	}

}
