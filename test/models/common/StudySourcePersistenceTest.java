package models.common;

import general.common.JpaTransactions;
import jakarta.persistence.EntityManager;
import org.junit.Test;
import play.libs.Json;
import testutils.JatosTest;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class StudySourcePersistenceTest extends JatosTest {

    @Test public void callbacksFollowJoinedAndIndependentTransactionOutcomes() {
        var api = jpaApi;
        assertSame(application.injector().instanceOf(JpaTransactions.class),
                application.injector().instanceOf(JpaTransactions.class));
        List<String> events = new ArrayList<>();
        assertThrows(IllegalStateException.class, () -> api.withTransaction((EntityManager em) -> {
            api.withTransaction((EntityManager joined) -> {
                assertSame(em, joined);
                api.afterCompletion("outer", () -> events.add("outer commit"), () -> events.add("outer rollback"));
            });
            api.withNewTransaction((EntityManager inner) -> {
                assertNotSame(em, inner);
                api.afterCommit("inner", () -> events.add("inner commit"));
            });
            assertEquals(List.of("inner commit"), events);
            throw new IllegalStateException("rollback outer");
        }));
        assertEquals(List.of("inner commit", "outer rollback"), events);
    }

    @Test public void sourceAndCredentialsRoundTripClearAndCascade() {
        Long id = jpaApi.withTransaction(em -> {
            Study study = new Study();
            study.setTitle("Source test");
            study.setOwner(em.find(User.class, admin.getUsername()));
            StudySource source = new StudySource(study);
            study.setStudySource(source);
            source.setType(StudySource.Type.GIT);
            source.setGitUrl("https://example.org/repo.git");

            GitCredentials credentials = new GitCredentials(source);
            credentials.setUsername("git-user");
            credentials.setOrigin("https://example.org:8443");
            credentials.setEncryptedSecret("encrypted-test-payload");
            source.setGitCredentials(credentials);
            em.persist(study);
            em.flush();
            assertFalse(Json.toJson(study).has("studySource"));
            assertFalse(Json.toJson(credentials).has("encryptedSecret"));
            return study.getId();
        });
        jpaApi.withTransaction(em -> {
            StudySource source = em.find(Study.class, id).getStudySource();
            assertEquals("git-user", source.getGitCredentials().getUsername());
            assertEquals("https://example.org:8443", source.getGitCredentials().getOrigin());
            source.getStudy().setStudySource(null);
        });
        jpaApi.withTransaction(em -> {
            assertNull(em.find(StudySource.class, id));
            assertNull(em.find(Study.class, id).getStudySource());
            assertEquals(Long.valueOf(0), em.createQuery("select count(c) from GitCredentials c", Long.class).getSingleResult());
            em.remove(em.find(Study.class, id));
        });
        jpaApi.withTransaction(em -> { assertNull(em.find(StudySource.class, id)); });
    }

    @Test public void userCredentialsRoundTripAndRemove() {
        jpaApi.withTransaction(em -> {
            User user = em.find(User.class, admin.getUsername());
            GitCredentials credentials = new GitCredentials(user);
            credentials.setUsername("user");
            credentials.setOrigin("https://example.org:8443");
            credentials.setEncryptedSecret("encrypted-test-payload");
            user.setGitCredentials(credentials);
        });
        jpaApi.withTransaction(em -> {
            User user = em.find(User.class, admin.getUsername());
            assertNotNull(user.getGitCredentials());
            assertFalse(Json.toJson(user).has("gitCredentials"));
            user.setGitCredentials(null);
        });
        jpaApi.withTransaction(em -> {
            assertEquals(Long.valueOf(0), em.createQuery("select count(c) from GitCredentials c", Long.class).getSingleResult());
        });
    }
}
