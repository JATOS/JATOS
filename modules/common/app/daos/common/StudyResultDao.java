package daos.common;

import daos.common.worker.WorkerType;
import models.common.*;
import models.common.workers.Worker;
import play.db.jpa.JPAApi;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.persistence.Tuple;
import java.util.*;
import java.util.stream.Collectors;

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

    public void persist(StudyResult studyResult) {
        super.persist(studyResult);
    }

    public StudyResult merge(StudyResult studyResult) {
        return super.merge(studyResult);
    }

    /**
     * Only update the 'studySessionData' field and leave everything else untouched
     */
    public void updateStudySessionData(Long id, String studySessionData) {
        jpa.withTransaction(em -> {
            em.createQuery("UPDATE StudyResult sr SET sr.studySessionData = :ssd WHERE sr.id = :id")
                    .setParameter("id", id)
                    .setParameter("ssd", studySessionData)
                    .executeUpdate();
        });
    }

    public void remove(StudyResult studyResult) {
        super.remove(studyResult);
    }

    public void refresh(StudyResult studyResult) {
        super.refresh(studyResult);
    }

    public StudyResult findById(Long id) {
        return jpa.withTransaction("default", true, (EntityManager em) -> em.find(StudyResult.class, id));
    }

    /**
     * Finds a StudyResult by ID and eagerly fetches the componentResultList.
     */
    public StudyResult findByIdWithComponentResults(Long id) {
        return jpa.withTransaction("default", true, em -> {
            return em.createQuery(
                            "SELECT sr FROM StudyResult sr " +
                                    "LEFT JOIN FETCH sr.componentResultList " +
                                    "WHERE sr.id = :id", StudyResult.class)
                    .setParameter("id", id)
                    .getSingleResult();
        });
    }

    public List<StudyResult> findByIds(List<Long> ids) {
        if (ids.isEmpty()) return Collections.emptyList();
        return jpa.withTransaction("default", true, (EntityManager em) -> em
                .createQuery("SELECT sr FROM StudyResult sr WHERE sr.id IN :ids", StudyResult.class)
                .setParameter("ids", ids)
                .getResultList());
    }

    public List<StudyResult> findByIds(List<Long> ids, int first, int max) {
        if (ids.isEmpty()) return Collections.emptyList();
        return jpa.withTransaction("default", true, (EntityManager em) -> em
                .createQuery(
                        "SELECT sr FROM StudyResult sr " +
                                "WHERE sr.id IN :ids " +
                                "ORDER BY sr.id ASC",
                        StudyResult.class)
                .setParameter("ids", ids)
                .setFirstResult(first)
                .setMaxResults(max)
                .getResultList());
    }

    public Optional<StudyResult> findByUuid(String uuid) {
        return jpa.withTransaction("default", true, em -> {
            List<StudyResult> studyResult = em
                    .createQuery("SELECT sr FROM StudyResult sr WHERE sr.uuid =:uuid", StudyResult.class)
                    .setParameter("uuid", uuid)
                    .setMaxResults(1)
                    .getResultList();
            return !studyResult.isEmpty() ? Optional.of(studyResult.get(0)) : Optional.empty();
        });
    }

    public Optional<StudyResult> findByStudyCode(String studyCode) {
        return jpa.withTransaction("default", true, em -> {
            List<StudyResult> studyResult = em
                    .createQuery("SELECT sr FROM StudyResult sr WHERE sr.studyCode =:studyCode", StudyResult.class)
                    .setParameter("studyCode", studyCode)
                    .setMaxResults(1)
                    .getResultList();
            return !studyResult.isEmpty() ? Optional.of(studyResult.get(0)) : Optional.empty();
        });
    }

    /**
     * Returns a list of StudyResults that are active members of a group and have been idle for a while. An idle
     * StudyResult is one that has an active group result and a lastSeenDate that is older than the given seconds.
     */
    public List<StudyResult> findIdleGroupMembers(int idleAfterSeconds) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.SECOND, -idleAfterSeconds);
        return jpa.withTransaction("default", true, (EntityManager em) -> em
                .createQuery("SELECT sr FROM StudyResult sr "
                        + "WHERE sr.activeGroupResult is not null "
                        + "AND sr.lastSeenDate < :date", StudyResult.class)
                .setParameter("date", cal.getTime())
                .getResultList());
    }

    /**
     * Returns the number of StudyResult rows
     */
    public int count() {
        return jpa.withTransaction("default", true, em -> {
            Number result = (Number) em.createQuery("SELECT COUNT(sr) FROM StudyResult sr").getSingleResult();
            return result != null ? result.intValue() : 0;
        });
    }

    /**
     * Returns the total number of StudyResults (including the deleted ones)
     */
    public int countTotal() {
        return jpa.withTransaction("default", true, em -> {
            Number result = (Number) em.createQuery("SELECT max(id) FROM StudyResult").getSingleResult();
            return result != null ? result.intValue() : 0;
        });
    }

    /**
     * Returns the number of StudyResults belonging to the given study.
     */
    public int countByStudy(Study study) {
        return jpa.withTransaction("default", true, em -> {
            String queryStr = "SELECT COUNT(sr) FROM StudyResult sr WHERE sr.study=:study";
            Query query = em.createQuery(queryStr);
            Number result = (Number) query.setParameter("study", study).getSingleResult();
            return result != null ? result.intValue() : 0;
        });
    }

    /**
     * Returns the number of StudyResults belonging to the given batch but excludes results from the given
     * workerTypeToBeExcluded.
     */
    public int countByBatch(Batch batch, WorkerType workerTypeToBeExcluded) {
        return jpa.withTransaction("default", true, em -> {
            Number result = (Number) em.createQuery("SELECT COUNT(sr) FROM StudyResult sr WHERE sr.batch=:batch "
                            + "AND sr.worker.class <> :workerType")
                    .setParameter("batch", batch)
                    .setParameter("workerType", workerTypeToBeExcluded.value())
                    .getSingleResult();
            return result != null ? result.intValue() : 0;
        });
    }

    /**
     * Returns the number of StudyResults belonging to the given worker. It checks for each StudyResult if its Study has
     * the given User as a member.
     */
    public int countByWorker(Worker worker, User user) {
        return jpa.withTransaction("default", true, em -> {
            Number result = (Number) em.createQuery("SELECT COUNT(sr) FROM StudyResult sr WHERE sr.worker = :worker "
                            + "AND sr.study IN (SELECT s FROM Study s JOIN s.userList ul where ul.username = :username)")
                    .setParameter("worker", worker)
                    .setParameter("username", user.getUsername())
                    .getSingleResult();
            return result != null ? result.intValue() : 0;
        });
    }

    /**
     * Returns the number of StudyResults belonging to the given group.
     */
    public int countByGroup(GroupResult groupResult) {
        return jpa.withTransaction("default", true, em -> {
            String queryStr = "SELECT COUNT(sr) FROM StudyResult sr WHERE sr.activeGroupResult = :groupResult "
                    + "OR sr.historyGroupResult = :groupResult";
            Number result = (Number) em.createQuery(queryStr)
                    .setParameter("groupResult", groupResult)
                    .getSingleResult();
            return result != null ? result.intValue() : 0;
        });
    }

    /**
     * Returns the number of StudyResults belonging to the given batch and given worker type. If the worker type is 'MT'
     * it additionally returns the number of 'MTSandbox' results.
     */
    public int countByBatchAndWorkerType(Batch batch, WorkerType workerType) {
        if (workerType == WorkerType.MT) {
            return jpa.withTransaction("default", true, em -> {
                String hql = "SELECT COUNT(sr) FROM StudyResult sr "
                        + "JOIN sr.worker w "
                        + "WHERE sr.batch = :batch "
                        + "AND w.workerType LIKE 'MT%'";
                Number result = (Number) em.createQuery(hql)
                        .setParameter("batch", batch)
                        .getSingleResult();
                return result != null ? result.intValue() : 0;
            });
        } else {
            return jpa.withTransaction("default", true, em -> {
                String query = "SELECT COUNT(sr) FROM StudyResult sr "
                        + "WHERE sr.batch = :batch AND sr.worker.class = :workerType";
                return ((Long) em
                        .createQuery(query)
                        .setParameter("batch", batch)
                        .setParameter("workerType", workerType.value())
                        .getSingleResult()).intValue();
            });
        }
    }

    public List<Long> findIdsByStudyId(Long studyId) {
        return jpa.withTransaction("default", true, em -> {
            return em.createQuery("SELECT sr.id FROM StudyResult sr WHERE sr.study.id = :studyId", Long.class)
                    .setParameter("studyId", studyId)
                    .getResultList();
        });
    }

    /**
     * Returns paginated StudyResults that belong to the given Study
     *
     * We can't use ScrollableResults for pagination since the MySQL Hibernate driver doesn't support it
     * (https://stackoverflow.com/a/2826512/1278769)
     */
    public List<StudyResult> findAllByStudy(Study study, int first, int max) {
        // Added 'LEFT JOIN FETCH' for performance (loads LAZY-linked Workers in StudyResults)
        return jpa.withTransaction("default", true, (EntityManager em) -> em
                .createQuery(
                        "SELECT sr FROM StudyResult sr " +
                                "LEFT JOIN FETCH sr.worker " +
                                "WHERE sr.study = :study " +
                                "ORDER BY sr.id ASC",
                        StudyResult.class)
                .setFirstResult(first)
                .setMaxResults(max)
                .setParameter("study", study)
                .getResultList());
    }

    public List<StudyResult> findAllByBatch(Batch batch) {
        return jpa.withTransaction("default", true, (EntityManager em) -> em
                .createQuery("SELECT sr FROM StudyResult sr WHERE sr.batch=:batch", StudyResult.class)
                .setParameter("batch", batch)
                .getResultList());
    }

    /**
     * Returns paginated StudyResults that belong to the given Batch but excludes results from the given
     * workerTypeToBeExcluded.
     *
     * We can't use ScrollableResults for pagination since the MySQL Hibernate driver doesn't support it
     * (https://stackoverflow.com/a/2826512/1278769)
     */
    public List<StudyResult> findAllByBatch(Batch batch, WorkerType workerTypeToBeExcluded, int first, int max) {
        return jpa.withTransaction("default", true, (EntityManager em) -> em
                .createQuery(
                        "SELECT sr FROM StudyResult sr " +
                                "WHERE sr.batch = :batch " +
                                "AND sr.worker.class <> :workerType " +
                                "ORDER BY sr.id ASC",
                        StudyResult.class)
                .setFirstResult(first)
                .setMaxResults(max)
                .setParameter("batch", batch)
                .setParameter("workerType", workerTypeToBeExcluded.value())
                .getResultList());
    }

    /**
     * Returns paginated StudyResults that belong to the given Batch and worker type. If the worker type is 'MT' it
     * additionally returns the MTSandbox results.
     *
     * We can't use ScrollableResults for pagination since the MySQL Hibernate driver doesn't support it
     * (https://stackoverflow.com/a/2826512/1278769)
     */
    public List<StudyResult> findAllByBatchAndWorkerType(Batch batch, WorkerType workerType, int first, int max) {
        if (workerType == WorkerType.MT) {
            return jpa.withTransaction("default", true, (EntityManager em) -> em
                    .createQuery(
                            "SELECT sr FROM StudyResult sr " +
                                    "WHERE sr.batch = :batch " +
                                    "AND sr.worker IN (" +
                                    "SELECT w FROM Worker w WHERE w.class LIKE 'MT%'" +
                                    ") " +
                                    "ORDER BY sr.id ASC",
                            StudyResult.class)
                    .setFirstResult(first)
                    .setMaxResults(max)
                    .setParameter("batch", batch)
                    .getResultList());
        } else {
            return jpa.withTransaction("default", true, (EntityManager em) -> em
                    .createQuery(
                            "SELECT sr FROM StudyResult sr " +
                                    "WHERE sr.batch = :batch " +
                                    "AND sr.worker.class = :workerType " +
                                    "ORDER BY sr.id ASC",
                            StudyResult.class)
                    .setFirstResult(first)
                    .setMaxResults(max)
                    .setParameter("batch", batch)
                    .setParameter("workerType", workerType.value())
                    .getResultList());
        }
    }

    /**
     * Returns paginated StudyResults that belong to the given Worker and User. It checks for each StudyResult if its
     * Study has the given User as a member.
     *
     * We can't use ScrollableResults for pagination since the MySQL Hibernate driver doesn't support it
     * (https://stackoverflow.com/a/2826512/1278769)
     */
    public List<StudyResult> findAllByWorker(Worker worker, User user, int first, int max) {
        return jpa.withTransaction("default", true, (EntityManager em) -> em
                .createQuery(
                        "SELECT sr FROM StudyResult sr " +
                                "WHERE sr.worker = :worker " +
                                "AND sr.study IN (" +
                                "SELECT s FROM Study s " +
                                "JOIN s.userList ul " +
                                "WHERE ul.username = :username" +
                                ") " +
                                "ORDER BY sr.id ASC",
                        StudyResult.class)
                .setFirstResult(first)
                .setMaxResults(max)
                .setParameter("worker", worker)
                .setParameter("username", user.getUsername())
                .getResultList());
    }

    /**
     * We can't use ScrollableResults for pagination since the MySQL Hibernate driver doesn't support it
     * (https://stackoverflow.com/a/2826512/1278769)
     */
    public List<StudyResult> findAllByGroup(GroupResult groupResult, int first, int max) {
        return jpa.withTransaction("default", true, (EntityManager em) -> em
                .createQuery(
                        "SELECT sr FROM StudyResult sr " +
                                "WHERE sr.activeGroupResult = :group " +
                                "OR sr.historyGroupResult = :group " +
                                "ORDER BY sr.id ASC",
                        StudyResult.class)
                .setFirstResult(first)
                .setMaxResults(max)
                .setParameter("group", groupResult)
                .getResultList());
    }

    /**
     * Find the StudyResultStatus with the most recent startDate that belongs to the given study
     */
    public Optional<StudyResultStatus> findLastStarted(Study study) {
        return jpa.withTransaction("default", true, em -> {
            String queryStr = "SELECT srs FROM StudyResultStatus srs "
                    + "WHERE srs.study = :study "
                    + "AND srs.startDate IS NOT NULL "
                    + "ORDER BY srs.startDate DESC";
            List<StudyResultStatus> resultList = em.createQuery(queryStr, StudyResultStatus.class)
                    .setParameter("study", study)
                    .setMaxResults(1)
                    .getResultList();
            return !resultList.isEmpty() ? Optional.of(resultList.get(0)) : Optional.empty();
        });
    }

    /**
     * Find the StudyResultStatus with the most recent lastSeen
     */
    public List<StudyResultStatus> findLastSeen(int limit) {
        return jpa.withTransaction("default", true, em -> {
            String queryStr = "SELECT srs FROM StudyResultStatus srs "
                    + "WHERE srs.lastSeenDate IS NOT NULL "
                    + "ORDER BY srs.lastSeenDate DESC";
            return em.createQuery(queryStr, StudyResultStatus.class)
                    .setMaxResults(limit)
                    .getResultList();
        });
    }

    /**
     * Returns a list of unique study result IDs that belong to the given list of component result IDs.
     */
    public List<Long> findIdsByComponentResultIds(List<Long> crids) {
        if (crids.isEmpty()) return Collections.emptyList();
        return jpa.withTransaction("default", true, em -> {
            return em.createQuery("SELECT cr.studyResult.id FROM ComponentResult cr WHERE cr.id IN :crids", Long.class)
                    .setParameter("crids", crids)
                    .getResultList().stream().distinct().collect(Collectors.toList());
        });
    }

    public List<Long> findIdsFromListThatBelongToStudy(List<Long> srids, Long studyId) {
        if (srids.isEmpty()) return Collections.emptyList();
        return jpa.withTransaction("default", true, em -> {
            return em.createQuery("SELECT sr.id FROM StudyResult sr WHERE sr.id IN :srids AND sr.study.id = :studyId", Long.class)
                    .setParameter("srids", srids)
                    .setParameter("studyId", studyId)
                    .getResultList().stream().distinct().collect(Collectors.toList());
        });
    }

    public List<Long> findIdsByBatchIds(List<Long> batchIds) {
        if (batchIds.isEmpty()) return Collections.emptyList();
        return jpa.withTransaction("default", true, em -> {
            return em.createQuery("SELECT sr.id FROM StudyResult sr WHERE sr.batch.id IN :batchIds", Long.class)
                    .setParameter("batchIds", batchIds)
                    .getResultList();
        });
    }

    public List<Long> findIdsByGroupIds(List<Long> groupIds) {
        if (groupIds.isEmpty()) return Collections.emptyList();
        return jpa.withTransaction("default", true, em -> {
            return em.createQuery("SELECT sr.id FROM StudyResult sr WHERE sr.activeGroupResult.id IN :groupIds OR sr.historyGroupResult.id IN :groupIds", Long.class)
                    .setParameter("groupIds", groupIds)
                    .getResultList();
        });
    }

    public Map<Long, Integer> countComponentResultsForStudyResultIds(List<Long> srids) {
        if (srids.isEmpty()) return Collections.emptyMap();
        return jpa.withTransaction("default", true, (EntityManager em) -> {
            List<Tuple> tuples = em
                    .createQuery("SELECT cr.studyResult.id AS srid, COUNT(cr) AS count FROM ComponentResult cr " +
                            "WHERE cr.studyResult.id IN :srids GROUP BY cr.studyResult.id", Tuple.class)
                    .setParameter("srids", srids)
                    .getResultList();
            return tuples.stream().collect(Collectors.toMap(
                    (Tuple t) -> ((Number) t.get("srid")).longValue(),
                    (Tuple t) -> ((Number) t.get("count")).intValue()
            ));
        });
    }

    public void setQuotaReached(Long studyResultId) {
        jpa.withTransaction(em -> {
            em.createQuery("UPDATE StudyResult sr SET sr.quotaReached = true WHERE sr.id = :id")
                    .setParameter("id", studyResultId)
                    .executeUpdate();
        });
    }

    /**
     * Checks if the worker finished this study already at least once. 'Finished' includes FINISHED, FAIL, and ABORTED
     * states.
     */
    public boolean hasFinishedStudy(Worker worker, Study study) {
        return jpa.withTransaction("default", true, em -> {
            String hql = "SELECT COUNT(sr) FROM StudyResult sr " +
                    "WHERE sr.worker = :worker AND sr.study = :study " +
                    "AND sr.studyState IN (:states)";
            List<StudyResult.StudyState> doneStates = Arrays.asList(
                    StudyResult.StudyState.FINISHED,
                    StudyResult.StudyState.ABORTED,
                    StudyResult.StudyState.FAIL
            );
            Number count = (Number) em.createQuery(hql)
                    .setParameter("worker", worker)
                    .setParameter("study", study)
                    .setParameter("states", doneStates)
                    .getSingleResult();
            return count != null && count.intValue() > 0;
        });
    }

}
