package controllers.publix.tester;

import com.google.inject.Singleton;

import controllers.publix.PublixErrorMessages;
import models.workers.TesterWorker;

/**
 * Special PublixErrorMessages for TesterPublix
 * 
 * @author Kristian Lange
 */
@Singleton
public class TesterErrorMessages extends PublixErrorMessages<TesterWorker> {

	public String workerNotCorrectType(Long workerId) {
		String errorMsg = "The worker with ID " + workerId
				+ " isn't a tester worker.";
		return errorMsg;
	}

}
