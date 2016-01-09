package services.publix.workers;

import javax.inject.Inject;
import javax.inject.Singleton;

import exceptions.publix.ForbiddenPublixException;
import models.common.Batch;
import models.common.Study;
import models.common.workers.MTSandboxWorker;
import models.common.workers.MTWorker;
import services.publix.StudyAuthorisation;
import services.publix.PublixErrorMessages;

/**
 * PersonalMultiplePublix's implementation of StudyAuthorization
 * 
 * @author Kristian Lange
 */
@Singleton
public class MTStudyAuthorisation extends StudyAuthorisation<MTWorker> {

	private final MTPublixUtils publixUtils;
	private final MTErrorMessages errorMessages;

	@Inject
	MTStudyAuthorisation(MTPublixUtils publixUtils,
			MTErrorMessages errorMessages) {
		super(publixUtils, errorMessages);
		this.publixUtils = publixUtils;
		this.errorMessages = errorMessages;
	}

	@Override
	public void checkWorkerAllowedToStartStudy(MTWorker worker, Study study,
			Batch batch) throws ForbiddenPublixException {
		if (!batch.isActive()) {
			throw new ForbiddenPublixException(errorMessages
					.batchInactive(batch.getId()));
		}
		if (!(worker instanceof MTSandboxWorker)
				&& publixUtils.didStudyAlready(worker, study)) {
			throw new ForbiddenPublixException(
					PublixErrorMessages.STUDY_CAN_BE_DONE_ONLY_ONCE);
		}
		checkMaxTotalWorkers(batch);
		checkWorkerAllowedToDoStudy(worker, study, batch);
	}

	@Override
	public void checkWorkerAllowedToDoStudy(MTWorker worker, Study study,
			Batch batch) throws ForbiddenPublixException {
		// Check if worker type is allowed
		if (!batch.hasAllowedWorkerType(worker.getWorkerType())) {
			throw new ForbiddenPublixException(errorMessages
					.workerTypeNotAllowed(worker.getUIWorkerType()));
		}
		// Sandbox workers can repeat studies
		if (worker instanceof MTSandboxWorker) {
			return;
		}
		// MTurk workers can't repeat studies
		if (publixUtils.finishedStudyAlready(worker, study)) {
			throw new ForbiddenPublixException(
					PublixErrorMessages.STUDY_CAN_BE_DONE_ONLY_ONCE);
		}
	}

}
