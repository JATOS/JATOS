package controllers.publix.open_standalone;

import com.google.inject.Singleton;

import controllers.publix.PublixErrorMessages;
import models.workers.OpenStandaloneWorker;

/**
 * Special PublixErrorMessages for ClosedStandalonePublix
 * 
 * @author Kristian Lange
 */
@Singleton
public class OpenStandaloneErrorMessages extends
		PublixErrorMessages<OpenStandaloneWorker> {

	@Override
	public String workerNeverStartedStudy(OpenStandaloneWorker worker,
			Long studyId) {
		String errorMsg = "Worker " + worker.getId() + " never started study "
				+ studyId + ".";
		return errorMsg;
	}

	@Override
	public String workerNeverDidStudy(OpenStandaloneWorker worker, Long studyId) {
		String errorMsg = "Worker " + worker.getId() + " never did study "
				+ studyId + ".";
		return errorMsg;
	}

	@Override
	public String workerNotAllowedStudy(OpenStandaloneWorker worker,
			Long studyId) {
		String errorMsg = "Worker " + worker.getId() + " is not allowed to do "
				+ "study " + studyId + ".";
		return errorMsg;
	}

	@Override
	public String workerFinishedStudyAlready(OpenStandaloneWorker worker,
			Long studyId) {
		String errorMsg = "Worker " + worker.getId() + " finished study "
				+ studyId + " already.";
		return errorMsg;
	}

	@Override
	public String workerNotCorrectType(Long workerId) {
		String errorMsg = "The worker with ID " + workerId
				+ " isn't an open standalone worker.";
		return errorMsg;
	}

}
