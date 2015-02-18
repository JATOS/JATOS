package controllers.publix.jatos;

import com.google.inject.Singleton;

import controllers.publix.PublixErrorMessages;
import models.workers.JatosWorker;

/**
 * Special PublixErrorMessages for JatosPublix (studies or components started via
 * JATOS' UI).
 * 
 * @author Kristian Lange
 */
@Singleton
public class JatosErrorMessages extends PublixErrorMessages<JatosWorker> {

	@Override
	public String workerNeverStartedStudy(JatosWorker worker, Long studyId) {
		String errorMsg = "Worker " + worker.getId() + " ("
				+ worker.getUser().getEmail() + ")" + " never started study "
				+ studyId + ".";
		return errorMsg;
	}

	@Override
	public String workerNeverDidStudy(JatosWorker worker, Long studyId) {
		String errorMsg = "Worker " + worker.getId() + " ("
				+ worker.getUser().getEmail() + ")" + " never did study "
				+ studyId + ".";
		return errorMsg;
	}

	@Override
	public String workerNotAllowedStudy(JatosWorker worker, Long studyId) {
		String errorMsg = "Worker " + worker.getId() + " ("
				+ worker.getUser().getEmail() + ")" + " is not allowed to do "
				+ "study " + studyId + ".";
		return errorMsg;
	}

	@Override
	public String workerFinishedStudyAlready(JatosWorker worker, Long studyId) {
		String errorMsg = "Worker " + worker.getId() + " ("
				+ worker.getUser().getEmail() + ")" + " finished study "
				+ studyId + " already.";
		return errorMsg;
	}

	@Override
	public String workerNotCorrectType(Long workerId) {
		String errorMsg = "The worker with ID " + workerId
				+ " isn't a JATOS worker.";
		return errorMsg;
	}

}
