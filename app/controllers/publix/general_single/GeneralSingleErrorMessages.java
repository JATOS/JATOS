package controllers.publix.general_single;

import models.workers.GeneralSingleWorker;

import com.google.inject.Singleton;

import controllers.publix.PublixErrorMessages;

/**
 * GeneralSinglePublix' implementation of PublixErrorMessages
 * 
 * @author Kristian Lange
 */
@Singleton
public class GeneralSingleErrorMessages extends PublixErrorMessages {

	@Override
	public String workerNotCorrectType(Long workerId) {
		String errorMsg = "The worker with ID " + workerId + " isn't a "
				+ GeneralSingleWorker.UI_WORKER_TYPE + " Worker.";
		return errorMsg;
	}

}
