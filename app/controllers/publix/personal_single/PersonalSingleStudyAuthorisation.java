package controllers.publix.personal_single;

import models.StudyModel;
import models.workers.PersonalSingleWorker;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import controllers.publix.IStudyAuthorisation;
import controllers.publix.PublixErrorMessages;
import exceptions.publix.ForbiddenPublixException;

/**
 * PersonalSinglePublix's Implementation of IStudyAuthorization
 * 
 * @author Kristian Lange
 */
@Singleton
public class PersonalSingleStudyAuthorisation implements
		IStudyAuthorisation<PersonalSingleWorker> {

	private final PersonalSinglePublixUtils publixUtils;
	private final PersonalSingleErrorMessages errorMessages;

	@Inject
	PersonalSingleStudyAuthorisation(PersonalSinglePublixUtils publixUtils,
			PersonalSingleErrorMessages errorMessages) {
		this.publixUtils = publixUtils;
		this.errorMessages = errorMessages;
	}

	@Override
	public void checkWorkerAllowedToStartStudy(PersonalSingleWorker worker,
			StudyModel study) throws ForbiddenPublixException {
		// Personal Single Runs are used only once - don't start if worker has a
		// study result
		if (!worker.getStudyResultList().isEmpty()) {
			throw new ForbiddenPublixException(
					PublixErrorMessages.STUDY_CAN_BE_DONE_ONLY_ONCE);
		}
		checkWorkerAllowedToDoStudy(worker, study);
	}

	@Override
	public void checkWorkerAllowedToDoStudy(PersonalSingleWorker worker,
			StudyModel study) throws ForbiddenPublixException {
		if (!study.hasAllowedWorker(worker.getWorkerType())) {
			throw new ForbiddenPublixException(
					errorMessages.workerTypeNotAllowed(worker.getUIWorkerType()));
		}
		// Personal single workers can't repeat the same study
		if (publixUtils.finishedStudyAlready(worker, study)) {
			throw new ForbiddenPublixException(
					PublixErrorMessages.STUDY_CAN_BE_DONE_ONLY_ONCE);
		}
	}

}
