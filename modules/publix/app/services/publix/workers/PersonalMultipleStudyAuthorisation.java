package services.publix.workers;

import exceptions.publix.ForbiddenPublixException;
import models.common.Batch;
import models.common.Study;
import models.common.workers.PersonalMultipleWorker;
import models.common.workers.Worker;
import play.mvc.Http;
import services.publix.PublixErrorMessages;
import services.publix.StudyAuthorisation;

import javax.inject.Singleton;

/**
 * StudyAuthorization for PersonalMultipleWorker
 *
 * @author Kristian Lange
 */
@Singleton
public class PersonalMultipleStudyAuthorisation extends StudyAuthorisation<PersonalMultipleWorker> {

    @Override
    public void checkWorkerAllowedToStartStudy(Http.Request request, Worker worker, Study study,
            Batch batch) throws ForbiddenPublixException {
        if (!study.isActive()) {
            throw new ForbiddenPublixException(PublixErrorMessages.studyDeactivated(study.getId()));
        }
        if (!batch.isActive()) {
            throw new ForbiddenPublixException(PublixErrorMessages.batchInactive(batch.getId()));
        }
        checkMaxTotalWorkers(batch, worker);
        checkWorkerAllowedToDoStudy(request, worker, study, batch);
    }

    @Override
    public void checkWorkerAllowedToDoStudy(Http.Request request, Worker worker, Study study, Batch batch)
            throws ForbiddenPublixException {
        // Check if worker type is allowed
        if (!batch.hasAllowedWorkerType(worker.getWorkerType())) {
            throw new ForbiddenPublixException(PublixErrorMessages
                    .workerTypeNotAllowed(worker.getUIWorkerType(), study.getId(), batch.getId()));
        }
    }

}
