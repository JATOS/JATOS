package services.gui;

import daos.common.BatchDao;
import daos.common.GroupResultDao;
import daos.common.StudyDao;
import daos.common.StudyLinkDao;
import daos.common.worker.WorkerDao;
import daos.common.worker.WorkerType;
import exceptions.common.NotFoundException;
import http.common.Http.Context;
import general.common.StudyLogger;
import models.common.Batch;
import models.common.Study;
import models.common.User;
import models.common.workers.JatosWorker;
import models.common.workers.Worker;
import models.gui.BatchProperties;
import play.Logger;
import play.data.validation.ValidationError;
import play.db.jpa.JPAApi;
import utils.common.StringUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.ValidationException;
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

    private final JPAApi jpa;
    private final ResultRemover resultRemover;
    private final BatchDao batchDao;
    private final StudyDao studyDao;
    private final WorkerDao workerDao;
    private final GroupResultDao groupResultDao;
    private final StudyLinkDao studyLinkDao;
    private final StudyLogger studyLogger;

    @Inject
    BatchService(JPAApi jpa,
                 ResultRemover resultRemover,
                 BatchDao batchDao,
                 StudyDao studyDao,
                 WorkerDao workerDao,
                 GroupResultDao groupResultDao,
                 StudyLinkDao studyLinkDao,
                 StudyLogger studyLogger) {
        this.jpa = jpa;
        this.resultRemover = resultRemover;
        this.batchDao = batchDao;
        this.studyDao = studyDao;
        this.workerDao = workerDao;
        this.groupResultDao = groupResultDao;
        this.studyLinkDao = studyLinkDao;
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
     * Removes batch, all it's StudyResults, ComponentResults, GroupResults and Workers (if they don't belong to another
     * batch) and persists the changes to the database.
     */
    public void remove(Batch batch) {
        jpa.withTransaction(entityManager -> {
            // Delete all StudyResults and all ComponentResults
            resultRemover.removeAllStudyResults(batch);

            // Delete all StudyLinks that belong to this Batch
            studyLinkDao.removeAllByBatch(batch);

            // Delete all GroupResults
            groupResultDao.findAllByBatch(batch).forEach(groupResultDao::remove);

            // Remove or update Workers of this batch
            for (Worker worker : batch.getWorkerList()) {
                removeOrUpdateJatosWorker(batch, worker);
                removeOrUpdateNonJatosWorkers(batch, worker);
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
     * Remove or update JatosWorker from batch. This isn't necessary anymore because we don't add the JatosWorker to a
     * batch anymore. We keep it to clean up old batches that still have a JatosWorker.
     */
    private void removeOrUpdateJatosWorker(Batch batch, Worker worker) {
        // We can't check type with 'instanceof JatosWorker' because sometimes Hibernate doesn't map the proper type
        if (worker.getWorkerType() != WorkerType.JATOS) {
            return;
        }

        // If worker is part of other batches do nothing
        if (worker.getBatchList().size() != 1) {
            return;
        }

        // This is a Hibernate issue: If this worker was a JatosWorker
        // before but its user was already deleted, it's not an instance of
        // JatosWorker anymore. But anyway, since the worker is only in this
        // batch, now it can be removed.
        if (!(worker instanceof JatosWorker)) {
            workerDao.remove(worker);
            return;
        }

        // Worker is only in this batch
        JatosWorker jatosWorker = (JatosWorker) worker;
        if (jatosWorker.getUser() == null) {
            // Last one in the batch list and User gone -> remove worker
            workerDao.remove(worker);
        } else {
            // If the JatosWorker's User does still exist, don't remove
            worker.removeBatch(batch);
            workerDao.merge(worker);
        }
    }

    private void removeOrUpdateNonJatosWorkers(Batch batch, Worker worker) {
        // We can't check type with 'instanceof JatosWorker' because sometimes Hibernate doesn't map the proper type
        if (worker.getWorkerType() == WorkerType.JATOS) {
            return;
        }

        if (worker.getBatchList().size() == 1) {
            // If this worker does not belong to any other batches, remove it from the database
            workerDao.remove(worker);
        } else {
            // If this worker belongs to other batches, it can't be removed from the database, but this batch has to be
            // removed from the worker's batch list
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
