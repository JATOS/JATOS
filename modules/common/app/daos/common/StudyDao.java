package daos.common;

import exceptions.common.NotFoundException;
import general.common.JpaTransactions;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import models.common.AdminStudyData;
import models.common.AdminStudyMemberData;
import models.common.Study;
import models.common.Study.GroupSessionWriteScope;
import models.common.User;
import org.hibernate.Hibernate;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * DAO of Study entities
 */
@Singleton
public class StudyDao extends AbstractDao {

    @Inject
    StudyDao(JpaTransactions jpa) {
        super(jpa);
    }

    public void persist(Study study) {
        super.persist(study);
    }

    public void remove(Study study) {
        super.remove(study);
    }

    public Study merge(Study study) {
        return super.merge(study);
    }

    public Study findById(Long id) {
        return withReadOnlyTransaction((EntityManager em) -> em.find(Study.class, id));
    }

    /**
     * Use before checking or changing membership; the caller must keep the transaction open.
     */
    public Study findAndLock(Study study) {
        return withTransaction(em -> {
            Study managed = em.find(Study.class, study.getId());
            if (managed == null) throw new NotFoundException("Study doesn't exist.");
            // find(..., lockMode) alone does not refresh an already managed entity/collection.
            // Do not refresh twice in one transaction: that would discard this transaction's changes.
            if (em.getLockMode(managed) != LockModeType.PESSIMISTIC_WRITE) {
                em.refresh(managed, LockModeType.PESSIMISTIC_WRITE);
                Hibernate.initialize(managed.getUserList());
            }
            return managed;
        });
    }

    /**
     * Loads and locks a study and refreshes the source and credentials loaded before that lock.
     */
    public Study findByIdWithSourceAndLock(long studyId) {
        return withTransaction(em -> {
            Study study = findById(studyId);
            if (study == null) return null;
            boolean alreadyLocked = em.getLockMode(study) == LockModeType.PESSIMISTIC_WRITE;
            study = findAndLock(study);
            if (!alreadyLocked && study.getStudySource() != null) {
                em.refresh(study.getStudySource());
                if (study.getStudySource().getGitCredentials() != null) {
                    em.refresh(study.getStudySource().getGitCredentials());
                }
            }
            return study;
        });
    }

    /**
     * Finds a study by its ID and eagerly fetches the batchList to avoid LazyInitializationException.
     */
    public Study findByIdWithBatches(Long id) {
        return withReadOnlyTransaction((EntityManager em) -> {
            String queryStr = "SELECT s FROM Study s LEFT JOIN FETCH s.batchList WHERE s.id = :id";
            return em.createQuery(queryStr, Study.class)
                    .setParameter("id", id)
                    .getSingleResult();
        });
    }

    /**
     * Finds a study by its ID and eagerly fetches the componentList to avoid LazyInitializationException.
     */
    public Study findByIdWithComponents(Long id) {
        return withReadOnlyTransaction((EntityManager em) -> {
            String queryStr = "SELECT s FROM Study s LEFT JOIN FETCH s.componentList WHERE s.id = :id";
            return em.createQuery(queryStr, Study.class)
                    .setParameter("id", id)
                    .getSingleResult();
        });
    }

    public Study findByIdWithComponentsAndBatches(Long id) {
        return withReadOnlyTransaction((EntityManager em) ->
                em.createQuery("""
                                        SELECT DISTINCT s FROM Study s
                                        LEFT JOIN FETCH s.componentList
                                        LEFT JOIN FETCH s.batchList
                                        WHERE s.id = :id""",
                                Study.class)
                        .setParameter("id", id)
                        .getSingleResult());
    }

    /**
     * Finds a study by its ID and eagerly fetches the userList to avoid LazyInitializationException.
     */
    public Study findByIdWithUsers(Long id) {
        return withReadOnlyTransaction((EntityManager em) -> {
            String queryStr = "SELECT s FROM Study s LEFT JOIN FETCH s.userList WHERE s.id = :id";
            return em.createQuery(queryStr, Study.class)
                    .setParameter("id", id)
                    .getSingleResult();
        });
    }

    /**
     * Finds a study by its ID and eagerly fetches the userList to avoid LazyInitializationException.
     */
    public Study findByIdWithUsersAndBatches(Long id) {
        return withReadOnlyTransaction((EntityManager em) -> {
            String queryStr = """
                    SELECT DISTINCT s FROM Study s
                    LEFT JOIN FETCH s.userList
                    LEFT JOIN FETCH s.batchList
                    WHERE s.id = :id""";
            return em.createQuery(queryStr, Study.class)
                    .setParameter("id", id)
                    .getSingleResult();
        });
    }

    /**
     * Finds a study by its ID and eagerly fetches the userList to avoid LazyInitializationException.
     */
    public Study findByIdWithUsersAndComponents(Long id) {
        return withReadOnlyTransaction((EntityManager em) -> {
            String queryStr = """
                    SELECT DISTINCT s FROM Study s
                    LEFT JOIN FETCH s.userList
                    LEFT JOIN FETCH s.componentList
                    WHERE s.id = :id""";
            return em.createQuery(queryStr, Study.class)
                    .setParameter("id", id)
                    .getSingleResult();
        });
    }

    public Optional<Study> findByUuid(String uuid) {
        return withReadOnlyTransaction((EntityManager em) -> {
            String queryStr = "SELECT s FROM Study s WHERE s.uuid=:uuid";
            return em.createQuery(queryStr, Study.class)
                    .setParameter("uuid", uuid)
                    .setMaxResults(1)
                    .getResultStream()
                    .findFirst();
        });
    }

    /**
     * Finds all studies with the given title and returns them in a list. If there is none it returns an empty list.
     */
    public List<Study> findByTitle(String title) {
        return withReadOnlyTransaction((EntityManager em) -> {
            String queryStr = "SELECT s FROM Study s WHERE s.title=:title";
            TypedQuery<Study> query = em.createQuery(queryStr, Study.class);
            return query.setParameter("title", title).getResultList();
        });
    }

    public List<Study> findByStudyResultIds(Collection<Long> srids) {
        if (srids.isEmpty()) return Collections.emptyList();
        return withReadOnlyTransaction((EntityManager em) ->
                em.createQuery("SELECT s FROM Study s WHERE s IN (SELECT sr.study FROM StudyResult sr WHERE sr.id IN :srids)", Study.class)
                        .setParameter("srids", srids)
                        .getResultList());
    }

    public List<Long> findIdsByStudyResultIds(Collection<Long> srids) {
        if (srids.isEmpty()) return Collections.emptyList();
        return withReadOnlyTransaction((EntityManager em) ->
                em.createQuery("SELECT sr.study.id FROM StudyResult sr WHERE sr.id IN :srids", Long.class)
                        .setParameter("srids", srids)
                        .getResultList().stream().distinct().collect(Collectors.toList()));
    }

    public List<Study> findAll() {
        return withReadOnlyTransaction((EntityManager em) -> {
            TypedQuery<Study> query = em.createQuery("SELECT s FROM Study s", Study.class);
            return query.getResultList();
        });
    }

    public List<Study> findAllByUser(User user) {
        return withReadOnlyTransaction((EntityManager em) ->
                em.createQuery("SELECT s FROM Study s INNER JOIN s.userList u WHERE u = :user", Study.class)
                        .setParameter("user", user)
                        .getResultList());
    }

    /**
     * Finds, locks, and refreshes the user's studies and initializes their members in ID order. Acquire the user lock
     * first and keep the transaction open throughout deletion.
     */
    public List<Study> findAllByUserWithLock(User user) {
        return withTransaction(em -> {
            List<Study> studies = em.createQuery("SELECT s FROM Study s JOIN s.userList u WHERE u.username = :username ORDER BY s.id", Study.class)
                    .setParameter("username", user.getUsername())
                    .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                    .getResultList();
            for (Study study : studies) {
                em.refresh(study, LockModeType.PESSIMISTIC_WRITE);
                Hibernate.initialize(study.getUserList());
            }
            return studies;
        });
    }

    /**
     * Checks if the given study has the given user in its userList.
     */
    public boolean hasUser(Study study, User user) {
        return withReadOnlyTransaction((EntityManager em) -> {
            String queryStr = "SELECT COUNT(s) FROM Study s INNER JOIN s.userList u WHERE s = :study AND u = :user";
            Number result = (Number) em.createQuery(queryStr)
                    .setParameter("study", study)
                    .setParameter("user", user)
                    .getSingleResult();
            return result != null && result.intValue() > 0;
        });
    }

    public boolean isLocked(Long studyId) {
        return withReadOnlyTransaction(em -> {
            return em.createQuery("SELECT s.locked FROM Study s WHERE s.id = :id", Boolean.class)
                    .setParameter("id", studyId)
                    .getSingleResult();
        });
    }

    public String findStudyDirNameByComponentId(Long componentId) {
        return withReadOnlyTransaction(em -> {
            return em.createQuery("SELECT s.dirName FROM Component c JOIN c.study s WHERE c.id = :componentId",
                            String.class)
                    .setParameter("componentId", componentId)
                    .getSingleResult();
        });
    }

    public String findStudyDirNameByStudyResultUuid(String studyResultUuid) {
        return withReadOnlyTransaction(em -> {
            return em.createQuery("SELECT s.dirName FROM StudyResult sr JOIN sr.study s WHERE sr.uuid = :studyResultUuid",
                            String.class)
                    .setParameter("studyResultUuid", studyResultUuid)
                    .getSingleResult();
        });
    }

    public String findStudyEndRedirectUrlByStudyResultUuid(String studyResultUuid) {
        return withReadOnlyTransaction(em -> {
            return em.createQuery("SELECT s.endRedirectUrl FROM StudyResult sr JOIN sr.study s WHERE sr.uuid = :studyResultUuid",
                            String.class)
                    .setParameter("studyResultUuid", studyResultUuid)
                    .getSingleResult();

        });
    }

    /**
     * Returns the number of users that are members of the given study.
     */
    public int countUsersByStudy(Study study) {
        return withReadOnlyTransaction((EntityManager em) -> {
            String queryStr = "SELECT COUNT(u) FROM Study s JOIN s.userList u WHERE s = :study";
            Number result = (Number) em.createQuery(queryStr)
                    .setParameter("study", study)
                    .getSingleResult();
            return result != null ? result.intValue() : 0;
        });
    }

    /**
     * Returns a list of user IDs of all users that are members of the study with the given ID.
     */
    public List<Long> findAllMembersByStudyId(Long studyId) {
        return withReadOnlyTransaction((EntityManager em) -> {
            String queryStr = "SELECT u.id FROM Study s JOIN s.userList u WHERE s.id = :studyId";
            return em.createQuery(queryStr, Long.class)
                    .setParameter("studyId", studyId)
                    .getResultList();
        });
    }

    /**
     * Returns the number of Study rows
     */
    public int count() {
        return withReadOnlyTransaction((EntityManager em) -> {
            Number result = (Number) em.createQuery("SELECT COUNT(s) FROM Study s").getSingleResult();
            return result != null ? result.intValue() : 0;
        });
    }

    /**
     * Returns the total number of Studys (including the deleted ones)
     */
    public int countTotal() {
        return withReadOnlyTransaction((EntityManager em) -> {
            Number result = (Number) em.createQuery("SELECT max(id) FROM Study").getSingleResult();
            return result != null ? result.intValue() : 0;
        });
    }

    /**
     * Returns all data needed by the admin study manager without loading full Study entities.
     */
    public List<AdminStudyData> findAllAdminStudyData(boolean includeResultDataSize) {
        return findAdminStudyData(null, includeResultDataSize);
    }

    /**
     * Returns all data needed by the admin study manager for studies that belong to the given user.
     */
    public List<AdminStudyData> findAdminStudyDataByUsername(String username, boolean includeResultDataSize) {
        return findAdminStudyData(username, includeResultDataSize);
    }

    private List<AdminStudyData> findAdminStudyData(String username, boolean includeResultDataSize) {
        return withReadOnlyTransaction(em -> {
            boolean filterByUsername = username != null && !username.trim().isEmpty();

            String studyQueryStr = """
                    SELECT s.id AS id,
                    s.uuid AS uuid,
                    s.owner.username AS ownerUsername,
                    s.title AS title,
                    s.active AS active,
                    s.dirName AS dirName,
                    (SELECT COUNT(sr.id) FROM StudyResult sr WHERE sr.study = s) AS studyResultCount,
                    (SELECT MAX(srs.startDate)
                    FROM StudyResultStatus srs
                    WHERE srs.study = s AND srs.startDate IS NOT NULL) AS lastStarted
                    FROM Study s\s""" +
                    (filterByUsername
                            ? """
                            WHERE EXISTS (
                            SELECT 1 FROM Study s2 JOIN s2.userList filterUser
                            WHERE s2 = s AND filterUser.username = :username
                            )\s"""
                            : "") +
                    "ORDER BY s.id";

            TypedQuery<Tuple> studyQuery = em.createQuery(studyQueryStr, Tuple.class);
            if (filterByUsername) {
                studyQuery.setParameter("username", username);
            }

            List<Tuple> studyTuples = studyQuery.getResultList();

            List<Long> studyIds = studyTuples.stream()
                    .map(t -> ((Number) t.get("id")).longValue())
                    .collect(Collectors.toList());

            Map<Long, Long> resultDataSizeByStudyId = includeResultDataSize && !studyIds.isEmpty()
                    ? em.createQuery("""
                                    SELECT cr.studyResult.study.id AS studyId, COALESCE(SUM(cr.dataSize), 0) AS size
                                    FROM ComponentResult cr
                                    WHERE cr.studyResult.study.id IN :studyIds
                                    GROUP BY cr.studyResult.study.id""",
                            Tuple.class)
                    .setParameter("studyIds", studyIds)
                    .getResultList()
                    .stream()
                    .collect(Collectors.toMap(
                            t -> ((Number) t.get("studyId")).longValue(),
                            t -> ((Number) t.get("size")).longValue()))
                    : Collections.emptyMap();

            Map<Long, List<AdminStudyMemberData>> membersByStudyId = studyIds.isEmpty()
                    ? Collections.emptyMap()
                    : em.createQuery("""
                                    SELECT s.id AS studyId,
                                    u.id AS userId,
                                    u.username AS username,
                                    u.name AS name,
                                    u.authMethod AS authMethod
                                    FROM Study s
                                    JOIN s.userList u
                                    WHERE s.id IN :studyIds
                                    ORDER BY s.id, u.username""",
                            Tuple.class)
                    .setParameter("studyIds", studyIds)
                    .getResultList()
                    .stream()
                    .map(t -> new AdminStudyMemberData(
                            ((Number) t.get("studyId")).longValue(),
                            ((Number) t.get("userId")).longValue(),
                            (String) t.get("username"),
                            (String) t.get("name"),
                            String.valueOf(t.get("authMethod"))))
                    .collect(Collectors.groupingBy(AdminStudyMemberData::studyId));

            Function<Tuple, AdminStudyData> toAdminStudyData = t -> {
                Long studyId = ((Number) t.get("id")).longValue();
                return new AdminStudyData(
                        studyId,
                        (String) t.get("uuid"),
                        (String) t.get("title"),
                        (Boolean) t.get("active"),
                        (String) t.get("dirName"),
                        ((Number) t.get("studyResultCount")).longValue(),
                        resultDataSizeByStudyId.getOrDefault(studyId, 0L),
                        (Date) t.get("lastStarted"),
                        membersByStudyId.getOrDefault(studyId, Collections.emptyList()),
                        (String) t.get("ownerUsername"));
            };

            return studyTuples.stream()
                    .map(toAdminStudyData)
                    .collect(Collectors.toList());
        });
    }

    public GroupSessionWriteScope findGroupSessionWriteScope(Long groupResultId) {
        return withReadOnlyTransaction(em -> {
            String queryStr = "SELECT s.groupSessionWriteScope " +
                    "FROM GroupResult gr " +
                    "JOIN gr.batch b " +
                    "JOIN b.study s " +
                    "WHERE gr.id = :groupResultId";

            List<GroupSessionWriteScope> result = em.createQuery(queryStr, GroupSessionWriteScope.class)
                    .setParameter("groupResultId", groupResultId)
                    .setMaxResults(1)
                    .getResultList();

            return result.isEmpty() ? null : result.getFirst();
        });
    }

    public boolean isOwner(Study study, User user) {
        if (study == null || user == null) return false;
        return withReadOnlyTransaction(em -> em.createQuery("select count(s) from Study s where s.id = :id and s.owner.username = :username", Long.class)
                .setParameter("id", study.getId()).setParameter("username", user.getUsername())
                .getSingleResult() == 1);
    }

}
