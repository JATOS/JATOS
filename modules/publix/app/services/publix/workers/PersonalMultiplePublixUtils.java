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
import services.publix.PublixErrorMessages;
import services.publix.PublixUtils;
import services.publix.ResultCreator;
import services.publix.idcookie.IdCookieService;
import session.group.GroupAdministration;

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
			IdCookieService idCookieService,
			GroupAdministration groupAdministration,
			PersonalMultipleErrorMessages errorMessages, StudyDao studyDao,
			StudyResultDao studyResultDao, ComponentDao componentDao,
			ComponentResultDao componentResultDao, WorkerDao workerDao,
			BatchDao batchDao) {
		super(resultCreator, idCookieService, groupAdministration,
				errorMessages, studyDao, studyResultDao, componentDao,
				componentResultDao, workerDao, batchDao);
	}

	@Override
	public PersonalMultipleWorker retrieveTypedWorker(Long workerId)
			throws ForbiddenPublixException {
		Worker worker = super.retrieveWorker(workerId);
		if (!(worker instanceof PersonalMultipleWorker)) {
			throw new ForbiddenPublixException(
					errorMessages.workerNotCorrectType(worker.getId()));
		}
		return (PersonalMultipleWorker) worker;
	}

	public PersonalMultipleWorker retrieveTypedWorker(String workerIdStr)
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
					PublixErrorMessages.workerNotExist(workerIdStr));
		}
		return retrieveTypedWorker(workerId);
	}

}
