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
import models.common.workers.PersonalSingleWorker;
import models.common.workers.Worker;
import services.publix.PublixUtils;
import services.publix.ResultCreator;

/**
 * PersonalSinglePublix' implementation of PublixUtils
 * 
 * @author Kristian Lange
 */
@Singleton
public class PersonalSinglePublixUtils
		extends PublixUtils<PersonalSingleWorker> {

	@Inject
	PersonalSinglePublixUtils(ResultCreator resultCreator,
			PersonalSingleErrorMessages errorMessages, StudyDao studyDao,
			StudyResultDao studyResultDao, ComponentDao componentDao,
			ComponentResultDao componentResultDao, WorkerDao workerDao,
			BatchDao batchDao) {
		super(resultCreator, errorMessages, studyDao, studyResultDao,
				componentDao, componentResultDao, workerDao, batchDao);
	}

	@Override
	public PersonalSingleWorker retrieveTypedWorker(String workerIdStr)
			throws ForbiddenPublixException {
		Worker worker = retrieveWorker(workerIdStr);
		if (!(worker instanceof PersonalSingleWorker)) {
			throw new ForbiddenPublixException(
					errorMessages.workerNotCorrectType(worker.getId()));
		}
		return (PersonalSingleWorker) worker;
	}

}
