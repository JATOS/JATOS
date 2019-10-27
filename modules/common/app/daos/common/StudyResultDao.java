package daos.common;

import models.common.Batch;
import models.common.GroupResult;
import models.common.Study;
import models.common.StudyResult;
import models.common.workers.Worker;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
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
        Number result = (Number) query.setParameter("study", study).getSingleResult();
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
     * Returns the number of StudyResults belonging to the given worker.
     */
    public int countByWorker(Worker worker) {
        String queryStr = "SELECT COUNT(*) FROM StudyResult sr WHERE sr.worker_id = :workerId";
        Query query = jpa.em().createNativeQuery(queryStr).setParameter("workerId", worker.getId());
        Number result = (Number) query.getSingleResult();
        return result.intValue();
    }

    /**
     * Returns the number of StudyResults belonging to the given group.
     */
    public int countByGroup(GroupResult groupResult) {
        String queryStr = "SELECT COUNT(*) FROM StudyResult sr WHERE sr.activeGroupMember_id = :groupId "
                + "OR sr.historyGroupMember_id = :groupId";
        Query query = jpa.em().createNativeQuery(queryStr).setParameter("groupId", groupResult.getId());
        Number result = (Number) query.getSingleResult();
        return result.intValue();
    }

    /**
     * Returns the number of StudyResults belonging to the given batch and given worker type.
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

    public ScrollableResults findAllByStudyScrollable(Study study) {
        String queryStr = "SELECT sr FROM StudyResult sr WHERE sr.study=:study";
        org.hibernate.query.Query query = (org.hibernate.query.Query) jpa.em().createQuery(queryStr, StudyResult.class);
        return query.setParameter("study", study).scroll(ScrollMode.FORWARD_ONLY);
    }

    public ScrollableResults findAllByBatchScrollable(Batch batch) {
        String queryStr = "SELECT sr FROM StudyResult sr WHERE sr.batch=:batch";
        org.hibernate.query.Query query = (org.hibernate.query.Query) jpa.em().createQuery(queryStr, StudyResult.class);
        return query.setParameter("batch", batch).scroll(ScrollMode.FORWARD_ONLY);
    }

    public List<StudyResult> findAllByBatch(Batch batch) {
        String queryStr = "SELECT sr FROM StudyResult sr WHERE sr.batch=:batch";
        TypedQuery<StudyResult> query = jpa.em().createQuery(queryStr, StudyResult.class);
        return query.setParameter("batch", batch).getResultList();
    }

    /**
     * Returns a ScrollableResults of all StudyResults that belongs to the given Batch and worker type.
     */
    public ScrollableResults findAllByBatchAndWorkerTypeScrollable(Batch batch, String workerType) {
        String queryStr = "SELECT sr FROM StudyResult sr WHERE sr.batch=:batch "
                + "AND sr.worker IN (SELECT w FROM Worker w WHERE w.class=:workerType)";
        org.hibernate.query.Query query = (org.hibernate.query.Query) jpa.em().createQuery(queryStr, StudyResult.class);
        return query.setParameter("batch", batch).setParameter("workerType", workerType).scroll(
                ScrollMode.FORWARD_ONLY);
    }

    public ScrollableResults findAllByWorkerScrollable(Worker worker) {
        String queryStr = "SELECT sr FROM StudyResult sr WHERE sr.worker=:worker";
        org.hibernate.query.Query query = (org.hibernate.query.Query)  jpa.em().createQuery(queryStr, StudyResult.class);
        return query.setParameter("worker", worker).scroll(ScrollMode.FORWARD_ONLY);
    }

    public ScrollableResults findAllByGroupScrollable(GroupResult groupResult) {
        String queryStr = "SELECT sr FROM StudyResult sr WHERE sr.activeGroupMember_id = :groupId "
                + "OR sr.historyGroupMember_id = :groupId";
        org.hibernate.query.Query query = (org.hibernate.query.Query)  jpa.em().createQuery(queryStr, StudyResult.class);
        return query.setParameter("groupId", groupResult.getId()).scroll(ScrollMode.FORWARD_ONLY);
    }

}
