package controllers.publix;

import models.workers.MTWorker;

/**
 * Special ErrorMessages for MTPublix (studies started via MTurk).
 * 
 * @author madsen
 */
public class MTErrorMessages extends ErrorMessages<MTWorker> {

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

	public String assignmentIdNotSpecified() {
		String errorMsg = "No assignment id specified in query parameters.";
		return errorMsg;
	}

	public String workerNotInQueryParameter(String mtWorkerId) {
		String errorMsg = "MTurk's workerId is missing in the query parameters.";
		return errorMsg;
	}

	public String workerNotFromMTurk(Long workerId) {
		String errorMsg = "The worker with id " + workerId
				+ " isn't a MTurk worker.";
		return errorMsg;
	}

}
