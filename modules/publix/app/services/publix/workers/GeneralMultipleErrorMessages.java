package services.publix.workers;

import models.common.workers.GeneralMultipleWorker;
import services.publix.PublixErrorMessages;

import javax.inject.Singleton;

/**
 * GeneralMultiplePublix' implementation of PublixErrorMessages
 * 
 * @author Kristian Lange
 */
@Singleton
public class GeneralMultipleErrorMessages extends PublixErrorMessages {

	@Override
	public String workerNotCorrectType(Long workerId) {
		return "The worker with ID " + workerId + " isn't a "
				+ GeneralMultipleWorker.UI_WORKER_TYPE + " Worker.";
	}

}
