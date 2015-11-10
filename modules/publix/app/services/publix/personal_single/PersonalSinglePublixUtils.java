package services.publix.personal_single;

import javax.inject.Inject;
import javax.inject.Singleton;

import models.common.workers.PersonalSingleWorker;
import models.common.workers.Worker;
import services.publix.PublixUtils;
import daos.common.ComponentDao;
import daos.common.ComponentResultDao;
import daos.common.StudyDao;
import daos.common.StudyResultDao;
import daos.common.worker.WorkerDao;
import exceptions.publix.ForbiddenPublixException;

/**
 * PersonalSinglePublix' implementation of PublixUtils
 * 
 * @author Kristian Lange
 */
@Singleton
public class PersonalSinglePublixUtils extends
		PublixUtils<PersonalSingleWorker> {

	@Inject
	PersonalSinglePublixUtils(PersonalSingleErrorMessages errorMessages,
			StudyDao studyDao, StudyResultDao studyResultDao,
			ComponentDao componentDao, ComponentResultDao componentResultDao,
			WorkerDao workerDao) {
		super(errorMessages, studyDao, studyResultDao, componentDao,
				componentResultDao, workerDao);
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
