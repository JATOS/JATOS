package services.publix;

import daos.common.BatchDao;
import daos.common.worker.WorkerDao;
import general.common.JpaTransactions;
import models.common.Batch;
import models.common.workers.GeneralMultipleWorker;
import models.common.workers.GeneralSingleWorker;
import models.common.workers.MTSandboxWorker;
import models.common.workers.MTWorker;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class WorkerCreator {

    private final JpaTransactions transactions;
    private final WorkerDao workerDao;
    private final BatchDao batchDao;

    @Inject
    WorkerCreator(JpaTransactions transactions,
                  WorkerDao workerDao,
                  BatchDao batchDao) {
        this.transactions = transactions;
        this.workerDao = workerDao;
        this.batchDao = batchDao;
    }

    /**
     * Creates and persists a MTWorker or an MTSandboxWorker.
     */
    public MTWorker createAndPersistMTWorker(String mtWorkerId,
                                             boolean mTurkSandbox, Batch batch) {
        return transactions.withTransaction(em -> {
            MTWorker worker;
            if (mTurkSandbox) {
                worker = new MTSandboxWorker(mtWorkerId);
            } else {
                worker = new MTWorker(mtWorkerId);
            }
            workerDao.persist(worker);
            batchDao.addWorkerToBatch(batch.getId(), worker.getId());
            return worker;
        });
    }

    /**
     * Create and persist a GeneralSingleWorker
     */
    public GeneralSingleWorker createAndPersistGeneralSingleWorker(Batch batch) {
        return transactions.withTransaction(em -> {
            GeneralSingleWorker worker = new GeneralSingleWorker();
            workerDao.persist(worker);
            batchDao.addWorkerToBatch(batch.getId(), worker.getId());
            return worker;
        });
    }

    /**
     * Create and persist a GeneralMultipleWorker
     */
    public GeneralMultipleWorker createAndPersistGeneralMultipleWorker(Batch batch) {
        return transactions.withTransaction(em -> {
            GeneralMultipleWorker worker = new GeneralMultipleWorker();
            workerDao.persist(worker);
            batchDao.addWorkerToBatch(batch.getId(), worker.getId());
            return worker;
        });
    }

}
