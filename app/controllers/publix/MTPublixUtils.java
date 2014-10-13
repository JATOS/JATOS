package controllers.publix;

import models.workers.MTWorker;
import models.workers.Worker;
import services.ErrorMessages;
import services.MTErrorMessages;

import com.google.common.net.MediaType;

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

	public MTPublixUtils(MTErrorMessages errorMessages) {
		super(errorMessages);
	}

	@Override
	public MTWorker retrieveWorker() throws PublixException {
		return retrieveWorker(MediaType.HTML_UTF_8);
	}

	@Override
	public MTWorker retrieveWorker(MediaType errorMediaType)
			throws PublixException {
		String workerIdStr = Publix.session(Publix.WORKER_ID);
		if (workerIdStr == null) {
			// No worker ID in session -> study never started
			throw new ForbiddenPublixException(
					ErrorMessages.NO_WORKERID_IN_SESSION, errorMediaType);
		}
		long workerId;
		try {
			workerId = Long.parseLong(workerIdStr);
		} catch (NumberFormatException e) {
			throw new BadRequestPublixException(
					ErrorMessages.workerNotExist(workerIdStr), errorMediaType);
		}

		Worker worker = Worker.findById(workerId);
		if (worker == null) {
			throw new NotFoundPublixException(
					ErrorMessages.workerNotExist(workerId), errorMediaType);
		}
		if (!(worker instanceof MTWorker)) {
			throw new NotFoundPublixException(
					ErrorMessages.workerNotFromMTurk(workerId), errorMediaType);
		}
		return (MTWorker) worker;
	}

}
