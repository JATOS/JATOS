package services.publix.workers;

import controllers.publix.workers.GeneralMultiplePublix;
import daos.common.*;
import daos.common.worker.WorkerDao;
import exceptions.publix.ForbiddenPublixException;
import general.common.StudyLogger;
import group.GroupAdministration;
import models.common.workers.GeneralMultipleWorker;
import models.common.workers.Worker;
import play.mvc.Http;
import services.publix.PublixUtils;
import services.publix.ResultCreator;
import services.publix.idcookie.IdCookieService;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

/**
 * GeneralMultiplePublix' implementation of PublixUtils
 *
 * @author Kristian Lange
 */
@Singleton
public class GeneralMultiplePublixUtils extends PublixUtils<GeneralMultipleWorker> {

    @Inject
    GeneralMultiplePublixUtils(ResultCreator resultCreator,
            IdCookieService idCookieService,
            GroupAdministration groupAdministration,
            GeneralMultipleErrorMessages errorMessages, StudyDao studyDao,
            StudyResultDao studyResultDao, ComponentDao componentDao,
            ComponentResultDao componentResultDao, WorkerDao workerDao,
            BatchDao batchDao, StudyLogger studyLogger) {
        super(resultCreator, idCookieService, groupAdministration,
                errorMessages, studyDao, studyResultDao, componentDao,
                componentResultDao, workerDao, batchDao, studyLogger);
    }

    @Override
    public GeneralMultipleWorker retrieveTypedWorker(Long workerId)
            throws ForbiddenPublixException {
        Worker worker = super.retrieveWorker(workerId);
        if (!(worker instanceof GeneralMultipleWorker)) {
            throw new ForbiddenPublixException(errorMessages.workerNotCorrectType(worker.getId()));
        }
        return (GeneralMultipleWorker) worker;
    }

    @Override
    public Map<String, String> getNonJatosUrlQueryParameters() {
        Map<String, String> queryMap = new HashMap<>();
        Http.Context.current().request().queryString().forEach((k, v) -> queryMap.put(k, v[0]));
        queryMap.remove(GeneralMultiplePublix.GENERALMULTIPLE);
        queryMap.remove("batchId");
        return queryMap;
    }

}
