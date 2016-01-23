package services.gui;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import javax.inject.Inject;
import javax.inject.Singleton;

import daos.common.BatchDao;
import daos.common.GroupResultDao;
import daos.common.StudyDao;
import daos.common.StudyResultDao;
import models.common.Batch;
import models.common.Study;
import models.common.User;
import models.common.workers.GeneralSingleWorker;
import models.common.workers.JatosWorker;
import models.common.workers.MTSandboxWorker;
import models.common.workers.MTWorker;
import models.common.workers.PersonalMultipleWorker;
import models.common.workers.PersonalSingleWorker;
import models.common.workers.Worker;
import models.gui.BatchProperties;

/**
 * Service class for JATOS Controllers (not Publix).
 * 
 * @author Kristian Lange
 */
@Singleton
public class BatchService {

	private final ResultRemover resultRemover;
	private final BatchDao batchDao;
	private final StudyDao studyDao;
	private final StudyResultDao studyResultDao;
	private final GroupResultDao groupResultDao;

	@Inject
	BatchService(ResultRemover resultRemover, BatchDao batchDao,
			StudyDao studyDao, StudyResultDao studyResultDao,
			GroupResultDao groupResultDao) {
		this.resultRemover = resultRemover;
		this.batchDao = batchDao;
		this.studyDao = studyDao;
		this.studyResultDao = studyResultDao;
		this.groupResultDao = groupResultDao;
	}

	/**
	 * Clones a Batch but does not persists
	 */
	public Batch clone(Batch batch) {
		Batch clone = new Batch();
		// Generate new UUID for clone
		clone.setUuid(UUID.randomUUID().toString());
		clone.setTitle(batch.getTitle());
		clone.setActive(batch.isActive());
		clone.setMinActiveMembers(batch.getMinActiveMembers());
		clone.setMaxActiveMembers(batch.getMaxActiveMembers());
		clone.setMaxTotalMembers(batch.getMaxTotalMembers());
		clone.setMaxTotalWorkers(batch.getMaxTotalWorkers());
		batch.getWorkerList().forEach(clone::addWorker);
		batch.getAllowedWorkerTypes().forEach(clone::addAllowedWorkerType);
		return clone;
	}

	/**
	 * Initialises Batch. Does NOT persist.
	 */
	public Batch createBatch(Batch batch, Study study, User loggedInUser) {
		initBatch(batch, loggedInUser);
		batch.setStudy(study);
		return batch;
	}

	/**
	 * Create and initialises default Batch. Each Study has a default batch.
	 * Does NOT persist.
	 */
	public Batch createDefaultBatch(Study study, User loggedInUser) {
		Batch batch = new Batch();
		batch.setTitle(BatchProperties.DEFAULT_TITLE);
		initBatch(batch, loggedInUser);
		batch.setStudy(study);
		return batch;
	}

	/**
	 * Creates batch, initialises it and persists it. Updates study with new
	 * batch.
	 */
	public void createAndPersistBatch(Batch batch, Study study,
			User loggedInUser) {
		initBatch(batch, loggedInUser);
		batch.setStudy(study);
		if (!study.hasBatch(batch)) {
			study.addBatch(batch);
		}
		batchDao.create(batch);
		studyDao.update(study);
	}

	/**
	 * Add default allowed worker types and the Jatos worker. Generates UUID.
	 */
	private void initBatch(Batch batch, User loggedInUser) {
		if (batch.getUuid() == null) {
			batch.setUuid(UUID.randomUUID().toString());
		}
		batch.addAllowedWorkerType(JatosWorker.WORKER_TYPE);
		batch.addAllowedWorkerType(PersonalMultipleWorker.WORKER_TYPE);
		batch.addAllowedWorkerType(PersonalSingleWorker.WORKER_TYPE);
		batch.addWorker(loggedInUser.getWorker());
	}

	public void updateBatch(Batch batch, BatchProperties updatedBatchProps) {
		batch.setTitle(updatedBatchProps.getTitle());
		batch.setActive(updatedBatchProps.isActive());
		batch.setMinActiveMembers(updatedBatchProps.getMinActiveMembers());
		batch.setMaxActiveMembers(updatedBatchProps.getMaxActiveMembers());
		batch.setMaxTotalMembers(updatedBatchProps.getMaxTotalMembers());
		batch.setMaxTotalWorkers(updatedBatchProps.getMaxTotalWorkers());
		batch.getAllowedWorkerTypes().clear();
		updatedBatchProps.getAllowedWorkerTypes()
				.forEach(batch::addAllowedWorkerType);
		batchDao.update(batch);
	}

	public BatchProperties bindToProperties(Batch batch) {
		BatchProperties props = new BatchProperties();
		props.setId(batch.getId());
		props.setTitle(batch.getTitle());
		props.setActive(batch.isActive());
		props.setMaxActiveMembers(batch.getMaxActiveMembers());
		props.setMaxActiveMemberLimited(batch.getMaxActiveMembers() != null);
		props.setMaxTotalMembers(batch.getMaxTotalMembers());
		props.setMaxTotalMemberLimited(batch.getMaxTotalMembers() != null);
		props.setMinActiveMembers(batch.getMinActiveMembers());
		props.setMaxTotalWorkerLimited(batch.getMaxTotalWorkers() != null);
		props.setMaxTotalWorkers(batch.getMaxTotalWorkers());
		batch.getAllowedWorkerTypes().forEach(props::addAllowedWorkerType);
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
		batch.setMinActiveMembers(props.getMinActiveMembers());
		if (props.isMaxTotalWorkerLimited()) {
			batch.setMaxTotalWorkers(props.getMaxTotalWorkers());
		} else {
			batch.setMaxTotalWorkers(null);
		}
		props.getAllowedWorkerTypes().forEach(batch::addAllowedWorkerType);
		return batch;
	}

	public void remove(Batch batch) {
		// Update Study
		Study study = batch.getStudy();
		study.removeBatch(batch);
		studyDao.update(study);

		// Delete all StudyResults and all ComponentResults
		studyResultDao.findAllByBatch(batch)
				.forEach(resultRemover::removeStudyResult);

		// Delete all GroupResults
		groupResultDao.findAllByBatch(batch).forEach(groupResultDao::remove);

		batchDao.remove(batch);
	}

	/**
	 * Get all workers (PersonalSingleWorker, PersonalMultipleWorker,
	 * GeneralSingle, MTWorker, MTSandboxWorker and JatosWorker) that belong to
	 * the given batch.
	 */
	public Set<Worker> retrieveAllWorkers(Study study, Batch batch) {
		// Put personal single worker & personal multiple workers in a list.
		// They are created prior to the run and are in the allowed workers
		Set<Worker> workerSet = batch.getWorkerList();

		// Add Jatos workers of this study.
		study.getUserList().stream().map(u -> u.getWorker())
				.forEachOrdered(workerSet::add);
		return workerSet;
	}

	/**
	 * Retrieves the count of StudyResults for each worker type in map:
	 * workerType -> count.
	 */
	public Map<String, Integer> retrieveStudyResultCountsPerWorker(
			Batch batch) {
		Map<String, Integer> resultsPerWorker = new HashMap<>();
		Consumer<String> putCountToMap = (String workerType) -> resultsPerWorker
				.put(workerType, studyResultDao.countByBatchAndWorkerType(batch,
						workerType));
		putCountToMap.accept(JatosWorker.WORKER_TYPE);
		putCountToMap.accept(GeneralSingleWorker.WORKER_TYPE);
		putCountToMap.accept(PersonalSingleWorker.WORKER_TYPE);
		putCountToMap.accept(PersonalMultipleWorker.WORKER_TYPE);
		putCountToMap.accept(MTWorker.WORKER_TYPE);
		putCountToMap.accept(MTSandboxWorker.WORKER_TYPE);
		return resultsPerWorker;
	}

}
