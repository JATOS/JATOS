package controllers.publix.personal_multiple;

import models.StudyModel;
import models.workers.PMWorker;
import models.workers.Worker;
import persistance.ComponentDao;
import persistance.ComponentResultDao;
import persistance.StudyDao;
import persistance.StudyResultDao;
import persistance.workers.WorkerDao;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import controllers.publix.PublixUtils;
import exceptions.publix.ForbiddenPublixException;
import exceptions.publix.PublixException;

/**
 * Special PublixUtils for PMPublix
 * 
 * @author Kristian Lange
 */
@Singleton
public class PMPublixUtils extends PublixUtils<PMWorker> {

	private PMErrorMessages errorMessages;

	@Inject
	PMPublixUtils(PMErrorMessages errorMessages, StudyDao studyDao,
			StudyResultDao studyResultDao, ComponentDao componentDao,
			ComponentResultDao componentResultDao, WorkerDao workerDao) {
		super(errorMessages, studyDao, studyResultDao, componentDao,
				componentResultDao, workerDao);
		this.errorMessages = errorMessages;
	}

	@Override
	public PMWorker retrieveTypedWorker(String workerIdStr)
			throws PublixException {
		Worker worker = retrieveWorker(workerIdStr);
		if (!(worker instanceof PMWorker)) {
			throw new ForbiddenPublixException(
					errorMessages.workerNotCorrectType(worker.getId()));
		}
		return (PMWorker) worker;
	}

	@Override
	public void checkWorkerAllowedToStartStudy(PMWorker worker,
			StudyModel study) throws ForbiddenPublixException {
		checkWorkerAllowedToDoStudy(worker, study);
	}

	@Override
	public void checkWorkerAllowedToDoStudy(PMWorker worker,
			StudyModel study) throws ForbiddenPublixException {
		if (!study.hasAllowedWorker(worker.getWorkerType())) {
			throw new ForbiddenPublixException(
					errorMessages.workerTypeNotAllowed(worker.getUIWorkerType()));
		}
	}

}
