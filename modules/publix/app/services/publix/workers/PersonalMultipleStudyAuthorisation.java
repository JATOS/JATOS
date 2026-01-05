package services.publix.workers;

import exceptions.common.ForbiddenException;
import models.common.Batch;
import models.common.Study;
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
public class PersonalMultipleStudyAuthorisation extends StudyAuthorisation {

    @Override
    public void checkWorkerAllowedToStartStudy(Http.Session session, Worker worker, Study study,
                                               Batch batch) {
        if (!study.isActive()) {
            throw new ForbiddenException(PublixErrorMessages.studyDeactivated(study.getId()));
        }
        if (!batch.isActive()) {
            throw new ForbiddenException(PublixErrorMessages.batchInactive(batch.getId()));
        }
        checkMaxTotalWorkers(batch, worker);
        checkWorkerAllowedToDoStudy(session, worker, study, batch);
    }

    @Override
    public void checkWorkerAllowedToDoStudy(Http.Session session, Worker worker, Study study, Batch batch) {
        // Check if the worker type is allowed
        if (!batch.hasAllowedWorkerType(worker.getWorkerType())) {
            throw new ForbiddenException(PublixErrorMessages
                    .workerTypeNotAllowed(worker.getWorkerType().uiValue(), study.getId(), batch.getId()));
        }
    }

}
