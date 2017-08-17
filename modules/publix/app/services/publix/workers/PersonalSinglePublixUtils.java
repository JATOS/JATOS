package services.publix.workers;

import daos.common.*;
import daos.common.worker.WorkerDao;
import exceptions.publix.ForbiddenPublixException;
import group.GroupAdministration;
import models.common.workers.PersonalSingleWorker;
import models.common.workers.Worker;
import services.publix.PublixErrorMessages;
import services.publix.PublixUtils;
import services.publix.ResultCreator;
import services.publix.idcookie.IdCookieService;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * PersonalSinglePublix' implementation of PublixUtils
 *
 * @author Kristian Lange
 */
@Singleton
public class PersonalSinglePublixUtils
        extends PublixUtils<PersonalSingleWorker> {

    @Inject
    PersonalSinglePublixUtils(ResultCreator resultCreator,
            IdCookieService idCookieService,
            GroupAdministration groupAdministration,
            PersonalSingleErrorMessages errorMessages, StudyDao studyDao,
            StudyResultDao studyResultDao, ComponentDao componentDao,
            ComponentResultDao componentResultDao, WorkerDao workerDao,
            BatchDao batchDao) {
        super(resultCreator, idCookieService, groupAdministration,
                errorMessages, studyDao, studyResultDao, componentDao,
                componentResultDao, workerDao, batchDao);
    }

    @Override
    public PersonalSingleWorker retrieveTypedWorker(Long workerId)
            throws ForbiddenPublixException {
        Worker worker = super.retrieveWorker(workerId);
        if (!(worker instanceof PersonalSingleWorker)) {
            throw new ForbiddenPublixException(
                    errorMessages.workerNotCorrectType(worker.getId()));
        }
        return (PersonalSingleWorker) worker;
    }

    public PersonalSingleWorker retrieveTypedWorker(String workerIdStr)
            throws ForbiddenPublixException {
        if (workerIdStr == null) {
            throw new ForbiddenPublixException(
                    PublixErrorMessages.NO_WORKER_IN_QUERY_STRING);
        }
        long workerId;
        try {
            workerId = Long.parseLong(workerIdStr);
        } catch (NumberFormatException e) {
            throw new ForbiddenPublixException(
                    PublixErrorMessages.workerNotExist(workerIdStr));
        }
        return retrieveTypedWorker(workerId);
    }

}
