package services.publix.personal_multiple;

import javax.inject.Singleton;

import models.common.workers.PersonalMultipleWorker;
import services.publix.PublixErrorMessages;

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
