package services.publix.workers;

import javax.inject.Singleton;

import exceptions.publix.ForbiddenPublixException;
import models.common.Batch;
import models.common.Study;
import models.common.workers.GeneralSingleWorker;
import services.publix.PublixErrorMessages;
import services.publix.PublixHelpers;
import services.publix.StudyAuthorisation;

/**
 * GeneralSinglePublix's implementation of StudyAuthorization
 * 
 * @author Kristian Lange
 */
@Singleton
public class GeneralSingleStudyAuthorisation
		extends StudyAuthorisation<GeneralSingleWorker> {

	@Override
	public void checkWorkerAllowedToStartStudy(GeneralSingleWorker worker,
			Study study, Batch batch) throws ForbiddenPublixException {
		if (!batch.isActive()) {
			throw new ForbiddenPublixException(
					PublixErrorMessages.batchInactive(batch.getId()));
		}
		checkMaxTotalWorkers(batch, worker);
		checkWorkerAllowedToDoStudy(worker, study, batch);
	}

	@Override
	public void checkWorkerAllowedToDoStudy(GeneralSingleWorker worker,
			Study study, Batch batch) throws ForbiddenPublixException {
		// Check if worker type is allowed
		if (!batch.hasAllowedWorkerType(worker.getWorkerType())) {
			throw new ForbiddenPublixException(PublixErrorMessages
					.workerTypeNotAllowed(worker.getUIWorkerType(),
							study.getId(), batch.getId()));
		}
		// General single workers can't repeat the same study
		if (PublixHelpers.finishedStudyAlready(worker, study)) {
			throw new ForbiddenPublixException(
					PublixErrorMessages.STUDY_CAN_BE_DONE_ONLY_ONCE);
		}
	}

}
