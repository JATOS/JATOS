package services.gui;

import java.util.Set;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Singleton;

import daos.common.BatchDao;
import daos.common.StudyDao;
import daos.common.StudyResultDao;
import exceptions.gui.BadRequestException;
import exceptions.gui.ForbiddenException;
import general.common.MessagesStrings;
import models.common.Batch;
import models.common.Study;
import models.common.User;
import models.common.workers.GeneralSingleWorker;
import models.common.workers.JatosWorker;
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

	private final BatchDao batchDao;
	private final StudyDao studyDao;
	private final StudyResultDao studyResultDao;

	@Inject
	BatchService(BatchDao batchDao, StudyDao studyDao,
			StudyResultDao studyResultDao) {
		this.batchDao = batchDao;
		this.studyDao = studyDao;
		this.studyResultDao = studyResultDao;
	}

	/**
	 * Clones a Batch and persists
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
		batch.getAllowedWorkers().forEach(clone::addAllowedWorker);
		batch.getAllowedWorkerTypes().forEach(clone::addAllowedWorkerType);
		return clone;
	}

	/**
	 * Create, init and persist default Batch. Each Study has a default batch.
	 */
	public Batch createDefaultBatch(User loggedInUser) {
		Batch batch = new Batch();
		initBatch(batch, loggedInUser);
		batch.setTitle("Default");
		batchDao.create(batch);
		return batch;
	}

	public void createBatch(Batch batch, Study study, User loggedInUser) {
		initBatch(batch, loggedInUser);
		batchDao.create(batch);
		study.addBatch(batch);
		studyDao.update(study);
	}

	/**
	 * Add default allowed worker types and the Jatos worker
	 */
	private void initBatch(Batch batch, User loggedInUser) {
		batch.addAllowedWorkerType(JatosWorker.WORKER_TYPE);
		batch.addAllowedWorkerType(PersonalMultipleWorker.WORKER_TYPE);
		batch.addAllowedWorkerType(PersonalSingleWorker.WORKER_TYPE);
		batch.addAllowedWorker(loggedInUser.getWorker());
	}

	public void updateBatch(Batch batch, Batch updatedBatch) {
		batch.setTitle(updatedBatch.getTitle());
		batch.setActive(updatedBatch.isActive());
		batch.setMinActiveMembers(updatedBatch.getMinActiveMembers());
		batch.setMaxActiveMembers(updatedBatch.getMaxActiveMembers());
		batch.setMaxTotalMembers(updatedBatch.getMaxTotalMembers());
		batch.setMaxTotalWorkers(updatedBatch.getMaxTotalWorkers());
		batch.getAllowedWorkers().clear();
		updatedBatch.getAllowedWorkers().forEach(batch::addAllowedWorker);
		batch.getAllowedWorkerTypes().clear();
		updatedBatch.getAllowedWorkerTypes()
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
		batch.getAllowedWorkers().forEach(props::addAllowedWorker);
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
		props.getAllowedWorkers().forEach(batch::addAllowedWorker);
		return batch;
	}

	public void removeBatch(Batch batch, Study study) {
		study.removeBatch(batch);
		studyDao.update(study);
		batchDao.remove(batch);
	}

	/**
	 * Checks the batch and throws an Exception in case of a problem.
	 */
	public void checkStandardForBatch(Batch batch, Study study, Long batchId)
			throws ForbiddenException, BadRequestException {
		if (batch == null) {
			String errorMsg = MessagesStrings.batchNotExist(batchId);
			throw new BadRequestException(errorMsg);
		}
		// Check that the study has this batch
		if (!study.hasBatch(batch)) {
			String errorMsg = MessagesStrings.batchNotInStudy(batchId,
					study.getId());
			throw new ForbiddenException(errorMsg);
		}
	}

	/**
	 * Get all workers (personal single, personal multiple, Jatos, general
	 * single, MTurk worker) that belong to the given batch. In case of personal
	 * single and personal multiple workers they must be in the batch's allowed
	 * worker list. The Jatos workers are the study's users. The general single
	 * and MTurk workers are retrieved via the StudyResults.
	 */
	public Set<Worker> retrieveAllWorkers(Study study, Batch batch) {
		// Put personal single worker & personal multiple workers in a list.
		// They are created prior to the run and are in the allowed workers
		Set<Worker> workerSet = batch.getAllowedWorkers();

		// Add Jatos workers of this study. They are the users of the batch's
		// study.
		study.getUserList().stream().map(u -> u.getWorker())
				.forEachOrdered(workerSet::add);

		// Add general single worker & MTurk workers. They are created
		// on-the-fly during the study run and we can get them via the
		// StudyResults.
		studyResultDao.findAllByBatch(batch).stream().map(sr -> sr.getWorker())
				.filter(w -> GeneralSingleWorker.WORKER_TYPE
						.equals(w.getWorkerType())
						|| MTWorker.WORKER_TYPE.equals(w.getWorkerType()))
				.forEachOrdered(workerSet::add);
		return workerSet;
	}

}
