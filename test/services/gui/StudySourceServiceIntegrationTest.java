package services.gui;

import exceptions.common.BadRequestException;
import exceptions.common.FieldValidationException;
import exceptions.common.ForbiddenException;
import general.common.Common;
import general.common.StudyLogger;
import http.common.Http.Context;
import models.common.Study;
import models.common.StudySource;
import models.common.User;
import models.gui.StudySourceProperties;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import play.data.FormFactory;
import play.libs.Json;
import testutils.JatosTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import static auth.gui.AuthAction.SIGNEDIN_USER;
import static org.junit.Assert.*;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

public class StudySourceServiceIntegrationTest extends JatosTest {

    private StudySourceService service;
    private long id;

    @Before
    public void setupSource() {
        service = application.injector().instanceOf(StudySourceService.class);
        Context.current().args().put(SIGNEDIN_USER, admin);
        id = jpaApi.withTransaction(em -> {
            Study study = new Study();
            study.setTitle("Source settings");
            study.setOwner(em.find(User.class, admin.getUsername()));
            em.persist(study);
            em.flush();
            return study.getId();
        });
    }

    @Test
    public void sourceEndpointsAcceptStudyUuidsAndHandleMissingStudies() {
        String uuid = jpaApi.withTransaction(em -> { return em.find(Study.class, id).getUuid(); });
        String uuidUrl = "/jatos/api/v1/studies/" + uuid + "/source";
        String idUrl = "/jatos/api/v1/studies/" + id + "/source";
        assertEquals(200, api(uuidUrl).get().getStatus());
        var settings = Json.parse("{\"type\":\"GIT\",\"gitUrl\":\"https://example.org/study.git\"}");
        assertEquals(200, api(uuidUrl).put(settings).getStatus());
        assertEquals(api(idUrl).get().asJson().path("data"), api(uuidUrl).get().asJson().path("data"));
        String otherToken = createApiToken(createUser("source-outsider"));
        assertEquals(403, api(uuidUrl).token(otherToken).get().getStatus());
        assertEquals(403, api(uuidUrl).token(otherToken).put(settings).getStatus());
        for (String missing : List.of("9223372036854775807", java.util.UUID.randomUUID().toString())) {
            String url = "/jatos/api/v1/studies/" + missing + "/source";
            assertEquals(404, api(url).get().getStatus());
            assertEquals(404, api(url).put(settings).getStatus());
        }
    }

    @Test
    public void apiReturnsFieldErrorsForSourceAndCredentialValidation() {
        String url = "/jatos/api/v1/studies/" + id + "/source";
        var invalid = api(url).put(Json.parse("""
                {"type":"GIT","gitUrl":"ssh://example.org/study.git","gitRef":"invalid..ref"}
                """));
        assertEquals(400, invalid.getStatus());
        var fields = invalid.asJson().path("error").path("fields");
        assertTrue(fields.path("gitUrl").isArray());
        assertTrue(fields.path("gitRef").isArray());

        var saved = api(url).put(Json.parse("""
                {"type":"GIT","gitUrl":"https://example.org/study.git","username":"git-user","secret":"private-token"}
                """));
        assertEquals(200, saved.getStatus());
        var changedUsername = api(url).put(Json.parse("""
                {"type":"GIT","gitUrl":"https://example.org/study.git","username":"changed-user"}
                """));
        assertEquals(400, changedUsername.getStatus());
        assertTrue(changedUsername.asJson().path("error").path("fields").path("secret").isArray());
        assertFalse(changedUsername.getBody().contains("private-token"));

        var changedOrigin = api(url).put(Json.parse("""
                {"type":"GIT","gitUrl":"https://other.org/study.git","username":"git-user"}
                """));
        assertEquals(400, changedOrigin.getStatus());
        assertTrue(changedOrigin.asJson().path("error").path("fields").path("gitUrl").isArray());
        assertEquals("https://example.org/study.git", service.get(id).getGitUrl());
    }

    @Test
    public void apiReadsAndSavesJsonSourceConfiguration() {
        String url = "/jatos/api/v1/studies/" + id + "/source";
        var read = play.test.Helpers.route(application, new play.mvc.Http.RequestBuilder()
                .method("GET").uri(url).header("Authorization", "Bearer " + apiToken));
        assertEquals(200, read.status());
        assertEquals("NONE", Json.parse(play.test.Helpers.contentAsString(read)).path("data").path("type").asText());
        var write = play.test.Helpers.route(application, new play.mvc.Http.RequestBuilder()
                .method("PUT").uri(url).header("Authorization", "Bearer " + apiToken)
                .bodyJson(Json.parse("{\"type\":\"GIT\",\"gitUrl\":\"https://example.org/study.git\",\"anonymous\":true}")));
        assertEquals(play.test.Helpers.contentAsString(write), 200, write.status());
        assertTrue(Json.parse(play.test.Helpers.contentAsString(write)).path("data").path("anonymous").asBoolean());
        var invalid = assertThrows(FieldValidationException.class, () -> play.test.Helpers.route(application, new play.mvc.Http.RequestBuilder()
                .method("PUT").uri(url).header("Authorization", "Bearer " + apiToken)
                .bodyJson(Json.parse("{\"type\":\"GIT\",\"gitUrl\":\"ssh://example.org/study.git\"}"))));
        assertEquals(general.common.ApiEnvelope.ErrorCode.VALIDATION_ERROR, invalid.getErrorCode());
    }

    @Test
    public void apiRejectsUnknownFieldsAndInvalidJsonTypes() {
        for (String body : List.of(
                "{\"type\":\"NONE\",\"unexpected\":true}",
                "{\"type\":\"GIT\",\"gitUrl\":\"https://example.org/study.git\",\"anonymous\":\"true\"}",
                "{\"type\":\"INVALID\"}")) {
            var response = assertThrows(BadRequestException.class, () -> play.test.Helpers.route(application, new play.mvc.Http.RequestBuilder()
                    .method("PUT").uri("/jatos/api/v1/studies/" + id + "/source")
                    .header("Authorization", "Bearer " + apiToken).bodyJson(Json.parse(body))));
            assertEquals(400, response.getHttpStatus());
        }
    }

    @Test
    public void unifiedDtoKeepsInputAndOutputFieldsSeparate() {
        var props = application.injector().instanceOf(json.common.StrictJson.class)
                .updateFromJson(new StudySourceProperties(), Json.parse("""
                        {"type":"GIT","gitUrl":"https://example.org/study.git",
                         "username":"git-user","secret":"token",
                         "credentialsConfigured":true,"localFolderAllowed":true}
                        """));
        assertFalse(props.isCredentialsConfigured());
        assertFalse(props.isLocalFolderAllowed());
        assertEquals("token", props.getSecret());
        var json = Json.toJson(props);
        assertEquals("git-user", json.path("username").asText());
        assertFalse(json.has("secret"));
        assertFalse(json.has("removeCredentials"));
        assertTrue(json.has("credentialsConfigured"));
        assertTrue(json.has("localFolderAllowed"));
        var saved = service.save(id, props);
        assertEquals("git-user", saved.getUsername());
        assertNull(saved.getSecret());
        assertTrue(saved.isCredentialsConfigured());
    }

    @Test
    public void secretControlsReplacementAndRemovalMustBeExplicit() {
        service.save(id, withCredentials());
        String original = jpaApi.withTransaction(em -> {
            return em.find(StudySource.class, id).getGitCredentials().getEncryptedSecret();
        });
        var unchanged = git();
        unchanged.setSecret("");
        service.save(id, unchanged);
        assertEquals(original, jpaApi.withTransaction(em -> {
            return em.find(StudySource.class, id).getGitCredentials().getEncryptedSecret();
        }));
        var conflicting = withCredentials();
        conflicting.setRemoveCredentials(true);
        assertNotNull(conflicting.validate());
        assertThrows(FieldValidationException.class, () -> service.save(id, conflicting));
        assertEquals(original, jpaApi.withTransaction(em -> {
            return em.find(StudySource.class, id).getGitCredentials().getEncryptedSecret();
        }));
        var replacement = withCredentials();
        replacement.setSecret("new-token");
        service.save(id, replacement);
        assertNotEquals(original, jpaApi.withTransaction(em -> {
            return em.find(StudySource.class, id).getGitCredentials().getEncryptedSecret();
        }));
        var remove = git();
        remove.setRemoveCredentials(true);
        assertFalse(service.save(id, remove).isCredentialsConfigured());
    }

    @Test
    public void usernameChangesRequireNewSecret() {
        service.save(id, withCredentials());
        var edit = git();
        edit.setUsername("new-git-user");
        edit.setSecret("");
        assertThrows(FieldValidationException.class, () -> service.save(id, edit));
        assertEquals("git-user", service.get(id).getUsername());
        edit.setSecret("replacement-token");
        var saved = service.save(id, edit);
        assertEquals("new-git-user", saved.getUsername());
        assertNull(saved.getSecret());
        assertEquals("new-git-user", service.get(id).getUsername());
        jpaApi.withTransaction(em -> {
            var credentials = em.find(StudySource.class, id).getGitCredentials();
            var encryption = application.injector().instanceOf(utils.common.GitCredentialEncryption.class);
            assertEquals("replacement-token", encryption.decrypt(credentials.getEncryptedSecret(),
                    GitCredentialsService.encryptionContext(credentials)));
        });
        edit.setSecret("");
        edit.setGitUrl("https://another.example.org/study.git");
        assertThrows(FieldValidationException.class, () -> service.save(id, edit));
        var invalid = git();
        invalid.setUsername("invalid:username");
        assertThrows(FieldValidationException.class, () -> service.save(id, invalid));
    }

    private StudySourceProperties git() {
        StudySourceProperties input = new StudySourceProperties();
        input.setType(StudySource.Type.GIT);
        input.setGitUrl("https://example.org/study.git");
        input.setGitRef("release/v1");
        return input;
    }

    private StudySourceProperties withCredentials() {
        var input = git();
        input.setUsername("git-user");
        input.setSecret("private-access-token");
        return input;
    }

    @Test
    public void createsChangesClearsSourceAndPreservesHistoryWithoutLeakingCredentials() {
        assertEquals(StudySource.Type.NONE, service.get(id).getType());
        var response = service.save(id, withCredentials());
        assertTrue(response.isCredentialsConfigured());
        String json = Json.toJson(response).toString();
        assertTrue(json.contains("git-user"));
        assertFalse(json.contains("private-access-token"));
        jpaApi.withTransaction(em -> {
            StudySource source = em.find(StudySource.class, id);
            source.setLastAssetsSyncAt(Timestamp.valueOf("2026-10-27 12:00:00"));
            source.setLastAssetsSyncGitCommit("recorded-commit");
        });
        var anonymous = git();
        anonymous.setAnonymous(true);
        assertTrue(service.save(id, anonymous).isAnonymous());
        assertTrue(service.get(id).isCredentialsConfigured());
        var remove = git();
        remove.setRemoveCredentials(true);
        assertFalse(service.save(id, remove).isCredentialsConfigured());
        service.save(id, withCredentials());
        var none = new StudySourceProperties();
        none.setType(StudySource.Type.NONE);
        assertEquals(StudySource.Type.NONE, service.save(id, none).getType());
        jpaApi.withTransaction(em -> {
            Study study = em.find(Study.class, id);
            StudySource source = study.getStudySource();
            assertNotNull(source);
            assertNull(source.getGitUrl());
            assertNull(source.getGitRef());
            assertNull(source.getGitCredentials());
            assertEquals("recorded-commit", source.getLastAssetsSyncGitCommit());
            assertNotNull(source.getLastAssetsSyncAt());
            assertFalse(Json.toJson(study).has("studySource"));
            assertEquals(Long.valueOf(0), em.createQuery("select count(c) from GitCredentials c", Long.class).getSingleResult());
        });
    }

    @Test
    public void rejectsCrossOriginCredentialReuseAndRollsBackOuterTransaction() {
        service.save(id, withCredentials());
        var other = git();
        other.setGitUrl("https://other.example/study.git");
        assertThrows(FieldValidationException.class, () -> service.save(id, other));
        assertEquals("https://example.org/study.git", service.get(id).getGitUrl());
        assertThrows(IllegalStateException.class, () -> jpaApi.withTransaction((jakarta.persistence.EntityManager em) -> {
            other.setRemoveCredentials(true);
            service.save(id, other);
            em.flush();
            throw new IllegalStateException("rollback");
        }));
        assertTrue(service.get(id).isCredentialsConfigured());
        assertEquals("https://example.org/study.git", service.get(id).getGitUrl());
    }

    @Test
    public void membersCannotReadOrChangeSourceButEnabledSuperusersCan() {
        User member = new User("source-member", "Member", "member@example.org");
        member.setAuthMethod(User.AuthMethod.DB);
        jpaApi.withTransaction(em -> {
            em.persist(member);
            em.find(Study.class, id).addUser(member);
        });
        Context.current().args().put(SIGNEDIN_USER, member);
        assertThrows(ForbiddenException.class, () -> service.get(id));
        assertThrows(ForbiddenException.class, () -> service.save(id, git()));
        jpaApi.withTransaction(em -> {
            em.find(User.class, member.getUsername()).updateRoles(User.Role.SUPERUSER);
        });
        try (MockedStatic<Common> common = mockStatic(Common.class, CALLS_REAL_METHODS)) {
            common.when(Common::isUserRoleAllowSuperuser).thenReturn(true);
            assertEquals(StudySource.Type.GIT, service.save(id, git()).getType());
            common.when(Common::isUserRoleAllowSuperuser).thenReturn(false);
            assertThrows(ForbiddenException.class, () -> service.save(id, git()));
        }
    }

    @Test
    public void foldersFollowServerAndAllowedRootsPolicy() throws Exception {
        Path root = Files.createTempDirectory("source-allowed");
        Path outside = Files.createTempDirectory("source-outside");
        Path link = root.resolve("escape");
        try (MockedStatic<Common> common = mockStatic(Common.class, CALLS_REAL_METHODS)) {
            common.when(Common::isServer).thenReturn(false);
            common.when(Common::getStudySourcesAllowedRoots).thenReturn(List.of(root.toString()));
            service.save(id, withCredentials());
            var folder = new StudySourceProperties();
            folder.setType(StudySource.Type.LOCAL_FOLDER);
            folder.setLocalFolderPath(root.toString());
            var response = service.save(id, folder);
            assertEquals(root.toRealPath().toString(), response.getLocalFolderPath());
            assertFalse(response.isCredentialsConfigured());
            assertNull(response.getGitUrl());
            folder.setLocalFolderPath(outside.toString());
            assertThrows(FieldValidationException.class, () -> service.save(id, folder));
            Files.createSymbolicLink(link, outside);
            folder.setLocalFolderPath(link.toString());
            assertThrows(FieldValidationException.class, () -> service.save(id, folder));
            folder.setLocalFolderPath(root.resolve("missing").toString());
            assertThrows(FieldValidationException.class, () -> service.save(id, folder));
            common.when(Common::isServer).thenReturn(true);
            folder.setLocalFolderPath(root.toString());
            assertTrue(service.save(id, folder).isLocalFolderAllowed());
            folder.setLocalFolderPath(link.toString());
            assertThrows(FieldValidationException.class, () -> service.save(id, folder));
            folder.setLocalFolderPath(outside.toString());
            assertThrows(FieldValidationException.class, () -> service.save(id, folder));
            common.when(Common::getStudySourcesAllowedRoots).thenReturn(List.of());
            assertThrows(BadRequestException.class, () -> service.save(id, folder));
            assertFalse(service.get(id).isLocalFolderAllowed());
            assertEquals(StudySource.Type.GIT, service.save(id, git()).getType());
            common.when(Common::isServer).thenReturn(false);
            assertTrue(service.save(id, folder).isLocalFolderAllowed());
            assertEquals(outside.toRealPath().toString(), service.get(id).getLocalFolderPath());
            folder.setLocalFolderPath(outside.resolve("missing").toString());
            assertThrows(FieldValidationException.class, () -> service.save(id, folder));
            common.when(Common::isServer).thenReturn(true);
            // Existing folder sources remain removable while server mode is enabled.
            var none = new StudySourceProperties();
            none.setType(StudySource.Type.NONE);
            assertEquals(StudySource.Type.NONE, service.save(id, none).getType());
        } finally {
            Files.deleteIfExists(link);
            Files.deleteIfExists(root);
            Files.deleteIfExists(outside);
        }
    }

    @Test
    public void formValidatesFieldsAndNeverSerializesEnteredCredentials() {
        var forms = application.injector().instanceOf(FormFactory.class);
        assertTrue(forms.form(StudySourceProperties.class).bind(play.i18n.Lang.forCode("en"), play.libs.typedmap.TypedMap.empty(), Map.of("type", "GIT", "gitUrl", "http://example.org")).hasErrors());
        assertTrue(forms.form(StudySourceProperties.class).bind(play.i18n.Lang.forCode("en"), play.libs.typedmap.TypedMap.empty(), Map.of("type", "BAD")).hasErrors());
        assertTrue(forms.form(StudySourceProperties.class).bind(play.i18n.Lang.forCode("en"), play.libs.typedmap.TypedMap.empty(), Map.of("type", "GIT", "gitUrl", "https://example.org",
                "secret", "token")).hasErrors());
        for (String ref : List.of("../bad", "x.lock", "a b", "HEAD~1", "a".repeat(40))) {
            var input = git();
            input.setGitRef(ref);
            assertNotNull(input.validate());
        }
        assertNull(git().validate());
        assertFalse(Json.toJson(withCredentials()).has("secret"));
        assertEquals("git-user", Json.toJson(withCredentials()).path("username").asText());
    }

    @Test
    public void logsOnlyCommittedChangesWithoutSecrets() throws Exception {
        service.save(id, git());
        var logger = application.injector().instanceOf(StudyLogger.class);
        Path log = jpaApi.withTransaction(em -> {
            return Path.of(logger.getPath(em.find(Study.class, id)));
        });
        String before = Files.readString(log, java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(before.contains("Changed study source configuration to GIT"));
        service.save(id, git());
        assertEquals(before, Files.readString(log, java.nio.charset.StandardCharsets.ISO_8859_1));
        assertThrows(IllegalStateException.class, () -> jpaApi.withTransaction((jakarta.persistence.EntityManager em) -> {
            var none = new StudySourceProperties();
            none.setType(StudySource.Type.NONE);
            service.save(id, none);
            throw new IllegalStateException("rollback");
        }));
        assertEquals(before, Files.readString(log, java.nio.charset.StandardCharsets.ISO_8859_1));
        service.save(id, withCredentials());
        String after = Files.readString(log, java.nio.charset.StandardCharsets.ISO_8859_1);
        assertFalse(after.contains("private-access-token"));
        assertFalse(after.contains("git-user"));
    }
}
