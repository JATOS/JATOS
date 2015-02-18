package controllers.publix.closed_standalone;

import persistance.IComponentDao;
import persistance.IComponentResultDao;
import persistance.IStudyDao;
import persistance.IStudyResultDao;
import persistance.workers.IWorkerDao;
import models.StudyModel;
import models.workers.ClosedStandaloneWorker;
import models.workers.Worker;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import controllers.publix.PublixErrorMessages;
import controllers.publix.PublixUtils;
import exceptions.ForbiddenPublixException;
import exceptions.PublixException;

/**
 * Special PublixUtils for ClosedStandalonePublix
 * 
 * @author Kristian Lange
 */
@Singleton
public class ClosedStandalonePublixUtils extends
		PublixUtils<ClosedStandaloneWorker> {

	private ClosedStandaloneErrorMessages errorMessages;

	@Inject
	ClosedStandalonePublixUtils(
			ClosedStandaloneErrorMessages errorMessages, IStudyDao studyDao,
			IStudyResultDao studyResultDao, IComponentDao componentDao,
			IComponentResultDao componentResultDao, IWorkerDao workerDao) {
		super(errorMessages, studyDao, studyResultDao, componentDao,
				componentResultDao, workerDao);
		this.errorMessages = errorMessages;
	}

	@Override
	public ClosedStandaloneWorker retrieveTypedWorker(String workerIdStr)
			throws PublixException {
		Worker worker = retrieveWorker(workerIdStr);
		if (!(worker instanceof ClosedStandaloneWorker)) {
			throw new ForbiddenPublixException(
					errorMessages.workerNotCorrectType(worker.getId()));
		}
		return (ClosedStandaloneWorker) worker;
	}

	@Override
	public void checkWorkerAllowedToStartStudy(ClosedStandaloneWorker worker,
			StudyModel study) throws ForbiddenPublixException {
		// Standalone runs are used only once - don't start if worker has a
		// study result
		if (!worker.getStudyResultList().isEmpty()) {
			throw new ForbiddenPublixException(
					PublixErrorMessages.STUDY_CAN_BE_DONE_ONLY_ONCE);
		}
		checkWorkerAllowedToDoStudy(worker, study);
	}

	@Override
	public void checkWorkerAllowedToDoStudy(ClosedStandaloneWorker worker,
			StudyModel study) throws ForbiddenPublixException {
		if (!study.hasAllowedWorker(worker.getWorkerType())) {
			throw new ForbiddenPublixException(
					errorMessages.workerTypeNotAllowed(worker.getUIWorkerType()));
		}
		// Closed standalone workers can't repeat the same study
		if (finishedStudyAlready(worker, study)) {
			throw new ForbiddenPublixException(
					PublixErrorMessages.STUDY_CAN_BE_DONE_ONLY_ONCE);
		}
	}

}
