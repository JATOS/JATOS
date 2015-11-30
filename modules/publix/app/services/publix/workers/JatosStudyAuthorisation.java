package services.publix.workers;

import javax.inject.Inject;
import javax.inject.Singleton;

import models.common.Study;
import models.common.User;
import models.common.workers.JatosWorker;
import services.publix.IStudyAuthorisation;
import controllers.publix.Publix;
import controllers.publix.workers.JatosPublix;
import exceptions.publix.ForbiddenPublixException;

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
		if (!study.getGroupList().get(0).hasAllowedWorkerType(worker.getWorkerType())) {
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
		String email = Publix.session(JatosPublix.SESSION_EMAIL);
		if (!user.getEmail().equals(email)) {
			throw new ForbiddenPublixException(
					errorMessages.workerNotAllowedStudy(worker, study.getId()));
		}
	}

}
