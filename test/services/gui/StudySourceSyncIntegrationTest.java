package services.gui;

import exceptions.common.BadRequestException;
import exceptions.common.ForbiddenException;
import exceptions.common.ConflictException;
import general.common.Common;
import general.common.StudyLogger;
import http.common.Http.Context;
import models.common.Study;
import models.gui.StudySourceSyncProperties;
import models.common.User;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import play.libs.Json;
import services.gui.importexport.*;
import services.gui.importexport.StudySourceSyncIO;
import testutils.JatosTest;
import utils.common.IOUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static auth.gui.AuthAction.SIGNEDIN_USER;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class StudySourceSyncIntegrationTest extends JatosTest {

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();
    private StudySourceSync sync;
    private Path source;
    private Path assets;
    private long studyId;
    private String uuid;

    @Before
    public void createSourceStudy() throws Exception {
        sync = application.injector().instanceOf(StudySourceSync.class);
        Context.current().args().put(SIGNEDIN_USER, admin);
        source = temporary.newFolder("source").toPath();
        uuid = UUID.randomUUID().toString();
        properties(uuid, "Original");
        Files.createDirectory(source.resolve("assets"));
        Files.writeString(source.resolve("assets/old.txt"), "old");
        var created = application.injector().instanceOf(LocalFolderStudyImportExport.class).createStudyFromLocalFolder(source.toString());
        studyId = created.study().path("id").asLong();
        assets = IOUtils.generateStudyAssetsPath(created.study().path("dirName").asText());
    }

    private void properties(String studyUuid, String title) throws Exception {
        var data = Json.newObject().put("uuid", studyUuid).put("title", title).put("dirName", "sync-test");
        var envelope = Json.newObject().put("version", "3");
        envelope.set("data", data);
        Files.writeString(source.resolve("study.jas"), envelope.toString());
    }

    private StudySourceSync.LocalFolderPreview preview() {
        return jpaApi.withTransaction(em -> {return sync.previewImport(studyId);});
    }

    private play.libs.ws.WSResponse previewAs(User user) {
        return api("/jatos/api/v1/studies/" + studyId + "/source/folder/import")
                .token(createApiToken(user)).get();
    }

    private StudySourceSyncProperties selection(boolean properties, boolean assets) {
        var selection = new StudySourceSyncProperties();
        selection.setProperties(properties);
        selection.setAssets(assets);
        return selection;
    }

    private void assertNoStaging() throws Exception {
        for (Path root : List.of(IOUtils.tmpDir(), Path.of(Common.getStudyAssetsRootPath()))) {
            try (var paths = Files.list(root)) {
                assertFalse(paths.anyMatch(p -> p.getFileName().toString().startsWith("study-source-")
                        || p.getFileName().toString().startsWith(".study-sync-")));
            }
        }
    }

    @Test
    public void memberCanSyncBothWithoutChangingOwnershipOrSource() throws Exception {
        User member = createUser("sync-member");
        jpaApi.withTransaction(em -> {
            em.find(Study.class, studyId).addUser(em.find(User.class, member.getUsername()));
        });
        properties(uuid, "Updated");
        Files.delete(source.resolve("assets/old.txt"));
        Files.writeString(source.resolve("assets/new.txt"), "new");
        Context.current().args().put(SIGNEDIN_USER, member);
        var result = sync.importFromFolder(studyId, selection(true, true));
        assertTrue(result.propertiesUpdated());
        assertTrue(result.assetsUpdated());
        String log = Files.readString(Path.of(Common.getStudyLogsPath(), uuid + ".log"), java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(log.contains("Imported properties and assets from local folder: " + source));
        assertFalse(Files.exists(assets.resolve("old.txt")));
        assertEquals("new", Files.readString(assets.resolve("new.txt")));
        jpaApi.withTransaction(em -> {
            Study study = em.find(Study.class, studyId);
            assertEquals("Updated", study.getTitle());
            assertEquals(admin.getUsername(), study.getOwner().getUsername());
            assertTrue(study.getUserList().stream().anyMatch(u -> u.getUsername().equals(member.getUsername())));
            assertEquals(source.toString(), study.getStudySource().getLocalFolderPath());
        });
        assertEquals(403, api("/jatos/api/v1/studies/" + studyId + "/source")
                .token(createApiToken(member)).get().getStatus());
        assertNoStaging();
    }

    @Test
    public void missingAssetsPreservesAssetsAndOnlyUpdatesProperties() throws Exception {
        IOUtils.deleteRecursively(source.resolve("assets"));
        properties(uuid, "Properties only");
        var preview = preview();
        assertFalse(preview.assetsAvailable());
        assertThrows(BadRequestException.class, () -> sync.importFromFolder(studyId, selection(true, true)));
        sync.importFromFolder(studyId, selection(true, false));
        assertEquals("old", Files.readString(assets.resolve("old.txt")));
        jpaApi.withTransaction(em -> {
            var state = em.find(Study.class, studyId).getStudySource();
        });
        assertNoStaging();
    }

    @Test
    public void missingPropertiesAndEmptyAssetsAllowsExplicitAssetDeletion() throws Exception {
        Files.delete(source.resolve("study.jas"));
        Files.delete(source.resolve("assets/old.txt"));
        assertFalse(preview().propertiesAvailable());
        sync.importFromFolder(studyId, selection(false, true));
        try (var paths = Files.list(assets)) {assertEquals(0, paths.count());}
        jpaApi.withTransaction(em -> {
            Study study = em.find(Study.class, studyId);
            assertEquals("Original", study.getTitle());
        });
        assertNoStaging();
    }

    @Test
    public void rejectsUuidMismatchButAllowsFolderChangesAfterPreview() throws Exception {
        preview();
        properties(UUID.randomUUID().toString(), "Wrong study");
        assertThrows(BadRequestException.class, this::preview);
        assertThrows(BadRequestException.class, () -> sync.importFromFolder(studyId, selection(false, true)));
        properties(uuid, "Changed since preview");
        Files.writeString(source.resolve("assets/old.txt"), "changed after preview");
        sync.importFromFolder(studyId, selection(false, true));
        assertEquals("changed after preview", Files.readString(assets.resolve("old.txt")));
        assertNoStaging();
    }

    @Test
    public void rejectsSelectedContentThatDisappearsAfterPreview() throws Exception {
        preview();
        Files.delete(source.resolve("study.jas"));
        assertThrows(BadRequestException.class, () -> sync.importFromFolder(studyId, selection(true, false)));
        properties(uuid, "Properties restored");
        preview();
        IOUtils.deleteRecursively(source.resolve("assets"));
        assertThrows(BadRequestException.class, () -> sync.importFromFolder(studyId, selection(false, true)));
        assertEquals("old", Files.readString(assets.resolve("old.txt")));
        assertNoStaging();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Test
    public void refusesNonmembersLockedStudiesAndDisabledFolderSources() throws Exception {
        User outsider = createUser("sync-outsider");
        Context.current().args().put(SIGNEDIN_USER, outsider);
        assertEquals(403, previewAs(outsider).getStatus());
        assertThrows(ForbiddenException.class, () -> sync.importFromFolder(studyId, selection(true, true)));
        Context.current().args().put(SIGNEDIN_USER, admin);
        jpaApi.withTransaction(em -> {em.find(Study.class, studyId).setLocked(true);});
        assertEquals(403, previewAs(admin).getStatus());
        assertThrows(ForbiddenException.class, () -> sync.importFromFolder(studyId, selection(true, true)));
        jpaApi.withTransaction(em -> {em.find(Study.class, studyId).setLocked(false);});
        try (var common = mockStatic(Common.class, CALLS_REAL_METHODS)) {
            common.when(Common::isServer).thenReturn(true);
            common.when(Common::getStudySourcesLocalFolderAllowedRoots).thenReturn(List.of());
            assertThrows(BadRequestException.class, () -> sync.importFromFolder(studyId, selection(true, true)));
        }
        assertNoStaging();
    }

    @Test
    public void rollbackRestoresOldAssetsAndPropertiesAfterReplacementFailure() throws Exception {
        properties(uuid, "Must roll back");
        Files.writeString(source.resolve("assets/new.txt"), "new");
        StudySourceSyncIO failingIO = new StudySourceSyncIO(jpaApi, application.injector().instanceOf(IOUtils.class)) {
            @Override
            public void replaceAssets(Path path, String name) throws java.io.IOException {
                super.replaceAssets(path, name);
                throw new java.io.IOException("Failure after publication");
            }
        };
        var injector = application.injector();
        var failing = new StudySourceSync(jpaApi, injector.instanceOf(daos.common.StudyDao.class),
                injector.instanceOf(daos.common.UserDao.class), injector.instanceOf(AuthorizationService.class),
                failingIO, injector.instanceOf(StudyLogger.class),
                new LocalFolderStudyImportExport(injector.instanceOf(LocalFolderStudySourceIO.class),
                        injector.instanceOf(StudyImportService.class),
                        injector.instanceOf(services.gui.importexport.StudySourceService.class), jpaApi,
                        injector.instanceOf(json.common.DomainJsonMapper.class), injector.instanceOf(IOUtils.class), failingIO),
                injector.instanceOf(services.gui.importexport.GitStudyImportExport.class));
        assertThrows(exceptions.common.JatosException.class, () -> failing.importFromFolder(studyId, selection(true, true)));
        assertEquals("old", Files.readString(assets.resolve("old.txt")));
        assertFalse(Files.exists(assets.resolve("new.txt")));
        jpaApi.withTransaction(em -> {
            Study study = em.find(Study.class, studyId);
            assertEquals("Original", study.getTitle());
        });
        // Failure releases both the local claim and native lock.
        sync.importFromFolder(studyId, selection(true, true));
        assertNoStaging();
    }

    @Test
    public void commitFailureRestoresAssetsAndMetadata() throws Exception {
        properties(uuid, "Must not commit");
        Files.writeString(source.resolve("assets/new.txt"), "new");
        StudySourceSyncIO failingIO = new StudySourceSyncIO(jpaApi, application.injector().instanceOf(IOUtils.class)) {
            @Override
            public void replaceAssets(Path path, String name) throws java.io.IOException {
                super.replaceAssets(path, name);
                var transaction = jpaApi.em("default").unwrap(org.hibernate.Session.class).getTransaction();
                transaction.registerSynchronization(new jakarta.transaction.Synchronization() {
                    @Override
                    public void beforeCompletion() {
                        throw new IllegalStateException("Commit failure");
                    }

                    @Override
                    public void afterCompletion(int status) {
                    }
                });
            }
        };
        var injector = application.injector();
        var failing = new StudySourceSync(jpaApi, injector.instanceOf(daos.common.StudyDao.class),
                injector.instanceOf(daos.common.UserDao.class), injector.instanceOf(AuthorizationService.class),
                failingIO, injector.instanceOf(StudyLogger.class),
                new LocalFolderStudyImportExport(injector.instanceOf(LocalFolderStudySourceIO.class),
                        injector.instanceOf(StudyImportService.class),
                        injector.instanceOf(services.gui.importexport.StudySourceService.class), jpaApi,
                        injector.instanceOf(json.common.DomainJsonMapper.class), injector.instanceOf(IOUtils.class), failingIO),
                injector.instanceOf(services.gui.importexport.GitStudyImportExport.class));
        assertThrows(RuntimeException.class, () -> failing.importFromFolder(studyId, selection(true, true)));
        assertEquals("old", Files.readString(assets.resolve("old.txt")));
        assertFalse(Files.exists(assets.resolve("new.txt")));
        jpaApi.withTransaction(em -> {
            Study study = em.find(Study.class, studyId);
            assertEquals("Original", study.getTitle());
        });
        assertNoStaging();
    }

    @Test
    public void permissionsAndSourceConfigurationAreCheckedAgainOnApply() throws Exception {
        User member = createUser("removed-member");
        jpaApi.withTransaction(em -> {
            em.find(Study.class, studyId).addUser(em.find(User.class, member.getUsername()));
        });
        Context.current().args().put(SIGNEDIN_USER, member);
        jpaApi.withTransaction(em -> {
            em.find(Study.class, studyId).getUserList().removeIf(u -> u.getUsername().equals(member.getUsername()));
        });
        assertThrows(ForbiddenException.class, () -> sync.importFromFolder(studyId, selection(true, true)));
        Context.current().args().put(SIGNEDIN_USER, admin);
        jpaApi.withTransaction(em -> {
            em.find(Study.class, studyId).getStudySource().setType(models.common.StudySource.Type.NONE);
        });
        assertThrows(BadRequestException.class, () -> sync.importFromFolder(studyId, selection(true, true)));
        assertEquals("old", Files.readString(assets.resolve("old.txt")));
        assertNoStaging();
    }

    @Test
    public void sessionSyncRequiresCsrfAndReturnsSuccessFlash() throws Exception {
        var body = Json.newObject().put("assets", true);
        String csrf = application.injector().instanceOf(play.filters.csrf.CSRF.TokenProvider.class).generateToken();
        var request = play.test.Helpers.fakeRequest("POST", "/jatos/api/v1/studies/" + studyId + "/source/folder/import")
                .header("Cookie", "PLAY_SESSION=test-session")
                .cookie(play.mvc.Http.Cookie.builder("PLAY_SESSION", "test-session").build())
                .session("username", admin.getUsername())
                .session("signinTime", String.valueOf(System.currentTimeMillis()))
                .session("lastActivityTime", String.valueOf(System.currentTimeMillis()))
                .session("csrfToken", csrf).bodyJson(body);
        assertEquals(403, play.test.Helpers.route(application, request).status());
        var response = play.test.Helpers.route(application, request.header("Csrf-Token", csrf));
        assertEquals(play.test.Helpers.contentAsString(response), 200, response.status());
        assertTrue(response.flash().get("success").orElseThrow().contains("Imported assets"));
        var invalid = api("/jatos/api/v1/studies/" + studyId + "/source/folder/import")
                .post(Json.newObject());
        assertEquals(400, invalid.getStatus());
        assertNoStaging();
    }

    @Test
    public void concurrentSyncIsRejectedWithoutTouchingAssets() throws Exception {
        try (var ignored = new StudySourceSyncIO(jpaApi, application.injector().instanceOf(IOUtils.class)).lock(studyId)) {
            assertEquals(409, assertThrows(ConflictException.class, () -> sync.importFromFolder(studyId, selection(false, true))).getHttpStatus());
        }
        sync.importFromFolder(studyId, selection(false, true));
        assertNoStaging();
    }

    @Test
    public void apiRejectsInvalidSyncFlags() throws Exception {
        for (String body : List.of(
                "{\"properties\":\"true\",\"assets\":true}",
                "{\"assets\":1}",
                "{\"assets\":null}",
                "{\"assets\":true,\"unknown\":true}",
                "{\"properties\":false,\"assets\":false}")) {
            var response = api("/jatos/api/v1/studies/" + studyId + "/source/folder/import").post(Json.parse(body));
            assertEquals(response.getBody(), 400, response.getStatus());
        }
        assertEquals("old", Files.readString(assets.resolve("old.txt")));
        assertNoStaging();
    }

    @Test
    public void apiSupportsUuidPreviewAndSelectedScope() throws Exception {
        var response = api("/jatos/api/v1/studies/" + uuid + "/source/folder/import").get();
        assertEquals(response.getBody(), 200, response.getStatus());
        var body = Json.newObject().put("assets", true);
        var applied = api("/jatos/api/v1/studies/" + studyId + "/source/folder/import").post(body);
        assertEquals(applied.getBody(), 200, applied.getStatus());
        assertTrue(applied.asJson().path("data").path("assetsUpdated").asBoolean());
        assertFalse(applied.asJson().path("data").path("propertiesUpdated").asBoolean());
        assertNoStaging();
    }
}
