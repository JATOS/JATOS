package publix.services.mt;

import publix.services.PublixErrorMessages;
import models.workers.MTWorker;

import com.google.inject.Singleton;

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
		String errorMsg = "The worker with ID " + workerId + " isn't a "
				+ MTWorker.UI_WORKER_TYPE + " worker.";
		return errorMsg;
	}

	public String noPreviewAvailable(Long studyId) {
		String errorMsg = "No preview available for study " + studyId + ".";
		return errorMsg;
	}

}
