package daos.common;

import models.common.Batch;
import models.common.Study;
import models.common.workers.Worker;
import play.db.jpa.JPAApi;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.EntityManager;

/**
 * DAO of Batch entity
 *
 * @author Kristian Lange
 */
@Singleton
public class BatchDao extends AbstractDao {

    @Inject
    BatchDao(JPAApi jpa) {
        super(jpa);
    }

    public void persist(Batch batch) {
        super.persist(batch);
    }

    public Batch merge(Batch batch) {
        return super.merge(batch);
    }

    public void remove(Batch batch) {
        super.remove(batch);
    }

    public Batch findById(Long id) {
        return jpa.withTransaction("default", true, (EntityManager em) -> em.find(Batch.class, id));
    }

    /**
     * Finds a study by its ID and eagerly fetches the batchList to avoid LazyInitializationException.
     */
    public Batch findByIdWithStudy(Long id) {
        return jpa.withTransaction("default", true, (EntityManager em) -> {
            String queryStr = "SELECT b FROM Batch b LEFT JOIN FETCH b.study WHERE b.id = :id";
            return em.createQuery(queryStr, Batch.class)
                    .setParameter("id", id)
                    .getSingleResult();
        });
    }

    /**
     * Finds and returns the default Batch (the first Batch) of the given Study as defined by the order in the database.
     */
    public Batch findDefaultBatchByStudy(Study study) {
        return jpa.withTransaction("default", true, (EntityManager em) -> {
            String hql = "SELECT b FROM Study s JOIN s.batchList b WHERE s = :study ORDER BY INDEX(b)";
            return em.createQuery(hql, Batch.class)
                    .setParameter("study", study)
                    .setMaxResults(1)
                    .getResultList()
                    .stream().findFirst().orElse(null);
        });
    }

    /**
     * Returns the number of Workers belonging to the given Batch.
     */
    public int countWorkers(Batch batch) {
        return jpa.withTransaction("default", true, (EntityManager em) -> {
            String hql = "SELECT COUNT(w) FROM Batch b JOIN b.workerList w WHERE b = :batch";
            Number result = (Number) em.createQuery(hql)
                    .setParameter("batch", batch)
                    .getSingleResult();
            return result != null ? result.intValue() : 0;
        });
    }

    /**
     * Checks if the maximum number of workers is reached for this batch. If the given worker is already in the batch,
     * it returns false (because a worker can run study multiple times in a batch).
     */
    public boolean isMaxTotalReached(Batch batch, Worker worker) {
        return jpa.withTransaction("default", true, (EntityManager em) -> {
            if (batch.getMaxTotalWorkers() == null) return false;

            // Check if this specific worker is already a member
            String memberQuery = "SELECT COUNT(b) FROM Batch b JOIN b.workerList w " +
                    "WHERE b = :batch AND w = :worker";
            Number isMember = (Number) em.createQuery(memberQuery)
                    .setParameter("batch", batch)
                    .setParameter("worker", worker)
                    .getSingleResult();

            if (isMember != null && isMember.intValue() > 0) {
                return false;
            }

            // If not a member, check if adding them would exceed the limit
            int currentCount = countWorkers(batch);
            return currentCount >= batch.getMaxTotalWorkers();
        });
    }

}
