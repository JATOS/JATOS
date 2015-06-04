package controllers.publix.jatos;

import models.StudyModel;
import models.UserModel;
import models.workers.JatosWorker;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import controllers.gui.Users;
import controllers.publix.IStudyAuthorisation;
import controllers.publix.Publix;
import exceptions.publix.ForbiddenPublixException;

/**
 * JatosPublix's Implementation of IStudyAuthorization
 * 
 * @author Kristian Lange
 */
@Singleton
public class JatosStudyAuthorisation implements
		IStudyAuthorisation<JatosWorker> {

	private final JatosErrorMessages errorMessages;

	@Inject
	JatosStudyAuthorisation(JatosPublixUtils publixUtils,
			JatosErrorMessages errorMessages) {
		this.errorMessages = errorMessages;
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
					errorMessages.workerTypeNotAllowed(worker.getUIWorkerType()));
		}
		UserModel user = worker.getUser();
		// User has to be a member of this study
		if (!study.hasMember(user)) {
			throw new ForbiddenPublixException(
					errorMessages.workerNotAllowedStudy(worker, study.getId()));
		}
		// User has to be logged in
		String email = Publix.session(Users.SESSION_EMAIL);
		if (!user.getEmail().equals(email)) {
			throw new ForbiddenPublixException(
					errorMessages.workerNotAllowedStudy(worker, study.getId()));
		}
	}

}
