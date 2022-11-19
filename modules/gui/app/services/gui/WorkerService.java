package services.gui;

import daos.common.StudyResultDao;
import exceptions.gui.BadRequestException;
import models.common.Batch;
import models.common.Study;
import models.common.workers.*;
import play.data.validation.ValidationError;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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

    /**
     * Retrieve all workers that belong to the study including the ones that were not started yet
     */
    public Set<Worker> retrieveAllWorkers(Study study) {
        return study.getBatchList().stream()
                .map(Batch::getWorkerList)
                .flatMap(Collection::stream)
                .filter(w -> !w.getWorkerType().equals(JatosWorker.WORKER_TYPE))
                .collect(Collectors.toSet());
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

    public String extractWorkerType(String workerType) throws BadRequestException {
        if (workerType == null) return null;
        switch (workerType.toLowerCase()) {
            case "jatos":
            case "ja":
                return JatosWorker.WORKER_TYPE;
            case "personalsingle":
            case "ps":
                return PersonalSingleWorker.WORKER_TYPE;
            case "personalmultiple":
            case "pm":
                return PersonalMultipleWorker.WORKER_TYPE;
            case "generalsingle":
            case "gs":
                return GeneralSingleWorker.WORKER_TYPE;
            case "generalmultiple":
            case "gm":
                return GeneralMultipleWorker.WORKER_TYPE;
            case "mturk":
            case "mt":
                return MTWorker.WORKER_TYPE;
            case "mturksandbox":
            case "mts":
                return MTSandboxWorker.WORKER_TYPE;
            default:
                throw new BadRequestException("Unknown worker type");
        }
    }

}
