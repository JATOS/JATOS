package controllers.publix.tester;

import controllers.publix.PublixErrorMessages;
import models.workers.TesterWorker;

/**
 * Special PublixErrorMessages for TesterPublix
 * 
 * @author Kristian Lange
 */
public class TesterErrorMessages extends PublixErrorMessages<TesterWorker> {

	@Override
	public String workerNeverStartedStudy(TesterWorker worker, Long studyId) {
		String errorMsg = "Worker " + worker.getId() + " (comment: "
				+ worker.getComment() + ")" + " never started study " + studyId
				+ ".";
		return errorMsg;
	}

	@Override
	public String workerNeverDidStudy(TesterWorker worker, Long studyId) {
		String errorMsg = "Worker " + worker.getId() + " (comment: "
				+ worker.getComment() + ")" + " never did study " + studyId + ".";
		return errorMsg;
	}

	@Override
	public String workerNotAllowedStudy(TesterWorker worker, Long studyId) {
		String errorMsg = "Worker " + worker.getId() + " (comment: "
				+ worker.getComment() + ")" + " is not allowed to do " + "study "
				+ studyId + ".";
		return errorMsg;
	}

	@Override
	public String workerFinishedStudyAlready(TesterWorker worker, Long studyId) {
		String errorMsg = "Worker " + worker.getId() + " (comment: "
				+ worker.getComment() + ")" + " finished study " + studyId
				+ " already.";
		return errorMsg;
	}

	@Override
	public String workerNotCorrectType(Long workerId) {
		String errorMsg = "The worker with ID " + workerId
				+ " isn't a tester worker.";
		return errorMsg;
	}

}
