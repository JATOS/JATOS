package services.gui;

import daos.common.BatchDao;
import daos.common.StudyDao;
import daos.common.worker.WorkerDao;
import exceptions.common.NotFoundException;
import general.common.StudyLogger;
import http.common.Http.Context;
import models.common.Batch;
import models.common.Study;
import models.common.User;
import models.common.workers.Worker;
import models.common.workers.WorkerType;
import models.gui.BatchProperties;
import play.Logger;
import play.data.validation.ValidationError;
import utils.common.StringUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.ValidationException;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static auth.gui.AuthAction.SIGNEDIN_USER;

/**
 * Service class for everything Batch related.
 */
@Singleton
public class BatchService {

    private static final Logger.ALogger LOGGER = Logger.of(BatchService.class);

    private final BatchDao batchDao;
    private final StudyDao studyDao;
    private final WorkerDao workerDao;
    private final StudyLogger studyLogger;

    @Inject
    BatchService(BatchDao batchDao,
                 StudyDao studyDao,
                 WorkerDao workerDao,
                 StudyLogger studyLogger) {
        this.batchDao = batchDao;
        this.studyDao = studyDao;
        this.workerDao = workerDao;
        this.studyLogger = studyLogger;
    }

    /**
     * Clones a Batch but does not persist. Doesn't copy batch session data and version.
     */
    public Batch clone(Batch batch) {
        Batch clone = new Batch();
        // Generate new UUID for clone
        clone.setUuid(UUID.randomUUID().toString());
        clone.setTitle(batch.getTitle());
        clone.setActive(batch.isActive());
        clone.setMaxActiveMembers(batch.getMaxActiveMembers());
        clone.setMaxTotalMembers(batch.getMaxTotalMembers());
        clone.setMaxTotalWorkers(batch.getMaxTotalWorkers());
        clone.addAllWorkers(batch.getWorkerList());
        clone.addAllAllowedWorkerTypes(batch.getAllowedWorkerTypes());
        clone.setBatchInput(batch.getBatchInput());
        return clone;
    }

    /**
     * Create and initializes default Batch. Each Study has a default batch. Does NOT persist.
     */
    public Batch createDefaultBatch() {
        Batch batch = new Batch();
        batch.setTitle(BatchProperties.DEFAULT_TITLE);
        addDefaultAllowedWorkerTypes(batch);
        return batch;
    }

    public void initBatch(Batch batch, Study study) {
        if (batch.getUuid() == null) {
            batch.setUuid(UUID.randomUUID().toString());
        }
        study.getUserList().forEach(user -> batch.addWorker(user.getWorker()));
        batch.setBatchSessionData("{}");
    }

    public void initAndPersistBatch(Batch batch, Study study) {
        if (batch.getUuid() == null) {
            batch.setUuid(UUID.randomUUID().toString());
        }
        study.getUserList().forEach(user -> batch.addWorker(user.getWorker()));
        batch.setBatchSessionData("{}");
        study.addBatch(batch);

        batchDao.persist(batch);
        studyDao.merge(study);

        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        studyLogger.log(study, signedinUser, "Created batch", batch);
    }

    public void addDefaultAllowedWorkerTypes(Batch batch) {
        batch.addAllowedWorkerType(WorkerType.PERSONAL_MULTIPLE);
        batch.addAllowedWorkerType(WorkerType.PERSONAL_SINGLE);
    }

    /**
     * Updates the given batch in the database with the given BatchProperties
     */
    public void updateBatch(Batch batch, BatchProperties updatedBatchProps) {
        batch.setTitle(updatedBatchProps.getTitle());
        batch.setActive(updatedBatchProps.isActive());
        batch.setMaxActiveMembers(updatedBatchProps.getMaxActiveMembers());
        batch.setMaxTotalMembers(updatedBatchProps.getMaxTotalMembers());
        batch.setMaxTotalWorkers(updatedBatchProps.getMaxTotalWorkers());
        batch.getAllowedWorkerTypes().clear();
        updatedBatchProps.getAllowedWorkerTypes().forEach(batch::addAllowedWorkerType);
        batch.setComments(updatedBatchProps.getComments());
        batch.setBatchInput(updatedBatchProps.getBatchInput());
        batchDao.merge(batch);
    }

    public BatchProperties bindToProperties(Batch batch) {
        BatchProperties props = new BatchProperties();
        props.setId(batch.getId());
        props.setUuid(batch.getUuid());
        props.setTitle(batch.getTitle());
        props.setActive(batch.isActive());
        props.setMaxActiveMembers(batch.getMaxActiveMembers());
        props.setMaxTotalMembers(batch.getMaxTotalMembers());
        props.setMaxTotalWorkers(batch.getMaxTotalWorkers());
        props.addAllAllowedWorkerTypes(batch.getAllowedWorkerTypes());
        props.setComments(batch.getComments());
        props.setBatchInput(batch.getBatchInput());
        return props;
    }

    public Batch bindToBatch(BatchProperties props) {
        Batch batch = new Batch();
        batch.setTitle(props.getTitle());
        batch.setActive(props.isActive());
        batch.setMaxActiveMembers(props.getMaxActiveMembers());
        batch.setMaxTotalMembers(props.getMaxTotalMembers());
        batch.setMaxTotalWorkers(props.getMaxTotalWorkers());
        props.getAllowedWorkerTypes().forEach(batch::addAllowedWorkerType);
        batch.setComments(props.getComments());
        batch.setBatchInput(props.getBatchInput());
        return batch;
    }

    /**
     * Gets the batch with a given ID from the database or if the batchId is -1 returns the default batch of this study.
     * If the batch doesn't exist, it throws a NotFoundException.
     */
    public Batch fetchBatch(Long batchId, Study study) {
        if (batchId == -1) {
            return batchDao.findDefaultBatchByStudy(study);
        } else {
            Batch batch = batchDao.findById(batchId);
            if (batch == null) throw new NotFoundException("Batch with ID " + batchId + " does not exist");
            return batch;
        }
    }

    /**
     * Removes batch, all its StudyResults, ComponentResults, GroupResults, and StudyLinks. Handles BatchWorkerMap
     * cleanup (workers are managed application-layer since they can belong to multiple batches).
     */
    public void remove(Batch batch) {
        batchDao.withTransaction(entityManager -> {
            // Remove or update Workers of this batch
            // (StudyResults, ComponentResults, GroupResults, and StudyLinks are cascaded by database)
            for (Worker worker : new ArrayList<>(batch.getWorkerList())) {
                removeOrUpdateWorkerForBatch(batch, worker);
            }

            // Remove this Batch from its study
            Study study = batch.getStudy();
            study.removeBatch(batch);
            studyDao.merge(study);

            batchDao.remove(batch);

            User signedinUser = Context.current().args().get(SIGNEDIN_USER);
            studyLogger.log(study, signedinUser, "Removed batch", batch);
        });
    }

    /**
     * Remove or update worker from the batch. Workers can belong to multiple batches, so we only remove
     * the batch from the worker's list (the BatchWorkerMap entry will be cascaded by database).
     */
    private void removeOrUpdateWorkerForBatch(Batch batch, Worker worker) {
        if (worker.getBatchList().size() == 1) {
            // If this worker does not belong to any other batches, remove it entirely
            workerDao.remove(worker);
        } else {
            // If this worker belongs to other batches, remove only this batch from the worker's list
            worker.removeBatch(batch);
            workerDao.merge(worker);
        }
    }

    /**
     * Validates the batch by converting it to BatchProperties and uses its validate method. Throws ValidationException
     * in case of an error.
     */
    public void validate(Batch batch) {
        BatchProperties batchProperties = bindToProperties(batch);
        if (batchProperties.validate() != null) {
            LOGGER.warn(".validate: " + batchProperties.validate().stream().map(ValidationError::message)
                    .collect(Collectors.joining(", ")));
            throw new ValidationException("Batch is invalid");
        }
    }

    public Batch getBatchFromIdOrUuid(String idOrUuid) {
        Optional<Long> studyId = StringUtils.parseLong(idOrUuid.trim());
        if (studyId.isPresent()) {
            return batchDao.findById(studyId.get());
        } else {
            return batchDao.findByUuid(idOrUuid).orElse(null);
        }
    }

    public Batch getBatchOrDefaultBatch(Long batchId, Study study) {
        if (batchId != null) {
            return batchDao.findById(batchId);
        } else {
            return study.getDefaultBatch();
        }
    }

}
