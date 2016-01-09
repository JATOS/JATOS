package services.publix.workers;

import javax.inject.Inject;
import javax.inject.Singleton;

import exceptions.publix.ForbiddenPublixException;
import models.common.Batch;
import models.common.Study;
import models.common.workers.GeneralSingleWorker;
import services.publix.StudyAuthorisation;
import services.publix.PublixErrorMessages;

/**
 * GeneralSinglePublix's implementation of StudyAuthorization
 * 
 * @author Kristian Lange
 */
@Singleton
public class GeneralSingleStudyAuthorisation
		extends StudyAuthorisation<GeneralSingleWorker> {

	private final GeneralSingleErrorMessages errorMessages;
	private final GeneralSinglePublixUtils publixUtils;

	@Inject
	GeneralSingleStudyAuthorisation(GeneralSinglePublixUtils publixUtils,
			GeneralSingleErrorMessages errorMessages) {
		super(publixUtils, errorMessages);
		this.errorMessages = errorMessages;
		this.publixUtils = publixUtils;
	}

	@Override
	public void checkWorkerAllowedToStartStudy(GeneralSingleWorker worker,
			Study study, Batch batch) throws ForbiddenPublixException {
		if (!batch.isActive()) {
			throw new ForbiddenPublixException(errorMessages
					.batchInactive(batch.getId()));
		}
		checkMaxTotalWorkers(batch);
		checkWorkerAllowedToDoStudy(worker, study, batch);
	}

	@Override
	public void checkWorkerAllowedToDoStudy(GeneralSingleWorker worker,
			Study study, Batch batch) throws ForbiddenPublixException {
		// Check if worker type is allowed
		if (!batch.hasAllowedWorkerType(worker.getWorkerType())) {
			throw new ForbiddenPublixException(errorMessages
					.workerTypeNotAllowed(worker.getUIWorkerType()));
		}
		// General single workers can't repeat the same study
		if (publixUtils.finishedStudyAlready(worker, study)) {
			throw new ForbiddenPublixException(
					PublixErrorMessages.STUDY_CAN_BE_DONE_ONLY_ONCE);
		}
	}

}
