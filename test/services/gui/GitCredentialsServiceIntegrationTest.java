package services.gui;

import exceptions.common.BadRequestException;
import exceptions.common.FieldValidationException;
import exceptions.common.ForbiddenException;
import general.common.Common;
import http.common.Http.Context;
import models.common.GitCredentials;
import models.common.Study;
import models.common.StudySource;
import models.common.User;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import play.libs.Json;
import services.gui.importexport.GitCredentialsService;
import testutils.JatosTest;
import utils.common.GitCredentialEncryption;

import static auth.gui.AuthAction.SIGNEDIN_USER;
import static org.junit.Assert.*;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

public class GitCredentialsServiceIntegrationTest extends JatosTest {

    private GitCredentialsService service;
    private GitCredentialEncryption crypto;

    @Before
    public void credentialsSetup() {
        service = application.injector().instanceOf(GitCredentialsService.class);
        crypto = application.injector().instanceOf(GitCredentialEncryption.class);
        Context.current().args().put(SIGNEDIN_USER, admin);
    }

    private long study() {
        return jpaApi.withTransaction(em -> {
            Study study = new Study();
            study.setTitle("Credentials");
            study.setOwner(em.find(User.class, admin.getUsername()));
            StudySource source = new StudySource(study);
            source.setType(StudySource.Type.GIT);
            source.setGitUrl("https://example.org/study.git");
            study.setStudySource(source);
            em.persist(study);
            em.flush();
            return study.getId();
        });
    }

    private String userSecret() {
        return jpaApi.withTransaction(em -> {
            GitCredentials c = em.find(User.class, admin.getUsername()).getGitCredentials();
            return crypto.decrypt(c.getEncryptedSecret(), GitCredentialsService.encryptionContext(c));
        });
    }

    @Test
    public void userCredentialsCreatePreserveReplaceRemoveAndDoNotLeak() {
        assertFalse(service.getUserGitCredentialsConfigStatus().configured());
        service.saveGitCredentialsForUser("git-user", "HTTPS://EXAMPLE.org:443/", "token-one");
        assertEquals("https://example.org", service.getUserGitCredentialsConfigStatus().origin());
        assertEquals("token-one", userSecret());
        assertThrows(FieldValidationException.class, () -> service.saveGitCredentialsForUser("renamed-user", "https://example.org", ""));
        assertEquals("token-one", userSecret());
        assertThrows(FieldValidationException.class, () -> service.saveGitCredentialsForUser("git-user", "https://other.org", ""));
        assertEquals("token-one", userSecret());
        service.saveGitCredentialsForUser("git-user", "https://other.org:8443", "token-two");
        assertEquals("token-two", userSecret());
        jpaApi.withTransaction(em -> {
            User user = em.find(User.class, admin.getUsername());
            GitCredentials c = user.getGitCredentials();
            assertTrue(c.getEncryptedSecret().startsWith("v1:"));
            assertFalse(c.getEncryptedSecret().contains("token-two"));
            assertFalse(Json.toJson(user).has("gitCredentials"));
            assertFalse(Json.toJson(c).has("encryptedSecret"));
            assertEquals("git-user", service.getUserGitCredentialsConfigStatus().username());
            assertFalse(Json.toJson(service.getUserGitCredentialsConfigStatus()).toString().contains("token-two"));
        });
        service.removeGitCredentialsFromUser();
        assertFalse(service.getUserGitCredentialsConfigStatus().configured());
        jpaApi.withTransaction(em -> {
            assertEquals(Long.valueOf(0), em.createQuery("select count(c) from GitCredentials c", Long.class).getSingleResult());
        });
    }

    @Test
    public void sourceCredentialsRequireMatchingOriginAndSurviveRollback() {
        long id = study();
        assertThrows(BadRequestException.class, () -> service.saveGitCredentialsForSource(id, "git-user", "https://other.org", "token"));
        assertFalse(service.getSourceGitCredentialsConfigStatus(id).configured());
        service.saveGitCredentialsForSource(id, "git-user", "https://example.org", "original");
        assertThrows(IllegalStateException.class, () -> jpaApi.withTransaction((jakarta.persistence.EntityManager em) -> {
            service.saveGitCredentialsForSource(id, "git-user", "https://example.org", "replacement");
            throw new IllegalStateException("rollback");
        }));
        jpaApi.withTransaction(em -> {
            GitCredentials c = em.find(StudySource.class, id).getGitCredentials();
            assertEquals("original", crypto.decrypt(c.getEncryptedSecret(), GitCredentialsService.encryptionContext(c)));
        });
        service.removeGitCredentialsFromSource(id);
        assertFalse(service.getSourceGitCredentialsConfigStatus(id).configured());
    }

    @Test
    public void permissionsUseCurrentMembershipAndRolesNotStaleActor() {
        long id = study();
        User member = new User("credential-member", "Member", "member@example.org");
        member.setAuthMethod(User.AuthMethod.DB);
        jpaApi.withTransaction(em -> {
            em.persist(member);
            em.find(Study.class, id).addUser(member);
        });
        Context.current().args().put(SIGNEDIN_USER, member);
        assertThrows(ForbiddenException.class, () -> service.saveGitCredentialsForSource(id, "user", "https://example.org", "token"));
        assertThrows(ForbiddenException.class, () -> service.removeGitCredentialsFromSource(id));
        assertThrows(ForbiddenException.class, () -> service.getSourceGitCredentialsConfigStatus(id));
        service.saveGitCredentialsForUser("own-git-user", "https://example.org", "own-token");
        assertTrue(service.getUserGitCredentialsConfigStatus().configured());
        jpaApi.withTransaction(em -> {em.find(User.class, member.getUsername()).updateRoles(User.Role.SUPERUSER);});
        try (MockedStatic<Common> common = mockStatic(Common.class, CALLS_REAL_METHODS)) {
            common.when(Common::isUserRoleAllowSuperuser).thenReturn(true);
            service.saveGitCredentialsForSource(id, "user", "https://example.org", "token");
            common.when(Common::isUserRoleAllowSuperuser).thenReturn(false);
            assertThrows(ForbiddenException.class, () -> service.removeGitCredentialsFromSource(id));
        }
    }

    @Test
    public void invalidInputUnreadableCredentialsAndRepeatedSaves() {
        assertThrows(FieldValidationException.class, () -> service.saveGitCredentialsForUser("user", "https://example.org", ""));
        assertThrows(FieldValidationException.class, () -> service.saveGitCredentialsForUser("invalid:user", "https://example.org", "token"));
        assertThrows(FieldValidationException.class, () -> service.saveGitCredentialsForUser("user", "http://example.org", "token"));
        jpaApi.withTransaction((jakarta.persistence.EntityManager em) -> {
            service.saveGitCredentialsForUser("user", "https://example.org", "first");
            service.saveGitCredentialsForUser("user", "https://example.org", "second");
            service.saveGitCredentialsForUser("user", "https://example.org", "");
        });
        assertEquals("second", userSecret());
        jpaApi.withTransaction(em -> {
            em.find(User.class, admin.getUsername()).getGitCredentials().setEncryptedSecret("unreadable");
        });
        assertThrows(FieldValidationException.class, () -> service.saveGitCredentialsForUser("changed", "https://example.org", ""));
        service.saveGitCredentialsForUser("changed", "https://example.org", "replacement");
        assertEquals("replacement", userSecret());
    }

    @Test
    public void requiresActiveSignedInUserAndGitSource() {
        Context.current().args().clear();
        assertThrows(ForbiddenException.class, () -> service.saveGitCredentialsForUser("user", "https://example.org", "token"));
        Context.current().args().put(SIGNEDIN_USER, admin);
        long id = study();
        jpaApi.withTransaction(em -> {
            em.find(StudySource.class, id).setType(StudySource.Type.NONE);
        });
        assertThrows(BadRequestException.class, () -> service.saveGitCredentialsForSource(id, "user", "https://example.org", "token"));
        jpaApi.withTransaction(em -> {
            em.find(User.class, admin.getUsername()).setActive(false);
        });
        assertThrows(ForbiddenException.class, () -> service.saveGitCredentialsForUser("user", "https://example.org", "token"));
    }

    @Test
    public void repeatedSourceUpdatesAndCredentialRemovalsJoinOuterTransaction() {
        long id = study();
        service.saveGitCredentialsForUser("user", "https://example.org", "user-token");
        jpaApi.withTransaction(em -> {
            service.saveGitCredentialsForSource(id, "user", "https://example.org", "first");
            service.saveGitCredentialsForSource(id, "user", "https://example.org", "second");
            service.saveGitCredentialsForSource(id, "renamed", "https://example.org", "second");
        });
        assertThrows(IllegalStateException.class, () -> jpaApi.withTransaction((jakarta.persistence.EntityManager em) -> {
            service.removeGitCredentialsFromUser();
            service.removeGitCredentialsFromSource(id);
            em.flush();
            throw new IllegalStateException("rollback removals");
        }));
        assertEquals("user-token", userSecret());
        jpaApi.withTransaction(em -> {
            GitCredentials credentials = em.find(StudySource.class, id).getGitCredentials();
            assertEquals("renamed", credentials.getUsername());
            assertEquals("second", crypto.decrypt(credentials.getEncryptedSecret(),
                    GitCredentialsService.encryptionContext(credentials)));
            assertEquals(Long.valueOf(2), em.createQuery(
                    "select count(c) from GitCredentials c", Long.class).getSingleResult());
        });
    }

}
