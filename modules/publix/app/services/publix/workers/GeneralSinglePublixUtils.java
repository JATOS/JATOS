package services.publix.workers;

import controllers.publix.GeneralSingleGroupChannel;
import daos.common.*;
import daos.common.worker.WorkerDao;
import exceptions.publix.ForbiddenPublixException;
import group.GroupAdministration;
import models.common.StudyResult;
import models.common.StudyResult.StudyState;
import models.common.workers.GeneralSingleWorker;
import models.common.workers.Worker;
import services.publix.PublixErrorMessages;
import services.publix.PublixUtils;
import services.publix.ResultCreator;
import services.publix.idcookie.IdCookieService;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * GeneralSinglePublix' implementation of PublixUtils
 *
 * @author Kristian Lange
 */
@Singleton
public class GeneralSinglePublixUtils extends PublixUtils<GeneralSingleWorker> {

    @Inject
    GeneralSinglePublixUtils(ResultCreator resultCreator,
            IdCookieService idCookieService,
            GroupAdministration groupAdministration,
            GeneralSingleErrorMessages errorMessages, StudyDao studyDao,
            StudyResultDao studyResultDao, ComponentDao componentDao,
            ComponentResultDao componentResultDao, WorkerDao workerDao,
            BatchDao batchDao) {
        super(resultCreator, idCookieService, groupAdministration,
                errorMessages, studyDao, studyResultDao, componentDao,
                componentResultDao, workerDao, batchDao);
    }

    @Override
    public GeneralSingleWorker retrieveTypedWorker(Long workerId)
            throws ForbiddenPublixException {
        Worker worker = super.retrieveWorker(workerId);
        if (!(worker instanceof GeneralSingleWorker)) {
            throw new ForbiddenPublixException(
                    errorMessages.workerNotCorrectType(worker.getId()));
        }
        return (GeneralSingleWorker) worker;
    }

    /**
     * Returns the StudyResult that belongs to the worker corresponding to the
     * given worker ID (each GeneralSingleWorker can have only one StudyResult)
     * if and only if it is in StudyState PRE. If the StudyResult exist but is
     * not in StudyState PRE an ForbiddenPublixException is thrown.
     */
    public StudyResult retrievePreStudyResult(Long workerId)
            throws ForbiddenPublixException {
        GeneralSingleWorker worker = retrieveTypedWorker(workerId);
        // Every GeneralSingleWorker can have only one StudyResult
        StudyResult studyResult = worker.getLastStudyResult();
        if (studyResult != null
                && studyResult.getStudyState() == StudyState.PRE) {
            return studyResult;
        } else {
            throw new ForbiddenPublixException(
                    PublixErrorMessages.STUDY_CAN_BE_DONE_ONLY_ONCE);
        }
    }

}
