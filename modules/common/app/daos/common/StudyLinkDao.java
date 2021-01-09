package daos.common;

import models.common.Batch;
import models.common.StudyLink;
import models.common.workers.Worker;
import play.db.jpa.JPAApi;

import javax.inject.Inject;
import javax.inject.Singleton;
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

    public StudyLink create(StudyLink studyLink) {
        persist(studyLink);
        return studyLink;
    }

    public void remove(StudyLink studyLink) {
        super.remove(studyLink);
    }

    public void update(StudyLink studyLink) {
        merge(studyLink);
    }

    public StudyLink findById(String id) {
        return jpa.em().find(StudyLink.class, id);
    }

    public Long countByBatchAndWorkerType(Batch batch, String workerType) {
        String queryStr = "SELECT count(*) FROM StudyLink sr WHERE sr.batch = :batch AND sr.workerType = :workerType";
        return (Long) jpa.em().createQuery(queryStr)
                .setParameter("batch", batch)
                .setParameter("workerType", workerType)
                .getSingleResult();
    }

    public List<StudyLink> findAllByBatchAndWorkerType(Batch batch, String workerType) {
        String queryStr = "SELECT sr FROM StudyLink sr WHERE sr.batch = :batch AND sr.workerType = :workerType";
        return jpa.em().createQuery(queryStr, StudyLink.class)
                .setParameter("batch", batch)
                .setParameter("workerType", workerType)
                .getResultList();
    }

    public Optional<StudyLink> findFirstByBatchAndWorkerType(Batch batch, String workerType) {
        String queryStr = "SELECT sr FROM StudyLink sr WHERE sr.batch =:batch AND sr.workerType = :workerType";
        List<StudyLink> studyLink = jpa.em().createQuery(queryStr, StudyLink.class)
                .setParameter("batch", batch)
                .setParameter("workerType", workerType)
                .setMaxResults(1)
                .getResultList();
        return !studyLink.isEmpty() ? Optional.of(studyLink.get(0)) : Optional.empty();
    }

    public Optional<StudyLink> findByBatchAndWorker(Batch batch, Worker worker) {
        String queryStr = "SELECT sr FROM StudyLink sr WHERE sr.batch =:batch AND sr.worker = :worker";
        List<StudyLink> studyLink = jpa.em().createQuery(queryStr, StudyLink.class)
                .setParameter("batch", batch)
                .setParameter("worker", worker)
                .setMaxResults(1)
                .getResultList();
        return !studyLink.isEmpty() ? Optional.of(studyLink.get(0)) : Optional.empty();
    }

    public int removeAllByBatch(Batch batch) {
        return jpa.em().createQuery("DELETE FROM StudyLink sr WHERE sr.batch = :batch")
                .setParameter("batch", batch)
                .executeUpdate();
    }

}
