package daos.common;

import models.common.Study;
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

        return withReadOnlyTransaction(em -> {
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
        return withReadOnlyTransaction((EntityManager em) -> em.find(User.class, normalizedUsername));
    }

    /**
     * Finds a user by username and eagerly fetches their studyList.
     */
    public User findByUsernameWithStudies(String normalizedUsername) {
        return withReadOnlyTransaction(em -> {
            List<User> result = em.createQuery(
                            "SELECT DISTINCT u FROM User u " +
                                    "LEFT JOIN FETCH u.studyList " +
                                    "WHERE u.username = :username",
                            User.class)
                    .setParameter("username", normalizedUsername)
                    .setMaxResults(1)
                    .getResultList();
            return result.isEmpty() ? null : result.get(0);
        });
    }

    public User findById(Long id) {
        return withReadOnlyTransaction((EntityManager em) -> {
            List<User> result = em.createQuery(
                            "SELECT u FROM User u WHERE u.id = :id", User.class)
                    .setParameter("id", id)
                    .setMaxResults(1)
                    .getResultList();
            return result.isEmpty() ? null : result.get(0);
        });
    }

    public List<User> findAll() {
        return withReadOnlyTransaction(em -> {
            return em.createQuery("SELECT u FROM User u", User.class).getResultList();
        });
    }

    /**
     * Returns a list of all users and eagerly fetches their studyList.
     */
    public List<User> findAllWithStudies() {
        return withReadOnlyTransaction(em -> {
            return em.createQuery("SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.studyList", User.class)
                    .getResultList();
        });
    }

    /**
     * Returns a mapping of usernames to a list of study IDs for which each user is a member.
     */
    public Map<String, List<Long>> findAllUsersAndTheirStudyIds() {
        return withReadOnlyTransaction((EntityManager em) -> {
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
     * Returns all users that are members of the given study.
     */
    public List<User> findAllByStudy(Study study) {
        return withReadOnlyTransaction(em -> {
            return em.createQuery("SELECT u FROM Study s JOIN s.userList u WHERE s = :study", User.class)
                    .setParameter("study", study)
                    .getResultList();
        });
    }

    /**
     * Returns the number of User rows
     */
    public int count() {
        return withReadOnlyTransaction(em -> {
            Number result = (Number) em.createQuery("SELECT COUNT(u) FROM User u").getSingleResult();
            return result != null ? result.intValue() : 0;
        });
    }

    /**
     * Returns the users with the most recent lastSeen datetime field. Limit the number by 'limit'.
     */
    public List<User> findLastSeen(int limit) {
        return withReadOnlyTransaction(em -> {
            return em.createQuery("SELECT u FROM User u ORDER BY lastSeen DESC", User.class)
                    .setMaxResults(limit)
                    .getResultList();
        });
    }

}
