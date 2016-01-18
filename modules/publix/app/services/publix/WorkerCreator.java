package services.publix;

import javax.inject.Inject;
import javax.inject.Singleton;

import daos.common.BatchDao;
import daos.common.worker.WorkerDao;
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
			boolean mTurkSandbox) {
		MTWorker worker;
		if (mTurkSandbox) {
			worker = new MTSandboxWorker(mtWorkerId);
		} else {
			worker = new MTWorker(mtWorkerId);
		}
		workerDao.create(worker);
		return worker;
	}
	
	/**
	 * Create and persist a GeneralSingleWorker
	 */
	public GeneralSingleWorker createAndPersistGeneralSingleWorker() {
		GeneralSingleWorker worker = new GeneralSingleWorker();
		workerDao.create(worker);
		return worker;
	}

}
