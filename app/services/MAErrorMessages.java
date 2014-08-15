package services;

import models.workers.MAWorker;

/**
 * Special ErrorMessages for MAPublix (studies or components started via
 * MechArg's UI).
 * 
 * @author madsen
 */
public class MAErrorMessages extends ErrorMessages<MAWorker> {

	@Override
	public String workerNeverStartedStudy(MAWorker worker, Long studyId) {
		String errorMsg = "Worker " + worker.getId() + " ("
				+ worker.getUser().getEmail() + ")" + " never started study "
				+ studyId + ".";
		return errorMsg;
	}

	@Override
	public String workerNeverDidStudy(MAWorker worker, Long studyId) {
		String errorMsg = "Worker " + worker.getId() + " ("
				+ worker.getUser().getEmail() + ")" + " never did study "
				+ studyId + ".";
		return errorMsg;
	}

	@Override
	public String workerNotAllowedStudy(MAWorker worker, Long studyId) {
		String errorMsg = "Worker " + worker.getId() + " ("
				+ worker.getUser().getEmail() + ")" + " is not allowed to do "
				+ "study " + studyId + ".";
		return errorMsg;
	}

}
