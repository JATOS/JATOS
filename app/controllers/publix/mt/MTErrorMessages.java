package controllers.publix.mt;

import com.google.inject.Singleton;

import controllers.publix.PublixErrorMessages;
import models.workers.MTWorker;

/**
 * Special PublixErrorMessages for MTPublix (studies started via MTurk).
 * 
 * @author Kristian Lange
 */
@Singleton
public class MTErrorMessages extends PublixErrorMessages<MTWorker> {

	@Override
	public String workerNeverStartedStudy(MTWorker worker, Long studyId) {
		String errorMsg = "Worker " + worker.getId() + " (MTurk's workerId: "
				+ worker.getMTWorkerId() + ")" + " never started study "
				+ studyId + ".";
		return errorMsg;
	}

	@Override
	public String workerNeverDidStudy(MTWorker worker, Long studyId) {
		String errorMsg = "Worker " + worker.getId() + " (MTurk's workerId: "
				+ worker.getMTWorkerId() + ")" + " never did study " + studyId
				+ ".";
		return errorMsg;
	}

	@Override
	public String workerNotAllowedStudy(MTWorker worker, Long studyId) {
		String errorMsg = "Worker " + worker.getId() + " (MTurk's workerId: "
				+ worker.getMTWorkerId() + ")" + " is not allowed to do "
				+ "study " + studyId + ".";
		return errorMsg;
	}
	
	@Override
	public String workerFinishedStudyAlready(MTWorker worker, Long studyId) {
		String errorMsg = "Worker " + worker.getId() + " ("
				+ worker.getMTWorkerId() + ")" + " finished study "
				+ studyId + " already.";
		return errorMsg;
	}
	
	@Override
	public String workerNotCorrectType(Long workerId) {
		String errorMsg = "The worker with ID " + workerId
				+ " isn't a MTurk worker.";
		return errorMsg;
	}
	
	public String noPreviewAvailable(Long studyId) {
		String errorMsg = "No preview available for study " + studyId + ".";
		return errorMsg;
	}

}
