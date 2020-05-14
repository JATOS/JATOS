package services.publix.workers;

import javax.inject.Singleton;

import controllers.publix.workers.JatosPublix;
import models.common.workers.JatosWorker;
import services.publix.PublixErrorMessages;

/**
 * JatosPublix' implementation of PublixErrorMessages (studies or components
 * started via JATOS' UI).
 * 
 * @author Kristian Lange
 */
@Singleton
public class JatosErrorMessages extends PublixErrorMessages {

	public static final String NO_USER_LOGGED_IN = "No user logged in";
	public static final String STUDY_NEVER_STARTED_FROM_JATOS = "This study was never started from within JATOS.";
	public static final String STUDY_OR_COMPONENT_NEVER_STARTED_FROM_JATOS = "This study or component was never started from within JATOS.";
	public static final String MALFORMED_JATOS_RUN_SESSION_PARAMETER = "The " + JatosPublix.SESSION_JATOS_RUN
			+ " session parameter is malformed.";

	@Override
	public String workerNotCorrectType(Long workerId) {
		return "The worker with ID " + workerId + " isn't a " + JatosWorker.UI_WORKER_TYPE + " worker.";
	}

	public String userNotExist(String username) {
		return "An user " + username + " doesn't exist.";
	}

}
