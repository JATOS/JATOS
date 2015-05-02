package controllers.publix.open_standalone;

import models.workers.OpenStandaloneWorker;

import com.google.inject.Singleton;

import controllers.publix.PublixErrorMessages;

/**
 * Special PublixErrorMessages for ClosedStandalonePublix
 * 
 * @author Kristian Lange
 */
@Singleton
public class OpenStandaloneErrorMessages extends
		PublixErrorMessages<OpenStandaloneWorker> {

	public String workerNotCorrectType(Long workerId) {
		String errorMsg = "The worker with ID " + workerId + " isn't a "
				+ OpenStandaloneWorker.UI_WORKER_TYPE + " Worker.";
		return errorMsg;
	}

}
