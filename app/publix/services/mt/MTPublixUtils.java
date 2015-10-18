package publix.services.mt;

import javax.inject.Inject;
import javax.inject.Singleton;

import models.workers.MTWorker;
import models.workers.Worker;
import persistance.ComponentDao;
import persistance.ComponentResultDao;
import persistance.StudyDao;
import persistance.StudyResultDao;
import persistance.workers.WorkerDao;
import publix.exceptions.ForbiddenPublixException;
import publix.services.PublixUtils;

/**
 * MTPublix' implementation of PublixUtils (studies started via MTurk).
 * 
 * @author Kristian Lange
 */
@Singleton
public class MTPublixUtils extends PublixUtils<MTWorker> {

	@Inject
	MTPublixUtils(MTErrorMessages errorMessages, StudyDao studyDao,
			StudyResultDao studyResultDao, ComponentDao componentDao,
			ComponentResultDao componentResultDao, WorkerDao workerDao) {
		super(errorMessages, studyDao, studyResultDao, componentDao,
				componentResultDao, workerDao);
	}

	@Override
	public MTWorker retrieveTypedWorker(String workerIdStr)
			throws ForbiddenPublixException {
		Worker worker = retrieveWorker(workerIdStr);
		if (!(worker instanceof MTWorker)) {
			throw new ForbiddenPublixException(
					errorMessages.workerNotCorrectType(worker.getId()));
		}
		return (MTWorker) worker;
	}

}
