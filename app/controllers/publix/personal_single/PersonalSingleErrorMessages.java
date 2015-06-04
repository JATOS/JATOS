package controllers.publix.personal_single;

import models.workers.PersonalSingleWorker;

import com.google.inject.Singleton;

import controllers.publix.PublixErrorMessages;

/**
 * PersonalSinglePublix' implementation of PublixErrorMessages
 * 
 * @author Kristian Lange
 */
@Singleton
public class PersonalSingleErrorMessages extends
		PublixErrorMessages {

	@Override
	public String workerNotCorrectType(Long workerId) {
		String errorMsg = "The worker with ID " + workerId + " isn't a "
				+ PersonalSingleWorker.UI_WORKER_TYPE + " Worker.";
		return errorMsg;
	}

}
