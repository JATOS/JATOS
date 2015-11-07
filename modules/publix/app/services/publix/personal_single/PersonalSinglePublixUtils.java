package services.publix.personal_single;

import javax.inject.Inject;
import javax.inject.Singleton;

import models.common.workers.PersonalSingleWorker;
import models.common.workers.Worker;
import services.publix.PublixUtils;
import daos.ComponentDao;
import daos.ComponentResultDao;
import daos.StudyDao;
import daos.StudyResultDao;
import daos.workers.WorkerDao;
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
