package daos.common;

import models.common.Study;
import models.common.User;
import play.db.jpa.JPAApi;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.TypedQuery;
import java.util.List;
import java.util.Optional;

/**
 * DAO of Study entity
 *
 * @author Kristian Lange
 */
@SuppressWarnings("deprecation")
@Singleton
public class StudyDao extends AbstractDao {

    @Inject
    StudyDao(JPAApi jpa) {
        super(jpa);
    }

    public void create(Study study) {
        persist(study);
    }

    public void remove(Study study) {
        super.remove(study);
    }

    public void update(Study study) {
        merge(study);
    }

    public Study findById(Long id) {
        return jpa.em().find(Study.class, id);
    }

    public Optional<Study> findByUuid(String uuid) {
        String queryStr = "SELECT s FROM Study s WHERE " + "s.uuid=:uuid";
        List<Study> studyList = jpa.em().createQuery(queryStr, Study.class)
                .setParameter("uuid", uuid)
                .setMaxResults(1)
                .getResultList();
        return !studyList.isEmpty() ? Optional.of(studyList.get(0)) : Optional.empty();
    }

    /**
     * Finds all studies with the given title and returns them in a list. If
     * there is none it returns an empty list.
     */
    public List<Study> findByTitle(String title) {
        String queryStr = "SELECT s FROM Study s WHERE s.title=:title";
        TypedQuery<Study> query = jpa.em().createQuery(queryStr, Study.class);
        return query.setParameter("title", title).getResultList();
    }

    public List<Study> findAll() {
        TypedQuery<Study> query = jpa.em().createQuery("SELECT s FROM Study s", Study.class);
        return query.getResultList();
    }

    public List<Study> findAllByUser(User user) {
        TypedQuery<Study> query = jpa.em().createQuery(
                "SELECT s FROM Study s INNER JOIN s.userList u LEFT JOIN FETCH s.componentList WHERE u = :user", Study.class);
        query.setParameter("user", user);
        return query.getResultList();
    }

    /**
     * Returns the number of Study rows
     */
    public int count() {
        Number result = (Number) jpa.em().createQuery("SELECT COUNT(s) FROM Study s").getSingleResult();
        return result != null ? result.intValue() : 0;
    }

    /**
     * Returns the total number of Studys (including the deleted ones)
     */
    public int countTotal() {
        Number result = (Number) jpa.em().createQuery("SELECT max(id) FROM Study").getSingleResult();
        return result != null ? result.intValue() : 0;
    }

}
