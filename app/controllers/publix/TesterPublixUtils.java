package controllers.publix;

import models.StudyModel;
import models.workers.TesterWorker;
import models.workers.Worker;
import exceptions.ForbiddenPublixException;
import exceptions.PublixException;

/**
 * Special PublixUtils for TesterPublix
 * 
 * @author Kristian Lange
 */
public class TesterPublixUtils extends PublixUtils<TesterWorker> {

	private TesterErrorMessages errorMessages;

	public TesterPublixUtils(TesterErrorMessages errorMessages) {
		super(errorMessages);
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
		// No restrictions for testers
		return;
	}

}
