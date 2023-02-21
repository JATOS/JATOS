package daos.common.worker;

import models.common.workers.MTSandboxWorker;
import models.common.workers.MTWorker;
import models.common.workers.Worker;
import play.db.jpa.JPAApi;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Optional;

/**
 * DAO for MTWorker entity
 *
 * @author Kristian Lange
 */
@SuppressWarnings("deprecation")
@Singleton
public class MTWorkerDao extends WorkerDao {

    @Inject
    MTWorkerDao(JPAApi jpa) {
        super(jpa);
    }

    /**
     * Create MTWorker. Distinguishes between normal MTurk and Sandbox MTurk via mTurkSandbox parameter.
     */
    public MTWorker create(String mtWorkerId, boolean mTurkSandbox) {
        MTWorker worker;
        if (mTurkSandbox) {
            worker = new MTSandboxWorker(mtWorkerId);
        } else {
            worker = new MTWorker(mtWorkerId);
        }
        persist(worker);
        return worker;
    }

    /**
     * Retrieves the worker with the given MTurk worker ID and worker type. The mtWorkerId is treated in a
     * case-insensitive way. The only possible worker types are "MT" or "MTSandbox".
     */
    public Optional<MTWorker> findByMTWorkerId(String mtWorkerId, String workerType) {
        String queryStr = "SELECT w FROM Worker w WHERE UPPER(w.mtWorkerId) = :mtWorkerId AND w.class != :workerType";
        List<Worker> workerList = jpa.em().createQuery(queryStr, Worker.class)
                .setParameter("mtWorkerId", mtWorkerId.toUpperCase())
                .setParameter("workerType", workerType)
                .setMaxResults(1)
                .getResultList();
        return !workerList.isEmpty() ? Optional.of((MTWorker) workerList.get(0)) : Optional.empty();
    }

}
