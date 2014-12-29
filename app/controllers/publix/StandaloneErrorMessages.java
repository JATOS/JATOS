package controllers.publix;

import models.workers.StandaloneWorker;

/**
 * Special PublixErrorMessages for StandalonePublix
 * 
 * @author Kristian Lange
 */
public class StandaloneErrorMessages extends PublixErrorMessages<StandaloneWorker> {

	@Override
	public String workerNeverStartedStudy(StandaloneWorker worker, Long studyId) {
		String errorMsg = "Worker " + worker.getId() + " (name: "
				+ worker.getComment() + ")" + " never started study " + studyId
				+ ".";
		return errorMsg;
	}

	@Override
	public String workerNeverDidStudy(StandaloneWorker worker, Long studyId) {
		String errorMsg = "Worker " + worker.getId() + " (name: "
				+ worker.getComment() + ")" + " never did study " + studyId + ".";
		return errorMsg;
	}

	@Override
	public String workerNotAllowedStudy(StandaloneWorker worker, Long studyId) {
		String errorMsg = "Worker " + worker.getId() + " (name: "
				+ worker.getComment() + ")" + " is not allowed to do " + "study "
				+ studyId + ".";
		return errorMsg;
	}

	@Override
	public String workerFinishedStudyAlready(StandaloneWorker worker,
			Long studyId) {
		String errorMsg = "Worker " + worker.getId() + " (name: "
				+ worker.getComment() + ")" + " finished study " + studyId
				+ " already.";
		return errorMsg;
	}

	@Override
	public String workerNotCorrectType(Long workerId) {
		String errorMsg = "The worker with ID " + workerId
				+ " isn't a standalone worker.";
		return errorMsg;
	}

}
