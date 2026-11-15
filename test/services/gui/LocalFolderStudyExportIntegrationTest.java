package services.gui;

import exceptions.common.BadRequestException;
import exceptions.common.HttpException;
import general.common.Common;
import http.common.Http.Context;
import models.common.Study;
import models.common.User;
import models.gui.StudySourceSyncProperties;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import play.libs.Json;
import services.gui.importexport.LocalFolderStudyImportExport;
import services.gui.importexport.StudySourceSync;
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

public class LocalFolderStudyExportIntegrationTest extends JatosTest {

    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private Path folder;
    private Path assets;
    private long studyId;
    private String uuid;
    private StudySourceSync sync;
    private LocalFolderStudyImportExport exporter;

    @Before public void setupExport() throws Exception {
        Context.current().args().put(SIGNEDIN_USER, admin);
        folder = temporary.newFolder("external").toPath();
        uuid = UUID.randomUUID().toString();
        writeJas(uuid);
        Files.createDirectory(folder.resolve("assets"));
        Files.writeString(folder.resolve("assets/old.txt"), "external");
        exporter = application.injector().instanceOf(LocalFolderStudyImportExport.class);
        var result = exporter.createStudyFromLocalFolder(folder.toString());
        studyId = result.study().path("id").asLong();
        assets = IOUtils.generateStudyAssetsPath(result.study().path("dirName").asText());
        Files.delete(assets.resolve("old.txt"));
        Files.writeString(assets.resolve("new.txt"), "JATOS");
        jpaApi.withTransaction(em -> { em.find(Study.class, studyId).setTitle("Exported title"); });
        sync = application.injector().instanceOf(StudySourceSync.class);
    }

    private void writeJas(String id) throws Exception {
        Files.writeString(folder.resolve("custom.jas"), Json.newObject().put("version", "3")
                .set("data", Json.newObject().put("uuid", id).put("title", "External").put("dirName", "export-test")).toString());
    }

    private StudySourceSyncProperties selection(boolean properties, boolean files) {
        var props = new StudySourceSyncProperties();
        props.setProperties(properties);
        props.setAssets(files);
        return props;
    }

    private String endpoint() { return "/jatos/api/v1/studies/" + studyId + "/source/folder/export"; }

    private void noTemporaryFiles() throws Exception {
        try (var files = Files.list(folder)) {
            assertFalse(files.anyMatch(p -> p.getFileName().toString().startsWith(".jatos-export-")));
        }
    }

    @Test public void exportsBothAndPreservesUnrelatedFiles() throws Exception {
        jpaApi.withTransaction(em -> {
            var source = em.find(Study.class, studyId).getStudySource();
            var credentials = new models.common.GitCredentials(source);
            credentials.setUsername("private-git-user");
            credentials.setOrigin("https://private-git.example.org");
            credentials.setEncryptedSecret("encrypted-secret-test-marker");
            source.setGitCredentials(credentials);
        });
        Files.writeString(folder.resolve("README.md"), "keep");
        Files.createDirectory(folder.resolve(".git"));
        Files.writeString(folder.resolve(".git/config"), "keep git");
        var result = sync.exportToFolder(studyId, selection(true, true));
        String jas = Files.readString(folder.resolve("custom.jas"));
        assertEquals("Exported title", Json.parse(jas).path("data").path("title").asText());
        assertEquals(uuid, Json.parse(jas).path("data").path("uuid").asText());
        for (String privateField : List.of("studySource", "gitCredentials", "encryptedSecret", "localFolderPath"))
            assertFalse(jas.contains(privateField));
        assertFalse(jas.contains(folder.toString()));
        assertFalse(jas.contains("encrypted-secret-test-marker"));
        assertFalse(jas.contains("private-git-user"));
        assertFalse(jas.contains("previous-import-user"));
        assertEquals("JATOS", Files.readString(folder.resolve("assets/new.txt")));
        assertFalse(Files.exists(folder.resolve("assets/old.txt")));
        assertEquals("keep", Files.readString(folder.resolve("README.md")));
        assertEquals("keep git", Files.readString(folder.resolve(".git/config")));
        String log = Files.readString(Path.of(Common.getStudyLogsPath(), uuid + ".log"), java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(log.contains("Exported properties and assets to local folder: " + folder));
        jpaApi.withTransaction(em -> {
            Study study = em.find(Study.class, studyId);
            assertEquals(admin.getUsername(), study.getOwner().getUsername());
        });
        noTemporaryFiles();
    }

    @Test public void selectedScopePreservesUnselectedContentAndEmptyAssetsClearDestination() throws Exception {
        String original = Files.readString(folder.resolve("custom.jas"));
        sync.exportToFolder(studyId, selection(false, true));
        assertEquals(original, Files.readString(folder.resolve("custom.jas")));
        Files.writeString(folder.resolve("assets/keep.txt"), "keep");
        sync.exportToFolder(studyId, selection(true, false));
        assertTrue(Files.exists(folder.resolve("assets/keep.txt")));
        Files.delete(assets.resolve("new.txt"));
        sync.exportToFolder(studyId, selection(false, true));
        try (var files = Files.list(folder.resolve("assets"))) { assertEquals(0, files.count()); }
        noTemporaryFiles();
    }

    @Test public void emptyDestinationUsesDefaultFilename() throws Exception {
        IOUtils.deleteRecursively(folder.resolve("assets"));
        Files.delete(folder.resolve("custom.jas"));
        var preview = api(endpoint()).get();
        assertEquals(preview.getBody(), 200, preview.getStatus());
        assertFalse(preview.asJson().path("data").has("conflicts"));
        sync.exportToFolder(studyId, selection(true, true));
        assertTrue(Files.exists(folder.resolve("study.jas")));
        assertTrue(Files.exists(folder.resolve("assets/new.txt")));
        noTemporaryFiles();
    }

    @Test public void previewReportsUuidConflictAndApplyRechecks() throws Exception {
        assertEquals(200, api(endpoint()).get().getStatus());
        writeJas(UUID.randomUUID().toString());
        String original = Files.readString(folder.resolve("custom.jas"));
        var preview = api(endpoint()).get();
        assertEquals(400, preview.getStatus());
        assertTrue(preview.asJson().path("error").path("message").asText().contains("UUID"));
        assertThrows(BadRequestException.class, () -> sync.exportToFolder(studyId, selection(false, true)));
        assertEquals(original, Files.readString(folder.resolve("custom.jas")));
        assertEquals("external", Files.readString(folder.resolve("assets/old.txt")));
        noTemporaryFiles();
    }

    @Test public void multipleOrInvalidJasFilesBlockExport() throws Exception {
        Files.copy(folder.resolve("custom.jas"), folder.resolve("another.jas"));
        assertThrows(BadRequestException.class, () -> sync.exportToFolder(studyId, selection(true, true)));
        Files.delete(folder.resolve("another.jas"));
        Files.writeString(folder.resolve("custom.jas"), "invalid JSON");
        assertThrows(BadRequestException.class, () -> sync.exportToFolder(studyId, selection(true, false)));
        noTemporaryFiles();
    }

    @Test public void missingStudyAssetsNeverClearDestination() throws Exception {
        IOUtils.deleteRecursively(assets);
        var preview = api(endpoint()).get();
        assertFalse(preview.asJson().path("data").path("assetsAvailable").asBoolean());
        assertThrows(BadRequestException.class, () -> sync.exportToFolder(studyId, selection(false, true)));
        sync.exportToFolder(studyId, selection(true, false));
        assertEquals("external", Files.readString(folder.resolve("assets/old.txt")));
        noTemporaryFiles();
    }

    @Test public void rollbackRestoresBothDestinationEntries() throws Exception {
        String original = Files.readString(folder.resolve("custom.jas"));
        LocalFolderStudyImportExport failingExporter = spy(exporter);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            jpaApi.em("default").unwrap(org.hibernate.Session.class).getTransaction()
                    .registerSynchronization(new jakarta.transaction.Synchronization() {
                        public void beforeCompletion() { throw new IllegalStateException("Commit failure"); }
                        public void afterCompletion(int status) {}
                    });
            return null;
        }).when(failingExporter).exportToLocalFolder(any(), anyString(), any());
        var injector = application.injector();
        var failing = new StudySourceSync(jpaApi, injector.instanceOf(daos.common.StudyDao.class),
                injector.instanceOf(daos.common.UserDao.class), injector.instanceOf(AuthorizationService.class),
                injector.instanceOf(StudySourceSyncIO.class), injector.instanceOf(general.common.StudyLogger.class), failingExporter);
        assertThrows(RuntimeException.class, () -> failing.exportToFolder(studyId, selection(true, true)));
        assertEquals(original, Files.readString(folder.resolve("custom.jas")));
        assertEquals("external", Files.readString(folder.resolve("assets/old.txt")));
        assertFalse(Files.exists(folder.resolve("assets/new.txt")));
        noTemporaryFiles();
    }

    @Test public void apiAllowsMembersRejectsNonMembersAndRejectsDirectionField() throws Exception {
        User member = createUser("export-member");
        jpaApi.withTransaction(em -> { em.find(Study.class, studyId).addUser(em.find(User.class, member.getUsername())); });
        String token = createApiToken(member);
        assertEquals(200, api(endpoint()).token(token).get().getStatus());
        var body = Json.newObject().put("properties", true).put("assets", true);
        assertEquals(200, api(endpoint()).token(token).post(body).getStatus());
        String outsiderToken = createApiToken(createUser("export-outsider"));
        assertEquals(403, api(endpoint()).token(outsiderToken).get().getStatus());
        assertEquals(403, api(endpoint()).token(outsiderToken).post(body).getStatus());
        assertEquals(400, api(endpoint()).post(Json.newObject().put("direction", "PUSH").put("assets", true)).getStatus());
        var result = api(endpoint()).post(body);
        assertEquals(result.getBody(), 200, result.getStatus());
        assertFalse(result.asJson().path("data").has("direction"));
        noTemporaryFiles();
    }

    @Test public void folderEndpointsRejectNonFolderSources() throws Exception {
        String importUrl = "/jatos/api/v1/studies/" + studyId + "/source/folder/import";
        var body = Json.newObject().put("assets", true);
        for (var type : List.of(models.common.StudySource.Type.NONE, models.common.StudySource.Type.GIT)) {
            jpaApi.withTransaction(em -> { em.find(Study.class, studyId).getStudySource().setType(type); });
            for (String url : List.of(importUrl, endpoint())) {
                assertEquals(400, api(url).get().getStatus());
                assertEquals(400, api(url).post(body).getStatus());
            }
        }
        assertEquals("external", Files.readString(folder.resolve("assets/old.txt")));
        noTemporaryFiles();
    }

    @Test public void honorsAllowedRootsAndSharesImportLock() throws Exception {
        try (var common = mockStatic(Common.class, CALLS_REAL_METHODS)) {
            common.when(Common::isServer).thenReturn(true);
            common.when(Common::getStudySourcesAllowedRoots).thenReturn(List.of());
            assertThrows(BadRequestException.class, () -> sync.exportToFolder(studyId, selection(true, true)));
            common.when(Common::isServer).thenReturn(false);
            common.when(Common::getStudySourcesAllowedRoots).thenReturn(List.of(temporary.newFolder("other").toPath().toString()));
            assertThrows(BadRequestException.class, () -> sync.exportToFolder(studyId, selection(true, true)));
        }
        try (var ignored = application.injector().instanceOf(StudySourceSyncIO.class).lock(studyId)) {
            assertEquals(409, assertThrows(HttpException.class, () -> sync.exportToFolder(studyId, selection(true, true))).getHttpStatus());
        }
        noTemporaryFiles();
    }

    @Test public void rejectsLinksAndOverlappingDestinations() throws Exception {
        Path outside = temporary.newFolder("outside").toPath();
        IOUtils.deleteRecursively(folder.resolve("assets"));
        try {
            Files.createSymbolicLink(folder.resolve("assets"), outside);
        } catch (UnsupportedOperationException | java.io.IOException e) {
            org.junit.Assume.assumeNoException(e);
        }
        assertThrows(BadRequestException.class, () -> sync.exportToFolder(studyId, selection(false, true)));
        Files.delete(folder.resolve("assets"));
        Files.createSymbolicLink(assets.resolve("linked"), outside);
        assertThrows(exceptions.common.ImportExportException.class, () -> sync.exportToFolder(studyId, selection(true, true)));
        assertFalse(Files.exists(folder.resolve("assets")));
        assertEquals("External", Json.parse(Files.readString(folder.resolve("custom.jas"))).path("data").path("title").asText());
        jpaApi.withTransaction(em -> { em.find(Study.class, studyId).getStudySource().setLocalFolderPath(assets.toString()); });
        assertThrows(BadRequestException.class, () -> sync.exportToFolder(studyId, selection(true, true)));
        noTemporaryFiles();
    }
}
