package daos.common;

import models.common.User;
import play.db.jpa.JPAApi;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.EntityManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DAO for User entity
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

    public void refresh(User user) {
        super.refresh(user);
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

    public User findById(Long id) {
        return jpa.withTransaction((EntityManager em) -> {
            List<User> result = em.createQuery(
                            "SELECT u FROM User u WHERE u.id = :id", User.class)
                    .setParameter("id", id)
                    .setMaxResults(1)
                    .getResultList();
            return result.isEmpty() ? null : result.get(0);
        });
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
     * Returns a mapping of usernames to a list of study IDs for which each user is a member.
     */
    public Map<String, List<Long>> findAllUsersAndTheirStudyIds() {
        return jpa.withTransaction((EntityManager em) -> {
            List<Object[]> userStudyMappings = em.createQuery(
                            "SELECT u.username, s.id FROM User u JOIN u.studyList s", Object[].class)
                    .getResultList();

            // Group study IDs by username
            Map<String, List<Long>> studyIdsByUsername = new HashMap<>();
            for (Object[] mapping : userStudyMappings) {
                String username = (String) mapping[0];
                Long studyId = ((Number) mapping[1]).longValue();
                studyIdsByUsername.computeIfAbsent(username, k -> new ArrayList<>()).add(studyId);
            }
            return studyIdsByUsername;
        });
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
