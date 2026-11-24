package services.gui;

import daos.common.StudyDao;
import daos.common.UserDao;
import exceptions.common.ForbiddenException;
import http.common.Http.Context;
import jakarta.persistence.EntityManager;
import models.common.Study;
import models.common.User;
import org.junit.Test;
import play.test.Helpers;
import testutils.JatosTest;

import javax.inject.Inject;
import java.util.concurrent.*;
import java.util.function.Consumer;

import static auth.gui.AuthAction.SIGNEDIN_USER;
import static org.junit.Assert.*;

/**
 * Two real transactions with deliberately preloaded (stale) ownership and membership.
 */
public class StudyMembershipConcurrencyTest extends JatosTest {
    @Inject
    private StudyService studies;
    @Inject
    private UserService users;
    @Inject
    private StudyDao studyDao;
    @Inject
    private UserDao userDao;

    @Test
    public void transferAfterBulkRemovalCannotReAddTheRemovedMember() throws Exception {
        Study study = importAndGetExampleStudy();
        User member = createUser("candidate");
        asAdmin(em -> studies.changeUserMember(study, member, true));
        Throwable failure = race(study.getId(),
                em -> studies.removeAllUserMembers(studyDao.findById(study.getId())),
                stale -> studies.changeOwner(stale, userDao.findByUsername(member.getUsername())));
        assertTrue(String.valueOf(failure), failure instanceof ForbiddenException);
        assertMembers(study.getId(), "admin");
    }

    @Test
    public void formerOwnerCannotRemoveNewOwnerUsingStaleAuthorization() throws Exception {
        Study study = importAndGetExampleStudy();
        User member = createUser("candidate");
        asAdmin(em -> studies.changeUserMember(study, member, true));
        Throwable failure = race(study.getId(),
                em -> studies.changeOwner(studyDao.findById(study.getId()), userDao.findByUsername(member.getUsername())),
                stale -> studies.changeUserMember(stale, userDao.findByUsername(member.getUsername()), false));
        assertTrue(String.valueOf(failure), failure instanceof ForbiddenException);
        asAdmin(em -> {
            Study current = studyDao.findById(study.getId());
            assertEquals(member.getUsername(), current.getOwner().getUsername());
            assertTrue(current.getUserList().contains(current.getOwner()));
        });
    }

    @Test
    public void concurrentAddsPreserveBothMembers() throws Exception {
        Study study = importAndGetExampleStudy();
        User first = createUser("first-member");
        User second = createUser("second-member");
        assertNull(race(study.getId(),
                em -> studies.changeUserMember(studyDao.findById(study.getId()), userDao.findByUsername(first.getUsername()), true),
                stale -> studies.changeUserMember(stale, userDao.findByUsername(second.getUsername()), true)));
        assertMembers(study.getId(), "admin", first.getUsername(), second.getUsername());
    }

    @Test
    public void accountDeletionPreventsConcurrentMembershipInAnotherStudy() throws Exception {
        Study study = importAndGetExampleStudy();
        User member = createUser("departing");
        // It is intentionally not a member of this study when account deletion starts.
        Throwable failure = race(study.getId(), em -> users.removeUser(member.getId()),
                stale -> studies.changeUserMember(stale, member, true));
        assertNotNull("Adding a deleted user must fail", failure);
        assertNull(userDao.findByUsername(member.getUsername()));
        assertMembers(study.getId(), "admin");
    }

    private void assertMembers(Long studyId, String... expected) {
        asAdmin(em -> {
            Study study = studyDao.findById(studyId);
            assertEquals(new java.util.HashSet<>(java.util.List.of(expected)),
                    study.getUserList().stream().map(User::getUsername).collect(java.util.stream.Collectors.toSet()));
            assertTrue(study.getUserList().contains(study.getOwner()));
        });
    }

    private void asAdmin(Consumer<EntityManager> action) {
        Context.setCurrent(new Context(Helpers.fakeRequest().build()));
        try {
            jpaApi.withTransaction(em -> {
                Context.current().args().put(SIGNEDIN_USER, userDao.findByUsername("admin"));
                action.accept(em);
            });
        } finally {
            Context.clear();
        }
    }

    private Throwable race(Long id, Consumer<EntityManager> first, Consumer<Study> second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch loaded = new CountDownLatch(1);
        CountDownLatch changed = new CountDownLatch(1);
        CountDownLatch attempting = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            Future<Throwable> waiting = executor.submit(() -> {
                try {
                    asAdmin(em -> {
                        Study stale = studyDao.findById(id);
                        //noinspection ResultOfMethodCallIgnored
                        stale.getUserList().size();
                        stale.getOwner().getUsername();
                        loaded.countDown();
                        await(changed);
                        attempting.countDown();
                        second.accept(stale);
                    });
                    return null;
                } catch (Throwable failure) {
                    return failure;
                }
            });
            assertTrue(loaded.await(10, TimeUnit.SECONDS));
            Future<?> holding = executor.submit(() -> asAdmin(em -> {
                first.accept(em);
                em.flush();
                changed.countDown();
                await(release);
            }));
            assertTrue(attempting.await(10, TimeUnit.SECONDS));
            try {
                waiting.get(200, TimeUnit.MILLISECONDS);
                fail("The second mutation must wait for the first transaction's lock");
            } catch (TimeoutException expected) {
                // The first transaction still holds the lock.
            } finally {
                release.countDown();
            }
            holding.get(10, TimeUnit.SECONDS);
            return waiting.get(10, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            changed.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
            Context.setCurrent(new Context(Helpers.fakeRequest().build()));
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("Concurrent transaction timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }
}
