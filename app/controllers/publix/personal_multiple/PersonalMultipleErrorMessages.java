package controllers.publix.personal_multiple;

import com.google.inject.Singleton;

import controllers.publix.PublixErrorMessages;
import models.workers.PersonalMultipleWorker;

/**
 * Special PublixErrorMessages for PersonalMultiplePublix
 * 
 * @author Kristian Lange
 */
@Singleton
public class PersonalMultipleErrorMessages extends
		PublixErrorMessages<PersonalMultipleWorker> {

	public String workerNotCorrectType(Long workerId) {
		String errorMsg = "The worker with ID " + workerId + " isn't a "
				+ PersonalMultipleWorker.UI_WORKER_TYPE + " worker.";
		return errorMsg;
	}

}
