package services.publix.workers;

import javax.inject.Singleton;

import controllers.publix.Publix;
import controllers.publix.workers.JatosPublix;
import exceptions.publix.ForbiddenPublixException;
import models.common.Batch;
import models.common.Study;
import models.common.User;
import models.common.workers.JatosWorker;
import services.publix.PublixErrorMessages;
import services.publix.StudyAuthorisation;

/**
 * JatosPublix's implementation of StudyAuthorization
 * 
 * @author Kristian Lange
 */
@Singleton
public class JatosStudyAuthorisation extends StudyAuthorisation<JatosWorker> {

	@Override
	public void checkWorkerAllowedToStartStudy(JatosWorker worker, Study study,
			Batch batch) throws ForbiddenPublixException {
		if (!batch.isActive()) {
			throw new ForbiddenPublixException(
					PublixErrorMessages.batchInactive(batch.getId()));
		}
		checkMaxTotalWorkers(batch, worker);
		checkWorkerAllowedToDoStudy(worker, study, batch);
	}

	@Override
	public void checkWorkerAllowedToDoStudy(JatosWorker worker, Study study,
			Batch batch) throws ForbiddenPublixException {
		// Check if worker type is allowed
		if (!batch.hasAllowedWorkerType(worker.getWorkerType())) {
			throw new ForbiddenPublixException(PublixErrorMessages
					.workerTypeNotAllowed(worker.getUIWorkerType(),
							study.getId(), batch.getId()));
		}
		User user = worker.getUser();
		// User has to be a user of this study
		if (!study.hasUser(user)) {
			throw new ForbiddenPublixException(PublixErrorMessages
					.workerNotAllowedStudy(worker, study.getId()));
		}
		// User has to be logged in
		String email = Publix.session(JatosPublix.SESSION_USER_EMAIL);
		if (!user.getEmail().equals(email)) {
			throw new ForbiddenPublixException(PublixErrorMessages
					.workerNotAllowedStudy(worker, study.getId()));
		}
	}

}
