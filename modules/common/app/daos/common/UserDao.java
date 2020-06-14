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
        boolean doesNotExist = jpa.em().createQuery(
                    "SELECT u FROM User u WHERE u.username=:username and u.passwordHash=:passwordHash", User.class)
                    .setParameter("username", normalizedUsername)
                    .setParameter("passwordHash", passwordHash)
                    .setMaxResults(1).getResultList().isEmpty();
        return !doesNotExist;
    }

    public User findByUsername(String normalizedUsername) {
        return jpa.em().find(User.class, normalizedUsername);
    }

    public List<User> findAll() {
        TypedQuery<User> query = jpa.em().createQuery("SELECT u FROM User u", User.class);
        return query.getResultList();
    }

    /**
     * Returns the number of User rows
     */
    public int count() {
        Number result = (Number) jpa.em().createQuery("SELECT COUNT(u) FROM User u").getSingleResult();
        return result.intValue();
    }

}
