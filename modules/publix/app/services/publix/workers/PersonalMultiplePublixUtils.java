package services.publix.workers;

import javax.inject.Inject;
import javax.inject.Singleton;

import daos.common.BatchDao;
import daos.common.ComponentDao;
import daos.common.ComponentResultDao;
import daos.common.StudyDao;
import daos.common.StudyResultDao;
import daos.common.worker.WorkerDao;
import exceptions.publix.ForbiddenPublixException;
import models.common.workers.PersonalMultipleWorker;
import models.common.workers.Worker;
import services.publix.PublixUtils;
import services.publix.ResultCreator;

/**
 * PersonalMultiplePublix' implementation of PublixUtils
 * 
 * @author Kristian Lange
 */
@Singleton
public class PersonalMultiplePublixUtils
		extends PublixUtils<PersonalMultipleWorker> {

	@Inject
	PersonalMultiplePublixUtils(ResultCreator resultCreator,
			PersonalMultipleErrorMessages errorMessages, StudyDao studyDao,
			StudyResultDao studyResultDao, ComponentDao componentDao,
			ComponentResultDao componentResultDao, WorkerDao workerDao,
			BatchDao batchDao) {
		super(resultCreator, errorMessages, studyDao, studyResultDao,
				componentDao, componentResultDao, workerDao, batchDao);
	}

	@Override
	public PersonalMultipleWorker retrieveTypedWorker(String workerIdStr)
			throws ForbiddenPublixException {
		Worker worker = retrieveWorker(workerIdStr);
		if (!(worker instanceof PersonalMultipleWorker)) {
			throw new ForbiddenPublixException(
					errorMessages.workerNotCorrectType(worker.getId()));
		}
		return (PersonalMultipleWorker) worker;
	}

}
