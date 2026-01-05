package daos.common;

import daos.common.worker.WorkerType;
import models.common.Batch;
import models.common.StudyLink;
import models.common.workers.Worker;
import play.db.jpa.JPAApi;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.EntityManager;
import java.util.List;
import java.util.Optional;

/**
 * @author Kristian Lange
 */
@Singleton
public class StudyLinkDao extends AbstractDao {

    @Inject
    StudyLinkDao(JPAApi jpa) {
        super(jpa);
    }

    public StudyLink persist(StudyLink studyLink) {
        super.persist(studyLink);
        return studyLink;
    }

    public void remove(StudyLink studyLink) {
        super.remove(studyLink);
    }

    public StudyLink merge(StudyLink studyLink) {
        return super.merge(studyLink);
    }

    public StudyLink findByStudyCode(String studyCode) {
        return jpa.withTransaction((javax.persistence.EntityManager em) -> em.find(StudyLink.class, studyCode));
    }

    public int countAll() {
        return jpa.withTransaction("default", true, (EntityManager em) -> {
            Number result = (Number) em.createQuery("SELECT count(sl) FROM StudyLink sl").getSingleResult();
            return result != null ? result.intValue() : 0;
        });
    }

    public int countByBatchAndWorkerType(Batch batch, WorkerType workerType) {
        return jpa.withTransaction("default", true, (EntityManager em) -> {
            String queryStr = "SELECT count(sl) FROM StudyLink sl WHERE sl.batch = :batch AND sl.workerType = :workerType";
            Number result = (Number) em.createQuery(queryStr)
                    .setParameter("batch", batch)
                    .setParameter("workerType", workerType)
                    .getSingleResult();
            return result != null ? result.intValue() : 0;
        });
    }

    public List<StudyLink> findAllByBatchAndWorkerType(Batch batch, WorkerType workerType) {
        return jpa.withTransaction("default", true, (EntityManager em) -> {
            String queryStr = "SELECT sl FROM StudyLink sl " +
                    "LEFT JOIN FETCH sl.worker w " +
                    "WHERE sl.batch = :batch AND sl.workerType = :workerType";
            return em.createQuery(queryStr, StudyLink.class)
                    .setParameter("batch", batch)
                    .setParameter("workerType", workerType)
                    .getResultList();
        });
    }

    public Optional<StudyLink> findFirstByBatchAndWorkerType(Batch batch, WorkerType workerType) {
        return jpa.withTransaction("default", true, (EntityManager em) -> {
            String queryStr = "SELECT sr FROM StudyLink sr WHERE sr.batch =:batch AND sr.workerType = :workerType";
            List<StudyLink> studyLink = em.createQuery(queryStr, StudyLink.class)
                    .setParameter("batch", batch)
                    .setParameter("workerType", workerType)
                    .setMaxResults(1)
                    .getResultList();
            return !studyLink.isEmpty() ? Optional.of(studyLink.get(0)) : Optional.empty();
        });
    }

    public Optional<StudyLink> findByBatchAndWorker(Batch batch, Worker worker) {
        return jpa.withTransaction("default", true, (EntityManager em) -> {
            String queryStr = "SELECT sr FROM StudyLink sr WHERE sr.batch =:batch AND sr.worker = :worker";
            List<StudyLink> studyLink = em.createQuery(queryStr, StudyLink.class)
                    .setParameter("batch", batch)
                    .setParameter("worker", worker)
                    .setMaxResults(1)
                    .getResultList();
            return !studyLink.isEmpty() ? Optional.of(studyLink.get(0)) : Optional.empty();
        });
    }

    public void removeAllByBatch(Batch batch) {
        jpa.withTransaction(em -> {
            return em.createQuery("DELETE FROM StudyLink sr WHERE sr.batch = :batch")
                    .setParameter("batch", batch)
                    .executeUpdate();
        });
    }

}
