package publix.services.jatos;

import javax.inject.Inject;
import javax.inject.Singleton;

import models.Study;
import models.User;
import models.workers.JatosWorker;
import publix.controllers.Publix;
import publix.exceptions.ForbiddenPublixException;
import publix.services.IStudyAuthorisation;
import controllers.Users;

/**
 * JatosPublix's Implementation of IStudyAuthorization
 * 
 * @author Kristian Lange
 */
@Singleton
public class JatosStudyAuthorisation implements IStudyAuthorisation<JatosWorker> {

	private final JatosErrorMessages errorMessages;

	@Inject
	JatosStudyAuthorisation(JatosErrorMessages errorMessages) {
		this.errorMessages = errorMessages;
	}

	@Override
	public void checkWorkerAllowedToStartStudy(JatosWorker worker,
			Study study) throws ForbiddenPublixException {
		checkWorkerAllowedToDoStudy(worker, study);
	}

	@Override
	public void checkWorkerAllowedToDoStudy(JatosWorker worker, Study study)
			throws ForbiddenPublixException {
		if (!study.hasAllowedWorkerType(worker.getWorkerType())) {
			throw new ForbiddenPublixException(
					errorMessages.workerTypeNotAllowed(worker.getUIWorkerType()));
		}
		User user = worker.getUser();
		// User has to be a user of this study
		if (!study.hasUser(user)) {
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
