package daos.common;

import models.common.workers.WorkerType;
import models.common.*;
import models.common.workers.Worker;
import play.db.jpa.JPAApi;

import javax.inject.Inject;
import javax.inject.Singleton;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * DAO for StudyResult and StudyResultStatus
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
        withTransaction(em -> {
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
        return withReadOnlyTransaction((EntityManager em) -> em.find(StudyResult.class, id));
    }

    public List<StudyResult> findByIds(List<Long> ids) {
        if (ids.isEmpty()) return Collections.emptyList();
        return withReadOnlyTransaction((EntityManager em) ->
                em.createQuery("SELECT sr FROM StudyResult sr WHERE sr.id IN :ids", StudyResult.class)
                        .setParameter("ids", ids)
                        .getResultList());
    }

    public List<StudyResult> findByIds(List<Long> ids, int first, int max) {
        if (ids.isEmpty()) return Collections.emptyList();
        return withReadOnlyTransaction((EntityManager em) ->
                em.createQuery("SELECT sr FROM StudyResult sr " +
                                        "WHERE sr.id IN :ids " +
                                        "ORDER BY sr.id ASC",
                                StudyResult.class)
                        .setParameter("ids", ids)
                        .setFirstResult(first)
                        .setMaxResults(max)
                        .getResultList());
    }

    public Optional<StudyResult> findByUuid(String uuid) {
        return withReadOnlyTransaction((EntityManager em) -> {
            List<StudyResult> studyResult = em
                    .createQuery("SELECT sr FROM StudyResult sr WHERE sr.uuid =:uuid", StudyResult.class)
                    .setParameter("uuid", uuid)
                    .setMaxResults(1)
                    .getResultList();
            return !studyResult.isEmpty() ? Optional.of(studyResult.get(0)) : Optional.empty();
        });
    }

    /**
     * Finds a StudyResult by UUID and initializes fields needed outside of the transaction.
     */
    public Optional<StudyResult> findByUuidWithBatchStudyAndGroup(String uuid) {
        return withReadOnlyTransaction((EntityManager em) -> {
            List<StudyResult> studyResults = em
                    .createQuery("SELECT DISTINCT sr FROM StudyResult sr " +
                                    "LEFT JOIN FETCH sr.batch " +
                                    "LEFT JOIN FETCH sr.historyGroupResult " +
                                    "LEFT JOIN FETCH sr.study s " +
                                    "LEFT JOIN FETCH s.userList " +
                                    "LEFT JOIN FETCH sr.activeGroupResult agr " +
                                    "LEFT JOIN FETCH agr.activeMemberList " +
                                    "WHERE sr.uuid = :uuid",
                            StudyResult.class)
                    .setParameter("uuid", uuid)
                    .setMaxResults(1)
                    .getResultList();

            return studyResults.isEmpty() ? Optional.empty() : Optional.of(studyResults.get(0));
        });
    }

    public boolean existsByUuid(String uuid) {
        return withReadOnlyTransaction(em -> {
            Number count = (Number) em.createQuery("SELECT COUNT(sr) FROM StudyResult sr WHERE sr.uuid = :uuid")
                    .setParameter("uuid", uuid)
                    .getSingleResult();
            return count != null && count.intValue() > 0;
        });
    }

    public Optional<StudyResult> findByStudyCode(String studyCode) {
        return withReadOnlyTransaction((EntityManager em) -> {
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
        return withReadOnlyTransaction((EntityManager em) ->
                em.createQuery("SELECT sr FROM StudyResult sr "
                                + "WHERE sr.activeGroupResult is not null "
                                + "AND sr.lastSeenDate < :date", StudyResult.class)
                        .setParameter("date", cal.getTime())
                        .getResultList());
    }

    /**
     * Returns the number of StudyResult rows
     */
    public int count() {
        return withReadOnlyTransaction((EntityManager em) -> {
            Number result = (Number) em.createQuery("SELECT COUNT(sr) FROM StudyResult sr").getSingleResult();
            return result != null ? result.intValue() : 0;
        });
    }

    /**
     * Returns the total number of StudyResults (including the deleted ones)
     */
    public int countTotal() {
        return withReadOnlyTransaction((EntityManager em) -> {
            Number result = (Number) em.createQuery("SELECT max(id) FROM StudyResult").getSingleResult();
            return result != null ? result.intValue() : 0;
        });
    }

    /**
     * Returns the number of StudyResults belonging to the given study.
     */
    public int countByStudy(Study study) {
        return withReadOnlyTransaction((EntityManager em) -> {
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
    public int countByBatchExcludingWorkerType(Batch batch, WorkerType workerTypeToBeExcluded) {
        return withReadOnlyTransaction((EntityManager em) -> {
            Number result = (Number) em.createQuery("SELECT COUNT(sr) FROM StudyResult sr WHERE sr.batch=:batch "
                            + "AND sr.worker.workerType <> :workerType")
                    .setParameter("batch", batch)
                    .setParameter("workerType", workerTypeToBeExcluded)
                    .getSingleResult();
            return result != null ? result.intValue() : 0;
        });
    }

    /**
     * Returns the number of StudyResults belonging to the given worker. It checks for each StudyResult if its Study has
     * the given User as a member.
     */
    public int countByWorker(Worker worker, User user) {
        return withReadOnlyTransaction((EntityManager em) -> {
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
        return withReadOnlyTransaction((EntityManager em) -> {
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
        List<String> workerTypes = workerType == WorkerType.MT
                ? Arrays.asList(WorkerType.MT.value(), WorkerType.MT_SANDBOX.value())
                : Collections.singletonList(workerType.value());

        return withReadOnlyTransaction((EntityManager em) -> {
            String hql = "SELECT COUNT(sr) FROM StudyResult sr "
                    + "WHERE sr.batch = :batch "
                    + "AND sr.worker.workerType IN (:workerTypes)";
            Number result = (Number) em.createQuery(hql)
                    .setParameter("batch", batch)
                    .setParameter("workerTypes", workerTypes)
                    .getSingleResult();
            return result != null ? result.intValue() : 0;
        });
    }

    /**
     * Returns the number of StudyResults belonging to the given batch, grouped by worker type.
     */
    public Map<WorkerType, Integer> countByBatchForAllWorkerTypes(Batch batch) {
        return withReadOnlyTransaction((EntityManager em) -> {
            Map<WorkerType, Integer> counts = Arrays.stream(WorkerType.values())
                    .filter(workerType -> workerType != WorkerType.NONE)
                    .collect(Collectors.toMap(workerType -> workerType, workerType -> 0));

            List<Tuple> tuples = em
                    .createQuery("SELECT sr.worker.workerType AS workerType, COUNT(sr) AS count "
                            + "FROM StudyResult sr "
                            + "WHERE sr.batch = :batch "
                            + "GROUP BY sr.worker.workerType", Tuple.class)
                    .setParameter("batch", batch)
                    .getResultList();

            tuples.forEach(t -> counts.put(
                    WorkerType.fromWireValue((String) t.get("workerType")),
                    ((Number) t.get("count")).intValue()
            ));

            return counts;
        });
    }

    public List<Long> findIdsByStudyId(Long studyId) {
        return withReadOnlyTransaction((EntityManager em) ->
                em.createQuery("SELECT sr.id FROM StudyResult sr WHERE sr.study.id = :studyId", Long.class)
                        .setParameter("studyId", studyId)
                        .getResultList());
    }

    /**
     * Returns paginated StudyResults that belong to the given Study
     *
     * We can't use ScrollableResults for pagination since the MySQL Hibernate driver doesn't support it
     * (https://stackoverflow.com/a/2826512/1278769)
     */
    public List<StudyResult> findAllByStudy(Study study, int first, int max) {
        // Added 'LEFT JOIN FETCH' for performance (loads LAZY-linked Workers in StudyResults)
        return withReadOnlyTransaction((EntityManager em) -> em
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
        return withReadOnlyTransaction((EntityManager em) -> em
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
        return withReadOnlyTransaction((EntityManager em) -> em
                .createQuery("SELECT sr FROM StudyResult sr " +
                                "WHERE sr.batch = :batch " +
                                "AND sr.worker.workerType <> :workerType " +
                                "ORDER BY sr.id ASC",
                        StudyResult.class)
                .setFirstResult(first)
                .setMaxResults(max)
                .setParameter("batch", batch)
                .setParameter("workerType", workerTypeToBeExcluded)
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
        List<String> workerTypes = workerType == WorkerType.MT
                ? Arrays.asList(WorkerType.MT.value(), WorkerType.MT_SANDBOX.value())
                : Collections.singletonList(workerType.value());

        return withReadOnlyTransaction((EntityManager em) -> em
                .createQuery("SELECT sr FROM StudyResult sr " +
                                "WHERE sr.batch = :batch " +
                                "AND sr.worker.workerType IN (:workerTypes) " +
                                "ORDER BY sr.id ASC",
                        StudyResult.class)
                .setFirstResult(first)
                .setMaxResults(max)
                .setParameter("batch", batch)
                .setParameter("workerTypes", workerTypes)
                .getResultList());
    }

    /**
     * Returns paginated StudyResults that belong to the given Worker and User. It checks for each StudyResult if its
     * Study has the given User as a member.
     *
     * We can't use ScrollableResults for pagination since the MySQL Hibernate driver doesn't support it
     * (https://stackoverflow.com/a/2826512/1278769)
     */
    public List<StudyResult> findAllByWorker(Worker worker, User user, int first, int max) {
        return withReadOnlyTransaction((EntityManager em) -> em
                .createQuery("SELECT sr FROM StudyResult sr " +
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
        return withReadOnlyTransaction((EntityManager em) -> em
                .createQuery("SELECT sr FROM StudyResult sr " +
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
     * Find the StudyResultStatus with the most recent lastSeen
     */
    public List<StudyResultStatus> findLastSeen(int limit) {
        return withReadOnlyTransaction((EntityManager em) -> {
            String queryStr = "SELECT DISTINCT srs FROM StudyResultStatus srs "
                    + "LEFT JOIN FETCH srs.study s "
                    + "LEFT JOIN FETCH s.userList "
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
        return withReadOnlyTransaction((EntityManager em) -> em.createQuery("SELECT cr.studyResult.id FROM ComponentResult cr WHERE cr.id IN :crids", Long.class)
                .setParameter("crids", crids)
                .getResultList().stream().distinct().collect(Collectors.toList()));
    }

    public List<Long> findIdsFromListThatBelongToStudy(List<Long> srids, Long studyId) {
        if (srids.isEmpty()) return Collections.emptyList();
        return withReadOnlyTransaction((EntityManager em) -> em.createQuery("SELECT sr.id FROM StudyResult sr WHERE sr.id IN :srids AND sr.study.id = :studyId", Long.class)
                .setParameter("srids", srids)
                .setParameter("studyId", studyId)
                .getResultList().stream().distinct().collect(Collectors.toList()));
    }

    public List<Long> findIdsByBatchIds(List<Long> batchIds) {
        if (batchIds.isEmpty()) return Collections.emptyList();
        return withReadOnlyTransaction((EntityManager em) -> em.createQuery("SELECT sr.id FROM StudyResult sr WHERE sr.batch.id IN :batchIds", Long.class)
                .setParameter("batchIds", batchIds)
                .getResultList());
    }

    public List<Long> findIdsByGroupIds(List<Long> groupIds) {
        if (groupIds.isEmpty()) return Collections.emptyList();
        return withReadOnlyTransaction((EntityManager em) -> em.createQuery("SELECT sr.id FROM StudyResult sr WHERE sr.activeGroupResult.id IN :groupIds OR sr.historyGroupResult.id IN :groupIds", Long.class)
                .setParameter("groupIds", groupIds)
                .getResultList());
    }

    public Map<Long, Integer> countComponentResultsForStudyResultIds(List<Long> srids) {
        if (srids.isEmpty()) return Collections.emptyMap();
        return withReadOnlyTransaction((EntityManager em) -> {
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
        withTransaction(em -> {
            em.createQuery("UPDATE StudyResult sr SET sr.quotaReached = true WHERE sr.id = :id")
                    .setParameter("id", studyResultId)
                    .executeUpdate();
        });
    }

    /**
     * Checks if the worker finished this study already at least once. 'Finished' includes FINISHED, FAIL, and ABORTED
     * states.
     */
    public boolean hasStudyWithStudyRunDone(Worker worker, Study study) {
        return withReadOnlyTransaction((EntityManager em) -> {
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

    /**
     * Checks if the StudyResult with the given ID is done. 'Done' includes FINISHED, ABORTED, and FAIL states.
     */
    public boolean isStudyRunDone(String studyResultUuid) {
        return withReadOnlyTransaction((EntityManager em) -> {
            String queryStr = "SELECT COUNT(sr) FROM StudyResult sr "
                    + "WHERE sr.uuid = :studyResultUuid "
                    + "AND sr.studyState IN (:states)";
            List<StudyResult.StudyState> doneStates = Arrays.asList(
                    StudyResult.StudyState.FINISHED,
                    StudyResult.StudyState.ABORTED,
                    StudyResult.StudyState.FAIL);

            Number count = (Number) em.createQuery(queryStr)
                    .setParameter("studyResultUuid", studyResultUuid)
                    .setParameter("states", doneStates)
                    .getSingleResult();
            return count != null && count.intValue() > 0;
        });
    }

    /**
     * Checks if the StudyResult with the given UUID is currently running. A study is considered running if its state is
     * not FINISHED, ABORTED, or FAIL.
     */
    public boolean isStudyRunning(String uuid) {
        return withReadOnlyTransaction((EntityManager em) -> {
            String queryStr = "SELECT COUNT(sr) FROM StudyResult sr WHERE sr.uuid = :uuid "
                    + "AND sr.studyState NOT IN (:states)";
            List<StudyResult.StudyState> finishedStates = Arrays.asList(
                    StudyResult.StudyState.FINISHED,
                    StudyResult.StudyState.ABORTED,
                    StudyResult.StudyState.FAIL);

            Number count = (Number) em.createQuery(queryStr)
                    .setParameter("uuid", uuid)
                    .setParameter("states", finishedStates)
                    .getSingleResult();
            return count.intValue() > 0;
        });
    }

    public void updateLastSeenDateIfOlderThan(Long id, Duration duration) {
        withTransaction(em -> {
            Timestamp now = new Timestamp(System.currentTimeMillis());
            Timestamp threshold = new Timestamp(now.getTime() - duration.toMillis());
            em.createQuery("UPDATE StudyResult sr "
                            + "SET sr.lastSeenDate = :now "
                            + "WHERE sr.id = :id "
                            + "AND (sr.lastSeenDate IS NULL OR sr.lastSeenDate < :threshold)")
                    .setParameter("id", id)
                    .setParameter("now", now)
                    .setParameter("threshold", threshold)
                    .executeUpdate();
        });
    }

    /**
     * Checks if the number of OpenAI calls for the given StudyResult is lower than the threshold (callLimit). If yes,
     * it increments the counter and returns true. If the threshold is reached, it returns false.
     */
    public boolean checkAndIncrementOpenAiApiCount(String uuid, int callLimit) {
        return withTransaction(em -> {
            int updatedRows = em.createQuery(
                            "UPDATE StudyResult sr SET sr.openAiApiCount = sr.openAiApiCount + 1 " +
                                    "WHERE sr.uuid = :uuid AND sr.openAiApiCount < :callLimit")
                    .setParameter("uuid", uuid)
                    .setParameter("callLimit", callLimit)
                    .executeUpdate();
            return updatedRows > 0;
        });
    }

    public String findUrlQueryParametersByUuid(String uuid) {
        return withReadOnlyTransaction((EntityManager em) ->
                em.createQuery("SELECT sr.urlQueryParameters FROM StudyResult sr WHERE sr.uuid = :uuid", String.class)
                        .setParameter("uuid", uuid)
                        .getSingleResult());
    }

}
