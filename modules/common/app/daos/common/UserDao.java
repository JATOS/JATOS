package daos.common;

import models.common.User;
import play.db.jpa.JPAApi;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.TypedQuery;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DAO for User entity
 *
 * @author Kristian Lange
 */
@SuppressWarnings("deprecation")
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
     * Returns a mapping of usernames to a list of study IDs for which each user is a member.
     */
    public Map<String, List<Long>> findAllUsersAndTheirStudyIds() {
        List<Object[]> userStudyMappings = jpa.em().createNativeQuery(
                        "SELECT user_username, study_id FROM StudyUserMap")
                .getResultList();

        // Group study IDs by username
        Map<String, List<Long>> studyIdsByUsername = new HashMap<>();
        for (Object[] mapping : userStudyMappings) {
            String username = (String) mapping[0];
            Long studyId = ((Number) mapping[1]).longValue();
            studyIdsByUsername.computeIfAbsent(username, k -> new ArrayList<>()).add(studyId);
        }
        return studyIdsByUsername;
    }

        /**
         * Returns the number of User rows
         */
    public int count() {
        Number result = (Number) jpa.em().createQuery("SELECT COUNT(u) FROM User u").getSingleResult();
        return result != null ? result.intValue() : 0;
    }

    /**
     * Returns the users with the most recent lastSeen datetime field. Limit the number by 'limit'.
     */
    public List<User> findLastSeen(int limit) {
        return jpa.em().createQuery("SELECT u FROM User u ORDER BY lastSeen DESC", User.class)
                .setMaxResults(limit)
                .getResultList();
    }

}
