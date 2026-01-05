package daos.common;

import models.common.User;
import play.db.jpa.JPAApi;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.EntityManager;
import java.util.List;

/**
 * DAO for User entity
 *
 * @author Kristian Lange
 */
@Singleton
public class UserDao extends AbstractDao {

    @Inject
    UserDao(JPAApi jpa) {
        super(jpa);
    }

    /**
     * Persist user (involves creating a JatosWorker)
     */
    public void persist(User user) {
        super.persist(user);
    }

    public User merge(User user) {
        return super.merge(user);
    }

    public void remove(User user) {
        super.remove(user);
    }

    public boolean authenticate(String normalizedUsername, String passwordHash) {
        if (normalizedUsername == null || passwordHash == null) return false;

        return jpa.withTransaction("default", true, (EntityManager em) -> {
            Long count = em.createQuery(
                            "SELECT COUNT(u) FROM User u " +
                                    "WHERE u.username = :username AND u.passwordHash = :passwordHash",
                            Long.class)
                    .setParameter("username", normalizedUsername)
                    .setParameter("passwordHash", passwordHash)
                    .getSingleResult();
            return count != null && count > 0;
        });
    }

    public User findByUsername(String normalizedUsername) {
        return jpa.withTransaction((EntityManager em) -> em.find(User.class, normalizedUsername));
    }

    public List<User> findAll() {
        return jpa.withTransaction("default", true, (EntityManager em) ->
                em.createQuery("SELECT u FROM User u", User.class).getResultList());
    }

    /**
     * Returns a list of all users and eagerly fetches their studyList.
     */
    public List<User> findAllWithStudies() {
        return jpa.withTransaction("default", true, (EntityManager em) ->
                em.createQuery("SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.studyList", User.class)
                        .getResultList());
    }

    /**
     * Returns the number of User rows
     */
    public int count() {
        return jpa.withTransaction("default", true, em -> {
            Number result = (Number) em.createQuery("SELECT COUNT(u) FROM User u").getSingleResult();
            return result != null ? result.intValue() : 0;
        });
    }

    /**
     * Returns the users with the most recent lastSeen datetime field. Limit the number by 'limit'.
     */
    public List<User> findLastSeen(int limit) {
        return jpa.withTransaction("default", true, em -> {
            return em.createQuery("SELECT u FROM User u ORDER BY lastSeen DESC", User.class)
                    .setMaxResults(limit)
                    .getResultList();
        });
    }

}
