package publix.services.personal_single;

import javax.inject.Inject;
import javax.inject.Singleton;

import models.Study;
import models.workers.PersonalSingleWorker;
import publix.exceptions.ForbiddenPublixException;
import publix.services.IStudyAuthorisation;
import publix.services.PublixErrorMessages;

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
			Study study) throws ForbiddenPublixException {
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
			Study study) throws ForbiddenPublixException {
		if (!study.hasAllowedWorkerType(worker.getWorkerType())) {
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
