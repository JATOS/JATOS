package publix.services.mt;

import javax.inject.Singleton;

import models.workers.MTWorker;
import publix.services.PublixErrorMessages;

/**
 * MTPublix' implementation of PublixErrorMessages (studies started via MTurk).
 * 
 * @author Kristian Lange
 */
@Singleton
public class MTErrorMessages extends PublixErrorMessages {

	public static final String NO_MTURK_WORKERID = "MTurk's workerId is missing in the query parameters.";

	@Override
	public String workerNotCorrectType(Long workerId) {
		return "The worker with ID " + workerId + " isn't a "
				+ MTWorker.UI_WORKER_TYPE + " worker.";
	}

	public String noPreviewAvailable(Long studyId) {
		return "No preview available for study " + studyId + ".";
	}

}
