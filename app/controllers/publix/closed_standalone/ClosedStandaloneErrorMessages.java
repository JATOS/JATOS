package controllers.publix.closed_standalone;

import models.workers.ClosedStandaloneWorker;

import com.google.inject.Singleton;

import controllers.publix.PublixErrorMessages;

/**
 * Special PublixErrorMessages for ClosedStandalonePublix
 * 
 * @author Kristian Lange
 */
@Singleton
public class ClosedStandaloneErrorMessages extends
		PublixErrorMessages<ClosedStandaloneWorker> {

	public String workerNotCorrectType(Long workerId) {
		String errorMsg = "The worker with ID " + workerId + " isn't a "
				+ ClosedStandaloneWorker.UI_WORKER_TYPE + " Worker.";
		return errorMsg;
	}

}
