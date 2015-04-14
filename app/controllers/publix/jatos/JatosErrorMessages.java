package controllers.publix.jatos;

import models.workers.JatosWorker;

import com.google.inject.Singleton;

import controllers.publix.PublixErrorMessages;

/**
 * Special PublixErrorMessages for JatosPublix (studies or components started via
 * JATOS' UI).
 * 
 * @author Kristian Lange
 */
@Singleton
public class JatosErrorMessages extends PublixErrorMessages<JatosWorker> {

	public static final String NO_USER_LOGGED_IN = "No user logged in";
	
	public String workerNotCorrectType(Long workerId) {
		String errorMsg = "The worker with ID " + workerId
				+ " isn't a JATOS worker.";
		return errorMsg;
	}
	
	public String userNotExist(String email) {
		String errorMsg = "An user with email " + email + " doesn't exist.";
		return errorMsg;
	}
	
}
