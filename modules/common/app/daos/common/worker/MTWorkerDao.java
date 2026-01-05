package daos.common.worker;

import models.common.workers.MTWorker;
import models.common.workers.Worker;
import play.db.jpa.JPAApi;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.EntityManager;
import java.util.List;
import java.util.Optional;

/**
 * DAO for MTWorker entity
 *
 * @author Kristian Lange
 */
@Singleton
public class MTWorkerDao extends WorkerDao {

    @Inject
    MTWorkerDao(JPAApi jpa) {
        super(jpa);
    }

    /**
     * Retrieves the worker with the given MTurk worker ID and worker type. The mtWorkerId is treated in a
     * case-insensitive way. The only possible worker types are "MT" or "MTSandbox".
     */
    public Optional<MTWorker> findByMTWorkerId(String mtWorkerId, WorkerType workerType) {
        return jpa.withTransaction("default", true, (EntityManager em) -> {
            String queryStr =
                    "SELECT w " +
                            "FROM Worker w " +
                            "WHERE UPPER(w.mtWorkerId) = :mtWorkerId " +
                            "  AND w.class != :workerType " +
                            "ORDER BY w.id ASC";
            List<Worker> workerList = em.createQuery(queryStr, Worker.class)
                    .setParameter("mtWorkerId", mtWorkerId.toUpperCase())
                    .setParameter("workerType", workerType)
                    .setMaxResults(1)
                    .getResultList();
            return !workerList.isEmpty() ? Optional.of((MTWorker) workerList.get(0)) : Optional.empty();
        });
    }

}
