package services.publix.workers;

import daos.common.StudyResultDao;
import daos.common.worker.WorkerDao;
import exceptions.common.ForbiddenException;
import models.common.Batch;
import models.common.Study;
import models.common.StudyResult;
import models.common.workers.Worker;
import play.mvc.Http;
import services.publix.PublixErrorMessages;
import services.publix.StudyAuthorisation;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;

/**
 * StudyAuthorization for PersonalSingleWorker
 *
 * @author Kristian Lange
 */
@Singleton
public class PersonalSingleStudyAuthorisation extends StudyAuthorisation {

    private final StudyResultDao studyResultDao;
    private final WorkerDao workerDao;

    @Inject
    PersonalSingleStudyAuthorisation(StudyResultDao studyResultDao,
                                     WorkerDao workerDao) {
        this.studyResultDao = studyResultDao;
        this.workerDao = workerDao;
    }

    @Override
    public void checkWorkerAllowedToStartStudy(Http.Session session, Worker worker, Study study, Batch batch) {
        if (!study.isActive()) {
            throw new ForbiddenException(PublixErrorMessages.studyDeactivated(study.getId()));
        }
        if (!batch.isActive()) {
            throw new ForbiddenException(PublixErrorMessages.batchInactive(batch.getId()));
        }
        // Personal Single Runs are used only once - don't start if the worker has a study result (although it is in
        // state PRE)
        Optional<StudyResult> first = workerDao.findFirstStudyResult(worker);
        if (first.isPresent() && first.get().getStudyState() != StudyResult.StudyState.PRE) {
            throw new ForbiddenException(PublixErrorMessages.STUDY_CAN_BE_DONE_ONLY_ONCE);
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
        // Personal single workers can't repeat the same study
        if (studyResultDao.hasFinishedStudy(worker, study)) {
            throw new ForbiddenException(PublixErrorMessages.STUDY_CAN_BE_DONE_ONLY_ONCE);
        }
    }

}
