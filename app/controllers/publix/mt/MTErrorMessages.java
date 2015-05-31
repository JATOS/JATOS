package controllers.publix.mt;

import models.workers.MTWorker;

import com.google.inject.Singleton;

import controllers.publix.PublixErrorMessages;

/**
 * Special PublixErrorMessages for MTPublix (studies started via MTurk).
 * 
 * @author Kristian Lange
 */
@Singleton
public class MTErrorMessages extends PublixErrorMessages<MTWorker> {

	public static final String NO_MTURK_WORKERID = "MTurk's workerId is missing in the query parameters.";

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
