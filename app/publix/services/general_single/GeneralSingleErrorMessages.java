package publix.services.general_single;

import javax.inject.Singleton;

import models.workers.GeneralSingleWorker;
import publix.services.PublixErrorMessages;

/**
 * GeneralSinglePublix' implementation of PublixErrorMessages
 * 
 * @author Kristian Lange
 */
@Singleton
public class GeneralSingleErrorMessages extends PublixErrorMessages {

	@Override
	public String workerNotCorrectType(Long workerId) {
		return "The worker with ID " + workerId + " isn't a "
				+ GeneralSingleWorker.UI_WORKER_TYPE + " Worker.";
	}

}
