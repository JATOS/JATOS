package services.gui;

import general.common.Common;
import http.common.Http.Context;
import models.common.Study;
import models.common.StudySource;
import models.common.User;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import play.libs.Json;
import services.gui.importexport.LocalFolderStudyImportExport;
import testutils.JatosTest;
import utils.common.IOUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static auth.gui.AuthAction.SIGNEDIN_USER;
import static org.junit.Assert.*;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

public class LocalFolderStudyCreationIntegrationTest extends JatosTest {

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();
    private LocalFolderStudyImportExport service;
    private Path source;

    @Before
    public void setupFolder() throws Exception {
        service = application.injector().instanceOf(LocalFolderStudyImportExport.class);
        Context.current().args().put(SIGNEDIN_USER, admin);
        source = temporary.newFolder("source").toPath();
    }

    private com.fasterxml.jackson.databind.node.ObjectNode sourceRequest(String path) {
        var body = Json.newObject();
        body.set("source", Json.newObject().put("type", "LOCAL_FOLDER").put("localFolderPath", path));
        return body;
    }

    @Test
    public void rejectsInvalidSourceRequestsAndArchiveOptions() throws Exception {
        String url = "/jatos/api/v1/studies";
        var mixed = sourceRequest(source.toString()).put("title", "Override");
        assertEquals(400, api(url).post(mixed).getStatus());
        assertEquals(400, api(url).post(Json.newObject().putNull("source")).getStatus());
        var unsupported = sourceRequest(source.toString());
        ((com.fasterxml.jackson.databind.node.ObjectNode) unsupported.get("source")).put("type", "GIT");
        assertEquals(400, api(url).post(unsupported).getStatus());
        for (String option : List.of("keepProperties", "keepAssets", "keepCurrentAssetsName", "renameAssets")) {
            assertEquals(400, api(url + "?" + option + "=true").post(sourceRequest(source.toString())).getStatus());
        }
        assertNoStaging();
    }

    private void properties(String uuid, String dirName) throws Exception {
        var data = Json.newObject().put("uuid", uuid).put("title", "Folder study").put("dirName", dirName);
        var envelope = Json.newObject().put("version", "3");
        envelope.set("data", data);
        Files.writeString(source.resolve("study.jas"), envelope.toString());
    }

    private void assertNoStaging() throws Exception {
        try (var paths = Files.list(IOUtils.tmpDir())) {
            assertFalse(paths.anyMatch(p -> p.getFileName().toString().startsWith("study-source-")));
        }
    }

    @Test
    public void createsFromPropertiesAndAssetsAndRejectsDuplicateUuid() throws Exception {
        String uuid = UUID.randomUUID().toString();
        properties(uuid, "folder-assets");
        Files.createDirectory(source.resolve("assets"));
        Files.writeString(source.resolve("assets/index.html"), "example");
        var result = service.createStudyFromLocalFolder(source.toString());
        long id = result.study().path("id").asLong();
        assertEquals("Study created from local folder: imported properties and assets.", result.message());
        jpaApi.withTransaction(em -> {
            Study study = em.find(Study.class, id);
            assertEquals(uuid, study.getUuid());
            assertEquals(admin.getUsername(), study.getOwner().getUsername());
            assertTrue(study.getUserList().contains(study.getOwner()));
            assertEquals(StudySource.Type.LOCAL_FOLDER, study.getStudySource().getType());
            assertEquals(source.toString(), study.getStudySource().getLocalFolderPath());
        });
        assertEquals("example", Files.readString(IOUtils.generateStudyAssetsPath("folder-assets").resolve("index.html")));
        var duplicate = api("/jatos/api/v1/studies").post(sourceRequest(source.toString()));
        assertEquals(409, duplicate.getStatus());
        assertEquals("example", Files.readString(source.resolve("assets/index.html")));
        assertNoStaging();
    }

    @Test
    public void createsDefaultsWithEmptyAssetsThroughApi() throws Exception {
        Files.createDirectory(source.resolve("assets"));
        var response = api("/jatos/api/v1/studies").post(sourceRequest(source.toString()));
        assertEquals(response.getBody(), 201, response.getStatus());
        assertEquals("Study created from local folder: imported assets only.",
                response.asJson().path("message").asText());
        long id = response.asJson().path("data").path("id").asLong();
        jpaApi.withTransaction(em -> {
            Study study = em.find(Study.class, id);
            assertEquals("New study", study.getTitle());
            assertFalse(study.getBatchList().isEmpty());
            assertTrue(Files.isDirectory(IOUtils.generateStudyAssetsPath(study.getDirName())));
        });
        assertNoStaging();
    }

    @Test
    public void sessionCreationSetsSuccessFlash() throws Exception {
        Files.createDirectory(source.resolve("assets"));
        String csrf = application.injector().instanceOf(play.filters.csrf.CSRF.TokenProvider.class).generateToken();
        var request = play.test.Helpers.fakeRequest("POST", "/jatos/api/v1/studies")
                .header("Cookie", "PLAY_SESSION=test-session")
                .cookie(play.mvc.Http.Cookie.builder("PLAY_SESSION", "test-session").build())
                .session("username", admin.getUsername())
                .session("signinTime", String.valueOf(System.currentTimeMillis()))
                .session("lastActivityTime", String.valueOf(System.currentTimeMillis()))
                .session("csrfToken", csrf)
                .header("Csrf-Token", csrf)
                .bodyJson(sourceRequest(source.toString()));
        var response = play.test.Helpers.route(application, request);
        assertEquals(play.test.Helpers.contentAsString(response), 201, response.status());
        assertEquals("Study created from local folder: imported assets only.",
                response.flash().get("success").orElseThrow());
        assertNoStaging();
    }

    @Test
    public void rejectsSourceWithoutPropertiesOrAssetsThroughApi() throws Exception {
        var response = api("/jatos/api/v1/studies").post(sourceRequest(source.toString()));
        assertEquals(response.getBody(), 400, response.getStatus());
        assertTrue(response.asJson().path("error").path("fields").path("localFolderPath").toString()
                .contains("The source folder must contain a .jas file or an assets/ directory, or both."));
        assertNoStaging();
    }

    @Test
    public void assetsCollisionAllocatesAnotherDirectory() throws Exception {
        properties(UUID.randomUUID().toString(), "occupied");
        Path existing = IOUtils.generateStudyAssetsPath("occupied");
        Files.createDirectory(existing);
        Files.writeString(existing.resolve("keep.txt"), "keep");
        var result = service.createStudyFromLocalFolder(source.toString());
        var response = result.study();
        assertEquals("Study created from local folder: imported properties only.", result.message());
        assertEquals("occupied_1", response.path("dirName").asText());
        assertEquals("keep", Files.readString(existing.resolve("keep.txt")));
        assertNoStaging();
    }

    @Test
    public void outerRollbackRemovesStudyAndNewAssets() throws Exception {
        properties(UUID.randomUUID().toString(), "rolled-back");
        assertThrows(IllegalStateException.class, () -> jpaApi.withTransaction((java.util.function.Consumer<jakarta.persistence.EntityManager>) em -> {
            try {service.createStudyFromLocalFolder(source.toString());} catch (Exception e) {
                throw new RuntimeException(e);
            }
            throw new IllegalStateException("force rollback");
        }));
        assertFalse(Files.exists(IOUtils.generateStudyAssetsPath("rolled-back")));
        jpaApi.withTransaction(em -> {
            assertEquals(Long.valueOf(0), em.createQuery("select count(s) from Study s where s.dirName = 'rolled-back'", Long.class).getSingleResult());
        });
        assertNoStaging();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Test
    public void invalidPropertiesAndDisallowedFoldersDoNotCreateStudies() throws Exception {
        Files.writeString(source.resolve("study.jas"), "{}");
        var response = api("/jatos/api/v1/studies").post(sourceRequest(source.toString()));
        assertEquals(response.getBody(), 400, response.getStatus());
        try (var common = mockStatic(Common.class, CALLS_REAL_METHODS)) {
            common.when(Common::isServer).thenReturn(true);
            common.when(Common::getStudySourcesLocalFolderAllowedRoots).thenReturn(List.of());
            assertThrows(exceptions.common.BadRequestException.class, () -> service.createStudyFromLocalFolder(source.toString()));
        }
        assertNoStaging();
    }

    @Test
    public void assetsOnlySourceAndMissingFolderAreHandled() throws Exception {
        Files.createDirectory(source.resolve("assets"));
        Files.writeString(source.resolve("assets/index.html"), "assets only");
        var result = service.createStudyFromLocalFolder(source.toString());
        var response = result.study();
        assertEquals("Study created from local folder: imported assets only.", result.message());
        assertEquals("assets only", Files.readString(IOUtils.generateStudyAssetsPath(response.path("dirName").asText()).resolve("index.html")));
        var missing = api("/jatos/api/v1/studies")
                .post(sourceRequest(source.resolve("missing").toString()));
        assertEquals(400, missing.getStatus());
        assertTrue(missing.asJson().path("error").path("fields").has("localFolderPath"));
        assertNoStaging();
    }

    @Test
    public void concurrentCreationOfSameUuidCreatesOnlyOneStudy() throws Exception {
        String uuid = UUID.randomUUID().toString();
        properties(uuid, "concurrent-folder");
        String url = "/jatos/api/v1/studies";
        String secondToken = createApiToken(createUser("second-folder-owner"));
        var firstCall = api(url);
        var secondCall = api(url).token(secondToken);
        var body = sourceRequest(source.toString());
        var first = java.util.concurrent.CompletableFuture.supplyAsync(() -> firstCall.post(body));
        var second = java.util.concurrent.CompletableFuture.supplyAsync(() -> secondCall.post(body));
        var statuses = List.of(first.get(30, java.util.concurrent.TimeUnit.SECONDS).getStatus(),
                second.get(30, java.util.concurrent.TimeUnit.SECONDS).getStatus());
        assertTrue(statuses.toString(), statuses.contains(201));
        assertTrue(statuses.toString(), statuses.contains(409));
        jpaApi.withTransaction(em -> {
            assertEquals(Long.valueOf(1), em.createQuery("select count(s) from Study s where s.uuid = :uuid", Long.class)
                    .setParameter("uuid", uuid).getSingleResult());
        });
        assertNoStaging();
    }

    @Test
    public void endpointRequiresStudyCreationPermission() throws Exception {
        String url = "/jatos/api/v1/studies";
        var data = sourceRequest(source.toString());
        assertEquals(401, api(url).noAuth().post(data).getStatus());
        User viewer = createUser("folder-viewer");
        jpaApi.withTransaction(em -> {em.find(User.class, viewer.getUsername()).updateRoles(User.Role.VIEWER);});
        assertEquals(403, api(url).token(createApiToken(viewer)).post(data).getStatus());
        assertNoStaging();
    }
}
