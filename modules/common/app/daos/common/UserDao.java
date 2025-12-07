package daos.common;

import models.common.User;
import play.db.jpa.JPAApi;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.TypedQuery;
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
    public void create(User user) {
        persist(user);
    }

    public void update(User user) {
        merge(user);
    }

    public void remove(User user) {
        super.remove(user);
    }

    public boolean authenticate(String normalizedUsername, String passwordHash) {
        return jpa.withTransaction(em -> {
            boolean doesNotExist = em.createQuery(
                            "SELECT u FROM User u WHERE u.username=:username and u.passwordHash=:passwordHash", User.class)
                    .setParameter("username", normalizedUsername)
                    .setParameter("passwordHash", passwordHash)
                    .setMaxResults(1).getResultList().isEmpty();
            return !doesNotExist;
        });
    }

    public User findByUsername(String normalizedUsername) {
        return jpa.withTransaction(em -> {
            return em.find(User.class, normalizedUsername);
        });
    }

    public List<User> findAll() {
        return jpa.withTransaction(em -> {
            TypedQuery<User> query = em.createQuery("SELECT u FROM User u", User.class);
            return query.getResultList();
        });
    }

    /**
     * Returns the number of User rows
     */
    public int count() {
        return jpa.withTransaction(em -> {
            Number result = (Number) em.createQuery("SELECT COUNT(u) FROM User u").getSingleResult();
            return result != null ? result.intValue() : 0;
        });
    }

    /**
     * Returns the users with the most recent lastSeen datetime field. Limit the number by 'limit'.
     */
    public List<User> findLastSeen(int limit) {
        return jpa.withTransaction(em -> {
            return em.createQuery("SELECT u FROM User u ORDER BY lastSeen DESC", User.class)
                    .setMaxResults(limit)
                    .getResultList();
        });
    }

}
