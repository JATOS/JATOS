package daos.common;

import models.common.Batch;
import play.db.jpa.JPAApi;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Optional;

/**
 * DAO of Batch entity
 *
 * @author Kristian Lange
 */
@SuppressWarnings("deprecation")
@Singleton
public class BatchDao extends AbstractDao {

    @Inject
    BatchDao(JPAApi jpa) {
        super(jpa);
    }

    public void create(Batch batch) {
        persist(batch);
    }

    public void update(Batch batch) {
        merge(batch);
    }

    public void remove(Batch batch) {
        super.remove(batch);
    }

    public Batch findById(Long id) {
        return jpa.em().find(Batch.class, id);
    }

    public Optional<Batch> findByUuid(String uuid) {
        String queryStr = "SELECT s FROM Batch s WHERE " + "s.uuid=:uuid";
        List<Batch> batchList = jpa.em().createQuery(queryStr, Batch.class)
                .setParameter("uuid", uuid)
                .setMaxResults(1)
                .getResultList();
        return !batchList.isEmpty() ? Optional.of(batchList.get(0)) : Optional.empty();
    }

    /**
     * Atomically updates batchSessionData and increments batchSessionVersion, but only if the current version matches
     * the expectedVersion (compare-and-set).
     *
     * @return The new batchSessionVersion if the update succeeded (exactly one row updated), null if the version mismatched
     */
    public Long updateBatchSession(Long batchId, Long expectedVersion, String sessionData) {
        String query =
                "UPDATE Batch b " +
                        "SET b.batchSessionData = :sessionData, " +
                        "    b.batchSessionVersion = b.batchSessionVersion + 1 " +
                        "WHERE b.id = :id " +
                        "  AND b.batchSessionVersion = :expectedVersion";

        int updated = jpa.em().createQuery(query)
                .setParameter("sessionData", sessionData)
                .setParameter("id", batchId)
                .setParameter("expectedVersion", expectedVersion)
                .executeUpdate();

        return updated == 1 ? expectedVersion + 1 : null;
    }

}
