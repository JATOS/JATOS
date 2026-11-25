package daos.common;

import exceptions.common.NotFoundException;
import general.common.JpaTransactions;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import models.common.Study;
import models.common.User;
import models.common.User.AuthMethod;

import javax.inject.Inject;
import javax.inject.Singleton;
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
    UserDao(JpaTransactions jpa) {
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

    /**
     * Upgrades a verified hash without overwriting a concurrent reset or revoking sessions.
     */
    public boolean upgradePasswordHash(User user, String expectedHash, String upgradedHash) {
        return withTransaction(em -> {
            int updated = em.createQuery("""
                            UPDATE User SET passwordHash = :upgradedHash
                            WHERE username = :username AND passwordHash = :expectedHash
                            AND sessionVersion = :sessionVersion AND authMethod = :authMethod
                            """)
                    .setParameter("upgradedHash", upgradedHash)
                    .setParameter("username", user.getUsername())
                    .setParameter("expectedHash", expectedHash)
                    .setParameter("sessionVersion", user.getSessionVersion())
                    .setParameter("authMethod", AuthMethod.DB)
                    .executeUpdate();
            // Reload User after bulk update
            User managed = em.find(User.class, user.getUsername());
            if (managed != null) em.refresh(managed);
            return updated == 1;
        });
    }

    public User findByUsername(String normalizedUsername) {
        return forReadOnly((EntityManager em) -> em.find(User.class, normalizedUsername));
    }

    /**
     * User locks precede study locks. Adding members/creating studies and account deletion share this lock, preventing
     * an account from acquiring new memberships while it is being deleted.
     */
    public User findByUsernameAndLock(User user) {
        return withTransaction(em -> {
            User managed = em.find(User.class, user.getUsername());
            if (managed == null) throw new NotFoundException("User doesn't exist.");
            if (em.getLockMode(managed) != LockModeType.PESSIMISTIC_WRITE) {
                em.refresh(managed, LockModeType.PESSIMISTIC_WRITE);
            }
            return managed;
        });
    }

    /**
     * Loads and locks a user and refreshes credentials loaded before that lock. User locks must be acquired before the
     * study locks.
     */
    public User findByUsernameWithGitCredentialsAndLock(String username) {
        return withTransaction(em -> {
            User user = findByUsername(username);
            if (user == null) return null;
            boolean alreadyLocked = em.getLockMode(user) == LockModeType.PESSIMISTIC_WRITE;
            user = findByUsernameAndLock(user);
            // Refresh only on the first lock, preserving changes from earlier calls in this transaction.
            if (!alreadyLocked && user.getGitCredentials() != null) em.refresh(user.getGitCredentials());
            return user;
        });
    }

    /**
     * Finds a user by username and eagerly fetches their studyList.
     */
    public User findByUsernameWithStudies(String normalizedUsername) {
        return forReadOnly((EntityManager em) ->
                em.createQuery("""
                                SELECT DISTINCT u FROM User u
                                        LEFT JOIN FETCH u.studyList
                                        WHERE u.username = :username
                                """, User.class)
                        .setParameter("username", normalizedUsername)
                        .getResultStream()
                        .findFirst()
                        .orElse(null)
        );
    }

    public User findById(Long id) {
        return forReadOnly((EntityManager em) ->
                em.createQuery("SELECT u FROM User u WHERE u.id = :id", User.class)
                        .setParameter("id", id)
                        .setMaxResults(1)
                        .getResultStream()
                        .findFirst()
                        .orElse(null));
    }

    public List<User> findAll() {
        return forReadOnly(em -> {
            return em.createQuery("SELECT u FROM User u", User.class).getResultList();
        });
    }


    public List<User> findAllAndLock() {
        return withTransaction(em -> {
            return em.createQuery("SELECT u FROM User u ORDER BY u.username", User.class)
                    .setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
        });
    }

    /**
     * Returns a list of all users and eagerly fetches their studyList.
     */
    public List<User> findAllWithStudies() {
        return forReadOnly(em -> {
            return em.createQuery("SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.studyList", User.class)
                    .getResultList();
        });
    }

    /**
     * Returns a mapping of usernames to a list of study IDs for which each user is a member.
     */
    public Map<String, List<Long>> findAllUsersAndTheirStudyIds() {
        return forReadOnly((EntityManager em) -> {
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
        return forReadOnly(em -> {
            return em.createQuery("SELECT u FROM Study s JOIN s.userList u WHERE s = :study", User.class)
                    .setParameter("study", study)
                    .getResultList();
        });
    }

    /**
     * Returns the number of User rows
     */
    public int count() {
        return forReadOnly(em -> {
            Number result = (Number) em.createQuery("SELECT COUNT(u) FROM User u").getSingleResult();
            return result != null ? result.intValue() : 0;
        });
    }

    /**
     * Returns the users with the most recent lastSeen datetime field. Limit the number by 'limit'.
     */
    public List<User> findLastSeen(int limit) {
        return forReadOnly(em -> {
            return em.createQuery("SELECT u FROM User u ORDER BY lastSeen DESC", User.class)
                    .setMaxResults(limit)
                    .getResultList();
        });
    }

    /**
     * Merges user changes and revokes all their sessions in one transaction.
     */
    public void mergeAndRevokeSessions(User user) {
        withTransaction(em -> {
            em.merge(user);
            em.flush();
            incrementSessionVersion(em, user.getUsername());
        });
    }

    /**
     * Invalidates the user's existing sessions on every device.
     */
    public void revokeSessions(String username) {
        withTransaction(em -> {
            incrementSessionVersion(em, username);
        });
    }

    /**
     * Atomically increments the version and refreshes the managed user.
     */
    private void incrementSessionVersion(EntityManager em, String username) {
        em.createQuery("UPDATE User SET sessionVersion = sessionVersion + 1 WHERE username = :username")
                .setParameter("username", username)
                .executeUpdate();
        // Bulk updates bypass managed entities.
        User user = em.find(User.class, username);
        if (user != null) em.refresh(user);
    }


}
