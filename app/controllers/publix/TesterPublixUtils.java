package controllers.publix;

import models.StudyModel;
import models.workers.TesterWorker;
import models.workers.Worker;
import services.ErrorMessages;
import services.TesterErrorMessages;
import exceptions.BadRequestPublixException;
import exceptions.ForbiddenPublixException;
import exceptions.NotFoundPublixException;
import exceptions.PublixException;

/**
 * Special PublixUtils for TesterPublix
 * 
 * @author Kristian Lange
 */
public class TesterPublixUtils extends PublixUtils<TesterWorker> {

	public TesterPublixUtils(TesterErrorMessages errorMessages) {
		super(errorMessages);
	}

	@Override
	public TesterWorker retrieveWorker() throws PublixException {
		String workerIdStr = Publix.session(Publix.WORKER_ID);
		if (workerIdStr == null) {
			throw new ForbiddenPublixException(
					ErrorMessages.NO_WORKERID_IN_SESSION);
		}
		long workerId;
		try {
			workerId = Long.parseLong(workerIdStr);
		} catch (NumberFormatException e) {
			throw new BadRequestPublixException(
					ErrorMessages.workerNotExist(workerIdStr));
		}

		Worker worker = Worker.findById(workerId);
		if (worker == null) {
			throw new NotFoundPublixException(
					ErrorMessages.workerNotExist(workerId));
		}
		if (!(worker instanceof TesterWorker)) {
			throw new NotFoundPublixException(
					TesterErrorMessages.workerNotTester(workerId));
		}
		return (TesterWorker) worker;
	}

	@Override
	public void checkWorkerAllowedToDoStudy(TesterWorker worker, StudyModel study)
			throws ForbiddenPublixException {
		// No restrictions for testers
		return;
	}

}
