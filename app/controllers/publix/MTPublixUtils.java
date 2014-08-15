package controllers.publix;

import models.workers.MTWorker;
import models.workers.Worker;
import services.ErrorMessages;
import services.MTErrorMessages;

import com.google.common.net.MediaType;

import exceptions.BadRequestPublixException;
import exceptions.ForbiddenPublixException;

/**
 * Special PublixUtils for MTPublix (studies started via MTurk). 
 * 
 * @author madsen
 */
public class MTPublixUtils extends PublixUtils<MTWorker> {

	public MTPublixUtils(MTErrorMessages errorMessages) {
		super(errorMessages);
	}

	@Override
	public MTWorker retrieveWorker() throws Exception {
		return retrieveWorker(MediaType.HTML_UTF_8);
	}
	
	@Override
	public MTWorker retrieveWorker(MediaType errorMediaType) throws Exception {
		String workerIdStr = Publix.getWorkerIdFromSession();
		if (workerIdStr == null) {
			// No worker id in session -> study never started
			throw new ForbiddenPublixException(
					ErrorMessages.noWorkerIdInSession(), errorMediaType);
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
			throw new BadRequestPublixException(
					ErrorMessages.workerNotExist(workerId), errorMediaType);
		}
		if (!(worker instanceof MTWorker)) {
			throw new BadRequestPublixException(
					ErrorMessages.workerNotFromMTurk(workerId), errorMediaType);
		}
		return (MTWorker) worker;
	}

}
