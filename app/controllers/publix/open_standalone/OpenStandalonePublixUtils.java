package controllers.publix.open_standalone;

import models.StudyModel;
import models.workers.OpenStandaloneWorker;
import models.workers.Worker;
import play.mvc.Http.Cookie;
import utils.PersistanceUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import controllers.publix.Publix;
import controllers.publix.PublixErrorMessages;
import controllers.publix.PublixUtils;
import daos.ComponentDao;
import daos.ComponentResultDao;
import daos.StudyDao;
import daos.StudyResultDao;
import daos.workers.WorkerDao;
import exceptions.ForbiddenPublixException;
import exceptions.PublixException;

/**
 * Special PublixUtils for OpenStandalonePublix
 * 
 * @author Kristian Lange
 */
@Singleton
public class OpenStandalonePublixUtils extends
		PublixUtils<OpenStandaloneWorker> {

	private OpenStandaloneErrorMessages errorMessages;

	@Inject
	public OpenStandalonePublixUtils(OpenStandaloneErrorMessages errorMessages,
			PersistanceUtils persistanceUtils, StudyDao studyDao,
			ComponentDao componentDao, ComponentResultDao componentResultDao,
			StudyResultDao studyResultDao, WorkerDao workerDao) {
		super(errorMessages, persistanceUtils, studyDao, componentDao,
				componentResultDao, studyResultDao, workerDao);
		this.errorMessages = errorMessages;
	}

	@Override
	public OpenStandaloneWorker retrieveTypedWorker(String workerIdStr)
			throws PublixException {
		Worker worker = retrieveWorker(workerIdStr);
		if (!(worker instanceof OpenStandaloneWorker)) {
			throw new ForbiddenPublixException(
					errorMessages.workerNotCorrectType(worker.getId()));
		}
		return (OpenStandaloneWorker) worker;
	}

	@Override
	public void checkWorkerAllowedToStartStudy(OpenStandaloneWorker worker,
			StudyModel study) throws ForbiddenPublixException {
		checkWorkerAllowedToDoStudy(worker, study);
	}

	@Override
	public void checkWorkerAllowedToDoStudy(OpenStandaloneWorker worker,
			StudyModel study) throws ForbiddenPublixException {
		if (!study.hasAllowedWorker(worker.getWorkerType())) {
			throw new ForbiddenPublixException(
					errorMessages.workerTypeNotAllowed(worker.getUIWorkerType()));
		}
		// Standalone workers can't repeat the same study
		if (finishedStudyAlready(worker, study)) {
			throw new ForbiddenPublixException(
					PublixErrorMessages.STUDY_CAN_BE_DONE_ONLY_ONCE);
		}
	}

	public void checkAllowedToDoStudy(StudyModel study)
			throws ForbiddenPublixException {
		// Check if study was done before - cookie has the study id stored
		Cookie cookie = Publix.request().cookie(OpenStandalonePublix.COOKIE);
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
		Cookie cookie = Publix.request().cookie(OpenStandalonePublix.COOKIE);
		String value;
		if (cookie != null) {
			value = cookie.value() + "," + study.getId();
		} else {
			value = study.getId().toString();
		}
		Publix.response().setCookie(OpenStandalonePublix.COOKIE, value);
	}

}
