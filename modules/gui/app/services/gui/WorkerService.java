package services.gui;

import daos.common.BatchDao;
import daos.common.StudyResultDao;
import daos.common.worker.WorkerDao;
import exceptions.gui.BadRequestException;
import models.common.Batch;
import models.common.Study;
import models.common.workers.*;
import play.data.validation.ValidationError;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service class for JATOS Controllers (not Publix).
 *
 * @author Kristian Lange
 */
@Singleton
public class WorkerService {

    private final StudyResultDao studyResultDao;
    private final WorkerDao workerDao;
    private final BatchDao batchDao;

    @Inject
    WorkerService(StudyResultDao studyResultDao, WorkerDao workerDao,
            BatchDao batchDao) {
        this.studyResultDao = studyResultDao;
        this.workerDao = workerDao;
        this.batchDao = batchDao;
    }

    /**
     * Retrieve all workers that belong to the study including the ones that were not started yet
     */
    public Set<Worker> retrieveAllWorkers(Study study) {
        return study.getBatchList().stream()
                .map(Batch::getWorkerList)
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());
    }

    /**
     * Creates, validates and persists PersonalSingleWorker(s).
     *
     * @param comment Each worker will get this label
     * @param amount  The number of workers to be created
     * @param batch   Each worker will belong to this batch
     * @return list of created workers
     */
    public List<PersonalSingleWorker> createAndPersistPersonalSingleWorker(
            String comment, int amount, Batch batch) throws BadRequestException {
        Function<String, PersonalSingleWorker> workerConstructor = PersonalSingleWorker::new;
        return createAndPersistWorker(comment, amount, batch, workerConstructor);
    }

    /**
     * Creates, validates and persists PersonalMultipleWorker(s).
     *
     * @param comment Each worker will get this label
     * @param amount  The number of workers to be created
     * @param batch   Each worker will belong to this batch
     * @return list of created workers
     */
    public List<PersonalMultipleWorker> createAndPersistPersonalMultipleWorker(
            String comment, int amount, Batch batch) throws BadRequestException {
        Function<String, PersonalMultipleWorker> workerConstructor = PersonalMultipleWorker::new;
        return createAndPersistWorker(comment, amount, batch, workerConstructor);
    }

    /**
     * The actual creation of PersonalSingleWorker(s) and PersonalMultipleWorker(s) is
     * the same - they just need a different constructor which is passed via workerConstructor.
     */
    private <T extends Worker> List<T> createAndPersistWorker(String comment, int amount,
            Batch batch, Function<String, T> workerConstructor) throws BadRequestException {
        amount = amount <= 1 ? 1 : amount;
        List<T> workerList = new ArrayList<>();
        while (amount > 0) {
            T worker = workerConstructor.apply(comment);
            validateWorker(worker);
            batch.addWorker(worker);
            workerDao.create(worker);
            batchDao.update(batch);
            workerList.add(worker);
            amount--;
        }
        return workerList;
    }

    private void validateWorker(Worker worker) throws BadRequestException {
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
        putCountToMap.accept(GeneralSingleWorker.WORKER_TYPE);
        putCountToMap.accept(PersonalSingleWorker.WORKER_TYPE);
        putCountToMap.accept(PersonalMultipleWorker.WORKER_TYPE);
        putCountToMap.accept(MTWorker.WORKER_TYPE);
        putCountToMap.accept(MTSandboxWorker.WORKER_TYPE);
        return resultsPerWorker;
    }

}
