package controllers.publix.jatos;

import models.StudyModel;
import models.UserModel;
import models.workers.JatosWorker;
import models.workers.Worker;
import services.ErrorMessages;
import controllers.Users;
import controllers.publix.Publix;
import controllers.publix.PublixErrorMessages;
import controllers.publix.PublixUtils;
import exceptions.ForbiddenPublixException;
import exceptions.PublixException;

/**
 * Special PublixUtils for JatosPublix (studies or components started via JATOS'
 * UI).
 * 
 * @author Kristian Lange
 */
public class JatosPublixUtils extends PublixUtils<JatosWorker> {

	private JatosErrorMessages errorMessages;

	public JatosPublixUtils(JatosErrorMessages errorMessages) {
		super(errorMessages);
		this.errorMessages = errorMessages;
	}

	@Override
	public JatosWorker retrieveTypedWorker(String workerIdStr)
			throws PublixException {
		Worker worker = retrieveWorker(workerIdStr);
		if (!(worker instanceof JatosWorker)) {
			throw new ForbiddenPublixException(
					errorMessages.workerNotCorrectType(worker.getId()));
		}
		return (JatosWorker) worker;
	}

	public UserModel retrieveUser() throws ForbiddenPublixException {
		String email = Publix.session(Users.SESSION_EMAIL);
		if (email == null) {
			throw new ForbiddenPublixException(ErrorMessages.NO_USER_LOGGED_IN);
		}
		UserModel loggedInUser = UserModel.findByEmail(email);
		if (loggedInUser == null) {
			throw new ForbiddenPublixException(
					ErrorMessages.userNotExist(email));
		}
		return loggedInUser;
	}

	@Override
	public void checkWorkerAllowedToStartStudy(JatosWorker worker,
			StudyModel study) throws ForbiddenPublixException {
		checkWorkerAllowedToDoStudy(worker, study);
	}

	@Override
	public void checkWorkerAllowedToDoStudy(JatosWorker worker, StudyModel study)
			throws ForbiddenPublixException {
		if (!study.hasAllowedWorker(worker.getWorkerType())) {
			throw new ForbiddenPublixException(
					PublixErrorMessages.workerTypeNotAllowed(worker
							.getUIWorkerType()));
		}
		UserModel loggedInUser = worker.getUser();
		// User has to be a member of this study
		if (!study.hasMember(loggedInUser)) {
			throw new ForbiddenPublixException(
					errorMessages.workerNotAllowedStudy(worker, study.getId()));
		}
		// User has to be logged in
		String email = Publix.session(Users.SESSION_EMAIL);
		if (!loggedInUser.getEmail().equals(email)) {
			throw new ForbiddenPublixException(
					errorMessages.workerNotAllowedStudy(worker, study.getId()));
		}
	}

	public String retrieveJatosShowFromSession() throws ForbiddenPublixException {
		String jatosShow = Publix.session(JatosPublix.JATOS_SHOW);
		if (jatosShow == null) {
			throw new ForbiddenPublixException(
					PublixErrorMessages.STUDY_OR_COMPONENT_NEVER_STARTED_FROM_JATOS);
		}
		return jatosShow;
	}

}
