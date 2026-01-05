package daos.common;

import models.common.LoginAttempt;
import play.db.jpa.JPAApi;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.EntityManager;
import java.util.Calendar;

/**
 * DAO for LoginAttempt entity
 *
 * @author Kristian Lange
 */
@Singleton
public class LoginAttemptDao extends AbstractDao {

    @Inject
    LoginAttemptDao(JPAApi jpa) {
        super(jpa);
    }

    public void persist(LoginAttempt loginAttempt) {
        super.persist(loginAttempt);
    }

    public LoginAttempt merge(LoginAttempt loginAttempt) {
        return super.merge(loginAttempt);
    }

    public void remove(LoginAttempt loginAttempt) {
        super.remove(loginAttempt);
    }

    public LoginAttempt find(Long id) {
        return jpa.withTransaction("default", true, (EntityManager em) ->
                em.find(LoginAttempt.class, id));
    }

    public void removeByUsername(String username) {
        jpa.withTransaction(em -> {
            em.createQuery("DELETE FROM LoginAttempt WHERE username = :username")
                    .setParameter("username", username)
                    .executeUpdate();
        });
    }

    /**
     * Removes all LoginAttempts that are older than 1 minute
     */
    public void removeOldAttempts() {
        jpa.withTransaction(em -> {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.MINUTE, -1);
            em.createQuery("DELETE FROM LoginAttempt WHERE date < :date")
                    .setParameter("date", cal.getTime())
                    .executeUpdate();
        });
    }

    /**
     * Returns the count of LoginAttempts that happened within the last minute for the given username and remoteAddress
     */
    public int countLoginAttemptsOfLastMin(String username, String remoteAddress) {
        return jpa.withTransaction("default", true, (EntityManager em) -> {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.MINUTE, -1);
            Number result = (Number) em.createQuery("SELECT COUNT(la) FROM LoginAttempt la " +
                            "WHERE username = :username " +
                            "AND remoteAddress = :remoteAddress " +
                            "AND date > :date")
                    .setParameter("username", username)
                    .setParameter("remoteAddress", remoteAddress)
                    .setParameter("date", cal.getTime())
                    .getSingleResult();
            return result != null ? result.intValue() : 0;
        });
    }

}
