package controllers.publix;

import models.StudyModel;
import models.workers.MTSandboxWorker;
import models.workers.MTTesterWorker;
import models.workers.MTWorker;
import models.workers.Worker;
import services.ErrorMessages;
import services.MTErrorMessages;
import exceptions.BadRequestPublixException;
import exceptions.ForbiddenPublixException;
import exceptions.NotFoundPublixException;
import exceptions.PublixException;

/**
 * Special PublixUtils for MTPublix (studies started via MTurk).
 * 
 * @author Kristian Lange
 */
public class MTPublixUtils extends PublixUtils<MTWorker> {

	private MTErrorMessages errorMessages;

	public MTPublixUtils(MTErrorMessages errorMessages) {
		super(errorMessages);
		this.errorMessages = errorMessages;
	}

	@Override
	public MTWorker retrieveWorker() throws PublixException {
		String workerIdStr = Publix.session(Publix.WORKER_ID);
		if (workerIdStr == null) {
			// No worker ID in session -> study never started
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
		if (!(worker instanceof MTWorker)) {
			throw new NotFoundPublixException(
					ErrorMessages.workerNotFromMTurk(workerId));
		}
		return (MTWorker) worker;
	}

	@Override
	public void checkWorkerAllowedToDoStudy(MTWorker worker, StudyModel study)
			throws ForbiddenPublixException {
		if (worker instanceof MTSandboxWorker
				|| worker instanceof MTTesterWorker) {
			return;
		}
		if (didStudyAlready(worker, study)) {
			throw new ForbiddenPublixException(
					errorMessages.workerNotAllowedStudy(worker, study.getId()));
		}
	}

}
