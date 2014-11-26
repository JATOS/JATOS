package services;

import models.workers.JatosWorker;

/**
 * Special ErrorMessages for JatosPublix (studies or components started via
 * JATOS' UI).
 * 
 * @author Kristian Lange
 */
public class JatosErrorMessages extends ErrorMessages<JatosWorker> {

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

}
