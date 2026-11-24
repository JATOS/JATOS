package services.gui;

import general.common.Common;
import general.common.JpaTransactions;
import http.common.Http.Context;
import models.common.Study;
import models.common.StudySource;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.URIish;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import play.inject.guice.GuiceApplicationBuilder;
import play.libs.Json;
import play.test.Helpers;
import services.gui.importexport.GitStudySourceIO;
import testutils.JatosTest;
import utils.common.IOUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static auth.gui.AuthAction.SIGNEDIN_USER;
import static org.junit.Assert.*;
import static play.inject.Bindings.bind;

public class GitStudyCreationIntegrationTest extends JatosTest {

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();
    private Git git;
    private Path repository;
    private String uuid;
    private String commit;
    private int fetches;

    @Override
    @Before
    public void startApp() {
        try {
            repository = temporary.newFolder("repo").toPath();
            git = Git.init().setDirectory(repository.toFile()).setInitialBranch("main").call();
        } catch (Exception e) {throw new RuntimeException(e);}
        var io = new GitStudySourceIO() {
            @Override
            protected Transport openTransport(Repository local, String url) throws IOException {
                fetches++;
                try {return Transport.open(local, new URIish(repository.toUri().toString()));} catch (
                        java.net.URISyntaxException e) {throw new IOException(e);}
            }
        };
        application = new GuiceApplicationBuilder().overrides(bind(GitStudySourceIO.class).toInstance(io)).build();
        Helpers.start(application);
        jpaApi = application.injector().instanceOf(JpaTransactions.class);
        admin = getAdmin();
        apiToken = createApiToken(admin);
        Context.setCurrent(new Context(Helpers.fakeRequest().build()));
        Context.current().args().put(SIGNEDIN_USER, admin);
    }

    @Before
    public void content() throws Exception {
        uuid = UUID.randomUUID().toString();
        Files.writeString(repository.resolve("study.jas"), Json.newObject().put("version", "3")
                .set("data", Json.newObject().put("uuid", uuid).put("title", "Git study").put("dirName", "git-study")).toString());
        Files.createDirectory(repository.resolve("assets"));
        Files.writeString(repository.resolve("assets/index.html"), "Git content");
        Files.writeString(repository.resolve("README.md"), "ignored");
        commit();
    }

    private void commit() throws Exception {
        git.add().addFilepattern(".").call();
        git.add().addFilepattern(".").setUpdate(true).call();
        commit = git.commit().setMessage("Test snapshot").setAuthor("Test", "test@example.org").setCommitter("Test", "test@example.org").call().name();
    }

    @After
    public void closeRepository() {
        if (git != null) git.close();
    }

    private com.fasterxml.jackson.databind.node.ObjectNode request() {
        return Json.newObject().set("source", Json.newObject().put("type", "GIT")
                .put("gitUrl", "https://example.org/repo.git").put("gitRef", "main"));
    }

    private void noStaging() throws Exception {
        try (var paths = Files.list(IOUtils.tmpDir())) {
            assertFalse(paths.anyMatch(p -> p.getFileName().toString().startsWith("study-git-")));
        }
    }

    private long createGitStudy() {
        var response = api("/jatos/api/v1/studies").post(request());
        assertEquals(response.getBody(), 201, response.getStatus());
        return response.asJson().path("data").path("id").asLong();
    }

    private String pullUrl(long id) {
        return "/jatos/api/v1/studies/" + id + "/source/git/pull";
    }

    private Path assets(long id) throws IOException {
        String name = jpaApi.withTransaction(em -> {return em.find(Study.class, id).getDirName();});
        return application.injector().instanceOf(IOUtils.class).getStudyAssetsDir(name);
    }

    private void properties(String sourceUuid, String title) throws Exception {
        Files.writeString(repository.resolve("study.jas"), Json.newObject().put("version", "3")
                .set("data", Json.newObject().put("uuid", sourceUuid).put("title", title).put("dirName", "changed-name")).toString());
    }

    private String pushUrl(long id) {
        return "/jatos/api/v1/studies/" + id + "/source/git/push";
    }

    @Test
    public void memberPushesWithoutPreviewAndReportsCommitAndNoChanges() throws Exception {
        long id = createGitStudy();
        var member = createUser("push-member");
        jpaApi.withTransaction(em -> {
            em.find(Study.class, id).addUser(em.find(models.common.User.class, member.getUsername()));
            em.find(Study.class, id).setTitle("Exported title");
        });
        var preview = api("/jatos/api/v1/studies/" + uuid + "/source/git/push").get();
        assertEquals(preview.getBody(), 200, preview.getStatus());
        assertEquals(commit, preview.asJson().path("data").path("commitId").asText());
        String token = createApiToken(member);
        var selection = Json.newObject().put("properties", true).put("assets", true);
        var response = api(pushUrl(id)).token(token).post(selection);
        assertEquals(response.getBody(), 200, response.getStatus());
        assertTrue(response.asJson().path("data").path("changed").asBoolean());
        String pushed = response.asJson().path("data").path("commitId").asText();
        assertEquals(pushed, git.getRepository().resolve("refs/heads/main").name());
        try (var walk = new org.eclipse.jgit.revwalk.RevWalk(git.getRepository())) {
            var revision = walk.parseCommit(org.eclipse.jgit.lib.ObjectId.fromString(pushed));
            try (var tree = org.eclipse.jgit.treewalk.TreeWalk.forPath(git.getRepository(), "study.jas", revision.getTree())) {
                String json = new String(git.getRepository().open(tree.getObjectId(0)).getBytes(), java.nio.charset.StandardCharsets.UTF_8);
                assertTrue(json.contains("Exported title"));
                assertFalse(json.contains("gitUrl"));
                assertFalse(json.contains("encryptedSecret"));
            }
        }
        response = api(pushUrl(id)).token(token).post(selection);
        assertEquals(response.getBody(), 200, response.getStatus());
        assertFalse(response.asJson().path("data").path("changed").asBoolean());
        assertEquals(pushed, response.asJson().path("data").path("commitId").asText());
        String log = Files.readString(Path.of(Common.getStudyLogsPath(), uuid + ".log"), java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(log.contains("Pushed properties and assets to Git at commit " + pushed));
        noStaging();
    }

    @Test
    public void pushChecksMembersLocksAndDestinationProperties() throws Exception {
        long id = createGitStudy();
        String outsider = createApiToken(createUser("push-outsider"));
        var selection = Json.newObject().put("assets", true);
        int before = fetches;
        assertEquals(403, api(pushUrl(id)).token(outsider).get().getStatus());
        assertEquals(403, api(pushUrl(id)).token(outsider).post(selection).getStatus());
        assertEquals(before, fetches);
        try (var lock = application.injector().instanceOf(services.gui.importexport.StudySourceSyncIO.class).lock(id)) {
            assertEquals(409, api(pushUrl(id)).post(selection).getStatus());
        }
        properties(UUID.randomUUID().toString(), "Different study");
        commit();
        assertEquals(400, api(pushUrl(id)).get().getStatus());
        assertEquals(400, api(pushUrl(id)).post(selection).getStatus());
        Files.writeString(repository.resolve("study.jas"), "invalid JSON");
        commit();
        assertEquals(400, api(pushUrl(id)).post(selection).getStatus());
        properties(uuid, "Correct study");
        Files.copy(repository.resolve("study.jas"), repository.resolve("other.jas"));
        commit();
        assertEquals(400, api(pushUrl(id)).post(selection).getStatus());
        assertEquals(commit, git.getRepository().resolve("refs/heads/main").name());
        noStaging();
    }

    @Test
    public void pushRejectsTagsLockedStudiesAndMissingSelections() throws Exception {
        long id = createGitStudy();
        git.tag().setName("release").setAnnotated(false).call();
        jpaApi.withTransaction(em -> { em.find(Study.class, id).getStudySource().setGitRef("release"); });
        var selection = Json.newObject().put("properties", true);
        assertEquals(400, api(pushUrl(id)).get().getStatus());
        assertEquals(400, api(pushUrl(id)).post(selection).getStatus());
        assertEquals(200, api(pullUrl(id)).get().getStatus());
        jpaApi.withTransaction(em -> {
            em.find(Study.class, id).getStudySource().setGitRef("main");
            em.find(Study.class, id).setLocked(true);
        });
        assertEquals(403, api(pushUrl(id)).get().getStatus());
        assertEquals(403, api(pushUrl(id)).post(selection).getStatus());
        jpaApi.withTransaction(em -> { em.find(Study.class, id).setLocked(false); });
        assertEquals(400, api(pushUrl(id)).post(Json.newObject()).getStatus());
        assertEquals(commit, git.getRepository().resolve("refs/heads/main").name());
        noStaging();
    }

    @Test
    public void memberPullsWithoutPreviewAndPreservesLocalSettings() throws Exception {
        long id = createGitStudy();
        var member = createUser("git-member");
        jpaApi.withTransaction(em -> {
            em.find(Study.class, id).addUser(em.find(models.common.User.class, member.getUsername()));
        });
        Path assets = assets(id);
        Files.writeString(assets.resolve("obsolete.txt"), "remove me");
        properties(uuid, "Pulled title");
        Files.writeString(repository.resolve("assets/index.html"), "Updated Git content");
        commit();
        var response = api(pullUrl(id)).token(createApiToken(member))
                .post(Json.newObject().put("properties", true).put("assets", true));
        assertEquals(response.getBody(), 200, response.getStatus());
        assertTrue(response.asJson().path("data").path("message").asText().contains(commit));
        assertEquals("Updated Git content", Files.readString(assets.resolve("index.html")));
        assertFalse(Files.exists(assets.resolve("obsolete.txt")));
        jpaApi.withTransaction(em -> {
            Study study = em.find(Study.class, id);
            assertEquals("Pulled title", study.getTitle());
            assertEquals("git-study", study.getDirName());
            assertEquals(admin.getUsername(), study.getOwner().getUsername());
            assertTrue(study.getUserList().stream().anyMatch(user -> user.getUsername().equals(member.getUsername())));
            assertEquals("https://example.org/repo.git", study.getStudySource().getGitUrl());
        });
        String log = Files.readString(Path.of(Common.getStudyLogsPath(), uuid + ".log"), java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(log.contains("Pulled properties and assets from Git at commit " + commit));
        noStaging();
    }

    @Test
    public void previewUsesUuidAndPullFetchesAgain() throws Exception {
        long id = createGitStudy();
        var response = api("/jatos/api/v1/studies/" + uuid + "/source/git/pull").get();
        assertEquals(response.getBody(), 200, response.getStatus());
        var preview = response.asJson().path("data");
        assertEquals(commit, preview.path("commitId").asText());
        assertEquals("anonymous", preview.path("credentialSource").asText());
        assertTrue(preview.path("propertiesAvailable").asBoolean());
        assertTrue(preview.path("assetsAvailable").asBoolean());
        properties(uuid, "Changed after preview");
        commit();
        response = api(pullUrl(id)).post(Json.newObject().put("properties", true));
        assertEquals(response.getBody(), 200, response.getStatus());
        assertEquals("Changed after preview", jpaApi.withTransaction(em -> {
            return em.find(Study.class, id).getTitle();
        }));
        assertEquals("Git content", Files.readString(assets(id).resolve("index.html")));
        noStaging();
    }

    @Test
    public void rejectsMismatchedUuidEvenForAssetsOnlyAndMissingSelections() throws Exception {
        long id = createGitStudy();
        properties(UUID.randomUUID().toString(), "Wrong study");
        commit();
        assertEquals(400, api(pullUrl(id)).get().getStatus());
        assertEquals(400, api(pullUrl(id)).post(Json.newObject().put("assets", true)).getStatus());
        assertEquals("Git content", Files.readString(assets(id).resolve("index.html")));
        Files.delete(repository.resolve("study.jas"));
        commit();
        assertEquals(400, api(pullUrl(id)).post(Json.newObject().put("properties", true)).getStatus());
        assertEquals(200, api(pullUrl(id)).post(Json.newObject().put("assets", true)).getStatus());
        properties(uuid, "Properties only");
        Files.delete(repository.resolve("assets/index.html"));
        commit();
        assertEquals(400, api(pullUrl(id)).post(Json.newObject().put("properties", true).put("assets", true)).getStatus());
        assertEquals("Git study", jpaApi.withTransaction(em -> {return em.find(Study.class, id).getTitle();}));
        assertEquals("Git content", Files.readString(assets(id).resolve("index.html")));
        assertEquals(200, api(pullUrl(id)).post(Json.newObject().put("properties", true)).getStatus());
        assertEquals(400, api(pullUrl(id)).post(Json.newObject()).getStatus());
        noStaging();
    }

    @Test
    public void pullRejectsNonMembersWrongSourceAndConcurrentSynchronization() throws Exception {
        long id = createGitStudy();
        var outsider = createUser("git-outsider");
        String token = createApiToken(outsider);
        int before = fetches;
        assertEquals(403, api(pullUrl(id)).token(token).get().getStatus());
        assertEquals(403, api(pullUrl(id)).token(token).post(Json.newObject().put("assets", true)).getStatus());
        assertEquals(before, fetches);
        var io = application.injector().instanceOf(services.gui.importexport.StudySourceSyncIO.class);
        try (var lock = io.lock(id)) {
            assertEquals(409, api(pullUrl(id)).post(Json.newObject().put("assets", true)).getStatus());
        }
        jpaApi.withTransaction(em -> {
            em.find(Study.class, id).getStudySource().setType(StudySource.Type.LOCAL_FOLDER);
        });
        assertEquals(400, api(pullUrl(id)).get().getStatus());
        assertEquals(400, api(pullUrl(id)).post(Json.newObject().put("assets", true)).getStatus());
        assertEquals(before, fetches);
        noStaging();
    }

    @Test
    public void memberUsesOwnersCredentialsAndSessionPullRequiresCsrf() throws Exception {
        long id = createGitStudy();
        application.injector().instanceOf(services.gui.importexport.GitCredentialsService.class)
                .saveGitCredentialsForUser("owner-git", "https://example.org", "owner-secret-marker");
        var member = createUser("credential-member");
        jpaApi.withTransaction(em -> {
            em.find(Study.class, id).addUser(em.find(models.common.User.class, member.getUsername()));
        });
        var response = api(pullUrl(id)).token(createApiToken(member)).get();
        assertEquals(response.getBody(), 200, response.getStatus());
        assertEquals("owner", response.asJson().path("data").path("credentialSource").asText());
        assertFalse(response.getBody().contains("owner-secret-marker"));
        assertFalse(response.getBody().contains("owner-git"));
        String csrf = application.injector().instanceOf(play.filters.csrf.CSRF.TokenProvider.class).generateToken();
        var request = Helpers.fakeRequest("POST", pullUrl(id))
                .header("Cookie", "PLAY_SESSION=test-session")
                .cookie(play.mvc.Http.Cookie.builder("PLAY_SESSION", "test-session").build())
                .session("username", member.getUsername())
                .session("signinTime", String.valueOf(System.currentTimeMillis()))
                .session("lastActivityTime", String.valueOf(System.currentTimeMillis()))
                .session("csrfToken", csrf).bodyJson(Json.newObject().put("assets", true));
        assertEquals(403, Helpers.route(application, request).status());
        var result = Helpers.route(application, request.header("Csrf-Token", csrf));
        assertEquals(Helpers.contentAsString(result), 200, result.status());
        assertTrue(result.flash().get("success").orElseThrow().contains("Pulled assets from Git"));
        jpaApi.withTransaction(em -> {em.find(Study.class, id).setLocked(true);});
        assertEquals(403, api(pullUrl(id)).get().getStatus());
        assertEquals(403, api(pullUrl(id)).post(Json.newObject().put("assets", true)).getStatus());
        noStaging();
    }

    @Test
    public void failedPullRestoresPropertiesAndAssetsAndDoesNotLogSuccess() throws Exception {
        long id = createGitStudy();
        properties(uuid, "Must roll back");
        Files.writeString(repository.resolve("assets/index.html"), "Must roll back");
        commit();
        var injector = application.injector();
        var failingIO = new services.gui.importexport.StudySourceSyncIO(jpaApi, injector.instanceOf(IOUtils.class)) {
            @Override
            public void replaceAssets(Path source, String dirName) throws IOException {
                super.replaceAssets(source, dirName);
                throw new IOException("Failure after replacement");
            }
        };
        var gitService = new services.gui.importexport.GitStudyImportExport(jpaApi,
                injector.instanceOf(services.gui.importexport.GitCredentialsResolver.class),
                injector.instanceOf(GitStudySourceIO.class),
                injector.instanceOf(services.gui.importexport.StudyImportService.class),
                injector.instanceOf(services.gui.importexport.StudySourceService.class),
                injector.instanceOf(json.common.DomainJsonMapper.class),
                injector.instanceOf(general.common.StudyLogger.class),
                injector.instanceOf(daos.common.UserDao.class), failingIO, injector.instanceOf(IOUtils.class));
        var sync = new services.gui.importexport.StudySourceSync(jpaApi,
                injector.instanceOf(daos.common.StudyDao.class), injector.instanceOf(daos.common.UserDao.class),
                injector.instanceOf(AuthorizationService.class), failingIO, injector.instanceOf(general.common.StudyLogger.class),
                injector.instanceOf(services.gui.importexport.LocalFolderStudyImportExport.class), gitService);
        var selection = new models.gui.StudySourceSyncProperties();
        selection.setProperties(true);
        selection.setAssets(true);
        assertThrows(exceptions.common.ImportExportException.class, () -> sync.pullFromGit(id, selection));
        assertEquals("Git study", jpaApi.withTransaction(em -> {return em.find(Study.class, id).getTitle();}));
        assertEquals("Git content", Files.readString(assets(id).resolve("index.html")));
        String log = Files.readString(Path.of(Common.getStudyLogsPath(), uuid + ".log"), java.nio.charset.StandardCharsets.ISO_8859_1);
        assertFalse(log.contains("Pulled "));
        noStaging();
        try (var paths = Files.list(Path.of(Common.getStudyAssetsRootPath()))) {
            assertFalse(paths.anyMatch(path -> path.getFileName().toString().startsWith(".study-sync-")));
        }
    }

    @Test
    public void createsStudySavesSourceAndLogsCommitWithoutExportingSettings() throws Exception {
        var body = request();
        ((com.fasterxml.jackson.databind.node.ObjectNode) body.get("source"))
                .put("username", "study-user").put("secret", "private-token-marker");
        var response = api("/jatos/api/v1/studies").post(body);
        assertEquals(response.getBody(), 201, response.getStatus());
        long id = response.asJson().path("data").path("id").asLong();
        assertFalse(response.getBody().contains("private-token-marker"));
        String dirName = jpaApi.withTransaction(em -> {
            Study study = em.find(Study.class, id);
            assertEquals(uuid, study.getUuid());
            assertEquals("Git study", study.getTitle());
            assertEquals(admin.getUsername(), study.getOwner().getUsername());
            var source = study.getStudySource();
            assertEquals(StudySource.Type.GIT, source.getType());
            assertEquals("https://example.org/repo.git", source.getGitUrl());
            assertEquals("main", source.getGitRef());
            assertNotNull(source.getGitCredentials());
            assertFalse(source.getGitCredentials().getEncryptedSecret().contains("private-token-marker"));
            return study.getDirName();
        });
        Path assets = application.injector().instanceOf(IOUtils.class).getStudyAssetsDir(dirName);
        assertEquals("Git content", Files.readString(assets.resolve("index.html")));
        assertFalse(Files.exists(assets.resolve(".git")));
        assertFalse(Files.exists(assets.resolve("README.md")));
        String log = Files.readString(Path.of(Common.getStudyLogsPath(), uuid + ".log"), java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(log.contains(commit));
        assertTrue(log.contains("https://example.org/repo.git"));
        assertFalse(log.contains("private-token-marker"));
        assertEquals(409, api("/jatos/api/v1/studies").post(request()).getStatus());
        noStaging();
    }

    @Test
    public void supportsAssetsOnlyAndPropertiesOnly() throws Exception {
        Files.delete(repository.resolve("study.jas"));
        commit();
        var assetsOnly = api("/jatos/api/v1/studies").post(request());
        assertEquals(assetsOnly.getBody(), 201, assetsOnly.getStatus());
        assertTrue(assetsOnly.asJson().path("message").asText().contains("assets only"));
        Files.delete(repository.resolve("assets/index.html"));
        Files.writeString(repository.resolve("study.jas"), Json.newObject().put("version", "3")
                .set("data", Json.newObject().put("uuid", uuid).put("title", "Properties only")).toString());
        commit();
        var propsOnly = api("/jatos/api/v1/studies").post(request());
        assertEquals(propsOnly.getBody(), 201, propsOnly.getStatus());
        assertTrue(propsOnly.asJson().path("message").asText().contains("properties only"));
        noStaging();
    }

    @Test
    public void validatesBeforeFetchingAndCleansUpInvalidStudy() throws Exception {
        for (String url : new String[]{"file:///tmp/repo", "ssh://example.org/repo", "http://example.org/repo", "https://user:token@example.org/repo"}) {
            var body = request();
            ((com.fasterxml.jackson.databind.node.ObjectNode) body.get("source")).put("gitUrl", url);
            assertEquals(400, api("/jatos/api/v1/studies").post(body).getStatus());
        }
        assertEquals(0, fetches);
        Files.writeString(repository.resolve("study.jas"), "invalid");
        commit();
        assertEquals(400, api("/jatos/api/v1/studies").post(request()).getStatus());
        jpaApi.withTransaction(em -> {
            assertEquals(Long.valueOf(0), em.createQuery("select count(s) from Study s", Long.class).getSingleResult());
        });
        noStaging();
    }

    @Test
    public void rejectsInvalidCreationFieldsBeforeFetching() {
        for (String field : new String[]{"removeCredentials", "unknownField"}) {
            var body = request();
            ((com.fasterxml.jackson.databind.node.ObjectNode) body.get("source")).put(field, true);
            assertEquals(400, api("/jatos/api/v1/studies").post(body).getStatus());
        }
        var body = request();
        ((com.fasterxml.jackson.databind.node.ObjectNode) body.get("source")).put("type", "NONE");
        assertEquals(400, api("/jatos/api/v1/studies").post(body).getStatus());
        assertEquals(0, fetches);
    }

    @Test
    public void usernameWithoutSecretIsRejectedBeforeFetching() {
        for (boolean anonymous : new boolean[]{false, true}) {
            var body = request();
            ((com.fasterxml.jackson.databind.node.ObjectNode) body.get("source"))
                    .put("username", "user").put("anonymous", anonymous);
            var response = api("/jatos/api/v1/studies").post(body);
            assertEquals(400, response.getStatus());
            assertTrue(response.asJson().path("error").path("fields").has("secret"));
        }
        assertEquals(0, fetches);
    }

    @Test
    public void failedCreationRollsBackSourceCredentialsAndAssets() throws Exception {
        var injector = application.injector();
        var sources = org.mockito.Mockito.spy(injector.instanceOf(services.gui.importexport.StudySourceService.class));
        org.mockito.Mockito.doAnswer(call -> {
            call.callRealMethod();
            throw new IllegalStateException("Failure after source configuration");
        }).when(sources).save(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
        var service = new services.gui.importexport.GitStudyImportExport(jpaApi,
                injector.instanceOf(services.gui.importexport.GitCredentialsResolver.class),
                injector.instanceOf(GitStudySourceIO.class),
                injector.instanceOf(services.gui.importexport.StudyImportService.class), sources,
                injector.instanceOf(json.common.DomainJsonMapper.class),
                injector.instanceOf(general.common.StudyLogger.class),
                injector.instanceOf(daos.common.UserDao.class),
                injector.instanceOf(services.gui.importexport.StudySourceSyncIO.class), injector.instanceOf(IOUtils.class));
        var props = new models.gui.StudySourceProperties();
        props.setType(StudySource.Type.GIT);
        props.setGitUrl("https://example.org/repo.git");
        props.setUsername("user");
        props.setSecret("token");
        assertEquals("Failure after source configuration",
                assertThrows(IllegalStateException.class, () -> service.createStudyFromGit(props)).getMessage());
        jpaApi.withTransaction(em -> {
            for (String entity : new String[]{"Study", "StudySource", "GitCredentials"})
                assertEquals(Long.valueOf(0), em.createQuery("select count(e) from " + entity + " e", Long.class).getSingleResult());
        });
        try (var paths = Files.list(Path.of(Common.getStudyAssetsRootPath()))) {assertEquals(0, paths.count());}
        noStaging();
    }

    @Test
    public void viewerCannotCloneAndSessionCreationRequiresCsrf() throws Exception {
        var viewer = createUser("viewer");
        jpaApi.withTransaction(em -> {
            em.find(models.common.User.class, viewer.getUsername()).updateRoles(models.common.User.Role.VIEWER);
        });
        assertEquals(403, api("/jatos/api/v1/studies").token(createApiToken(viewer)).post(request()).getStatus());
        String csrf = application.injector().instanceOf(play.filters.csrf.CSRF.TokenProvider.class).generateToken();
        var request = Helpers.fakeRequest("POST", "/jatos/api/v1/studies")
                .header("Cookie", "PLAY_SESSION=test-session")
                .cookie(play.mvc.Http.Cookie.builder("PLAY_SESSION", "test-session").build())
                .session("username", admin.getUsername())
                .session("signinTime", String.valueOf(System.currentTimeMillis()))
                .session("lastActivityTime", String.valueOf(System.currentTimeMillis()))
                .session("csrfToken", csrf).bodyJson(request());
        assertEquals(403, Helpers.route(application, request).status());
        assertEquals(0, fetches);
        var result = Helpers.route(application, request.header("Csrf-Token", csrf));
        assertEquals(Helpers.contentAsString(result), 201, result.status());
        assertTrue(result.flash().get("success").orElseThrow().contains("Git"));
        noStaging();
    }
}
