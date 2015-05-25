package controllers.publix.personal_single;

import models.workers.PersonalSingleWorker;

import com.google.inject.Singleton;

import controllers.publix.PublixErrorMessages;

/**
 * Special PublixErrorMessages for PersonalSinglePublix
 * 
 * @author Kristian Lange
 */
@Singleton
public class PersonalSingleErrorMessages extends
		PublixErrorMessages<PersonalSingleWorker> {

	public String workerNotCorrectType(Long workerId) {
		String errorMsg = "The worker with ID " + workerId + " isn't a "
				+ PersonalSingleWorker.UI_WORKER_TYPE + " Worker.";
		return errorMsg;
	}

}
