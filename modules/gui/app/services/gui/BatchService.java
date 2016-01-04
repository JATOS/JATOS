package services.gui;

import javax.inject.Inject;
import javax.inject.Singleton;

import daos.common.BatchDao;
import daos.common.StudyDao;
import exceptions.gui.BadRequestException;
import exceptions.gui.ForbiddenException;
import general.common.MessagesStrings;
import models.common.Batch;
import models.common.Study;
import models.common.workers.JatosWorker;
import models.common.workers.PersonalMultipleWorker;
import models.common.workers.PersonalSingleWorker;
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

	@Inject
	BatchService(BatchDao batchDao, StudyDao studyDao) {
		this.batchDao = batchDao;
		this.studyDao = studyDao;
	}

	/**
	 * Clones a Batch and persists
	 */
	public Batch clone(Batch batch) {
		Batch clone = new Batch();
		clone.setMinActiveMembers(batch.getMinActiveMembers());
		clone.setMaxActiveMembers(batch.getMaxActiveMembers());
		clone.setMaxTotalMembers(batch.getMaxTotalMembers());
		batch.getAllowedWorkerTypes().forEach(clone::addAllowedWorkerType);
		clone.setTitle(batch.getTitle());
		clone.setActive(batch.isActive());
		return clone;
	}
	
	/**
	 * Create, init and persist default Batch. Each Study has a default batch.
	 */
	public Batch createDefaultBatch() {
		Batch batch = new Batch();
		initBatch(batch);
		batch.setTitle("Default");
		batchDao.create(batch);
		return batch;
	}
	
	public void createBatch(Batch batch, Study study) {
		initBatch(batch);
		batchDao.create(batch);
		study.addBatch(batch);
		studyDao.update(study);
	}

	/**
	 * Add default allowed workers
	 */
	private void initBatch(Batch batch) {
		batch.addAllowedWorkerType(JatosWorker.WORKER_TYPE);
		batch.addAllowedWorkerType(PersonalMultipleWorker.WORKER_TYPE);
		batch.addAllowedWorkerType(PersonalSingleWorker.WORKER_TYPE);
	}

	public void updateBatch(Batch batch, Batch updatedBatch) {
		batch.setMinActiveMembers(updatedBatch.getMinActiveMembers());
		batch.setMaxActiveMembers(updatedBatch.getMaxActiveMembers());
		batch.setMaxTotalMembers(updatedBatch.getMaxTotalMembers());
		batch.getAllowedWorkerTypes().clear();
		updatedBatch.getAllowedWorkerTypes()
				.forEach(batch::addAllowedWorkerType);
		batch.setTitle(updatedBatch.getTitle());
		batch.setActive(updatedBatch.isActive());
		batchDao.update(batch);
	}

	public BatchProperties bindToBatchProperties(Batch batch) {
		BatchProperties props = new BatchProperties();
		batch.getAllowedWorkerTypes().forEach(props::addAllowedWorkerType);
		props.setId(batch.getId());
		props.setMaxActiveMembers(batch.getMaxActiveMembers());
		props.setMaxActiveMemberLimited(batch.getMaxActiveMembers() != null);
		props.setMaxTotalMembers(batch.getMaxTotalMembers());
		props.setMaxTotalMemberLimited(batch.getMaxTotalMembers() != null);
		props.setMinActiveMembers(batch.getMinActiveMembers());
		props.setTitle(batch.getTitle());
		props.setActive(batch.isActive());
		return props;
	}

	public Batch bindToBatch(BatchProperties props) {
		Batch batch = new Batch();
		props.getAllowedWorkerTypes().forEach(batch::addAllowedWorkerType);
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
		batch.setTitle(props.getTitle());
		batch.setActive(props.isActive());
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
			String errorMsg = MessagesStrings.batchNotInStudy(batchId, study.getId());
			throw new ForbiddenException(errorMsg);
		}
	}

}
