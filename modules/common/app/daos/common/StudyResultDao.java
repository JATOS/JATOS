package daos.common;

import models.common.*;
import models.common.workers.Worker;
import play.db.jpa.JPAApi;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.Query;
import java.util.List;
import java.util.Optional;

/**
 * DAO for StudyResult and StudyResultStatus
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
     * Returns the number of StudyResult rows
     */
    public int count() {
        Number result = (Number) jpa.em().createQuery("SELECT COUNT(sr) FROM StudyResult sr").getSingleResult();
        return result.intValue();
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
     * Returns the number of StudyResults belonging to the given worker. It checks for each StudyResult if its
     * Study has the given User as a member.
     */
    public int countByWorker(Worker worker, User user) {
        Number result = (Number) jpa.em().createQuery("SELECT COUNT(*) FROM StudyResult sr WHERE sr.worker = :worker "
                + "AND sr.study IN (SELECT s FROM Study s JOIN s.userList ul where ul.username = :username)")
                .setParameter("worker", worker)
                .setParameter("username", user.getUsername())
                .getSingleResult();
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

    public List<StudyResult> findAllByStudy(Study study) {
        return jpa.em().createQuery("SELECT sr FROM StudyResult sr WHERE sr.study=:study", StudyResult.class)
                .setParameter("study", study)
                .getResultList();
    }

    /**
     * Returns paginated StudyResults that belong to the given Study
     *
     * We can't use ScrollableResults for pagination since the MySQL Hibernate driver doesn't support it
     * (https://stackoverflow.com/a/2826512/1278769)
     */
    public List<StudyResult> findAllByStudy(Study study, int first, int max) {
        return jpa.em().createQuery("SELECT sr FROM StudyResult sr WHERE sr.study=:study", StudyResult.class)
                .setFirstResult(first)
                .setMaxResults(max)
                .setParameter("study", study)
                .getResultList();
    }

    public List<StudyResult> findAllByBatch(Batch batch) {
        return jpa.em().createQuery("SELECT sr FROM StudyResult sr WHERE sr.batch=:batch", StudyResult.class)
                .setParameter("batch", batch)
                .getResultList();
    }

    /**
     * Returns paginated StudyResults that belong to the given Batch.
     *
     * We can't use ScrollableResults for pagination since the MySQL Hibernate driver doesn't support it
     * (https://stackoverflow.com/a/2826512/1278769)
     */
    public List<StudyResult> findAllByBatch(Batch batch, int first, int max) {
        return jpa.em().createQuery("SELECT sr FROM StudyResult sr WHERE sr.batch=:batch", StudyResult.class)
                .setFirstResult(first)
                .setMaxResults(max)
                .setParameter("batch", batch)
                .getResultList();
    }

    /**
     * Returns paginated StudyResults that belong to the given Batch and worker type.
     *
     * We can't use ScrollableResults for pagination since the MySQL Hibernate driver doesn't support it
     * (https://stackoverflow.com/a/2826512/1278769)
     */
    public List<StudyResult> findAllByBatchAndWorkerType(Batch batch, String workerType, int first, int max) {
        return jpa.em().createQuery("SELECT sr FROM StudyResult sr WHERE sr.batch=:batch "
                + "AND sr.worker IN (SELECT w FROM Worker w WHERE w.class=:workerType)", StudyResult.class)
                .setFirstResult(first)
                .setMaxResults(max)
                .setParameter("batch", batch)
                .setParameter("workerType", workerType)
                .getResultList();
    }

    /**
     * Returns paginated StudyResults that belong to the given Worker and User. It checks for each StudyResult if its
     * Study has the given User as a member.
     *
     * We can't use ScrollableResults for pagination since the MySQL Hibernate driver doesn't support it
     * (https://stackoverflow.com/a/2826512/1278769)
     */
    public List<StudyResult> findAllByWorker(Worker worker, User user, int first, int max) {
        return jpa.em().createQuery("SELECT sr FROM StudyResult sr WHERE sr.worker = :worker AND sr.study IN "
                + "(SELECT s FROM Study s JOIN s.userList ul where ul.username = :username)", StudyResult.class)
                .setFirstResult(first)
                .setMaxResults(max)
                .setParameter("worker", worker)
                .setParameter("username", user.getUsername())
                .getResultList();
    }

    /**
     * We can't use ScrollableResults for pagination since the MySQL Hibernate driver doesn't support it
     * (https://stackoverflow.com/a/2826512/1278769)
     */
    public List<StudyResult> findAllByGroup(GroupResult groupResult, int first, int max) {
        return jpa.em().createQuery("SELECT sr FROM StudyResult sr WHERE sr.activeGroupResult = :group "
                + "OR sr.historyGroupResult = :group", StudyResult.class)
                .setFirstResult(first)
                .setMaxResults(max)
                .setParameter("group", groupResult)
                .getResultList();
    }

    /**
     * Find the StudyResultStatus with the most recent startDate that belongs to the given study
     */
    public Optional<StudyResultStatus> findLastStarted(Study study) {
        String queryStr = "SELECT srs FROM StudyResultStatus srs "
                + "WHERE srs.study = :study "
                + "AND srs.startDate is not null "
                + "ORDER BY srs.startDate desc";
        List<StudyResultStatus> resultList = jpa.em().createQuery(queryStr, StudyResultStatus.class)
                .setParameter("study", study)
                .setMaxResults(1)
                .getResultList();
        return !resultList.isEmpty() ? Optional.of(resultList.get(0)) : Optional.empty();
    }

    /**
     * Find the StudyResultStatus with the most recent lastSeen
     */
    public Optional<StudyResultStatus> findLastSeen() {
        String queryStr = "SELECT srs FROM StudyResultStatus srs "
                + "WHERE srs.lastSeenDate is not null "
                + "ORDER BY srs.lastSeenDate desc";
        List<StudyResultStatus> resultList = jpa.em().createQuery(queryStr, StudyResultStatus.class)
                .setMaxResults(1)
                .getResultList();
        return !resultList.isEmpty() ? Optional.of(resultList.get(0)) : Optional.empty();
    }

}
