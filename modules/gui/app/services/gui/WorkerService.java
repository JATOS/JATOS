package services.gui;

import daos.common.StudyResultDao;
import exceptions.gui.BadRequestException;
import models.common.Batch;
import models.common.workers.*;
import models.gui.ApiEnvelope.ErrorCode;
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
     * Retrieves the count of StudyResults for each worker type in a map ( workerType -> count).
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
        putCountToMap.accept(MTWorker.WORKER_TYPE); // Incl. MTSandbox worker
        return resultsPerWorker;
    }

    private static final Map<String, String> WORKER_TYPE_ALIASES = Map.ofEntries(
            Map.entry("jatos", JatosWorker.WORKER_TYPE),
            Map.entry("ja", JatosWorker.WORKER_TYPE),

            Map.entry("personalsingle", PersonalSingleWorker.WORKER_TYPE),
            Map.entry("ps", PersonalSingleWorker.WORKER_TYPE),

            Map.entry("personalmultiple", PersonalMultipleWorker.WORKER_TYPE),
            Map.entry("pm", PersonalMultipleWorker.WORKER_TYPE),

            Map.entry("generalsingle", GeneralSingleWorker.WORKER_TYPE),
            Map.entry("gs", GeneralSingleWorker.WORKER_TYPE),

            Map.entry("generalmultiple", GeneralMultipleWorker.WORKER_TYPE),
            Map.entry("gm", GeneralMultipleWorker.WORKER_TYPE),

            Map.entry("mturk", MTWorker.WORKER_TYPE),
            Map.entry("mt", MTWorker.WORKER_TYPE),

            Map.entry("mturksandbox", MTSandboxWorker.WORKER_TYPE),
            Map.entry("mts", MTSandboxWorker.WORKER_TYPE)
    );

    public static String extractWorkerType(String workerType) {
        if (workerType == null) return null;
        return WORKER_TYPE_ALIASES.get(workerType.toLowerCase());
    }

    public static String validateAndExtractWorkerType(String workerType) throws BadRequestException {
        if (workerType == null) return null;
        String normalized = extractWorkerType(workerType);
        if (normalized == null) {
            throw new BadRequestException("Unknown type", ErrorCode.VALIDATION_ERROR);
        }
        return normalized;
    }

    public static boolean isValidWorkerType(String workerType) {
        return extractWorkerType(workerType) != null;
    }

}
