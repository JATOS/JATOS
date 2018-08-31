package services.publix.workers;

import exceptions.publix.ForbiddenPublixException;
import models.common.Batch;
import models.common.Study;
import models.common.workers.GeneralMultipleWorker;
import services.publix.PublixErrorMessages;
import services.publix.StudyAuthorisation;

import javax.inject.Singleton;

/**
 * GeneralMultiplePublix's implementation of StudyAuthorization
 *
 * @author Kristian Lange
 */
@Singleton
public class GeneralMultipleStudyAuthorisation extends StudyAuthorisation<GeneralMultipleWorker> {

    @Override
    public void checkWorkerAllowedToStartStudy(GeneralMultipleWorker worker, Study study,
            Batch batch) throws ForbiddenPublixException {
        if (!batch.isActive()) {
            throw new ForbiddenPublixException(PublixErrorMessages.batchInactive(batch.getId()));
        }
        checkMaxTotalWorkers(batch, worker);
        checkWorkerAllowedToDoStudy(worker, study, batch);
    }

    @Override
    public void checkWorkerAllowedToDoStudy(GeneralMultipleWorker worker, Study study, Batch batch)
            throws ForbiddenPublixException {
        // Check if worker type is allowed
        if (!batch.hasAllowedWorkerType(worker.getWorkerType())) {
            throw new ForbiddenPublixException(PublixErrorMessages
                    .workerTypeNotAllowed(worker.getUIWorkerType(), study.getId(), batch.getId()));
        }
    }

}
