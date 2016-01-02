package services.gui;

import javax.inject.Inject;
import javax.inject.Singleton;

import daos.common.BatchDao;
import models.common.Batch;
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

	@Inject
	BatchService(BatchDao batchDao) {
		this.batchDao = batchDao;
	}

	/**
	 * Clones a Batch and persists
	 */
	public Batch clone(Batch batch) {
		Batch clone = new Batch();
		clone.setMinActiveMemberSize(batch.getMinActiveMemberSize());
		clone.setMaxActiveMemberSize(batch.getMaxActiveMemberSize());
		clone.setMaxTotalMemberSize(batch.getMaxTotalMemberSize());
		batch.getAllowedWorkerTypes().forEach(clone::addAllowedWorkerType);
		clone.setTitle(batch.getTitle());
		clone.setActive(batch.isActive());
		return clone;
	}

	/**
	 * Add default allowed workers
	 */
	public void initBatch(Batch batch) {
		batch.addAllowedWorkerType(JatosWorker.WORKER_TYPE);
		batch.addAllowedWorkerType(PersonalMultipleWorker.WORKER_TYPE);
		batch.addAllowedWorkerType(PersonalSingleWorker.WORKER_TYPE);
	}

	public void updateBatch(Batch batch, Batch updatedBatch) {
		batch.setMinActiveMemberSize(updatedBatch.getMinActiveMemberSize());
		batch.setMaxActiveMemberSize(updatedBatch.getMaxActiveMemberSize());
		batch.setMaxTotalMemberSize(updatedBatch.getMaxTotalMemberSize());
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
		props.setMaxActiveMemberSize(batch.getMaxActiveMemberSize());
		props.setMaxActiveMemberLimited(batch.getMaxActiveMemberSize() != null);
		props.setMaxTotalMemberSize(batch.getMaxTotalMemberSize());
		props.setMaxTotalMemberLimited(batch.getMaxTotalMemberSize() != null);
		props.setMinActiveMemberSize(batch.getMinActiveMemberSize());
		props.setTitle(batch.getTitle());
		props.setActive(batch.isActive());
		return props;
	}

	public Batch bindToBatch(BatchProperties props) {
		Batch batch = new Batch();
		props.getAllowedWorkerTypes().forEach(batch::addAllowedWorkerType);
		if (props.isMaxActiveMemberLimited()) {
			batch.setMaxActiveMemberSize(props.getMaxActiveMemberSize());
		} else {
			batch.setMaxActiveMemberSize(null);
		}
		if (props.isMaxTotalMemberLimited()) {
			batch.setMaxTotalMemberSize(props.getMaxTotalMemberSize());
		} else {
			batch.setMaxTotalMemberSize(null);
		}
		batch.setMinActiveMemberSize(props.getMinActiveMemberSize());
		batch.setTitle(props.getTitle());
		batch.setActive(props.isActive());
		return batch;
	}

}
