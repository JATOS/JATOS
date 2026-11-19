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
import org.junit.*;
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

import static org.junit.Assert.*;
import static play.inject.Bindings.bind;
import static auth.gui.AuthAction.SIGNEDIN_USER;

public class GitStudyCreationIntegrationTest extends JatosTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private Git git;
    private Path repository;
    private String uuid;
    private String commit;
    private int fetches;

    @Override @Before public void startApp() {
        try {
            repository = temporary.newFolder("repo").toPath();
            git = Git.init().setDirectory(repository.toFile()).setInitialBranch("main").call();
        } catch (Exception e) { throw new RuntimeException(e); }
        var io = new GitStudySourceIO() {
            @Override protected Transport openTransport(Repository local, String url) throws IOException {
                fetches++;
                try { return Transport.open(local, new URIish(repository.toUri().toString())); }
                catch (java.net.URISyntaxException e) { throw new IOException(e); }
            }
        };
        application = new GuiceApplicationBuilder().overrides(bind(GitStudySourceIO.class).toInstance(io)).build();
        Helpers.start(application);
        jpaApi = application.injector().instanceOf(JpaTransactions.class);
        admin = getAdmin(); apiToken = createApiToken(admin);
        Context.setCurrent(new Context(Helpers.fakeRequest().build()));
        Context.current().args().put(SIGNEDIN_USER, admin);
    }

    @Before public void content() throws Exception {
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

    @After public void closeRepository() { if (git != null) git.close(); }

    private com.fasterxml.jackson.databind.node.ObjectNode request() {
        return Json.newObject().set("source", Json.newObject().put("type", "GIT")
                .put("gitUrl", "https://example.org/repo.git").put("gitRef", "main"));
    }

    private void noStaging() throws Exception {
        try (var paths = Files.list(IOUtils.tmpDir())) {
            assertFalse(paths.anyMatch(p -> p.getFileName().toString().startsWith("study-git-")));
        }
    }

    @Test public void createsStudySavesSourceAndLogsCommitWithoutExportingSettings() throws Exception {
        var body = request(); ((com.fasterxml.jackson.databind.node.ObjectNode) body.get("source"))
                .put("username", "study-user").put("secret", "private-token-marker");
        var response = api("/jatos/api/v1/studies").post(body);
        assertEquals(response.getBody(), 201, response.getStatus());
        long id = response.asJson().path("data").path("id").asLong();
        assertFalse(response.getBody().contains("private-token-marker"));
        String dirName = jpaApi.withTransaction(em -> {
            Study study = em.find(Study.class, id);
            assertEquals(uuid, study.getUuid()); assertEquals("Git study", study.getTitle());
            assertEquals(admin.getUsername(), study.getOwner().getUsername());
            var source = study.getStudySource();
            assertEquals(StudySource.Type.GIT, source.getType());
            assertEquals("https://example.org/repo.git", source.getGitUrl()); assertEquals("main", source.getGitRef());
            assertNotNull(source.getGitCredentials());
            assertFalse(source.getGitCredentials().getEncryptedSecret().contains("private-token-marker"));
            return study.getDirName();
        });
        Path assets = application.injector().instanceOf(IOUtils.class).getStudyAssetsDir(dirName);
        assertEquals("Git content", Files.readString(assets.resolve("index.html")));
        assertFalse(Files.exists(assets.resolve(".git"))); assertFalse(Files.exists(assets.resolve("README.md")));
        String log = Files.readString(Path.of(Common.getStudyLogsPath(), uuid + ".log"), java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(log.contains(commit)); assertTrue(log.contains("https://example.org/repo.git"));
        assertFalse(log.contains("private-token-marker"));
        assertEquals(409, api("/jatos/api/v1/studies").post(request()).getStatus());
        noStaging();
    }

    @Test public void supportsAssetsOnlyAndPropertiesOnly() throws Exception {
        Files.delete(repository.resolve("study.jas")); commit();
        var assetsOnly = api("/jatos/api/v1/studies").post(request());
        assertEquals(assetsOnly.getBody(), 201, assetsOnly.getStatus());
        assertTrue(assetsOnly.asJson().path("message").asText().contains("assets only"));
        Files.delete(repository.resolve("assets/index.html"));
        Files.writeString(repository.resolve("study.jas"), Json.newObject().put("version", "3")
                .set("data", Json.newObject().put("uuid", uuid).put("title", "Properties only")).toString()); commit();
        var propsOnly = api("/jatos/api/v1/studies").post(request());
        assertEquals(propsOnly.getBody(), 201, propsOnly.getStatus());
        assertTrue(propsOnly.asJson().path("message").asText().contains("properties only"));
        noStaging();
    }

    @Test public void validatesBeforeFetchingAndCleansUpInvalidStudy() throws Exception {
        for (String url : new String[]{"file:///tmp/repo", "ssh://example.org/repo", "http://example.org/repo", "https://user:token@example.org/repo"}) {
            var body = request(); ((com.fasterxml.jackson.databind.node.ObjectNode) body.get("source")).put("gitUrl", url);
            assertEquals(400, api("/jatos/api/v1/studies").post(body).getStatus());
        }
        assertEquals(0, fetches);
        Files.writeString(repository.resolve("study.jas"), "invalid"); commit();
        assertEquals(400, api("/jatos/api/v1/studies").post(request()).getStatus());
        jpaApi.withTransaction(em -> { assertEquals(Long.valueOf(0), em.createQuery("select count(s) from Study s", Long.class).getSingleResult()); });
        noStaging();
    }

    @Test public void rejectsInvalidCreationFieldsBeforeFetching() {
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

    @Test public void usernameWithoutSecretIsRejectedBeforeFetching() {
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

    @Test public void failedCreationRollsBackSourceCredentialsAndAssets() throws Exception {
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
                injector.instanceOf(daos.common.UserDao.class));
        var props = new models.gui.StudySourceProperties(); props.setType(StudySource.Type.GIT);
        props.setGitUrl("https://example.org/repo.git"); props.setUsername("user"); props.setSecret("token");
        assertEquals("Failure after source configuration",
                assertThrows(IllegalStateException.class, () -> service.createStudyFromGit(props)).getMessage());
        jpaApi.withTransaction(em -> {
            for (String entity : new String[]{"Study", "StudySource", "GitCredentials"})
                assertEquals(Long.valueOf(0), em.createQuery("select count(e) from " + entity + " e", Long.class).getSingleResult());
        });
        try (var paths = Files.list(Path.of(Common.getStudyAssetsRootPath()))) { assertEquals(0, paths.count()); }
        noStaging();
    }

    @Test public void viewerCannotCloneAndSessionCreationRequiresCsrf() throws Exception {
        var viewer = createUser("viewer");
        jpaApi.withTransaction(em -> { em.find(models.common.User.class, viewer.getUsername()).updateRoles(models.common.User.Role.VIEWER); });
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
