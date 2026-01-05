package daos.common;

import models.common.Study;
import models.common.User;
import play.db.jpa.JPAApi;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.util.*;
import java.util.stream.Collectors;

/**
 * DAO of Study entity
 *
 * @author Kristian Lange
 */
@Singleton
public class StudyDao extends AbstractDao {

    @Inject
    StudyDao(JPAApi jpa) {
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
        return jpa.withTransaction((EntityManager em) -> em.find(Study.class, id));
    }

    /**
     * Finds a study by its ID and eagerly fetches the batchList to avoid LazyInitializationException.
     */
    public Study findByIdWithBatches(Long id) {
        return jpa.withTransaction("default", true, (EntityManager em) -> {
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
        return jpa.withTransaction("default", true, (EntityManager em) -> {
            String queryStr = "SELECT s FROM Study s LEFT JOIN FETCH s.componentList WHERE s.id = :id";
            return em.createQuery(queryStr, Study.class)
                    .setParameter("id", id)
                    .getSingleResult();
        });
    }

    public Optional<Study> findByUuid(String uuid) {
        return jpa.withTransaction("default", true, (EntityManager em) -> {
            String queryStr = "SELECT s FROM Study s WHERE s.uuid=:uuid";
            List<Study> studyList = em.createQuery(queryStr, Study.class)
                    .setParameter("uuid", uuid)
                    .setMaxResults(1)
                    .getResultList();
            return !studyList.isEmpty() ? Optional.of(studyList.get(0)) : Optional.empty();
        });
    }

    /**
     * Finds all studies with the given title and returns them in a list. If
     * there is none it returns an empty list.
     */
    public List<Study> findByTitle(String title) {
        return jpa.withTransaction("default", true, (EntityManager em) -> {
            String queryStr = "SELECT s FROM Study s WHERE s.title=:title";
            TypedQuery<Study> query = em.createQuery(queryStr, Study.class);
            return query.setParameter("title", title).getResultList();
        });
    }

    public List<Study> findByStudyResultIds(Collection<Long> srids) {
        if (srids.isEmpty()) return Collections.emptyList();
        return jpa.withTransaction("default", true, (EntityManager em) ->
                em.createQuery("SELECT s FROM Study s WHERE s IN (SELECT sr.study FROM StudyResult sr WHERE sr.id IN :srids)", Study.class)
                .setParameter("srids", srids)
                .getResultList());
    }

    public List<Long> findIdsByStudyResultIds(Collection<Long> srids) {
        if (srids.isEmpty()) return Collections.emptyList();
        return jpa.withTransaction("default", true, (EntityManager em) ->
                em.createQuery("SELECT sr.study.id FROM StudyResult sr WHERE sr.id IN :srids", Long.class)
                .setParameter("srids", srids)
                .getResultList().stream().distinct().collect(Collectors.toList()));
    }

    public List<Study> findAll() {
        return jpa.withTransaction("default", true, (EntityManager em) -> {
            TypedQuery<Study> query = em.createQuery("SELECT s FROM Study s", Study.class);
            return query.getResultList();
        });
    }

    public List<Study> findAllByUser(User user) {
        return jpa.withTransaction("default", true, (EntityManager em) -> {
            TypedQuery<Study> query = em.createQuery(
                    "SELECT s FROM Study s INNER JOIN s.userList u WHERE u = :user", Study.class);
            query.setParameter("user", user);
            return query.getResultList();
        });
    }

    /**
     * Checks if the given study has the given user in its userList.
     */
    public boolean hasUser(Study study, User user) {
        return jpa.withTransaction("default", true, (EntityManager em) -> {
            String queryStr = "SELECT COUNT(s) FROM Study s INNER JOIN s.userList u WHERE s = :study AND u = :user";
            Number result = (Number) em.createQuery(queryStr)
                    .setParameter("study", study)
                    .setParameter("user", user)
                    .getSingleResult();
            return result != null && result.intValue() > 0;
        });
    }

    /**
     * Returns the number of Study rows
     */
    public int count() {
        return jpa.withTransaction("default", true, (EntityManager em) -> {
            Number result = (Number) em.createQuery("SELECT COUNT(s) FROM Study s").getSingleResult();
            return result != null ? result.intValue() : 0;
        });
    }

    /**
     * Returns the total number of Studys (including the deleted ones)
     */
    public int countTotal() {
        return jpa.withTransaction("default", true, (EntityManager em) -> {
            Number result = (Number) em.createQuery("SELECT max(id) FROM Study").getSingleResult();
            return result != null ? result.intValue() : 0;
        });
    }

}
