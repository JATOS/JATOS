package services.publix.workers;

import javax.inject.Singleton;

import models.common.workers.PersonalSingleWorker;
import services.publix.PublixErrorMessages;

/**
 * PersonalSinglePublix' implementation of PublixErrorMessages
 * 
 * @author Kristian Lange
 */
@Singleton
public class PersonalSingleErrorMessages extends PublixErrorMessages {

	@Override
	public String workerNotCorrectType(Long workerId) {
		return "The worker with ID " + workerId + " isn't a "
				+ PersonalSingleWorker.UI_WORKER_TYPE + " Worker.";
	}

}
