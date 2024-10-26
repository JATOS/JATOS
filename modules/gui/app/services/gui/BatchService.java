package services.gui;

import com.google.common.base.Strings;
import daos.common.BatchDao;
import daos.common.GroupResultDao;
import daos.common.StudyDao;
import daos.common.StudyLinkDao;
import daos.common.worker.WorkerDao;
import exceptions.gui.NotFoundException;
import general.common.StudyLogger;
import models.common.Batch;
import models.common.Study;
import models.common.User;
import models.common.workers.JatosWorker;
import models.common.workers.PersonalMultipleWorker;
import models.common.workers.PersonalSingleWorker;
import models.common.workers.Worker;
import models.gui.BatchProperties;
import models.gui.BatchSession;
import play.Logger;
import play.data.validation.ValidationError;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.ValidationException;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service class for JATOS Controllers (not Publix).
 *
 * @author Kristian Lange
 */
@Singleton
public class BatchService {

    private static final Logger.ALogger LOGGER = Logger.of(BatchService.class);

    private final ResultRemover resultRemover;
    private final BatchDao batchDao;
    private final StudyDao studyDao;
    private final WorkerDao workerDao;
    private final GroupResultDao groupResultDao;
    private final StudyLinkDao studyLinkDao;
    private final StudyLogger studyLogger;

    @Inject
    BatchService(ResultRemover resultRemover, BatchDao batchDao, StudyDao studyDao, WorkerDao workerDao,
            GroupResultDao groupResultDao, StudyLinkDao studyLinkDao, StudyLogger studyLogger) {
        this.resultRemover = resultRemover;
        this.batchDao = batchDao;
        this.studyDao = studyDao;
        this.workerDao = workerDao;
        this.groupResultDao = groupResultDao;
        this.studyLinkDao = studyLinkDao;
        this.studyLogger = studyLogger;
    }

    /**
     * Clones a Batch but does not persists. Doesn't copy batch session data and
     * version.
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
        batch.getWorkerList().forEach(clone::addWorker);
        batch.getAllowedWorkerTypes().forEach(clone::addAllowedWorkerType);
        clone.setJsonData(batch.getJsonData());
        return clone;
    }

    /**
     * Initialises Batch. Does NOT persist.
     */
    void createBatch(Batch batch, Study study) {
        initBatch(batch, study);
        batch.setStudy(study);
    }

    /**
     * Create and initialises default Batch. Each Study has a default batch.
     * Does NOT persist.
     */
    public Batch createDefaultBatch(Study study) {
        Batch batch = new Batch();
        batch.setTitle(BatchProperties.DEFAULT_TITLE);
        initBatch(batch, study);
        batch.setStudy(study);
        batch.setBatchSessionData("{}");
        return batch;
    }

    /**
     * Creates batch, initialises it and persists it. Updates study with new
     * batch.
     */
    public void createAndPersistBatch(Batch batch, Study study, User signedinUser) {
        initBatch(batch, study);
        batch.setStudy(study);
        if (!study.hasBatch(batch)) {
            study.addBatch(batch);
        }
        batchDao.create(batch);
        studyDao.update(study);
        studyLogger.log(study, signedinUser, "Created batch", batch);
    }

    /**
     * Add default allowed worker types and all study's Jatos worker. Generates
     * UUID.
     */
    private void initBatch(Batch batch, Study study) {
        if (batch.getUuid() == null) {
            batch.setUuid(UUID.randomUUID().toString());
        }
        batch.addAllowedWorkerType(JatosWorker.WORKER_TYPE);
        batch.addAllowedWorkerType(PersonalMultipleWorker.WORKER_TYPE);
        batch.addAllowedWorkerType(PersonalSingleWorker.WORKER_TYPE);
        study.getUserList().forEach(user -> batch.addWorker(user.getWorker()));
        batch.setBatchSessionData("{}");
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
        updatedBatchProps.getAllowedWorkerTypes()
                .forEach(batch::addAllowedWorkerType);
        batch.setComments(updatedBatchProps.getComments());
        batch.setJsonData(updatedBatchProps.getJsonData());
        batchDao.update(batch);
    }

    public BatchProperties bindToProperties(Batch batch) {
        BatchProperties props = new BatchProperties();
        props.setId(batch.getId());
        props.setUuid(batch.getUuid());
        props.setTitle(batch.getTitle());
        props.setActive(batch.isActive());
        props.setMaxActiveMembers(batch.getMaxActiveMembers());
        props.setMaxActiveMemberLimited(batch.getMaxActiveMembers() != null);
        props.setMaxTotalMembers(batch.getMaxTotalMembers());
        props.setMaxTotalMemberLimited(batch.getMaxTotalMembers() != null);
        props.setMaxTotalWorkerLimited(batch.getMaxTotalWorkers() != null);
        props.setMaxTotalWorkers(batch.getMaxTotalWorkers());
        batch.getAllowedWorkerTypes().forEach(props::addAllowedWorkerType);
        props.setComments(batch.getComments());
        props.setJsonData(batch.getJsonData());
        return props;
    }

    public Batch bindToBatch(BatchProperties props) {
        Batch batch = new Batch();
        batch.setTitle(props.getTitle());
        batch.setActive(props.isActive());
        if (props.isMaxActiveMemberLimited()) {
            batch.setMaxActiveMembers(props.getMaxActiveMembers());
        } else {
            batch.setMaxActiveMembers(null);
        }
        if (props.isMaxTotalMemberLimited()) {
            batch.setMaxTotalMembers(props.getMaxTotalMembers());
        } else {
            batch.setMaxTotalMembers(null);
        }
        if (props.isMaxTotalWorkerLimited()) {
            batch.setMaxTotalWorkers(props.getMaxTotalWorkers());
        } else {
            batch.setMaxTotalWorkers(null);
        }
        props.getAllowedWorkerTypes().forEach(batch::addAllowedWorkerType);
        batch.setComments(props.getComments());
        batch.setJsonData(props.getJsonData());
        return batch;
    }

    public BatchSession bindToBatchSession(Batch batch) {
        BatchSession batchSession = new BatchSession();
        batchSession.setVersion(batch.getBatchSessionVersion());
        batchSession.setData(batch.getBatchSessionData());
        return batchSession;
    }

    public boolean updateBatchSession(long batchId, BatchSession batchSession) {
        Batch currentBatch = batchDao.findById(batchId);
        if (currentBatch == null ||
                !batchSession.getVersion().equals(currentBatch.getBatchSessionVersion())) {
            return false;
        }

        currentBatch.setBatchSessionVersion(
                currentBatch.getBatchSessionVersion() + 1);
        if (Strings.isNullOrEmpty(batchSession.getData())) {
            currentBatch.setBatchSessionData("{}");
        } else {
            currentBatch.setBatchSessionData(batchSession.getData());
        }
        batchDao.update(currentBatch);
        return true;
    }

    /**
     * Gets the batch with given ID from the database or if the batchId is -1
     * returns the default batch of this study. If the batch doesn't exist it throws an
     * NotFoundException.
     */
    public Batch fetchBatch(Long batchId, Study study) throws NotFoundException {
        if (batchId == -1) {
            // The default batch is always the first one in study's batch list
            return study.getDefaultBatch();
        } else {
            Batch batch = batchDao.findById(batchId);
            if (batch == null) throw new NotFoundException("Batch with ID " + batchId + " does not exist");
            return batch;
        }
    }

    /**
     * Removes batch, all it's StudyResults, ComponentResults, GroupResults and
     * Workers (if they don't belong to another batch) and persists the changes
     * to the database.
     */
    public void remove(Batch batch, User signedinUser) {
        // Remove this Batch from its study
        Study study = batch.getStudy();
        study.removeBatch(batch);
        studyDao.update(study);

        // Delete all StudyResults and all ComponentResults
        resultRemover.removeAllStudyResults(batch, signedinUser);

        // Delete all StudyLinks that belong to this Batch
        studyLinkDao.removeAllByBatch(batch);

        // Delete all GroupResults
        groupResultDao.findAllByBatch(batch).forEach(groupResultDao::remove);

        // Remove or update Workers of this batch
        for (Worker worker : batch.getWorkerList()) {
            removeOrUpdateJatosWorker(batch, worker);
            removeOrUpdateNonJatosWorkers(batch, worker);
        }

        batchDao.remove(batch);
        studyLogger.log(study, signedinUser, "Removed batch", batch);
    }

    private void removeOrUpdateJatosWorker(Batch batch, Worker worker) {
        // We can't check type with 'instanceof JatosWorker' because sometimes
        // Hibernate doesn't map the proper type
        if (!worker.getWorkerType().equals(JatosWorker.WORKER_TYPE)) {
            return;
        }

        // If worker is part of other batches just remove this batch
        if (worker.getBatchList().size() != 1) {
            worker.removeBatch(batch);
            workerDao.update(worker);
            return;
        }

        // This is a Hibernate issue: If this worker was a JatosWorker
        // before but its user was already deleted it's not instance of
        // JatosWorker anymore. But anyway, since the worker is only in this
        // batch, now it can be removed.
        if (!(worker instanceof JatosWorker)) {
            workerDao.remove(worker);
            return;
        }

        // Worker is only in this batch
        JatosWorker jatosWorker = (JatosWorker) worker;
        if (jatosWorker.getUser() == null) {
            // Last one in batch list and User gone -> remove worker
            workerDao.remove(worker);
        } else {
            // If the JatosWorker's User still exist don't remove
            worker.removeBatch(batch);
            workerDao.update(worker);
        }
    }

    private void removeOrUpdateNonJatosWorkers(Batch batch, Worker worker) {
        // We can't check type with 'instanceof JatosWorker' because sometimes
        // Hibernate doesn't map the proper type
        if (worker.getWorkerType().equals(JatosWorker.WORKER_TYPE)) {
            return;
        }

        if (worker.getBatchList().size() == 1) {
            // If this worker does not belong to any other batches remove it
            // from the database
            workerDao.remove(worker);
        } else {
            // If this worker belongs to other batches it can't be removed
            // from the database but this batch has to be removed from the
            // worker's batch list
            worker.removeBatch(batch);
            workerDao.update(worker);
        }
    }

    /**
     * Validates the batch by converting it to BatchProperties and uses its validate method. Throws ValidationException
     * in case of an error.
     */
    public void validate(Batch batch) throws ValidationException {
        BatchProperties batchProperties = bindToProperties(batch);
        if (batchProperties.validate() != null) {
            LOGGER.warn(".validate: " + batchProperties.validate().stream().map(ValidationError::message)
                    .collect(Collectors.joining(", ")));
            throw new ValidationException("Batch is invalid");
        }
    }

}
