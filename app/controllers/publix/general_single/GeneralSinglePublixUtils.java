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

import controllers.publix.Publix;
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

	public void checkAllowedToDoStudy(StudyModel study)
			throws ForbiddenPublixException {
		// Check if study was done before - cookie has the study id stored
		Cookie cookie = Publix.request().cookie(GeneralSinglePublix.COOKIE);
		if (cookie != null) {
			String[] studyIdArray = cookie.value().split(",");
			for (String idStr : studyIdArray) {
				if (study.getId().toString().equals(idStr)) {
					throw new ForbiddenPublixException(
							PublixErrorMessages.STUDY_CAN_BE_DONE_ONLY_ONCE);
				}
			}
		}
	}

	public void addStudyToCookie(StudyModel study) {
		Cookie cookie = Publix.request().cookie(GeneralSinglePublix.COOKIE);
		String value;
		if (cookie != null) {
			value = cookie.value() + "," + study.getId();
		} else {
			value = study.getId().toString();
		}
		Publix.response().setCookie(GeneralSinglePublix.COOKIE, value);
	}

}
