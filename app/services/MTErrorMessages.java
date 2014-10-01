package services;

import models.workers.MTWorker;

/**
 * Special ErrorMessages for MTPublix (studies started via MTurk).
 * 
 * @author Kristian Lange
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

}
