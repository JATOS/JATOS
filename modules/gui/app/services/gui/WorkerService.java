package services.gui;

import daos.common.StudyResultDao;
import exceptions.gui.BadRequestException;
import models.common.Batch;
import models.common.workers.*;
import play.data.validation.ValidationError;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Service class for JATOS Controllers (not Publix).
 *
 * @author Kristian Lange
 */
@Singleton
public class WorkerService {

    private final StudyResultDao studyResultDao;

    @Inject
    WorkerService(StudyResultDao studyResultDao) {
        this.studyResultDao = studyResultDao;
    }

    public void validateWorker(Worker worker) throws BadRequestException {
        List<ValidationError> errorList = worker.validate();
        if (errorList != null && !errorList.isEmpty()) {
            String errorMsg = errorList.get(0).message();
            throw new BadRequestException(errorMsg);
        }
    }

    /**
     * Retrieves the count of StudyResults for each worker type in a map (
     * workerType -> count).
     */
    public Map<String, Integer> retrieveStudyResultCountsPerWorker(Batch batch) {
        Map<String, Integer> resultsPerWorker = new HashMap<>();
        Consumer<String> putCountToMap = (String workerType) -> resultsPerWorker
                .put(workerType, studyResultDao.countByBatchAndWorkerType(batch, workerType));
        putCountToMap.accept(JatosWorker.WORKER_TYPE);
        putCountToMap.accept(PersonalSingleWorker.WORKER_TYPE);
        putCountToMap.accept(PersonalMultipleWorker.WORKER_TYPE);
        putCountToMap.accept(GeneralSingleWorker.WORKER_TYPE);
        putCountToMap.accept(GeneralMultipleWorker.WORKER_TYPE);
        putCountToMap.accept(MTWorker.WORKER_TYPE);
        putCountToMap.accept(MTSandboxWorker.WORKER_TYPE);
        return resultsPerWorker;
    }

}
