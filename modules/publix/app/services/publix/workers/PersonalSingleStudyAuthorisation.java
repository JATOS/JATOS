package services.publix.workers;

import exceptions.publix.ForbiddenPublixException;
import models.common.Batch;
import models.common.Study;
import models.common.StudyResult;
import models.common.workers.PersonalSingleWorker;
import models.common.workers.Worker;
import play.mvc.Http;
import services.publix.PublixErrorMessages;
import services.publix.PublixHelpers;
import services.publix.StudyAuthorisation;

import javax.inject.Singleton;
import java.util.Optional;

/**
 * StudyAuthorization for PersonalSingleWorker
 *
 * @author Kristian Lange
 */
@Singleton
public class PersonalSingleStudyAuthorisation extends StudyAuthorisation<PersonalSingleWorker> {

    @Override
    public void checkWorkerAllowedToStartStudy(Http.Request request, Worker worker, Study study, Batch batch)
            throws ForbiddenPublixException {
        if (!batch.isActive()) {
            throw new ForbiddenPublixException(PublixErrorMessages.batchInactive(batch.getId()));
        }
        // Personal Single Runs are used only once - don't start if worker has a study result (although it is in
        // state PRE)
        Optional<StudyResult> first = worker.getFirstStudyResult();
        if (first.isPresent() && first.get().getStudyState() != StudyResult.StudyState.PRE) {
            throw new ForbiddenPublixException(PublixErrorMessages.STUDY_CAN_BE_DONE_ONLY_ONCE);
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
        // Personal single workers can't repeat the same study
        if (PublixHelpers.finishedStudyAlready(worker, study)) {
            throw new ForbiddenPublixException(PublixErrorMessages.STUDY_CAN_BE_DONE_ONLY_ONCE);
        }
    }

}
