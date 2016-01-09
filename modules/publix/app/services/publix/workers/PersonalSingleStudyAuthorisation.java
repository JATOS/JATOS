package services.publix.workers;

import javax.inject.Inject;
import javax.inject.Singleton;

import exceptions.publix.ForbiddenPublixException;
import models.common.Batch;
import models.common.Study;
import models.common.workers.PersonalSingleWorker;
import services.publix.StudyAuthorisation;
import services.publix.PublixErrorMessages;

/**
 * PersonalSinglePublix's implementation of StudyAuthorization
 * 
 * @author Kristian Lange
 */
@Singleton
public class PersonalSingleStudyAuthorisation
		extends StudyAuthorisation<PersonalSingleWorker> {

	private final PersonalSinglePublixUtils publixUtils;
	private final PersonalSingleErrorMessages errorMessages;

	@Inject
	PersonalSingleStudyAuthorisation(PersonalSinglePublixUtils publixUtils,
			PersonalSingleErrorMessages errorMessages) {
		super(publixUtils, errorMessages);
		this.publixUtils = publixUtils;
		this.errorMessages = errorMessages;
	}

	@Override
	public void checkWorkerAllowedToStartStudy(PersonalSingleWorker worker,
			Study study, Batch batch) throws ForbiddenPublixException {
		if (!batch.isActive()) {
			throw new ForbiddenPublixException(errorMessages
					.batchInactive(batch.getId()));
		}
		// Personal Single Runs are used only once - don't start if worker has a
		// study result
		if (!worker.getStudyResultList().isEmpty()) {
			throw new ForbiddenPublixException(
					PublixErrorMessages.STUDY_CAN_BE_DONE_ONLY_ONCE);
		}
		checkMaxTotalWorkers(batch);
		checkWorkerAllowedToDoStudy(worker, study, batch);
	}

	@Override
	public void checkWorkerAllowedToDoStudy(PersonalSingleWorker worker,
			Study study, Batch batch) throws ForbiddenPublixException {
		// Check if worker type is allowed
		if (!batch.hasAllowedWorkerType(worker.getWorkerType())) {
			throw new ForbiddenPublixException(errorMessages
					.workerTypeNotAllowed(worker.getUIWorkerType()));
		}
		// Personal single workers can't repeat the same study
		if (publixUtils.finishedStudyAlready(worker, study)) {
			throw new ForbiddenPublixException(
					PublixErrorMessages.STUDY_CAN_BE_DONE_ONLY_ONCE);
		}
	}

}
