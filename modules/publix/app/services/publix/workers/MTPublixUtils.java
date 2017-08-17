package services.publix.workers;

import daos.common.*;
import daos.common.worker.WorkerDao;
import exceptions.publix.ForbiddenPublixException;
import group.GroupAdministration;
import models.common.workers.MTWorker;
import models.common.workers.Worker;
import services.publix.PublixUtils;
import services.publix.ResultCreator;
import services.publix.idcookie.IdCookieService;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * MTPublix' implementation of PublixUtils (studies started via MTurk).
 *
 * @author Kristian Lange
 */
@Singleton
public class MTPublixUtils extends PublixUtils<MTWorker> {

    @Inject
    MTPublixUtils(ResultCreator resultCreator, IdCookieService idCookieService,
            GroupAdministration groupAdministration,
            MTErrorMessages errorMessages, StudyDao studyDao,
            StudyResultDao studyResultDao, ComponentDao componentDao,
            ComponentResultDao componentResultDao, WorkerDao workerDao,
            BatchDao batchDao) {
        super(resultCreator, idCookieService, groupAdministration,
                errorMessages, studyDao, studyResultDao, componentDao,
                componentResultDao, workerDao, batchDao);
    }

    @Override
    public MTWorker retrieveTypedWorker(Long workerId)
            throws ForbiddenPublixException {
        Worker worker = super.retrieveWorker(workerId);
        if (!(worker instanceof MTWorker)) {
            throw new ForbiddenPublixException(
                    errorMessages.workerNotCorrectType(worker.getId()));
        }
        return (MTWorker) worker;
    }

}
