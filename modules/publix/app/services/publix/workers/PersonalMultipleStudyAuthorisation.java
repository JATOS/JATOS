package services.publix.workers;

import exceptions.common.ForbiddenException;
import models.common.Batch;
import models.common.Study;
import models.common.workers.Worker;
import services.publix.PublixErrorMessages;
import services.publix.StudyAuthorisation;

import javax.inject.Singleton;

/**
 * StudyAuthorization for PersonalMultipleWorker
 */
@Singleton
public class PersonalMultipleStudyAuthorisation extends StudyAuthorisation {

    @Override
    public void checkWorkerAllowedToStartStudy(Worker worker, Study study, Batch batch) {
        if (!study.isActive()) {
            throw new ForbiddenException(PublixErrorMessages.studyDeactivated(study.getId()));
        }
        if (!batch.isActive()) {
            throw new ForbiddenException(PublixErrorMessages.batchInactive(batch.getId()));
        }
        checkMaxTotalWorkers(batch, worker);
        checkWorkerAllowedToDoStudy(worker, study, batch);
    }

    @Override
    public void checkWorkerAllowedToDoStudy(Worker worker, Study study, Batch batch) {
        // Check if the worker type is allowed
        if (!batch.hasAllowedWorkerType(worker.getWorkerType())) {
            throw new ForbiddenException(PublixErrorMessages
                    .workerTypeNotAllowed(worker.getWorkerType().uiValue(), study.getId(), batch.getId()));
        }
    }

}
