package daos.common;

import models.common.Batch;
import models.common.Study;
import models.common.StudyResult;
import play.db.jpa.JPAApi;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import java.util.List;

/**
 * DAO for StudyResult entity
 *
 * @author Kristian Lange
 */
@Singleton
public class StudyResultDao extends AbstractDao {

    @Inject
    StudyResultDao(JPAApi jpa) {
        super(jpa);
    }

    public void create(StudyResult studyResult) {
        super.persist(studyResult);
    }

    public void update(StudyResult studyResult) {
        merge(studyResult);
    }

    public void remove(StudyResult studyResult) {
        super.remove(studyResult);
    }

    public void refresh(StudyResult studyResult) {
        super.refresh(studyResult);
    }

    public StudyResult findById(Long id) {
        return jpa.em().find(StudyResult.class, id);
    }

    /**
     * Returns the number of StudyResults belonging to the given study.
     */
    public int countByStudy(Study study) {
        String queryStr = "SELECT COUNT(sr) FROM StudyResult sr WHERE sr.study=:study";
        Query query = jpa.em().createQuery(queryStr);
        Number result = (Number) query.setParameter("study", study)
                .getSingleResult();
        return result.intValue();
    }

    /**
     * Returns the number of StudyResults belonging to the given batch.
     */
    public int countByBatch(Batch batch) {
        String queryStr = "SELECT COUNT(*) FROM StudyResult sr WHERE sr.batch_id = :batchId";
        Query query = jpa.em().createNativeQuery(queryStr).setParameter("batchId", batch.getId());
        Number result = (Number) query.getSingleResult();
        return result.intValue();
    }

    /**
     * Returns the number of StudyResults belonging to the given batch and given
     * worker type.
     */
    public int countByBatchAndWorkerType(Batch batch, String workerType) {
        String queryStr = "SELECT COUNT(*) FROM StudyResult sr WHERE sr.batch_id = :batchId "
                + "AND sr.worker_id IN (SELECT id FROM Worker w WHERE w.workerType = :workerType)";
        Query query = jpa.em().createNativeQuery(queryStr)
                .setParameter("batchId", batch.getId())
                .setParameter("workerType", workerType);
        Number result = (Number) query.getSingleResult();
        return result.intValue();
    }

    public List<StudyResult> findAllByStudy(Study study) {
        String queryStr = "SELECT sr FROM StudyResult sr "
                + "WHERE sr.study=:study";
        TypedQuery<StudyResult> query = jpa.em().createQuery(queryStr,
                StudyResult.class);
        return query.setParameter("study", study).getResultList();
    }

    public List<StudyResult> findAllByBatch(Batch batch) {
        String queryStr = "SELECT sr FROM StudyResult sr "
                + "WHERE sr.batch=:batch";
        TypedQuery<StudyResult> query = jpa.em().createQuery(queryStr,
                StudyResult.class);
        return query.setParameter("batch", batch).getResultList();
    }

    /**
     * Returns a list of all StudyResults that belongs to the given Batch and
     * worker type.
     */
    public List<StudyResult> findAllByBatchAndWorkerType(Batch batch,
            String workerType) {
        String queryStr = "SELECT sr FROM StudyResult sr WHERE "
                + "sr.batch=:batch "
                + "AND sr.worker IN (SELECT w FROM Worker w WHERE w.class=:workerType)";
        TypedQuery<StudyResult> query = jpa.em().createQuery(queryStr,
                StudyResult.class);
        return query.setParameter("batch", batch)
                .setParameter("workerType", workerType).getResultList();
    }

}
