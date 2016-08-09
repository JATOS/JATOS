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
import services.publix.PublixErrorMessages;
import services.publix.PublixUtils;
import services.publix.ResultCreator;
import services.publix.idcookie.IdCookieService;

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
			IdCookieService idCookieService,
			PersonalSingleErrorMessages errorMessages, StudyDao studyDao,
			StudyResultDao studyResultDao, ComponentDao componentDao,
			ComponentResultDao componentResultDao, WorkerDao workerDao,
			BatchDao batchDao) {
		super(resultCreator, idCookieService, errorMessages, studyDao,
				studyResultDao, componentDao, componentResultDao, workerDao,
				batchDao);
	}

	@Override
	public PersonalSingleWorker retrieveTypedWorker(Long workerId)
			throws ForbiddenPublixException {
		Worker worker = super.retrieveWorker(workerId);
		if (!(worker instanceof PersonalSingleWorker)) {
			throw new ForbiddenPublixException(
					errorMessages.workerNotCorrectType(worker.getId()));
		}
		return (PersonalSingleWorker) worker;
	}

	public PersonalSingleWorker retrieveTypedWorker(String workerIdStr)
			throws ForbiddenPublixException {
		if (workerIdStr == null) {
			throw new ForbiddenPublixException(
					PublixErrorMessages.NO_WORKER_IN_QUERY_STRING);
		}
		long workerId;
		try {
			workerId = Long.parseLong(workerIdStr);
		} catch (NumberFormatException e) {
			throw new ForbiddenPublixException(
					errorMessages.workerNotExist(workerIdStr));
		}
		return retrieveTypedWorker(workerId);
	}

}
