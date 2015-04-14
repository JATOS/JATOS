package controllers.publix.closed_standalone;

import com.google.inject.Singleton;

import controllers.publix.PublixErrorMessages;
import models.workers.ClosedStandaloneWorker;

/**
 * Special PublixErrorMessages for ClosedStandalonePublix
 * 
 * @author Kristian Lange
 */
@Singleton
public class ClosedStandaloneErrorMessages extends
		PublixErrorMessages<ClosedStandaloneWorker> {

	public String workerNotCorrectType(Long workerId) {
		String errorMsg = "The worker with ID " + workerId
				+ " isn't a closed standalone worker.";
		return errorMsg;
	}

}
