package services.publix.workers;

import controllers.publix.workers.PersonalMultiplePublix;
import daos.common.*;
import daos.common.worker.WorkerDao;
import exceptions.publix.ForbiddenPublixException;
import general.common.StudyLogger;
import group.GroupAdministration;
import models.common.workers.PersonalMultipleWorker;
import models.common.workers.Worker;
import play.mvc.Http;
import services.publix.PublixErrorMessages;
import services.publix.PublixUtils;
import services.publix.ResultCreator;
import services.publix.idcookie.IdCookieService;
import utils.common.IOUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

/**
 * PersonalMultiplePublix' implementation of PublixUtils
 *
 * @author Kristian Lange
 */
@Singleton
public class PersonalMultiplePublixUtils
        extends PublixUtils<PersonalMultipleWorker> {

    @Inject
    PersonalMultiplePublixUtils(ResultCreator resultCreator,
            IdCookieService idCookieService,
            GroupAdministration groupAdministration,
            PersonalMultipleErrorMessages errorMessages, StudyDao studyDao,
            StudyResultDao studyResultDao, ComponentDao componentDao,
            ComponentResultDao componentResultDao, WorkerDao workerDao,
            BatchDao batchDao, StudyLogger studyLogger, IOUtils ioUtils) {
        super(resultCreator, idCookieService, groupAdministration,
                errorMessages, studyDao, studyResultDao, componentDao,
                componentResultDao, workerDao, batchDao, studyLogger, ioUtils);
    }

    @Override
    public PersonalMultipleWorker retrieveTypedWorker(Long workerId)
            throws ForbiddenPublixException {
        Worker worker = super.retrieveWorker(workerId);
        if (!(worker instanceof PersonalMultipleWorker)) {
            throw new ForbiddenPublixException(
                    errorMessages.workerNotCorrectType(worker.getId()));
        }
        return (PersonalMultipleWorker) worker;
    }

    public PersonalMultipleWorker retrieveTypedWorker(String workerIdStr)
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

    @Override
    public Map<String, String> getNonJatosUrlQueryParameters() {
        Map<String, String> queryMap = new HashMap<>();
        Http.Context.current().request().queryString().forEach((k, v) -> queryMap.put(k, v[0]));
        queryMap.remove(PersonalMultiplePublix.PERSONAL_MULTIPLE_WORKER_ID);
        queryMap.remove("batchId");
        return queryMap;
    }

}
