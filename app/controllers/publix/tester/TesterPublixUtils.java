package controllers.publix.tester;

import models.StudyModel;
import models.workers.TesterWorker;
import models.workers.Worker;
import utils.PersistanceUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import controllers.publix.PublixUtils;
import daos.ComponentDao;
import daos.ComponentResultDao;
import daos.StudyDao;
import exceptions.ForbiddenPublixException;
import exceptions.PublixException;

/**
 * Special PublixUtils for TesterPublix
 * 
 * @author Kristian Lange
 */
@Singleton
public class TesterPublixUtils extends PublixUtils<TesterWorker> {

	private TesterErrorMessages errorMessages;

	@Inject
	public TesterPublixUtils(TesterErrorMessages errorMessages,
			PersistanceUtils persistanceUtils, StudyDao studyDao,
			ComponentDao componentDao, ComponentResultDao componentResultDao) {
		super(errorMessages, persistanceUtils, studyDao, componentDao,
				componentResultDao);
		this.errorMessages = errorMessages;
	}

	@Override
	public TesterWorker retrieveTypedWorker(String workerIdStr)
			throws PublixException {
		Worker worker = retrieveWorker(workerIdStr);
		if (!(worker instanceof TesterWorker)) {
			throw new ForbiddenPublixException(
					errorMessages.workerNotCorrectType(worker.getId()));
		}
		return (TesterWorker) worker;
	}

	@Override
	public void checkWorkerAllowedToStartStudy(TesterWorker worker,
			StudyModel study) throws ForbiddenPublixException {
		checkWorkerAllowedToDoStudy(worker, study);
	}

	@Override
	public void checkWorkerAllowedToDoStudy(TesterWorker worker,
			StudyModel study) throws ForbiddenPublixException {
		if (!study.hasAllowedWorker(worker.getWorkerType())) {
			throw new ForbiddenPublixException(
					errorMessages.workerTypeNotAllowed(worker.getUIWorkerType()));
		}
	}

}
