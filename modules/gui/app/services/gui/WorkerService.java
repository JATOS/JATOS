package services.gui;

import daos.common.StudyResultDao;
import daos.common.worker.WorkerType;
import exceptions.common.BadRequestException;
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

    public void validateWorker(Worker worker) {
        List<ValidationError> errorList = worker.validate();
        if (errorList != null && !errorList.isEmpty()) {
            String errorMsg = errorList.get(0).message();
            throw new BadRequestException(errorMsg);
        }
    }

    /**
     * Retrieves the count of StudyResults for each worker type in a map (workerType -> count).
     */
    public Map<String, Integer> retrieveStudyResultCountsPerWorker(Batch batch) {
        Map<String, Integer> resultsPerWorker = new HashMap<>();
        Consumer<WorkerType> putCountToMap = (WorkerType workerType) -> resultsPerWorker
                .put(workerType.value(), studyResultDao.countByBatchAndWorkerType(batch, workerType));
        putCountToMap.accept(WorkerType.JATOS);
        putCountToMap.accept(WorkerType.PERSONAL_SINGLE);
        putCountToMap.accept(WorkerType.PERSONAL_MULTIPLE);
        putCountToMap.accept(WorkerType.GENERAL_SINGLE);
        putCountToMap.accept(WorkerType.PERSONAL_MULTIPLE);
        putCountToMap.accept(WorkerType.MT); // Incl. MTSandbox worker
        return resultsPerWorker;
    }

}
