package daos.common.worker;

import daos.common.AbstractDao;
import models.common.StudyResult;
import models.common.workers.Worker;
import play.db.jpa.JPAApi;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import java.util.List;
import java.util.Optional;

/**
 * DAO for abstract Worker entity
 *
 * @author Kristian Lange
 */
@Singleton
public class WorkerDao extends AbstractDao {

    @Inject
    WorkerDao(JPAApi jpa) {
        super(jpa);
    }

    public void persist(Worker worker) {
        super.persist(worker);
    }

    public Worker merge(Worker worker) {
        return super.merge(worker);
    }

    public void remove(Worker worker) {
        super.remove(worker);
    }

    public Worker findById(Long id) {
        return jpa.withTransaction("default", true, (EntityManager em) -> em.find(Worker.class, id));
    }

    public List<Worker> findAll() {
        return jpa.withTransaction("default", true, (EntityManager em) ->
                em.createQuery("SELECT w FROM Worker w", Worker.class).getResultList());
    }

    /**
     * Returns the number of Worker rows
     */
    public int count() {
        return jpa.withTransaction("default", true, (EntityManager em) -> {
            Number result = (Number) em.createQuery("SELECT COUNT(w) FROM Worker w").getSingleResult();
            return result != null ? result.intValue() : 0;
        });
    }

    /**
     * Returns the total number of Worker (including the deleted ones)
     */
    public int countTotal() {
        return jpa.withTransaction("default", true, (EntityManager em) -> {
            Number result = (Number) em.createQuery("SELECT max(id) FROM Worker").getSingleResult();
            return result != null ? result.intValue() : 0;
        });
    }

    /**
     * Returns the number of StudyResults that belong to the given Worker.
     */
    public int countStudyResults(Worker worker) {
        return jpa.withTransaction("default", true, (EntityManager em) -> {
            Number result = (Number) em.createQuery(
                            "SELECT COUNT(sr) FROM StudyResult sr WHERE sr.worker = :worker")
                    .setParameter("worker", worker)
                    .getSingleResult();
            return result != null ? result.intValue() : 0;
        });
    }

    /**
     * Returns the first StudyResult of the given Worker.
     */
    public Optional<StudyResult> findFirstStudyResult(Worker worker) {
        return jpa.withTransaction("default", true, (EntityManager em) -> {
            try {
                StudyResult result = em.createQuery(
                                "SELECT sr FROM StudyResult sr WHERE sr.worker = :worker ORDER BY sr.id ASC",
                                StudyResult.class)
                        .setParameter("worker", worker)
                        .setMaxResults(1)
                        .getSingleResult();
                return Optional.ofNullable(result);
            } catch (NoResultException e) {
                return Optional.empty();
            }
        });
    }

    /**
     * Returns the last StudyResult of the given Worker.
     */
    public Optional<StudyResult> findLastStudyResult(Worker worker) {
        return jpa.withTransaction("default", true, (EntityManager em) -> {
            try {
                StudyResult result = em.createQuery(
                                "SELECT sr FROM StudyResult sr WHERE sr.worker = :worker ORDER BY sr.id DESC",
                                StudyResult.class)
                        .setParameter("worker", worker)
                        .setMaxResults(1)
                        .getSingleResult();
                return Optional.ofNullable(result);
            } catch (NoResultException e) {
                return Optional.empty();
            }
        });
    }

}
