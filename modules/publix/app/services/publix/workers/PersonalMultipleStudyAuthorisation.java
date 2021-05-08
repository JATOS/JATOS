package services.publix.workers;

import javax.inject.Singleton;

import exceptions.publix.ForbiddenPublixException;
import models.common.Batch;
import models.common.Study;
import models.common.workers.PersonalMultipleWorker;
import services.publix.PublixErrorMessages;
import services.publix.StudyAuthorisation;

/**
 * StudyAuthorization for PersonalMultipleWorker
 *
 * @author Kristian Lange
 */
@Singleton
public class PersonalMultipleStudyAuthorisation extends StudyAuthorisation<PersonalMultipleWorker> {

    @Override
    public void checkWorkerAllowedToStartStudy(PersonalMultipleWorker worker, Study study,
            Batch batch) throws ForbiddenPublixException {
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
    public void checkWorkerAllowedToDoStudy(PersonalMultipleWorker worker, Study study, Batch batch)
            throws ForbiddenPublixException {
        // Check if worker type is allowed
        if (!batch.hasAllowedWorkerType(worker.getWorkerType())) {
            throw new ForbiddenPublixException(PublixErrorMessages
                    .workerTypeNotAllowed(worker.getUIWorkerType(), study.getId(), batch.getId()));
        }
    }

}
