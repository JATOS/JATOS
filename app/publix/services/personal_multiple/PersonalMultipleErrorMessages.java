package publix.services.personal_multiple;

import publix.services.PublixErrorMessages;

import com.google.inject.Singleton;

import models.workers.PersonalMultipleWorker;

/**
 * PersonalMultiplePublix' implementation of PublixErrorMessages
 * 
 * @author Kristian Lange
 */
@Singleton
public class PersonalMultipleErrorMessages extends PublixErrorMessages {

	@Override
	public String workerNotCorrectType(Long workerId) {
		return "The worker with ID " + workerId + " isn't a "
				+ PersonalMultipleWorker.UI_WORKER_TYPE + " worker.";
	}

}
