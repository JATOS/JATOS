package services.publix.mt;

import javax.inject.Inject;
import javax.inject.Singleton;

import models.common.workers.MTWorker;
import models.common.workers.Worker;
import services.publix.PublixUtils;
import daos.ComponentDao;
import daos.ComponentResultDao;
import daos.StudyDao;
import daos.StudyResultDao;
import daos.workers.WorkerDao;
import exceptions.publix.ForbiddenPublixException;

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
