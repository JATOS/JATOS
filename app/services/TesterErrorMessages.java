package services;

import models.workers.TesterWorker;

/**
 * Special ErrorMessages for MTPublix (studies started via MTurk).
 * 
 * @author Kristian Lange
 */
public class TesterErrorMessages extends ErrorMessages<TesterWorker> {

	@Override
	public String workerNeverStartedStudy(TesterWorker worker, Long studyId) {
		String errorMsg = "Worker " + worker.getId() + " (name: "
				+ worker.getName() + ")" + " never started study " + studyId
				+ ".";
		return errorMsg;
	}

	@Override
	public String workerNeverDidStudy(TesterWorker worker, Long studyId) {
		String errorMsg = "Worker " + worker.getId() + " (name: "
				+ worker.getName() + ")" + " never did study " + studyId + ".";
		return errorMsg;
	}

	@Override
	public String workerNotAllowedStudy(TesterWorker worker, Long studyId) {
		String errorMsg = "Worker " + worker.getId() + " (name: "
				+ worker.getName() + ")" + " is not allowed to do " + "study "
				+ studyId + ".";
		return errorMsg;
	}

	@Override
	public String workerFinishedStudyAlready(TesterWorker worker, Long studyId) {
		String errorMsg = "Worker " + worker.getId() + " (name: "
				+ worker.getName() + ")" + " finished study " + studyId
				+ " already.";
		return errorMsg;
	}

	public static String workerNotTester(Long workerId) {
		String errorMsg = "The worker with ID " + workerId
				+ " isn't a tester worker.";
		return errorMsg;
	}

}
