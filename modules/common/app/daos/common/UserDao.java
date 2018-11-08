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

    public boolean authenticate(String email, String passwordHash) {
        String queryStr =
                "SELECT u FROM User u WHERE u.email=:email and u.passwordHash=:passwordHash";
        boolean doesNotExist = jpa.em().createQuery(queryStr, User.class)
                .setMaxResults(1).setParameter("email", email)
                .setParameter("passwordHash", passwordHash).getResultList()
                .isEmpty();
        return !doesNotExist;
    }

    public User findByEmail(String email) {
        return jpa.em().find(User.class, email);
    }

    public List<User> findAll() {
        TypedQuery<User> query = jpa.em().createQuery("SELECT u FROM User u", User.class);
        return query.getResultList();
    }

}
