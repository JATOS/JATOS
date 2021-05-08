package services.publix.workers;

import javax.inject.Singleton;

import exceptions.publix.ForbiddenPublixException;
import models.common.Batch;
import models.common.Study;
import models.common.workers.MTWorker;
import services.publix.PublixErrorMessages;
import services.publix.StudyAuthorisation;

/**
 * PersonalMultiplePublix's implementation of StudyAuthorization
 *
 * @author Kristian Lange
 */
@Singleton
public class MTStudyAuthorisation extends StudyAuthorisation<MTWorker> {

    @Override
    public void checkWorkerAllowedToStartStudy(MTWorker worker, Study study, Batch batch)
            throws ForbiddenPublixException {
        if (!study.isActive()) {
            throw new ForbiddenPublixException(PublixErrorMessages.studyDeactivated(study.getId()));
        }
        if (!batch.isActive()) {
            throw new ForbiddenPublixException(PublixErrorMessages.batchInactive(batch.getId()));
        }
        checkMaxTotalWorkers(batch, worker);
        checkWorkerAllowedToDoStudy(worker, study, batch);
    }

    @Override
    public void checkWorkerAllowedToDoStudy(MTWorker worker, Study study, Batch batch)
            throws ForbiddenPublixException {
        // Check if worker type is allowed
        if (!batch.hasAllowedWorkerType(MTWorker.WORKER_TYPE)) {
            throw new ForbiddenPublixException(PublixErrorMessages
                    .workerTypeNotAllowed(worker.getUIWorkerType(), study.getId(), batch.getId()));
        }
    }

}
