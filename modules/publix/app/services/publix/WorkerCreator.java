package services.publix;

import javax.inject.Inject;
import javax.inject.Singleton;

import daos.common.BatchDao;
import daos.common.worker.WorkerDao;
import models.common.Batch;
import models.common.workers.GeneralSingleWorker;
import models.common.workers.MTSandboxWorker;
import models.common.workers.MTWorker;

/**
 * Service class for JATOS Controllers (not Publix)..
 * 
 * @author Kristian Lange
 */
@Singleton
public class WorkerCreator {

	private final WorkerDao workerDao;
	private final BatchDao batchDao;

	@Inject
	WorkerCreator(WorkerDao workerDao, BatchDao batchDao) {
		this.workerDao = workerDao;
		this.batchDao = batchDao;
	}

	/**
	 * Creates and persists a MTWorker or a MTSandboxWorker.
	 */
	public MTWorker createAndPersistMTWorker(String mtWorkerId,
			boolean mTurkSandbox, Batch batch) {
		MTWorker worker;
		if (mTurkSandbox) {
			worker = new MTSandboxWorker(mtWorkerId);
		} else {
			worker = new MTWorker(mtWorkerId);
		}
		batch.addWorker(worker);
		workerDao.create(worker);
		batchDao.update(batch);
		return worker;
	}
	
	/**
	 * Create and persist a GeneralSingleWorker
	 */
	public GeneralSingleWorker createAndPersistGeneralSingleWorker(Batch batch) {
		GeneralSingleWorker worker = new GeneralSingleWorker();
		batch.addWorker(worker);
		workerDao.create(worker);
		batchDao.update(batch);
		return worker;
	}

}
