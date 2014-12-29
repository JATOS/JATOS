package controllers.publix;

import models.StudyModel;
import models.workers.StandaloneWorker;
import models.workers.Worker;
import exceptions.ForbiddenPublixException;
import exceptions.PublixException;

/**
 * Special PublixUtils for StandalonePublix
 * 
 * @author Kristian Lange
 */
public class StandalonePublixUtils extends PublixUtils<StandaloneWorker> {

	private StandaloneErrorMessages errorMessages;

	public StandalonePublixUtils(StandaloneErrorMessages errorMessages) {
		super(errorMessages);
		this.errorMessages = errorMessages;
	}

	@Override
	public StandaloneWorker retrieveTypedWorker(String workerIdStr)
			throws PublixException {
		Worker worker = retrieveWorker(workerIdStr);
		if (!(worker instanceof StandaloneWorker)) {
			throw new ForbiddenPublixException(
					errorMessages.workerNotCorrectType(worker.getId()));
		}
		return (StandaloneWorker) worker;
	}
	
	@Override
	public void checkWorkerAllowedToStartStudy(StandaloneWorker worker, StudyModel study)
			throws ForbiddenPublixException {
		if (!worker.getStudyResultList().isEmpty()) {
			throw new ForbiddenPublixException(
					PublixErrorMessages.STUDY_CAN_BE_DONE_ONLY_ONCE);
		}
		checkWorkerAllowedToDoStudy(worker, study);
	}

	@Override
	public void checkWorkerAllowedToDoStudy(StandaloneWorker worker,
			StudyModel study) throws ForbiddenPublixException {
		// Standalone workers can't repeat the same study
		if (didStudyAlready(worker, study)) {
			throw new ForbiddenPublixException(
					PublixErrorMessages.STUDY_CAN_BE_DONE_ONLY_ONCE);
		}
	}

}
