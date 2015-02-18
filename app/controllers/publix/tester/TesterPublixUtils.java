package controllers.publix.tester;

import persistance.IComponentDao;
import persistance.IComponentResultDao;
import persistance.IStudyDao;
import persistance.IStudyResultDao;
import persistance.workers.IWorkerDao;
import models.StudyModel;
import models.workers.TesterWorker;
import models.workers.Worker;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import controllers.publix.PublixUtils;
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
	TesterPublixUtils(TesterErrorMessages errorMessages,
			IStudyDao studyDao, IStudyResultDao studyResultDao,
			IComponentDao componentDao, IComponentResultDao componentResultDao,
			IWorkerDao workerDao) {
		super(errorMessages, studyDao, studyResultDao, componentDao,
				componentResultDao, workerDao);
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
