package controllers.publix.closed_standalone;

import controllers.publix.PublixErrorMessages;
import models.workers.ClosedStandaloneWorker;

/**
 * Special PublixErrorMessages for ClosedStandalonePublix
 * 
 * @author Kristian Lange
 */
public class ClosedStandaloneErrorMessages extends PublixErrorMessages<ClosedStandaloneWorker> {

	@Override
	public String workerNeverStartedStudy(ClosedStandaloneWorker worker, Long studyId) {
		String errorMsg = "Worker " + worker.getId() + " (comment: "
				+ worker.getComment() + ")" + " never started study " + studyId
				+ ".";
		return errorMsg;
	}

	@Override
	public String workerNeverDidStudy(ClosedStandaloneWorker worker, Long studyId) {
		String errorMsg = "Worker " + worker.getId() + " (comment: "
				+ worker.getComment() + ")" + " never did study " + studyId + ".";
		return errorMsg;
	}

	@Override
	public String workerNotAllowedStudy(ClosedStandaloneWorker worker, Long studyId) {
		String errorMsg = "Worker " + worker.getId() + " (comment: "
				+ worker.getComment() + ")" + " is not allowed to do " + "study "
				+ studyId + ".";
		return errorMsg;
	}

	@Override
	public String workerFinishedStudyAlready(ClosedStandaloneWorker worker,
			Long studyId) {
		String errorMsg = "Worker " + worker.getId() + " (comment: "
				+ worker.getComment() + ")" + " finished study " + studyId
				+ " already.";
		return errorMsg;
	}

	@Override
	public String workerNotCorrectType(Long workerId) {
		String errorMsg = "The worker with ID " + workerId
				+ " isn't a closed standalone worker.";
		return errorMsg;
	}

}
