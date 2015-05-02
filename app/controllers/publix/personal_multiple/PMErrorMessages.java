package controllers.publix.personal_multiple;

import com.google.inject.Singleton;

import controllers.publix.PublixErrorMessages;
import models.workers.PMWorker;

/**
 * Special PublixErrorMessages for PMPublix
 * 
 * @author Kristian Lange
 */
@Singleton
public class PMErrorMessages extends PublixErrorMessages<PMWorker> {

	public String workerNotCorrectType(Long workerId) {
		String errorMsg = "The worker with ID " + workerId
				+ " isn't a " + PMWorker.UI_WORKER_TYPE + " worker.";
		return errorMsg;
	}

}
