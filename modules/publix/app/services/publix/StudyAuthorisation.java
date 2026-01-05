package services.publix;

import daos.common.BatchDao;
import exceptions.common.ForbiddenException;
import models.common.Batch;
import models.common.Study;
import models.common.workers.Worker;
import play.mvc.Http;

import javax.inject.Inject;

public abstract class StudyAuthorisation {

    @Inject
    private BatchDao batchDao;

    /**
     * Checks whether the given worker is allowed to start this study in this batch. If the worker has no permission, a
     * ForbiddenException is thrown. This method should only be used during the start of a study.
     */
    public abstract void checkWorkerAllowedToStartStudy(Http.Session session, Worker worker, Study study,
                                                        Batch batch);

    /**
     * Checks whether the given worker is allowed to do this study in this batch. If the worker has no permission, a
     * ForbiddenException is thrown. This method can be used during all states of a StudyResult.
     */
    public abstract void checkWorkerAllowedToDoStudy(Http.Session session, Worker worker, Study study,
                                                     Batch batch);

    /**
     * Check if the max total worker number is reached for this batch. Only non-JatosWorker count here.
     */
    public void checkMaxTotalWorkers(Batch batch, Worker worker) {
        if (batchDao.isMaxTotalReached(batch, worker)) {
            throw new ForbiddenException(PublixErrorMessages.batchMaxTotalWorkerReached(batch.getId()));
        }
    }

}
