package controllers.publix.mt;

import models.StudyModel;
import models.workers.MTSandboxWorker;
import models.workers.MTWorker;
import models.workers.Worker;
import persistance.ComponentDao;
import persistance.ComponentResultDao;
import persistance.StudyDao;
import persistance.StudyResultDao;
import persistance.workers.WorkerDao;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import controllers.publix.PublixErrorMessages;
import controllers.publix.PublixUtils;
import exceptions.publix.ForbiddenPublixException;
import exceptions.publix.PublixException;

/**
 * Special PublixUtils for MTPublix (studies started via MTurk).
 * 
 * @author Kristian Lange
 */
@Singleton
public class MTPublixUtils extends PublixUtils<MTWorker> {

	private MTErrorMessages errorMessages;

	@Inject
	MTPublixUtils(MTErrorMessages errorMessages, StudyDao studyDao,
			StudyResultDao studyResultDao, ComponentDao componentDao,
			ComponentResultDao componentResultDao, WorkerDao workerDao) {
		super(errorMessages, studyDao, studyResultDao, componentDao,
				componentResultDao, workerDao);
		this.errorMessages = errorMessages;
	}

	@Override
	public MTWorker retrieveTypedWorker(String workerIdStr)
			throws PublixException {
		Worker worker = retrieveWorker(workerIdStr);
		if (!(worker instanceof MTWorker)) {
			throw new ForbiddenPublixException(
					errorMessages.workerNotCorrectType(worker.getId()));
		}
		return (MTWorker) worker;
	}

	@Override
	public void checkWorkerAllowedToStartStudy(MTWorker worker, StudyModel study)
			throws ForbiddenPublixException {
		if (!(worker instanceof MTSandboxWorker)
				&& didStudyAlready(worker, study)) {
			throw new ForbiddenPublixException(
					PublixErrorMessages.STUDY_CAN_BE_DONE_ONLY_ONCE);
		}
		checkWorkerAllowedToDoStudy(worker, study);
	}

	@Override
	public void checkWorkerAllowedToDoStudy(MTWorker worker, StudyModel study)
			throws ForbiddenPublixException {
		if (!study.hasAllowedWorker(worker.getWorkerType())) {
			throw new ForbiddenPublixException(
					errorMessages.workerTypeNotAllowed(worker.getUIWorkerType()));
		}
		// Sandbox workers can repeat studies
		if (worker instanceof MTSandboxWorker) {
			return;
		}
		// MTurk workers can't repeat studies
		if (finishedStudyAlready(worker, study)) {
			throw new ForbiddenPublixException(
					PublixErrorMessages.STUDY_CAN_BE_DONE_ONLY_ONCE);
		}
	}

}
