package services.gui.importexport;

import exceptions.common.BadRequestException;
import exceptions.common.FieldValidationException;
import general.common.Common;
import models.common.User;
import models.gui.StudySourceProperties;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.URIish;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import utils.common.GitCredentialEncryption;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

public class GitStudySourceIOTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();
    private Git git;
    private Path repository;
    private Path tmp;
    private GitStudySourceIO io;
    private final String url = "https://example.org/repo.git";

    @Before
    public void setup() throws Exception {
        repository = temporary.newFolder("repository").toPath();
        tmp = temporary.newFolder("tmp").toPath();
        git = Git.init().setDirectory(repository.toFile()).setInitialBranch("main").call();
        Files.createDirectory(repository.resolve("assets"));
        Files.writeString(repository.resolve("assets/index.html"), "first");
        Files.writeString(repository.resolve("study.jas"), "properties");
        Files.writeString(repository.resolve("README"), "ignored");
        commit();
        io = new GitStudySourceIO() {
            @Override
            protected Transport openTransport(Repository local, String url) throws IOException {
                try {return Transport.open(local, new URIish(repository.toUri().toString()));} catch (
                        java.net.URISyntaxException e) {throw new IOException(e);}
            }
        };
    }

    @After
    public void close() {
        if (git != null) git.close();
    }

    private String commit() throws Exception {
        git.add().addFilepattern(".").call();
        git.add().addFilepattern(".").setUpdate(true).call();
        return git.commit().setMessage("Snapshot").setAuthor("Test", "test@example.org")
                .setCommitter("Test", "test@example.org").call().name();
    }

    private GitCredentialsResolver.ResolvedCredentials anonymous() {
        var props = new StudySourceProperties();
        props.setGitUrl(url);
        props.setAnonymous(true);
        return new GitCredentialsResolver(mock(GitCredentialEncryption.class)).forStudyCreation(props, new User("user", "User", "u@example.org"));
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private org.mockito.MockedStatic<Common> configuration() {
        var common = mockStatic(Common.class);
        common.when(Common::getTmpPath).thenReturn(tmp.toString());
        common.when(Common::getStudySourcesGitTimeout).thenReturn(java.time.Duration.ofSeconds(30));
        common.when(Common::getStudySourcesGitIdentityName).thenReturn("JATOS");
        common.when(Common::getStudySourcesGitIdentityEmail).thenReturn("noreply@jatos.org");
        return common;
    }

    private void noStaging() throws IOException {
        try (var files = Files.list(tmp)) {assertEquals(0, files.count());}
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Test
    public void usesConfiguredIdentityAndOptionalUserDetails() throws Exception {
        try (var common = configuration(); var access = anonymous()) {
            common.when(Common::getStudySourcesGitIdentityName).thenReturn("Configured");
            common.when(Common::getStudySourcesGitIdentityEmail).thenReturn("configured@example.org");
            User[] users = {new User("user", "User", "u@example.org"),
                    new User("user", "User", "u@example.org"),
                    new User("user", " ", "u@example.org"),
                    new User("user", "User", null), null};
            String[] names = {"Configured", "User", "Configured", "User", "Configured"};
            String[] emails = {"configured@example.org", "u@example.org", "u@example.org",
                    "configured@example.org", "configured@example.org"};
            for (int i = 0; i < users.length; i++) {
                common.when(Common::isStudySourcesGitIdentityUseUserDetails).thenReturn(i != 0);
                try (var destination = io.preparePush(url, "main", access)) {
                    Path staged = Files.createDirectory(destination.fetchedStudy().content().getDirectory().resolve("export"));
                    Files.writeString(staged.resolve("study.jas"), "properties " + i);
                    var result = io.push(destination, url, access, staged, true, false, users[i]);
                    try (var walk = new org.eclipse.jgit.revwalk.RevWalk(git.getRepository())) {
                        var commit = walk.parseCommit(org.eclipse.jgit.lib.ObjectId.fromString(result.commitId()));
                        assertEquals(names[i], commit.getAuthorIdent().getName());
                        assertEquals(emails[i], commit.getAuthorIdent().getEmailAddress());
                        assertEquals(names[i], commit.getCommitterIdent().getName());
                        assertEquals(emails[i], commit.getCommitterIdent().getEmailAddress());
                    }
                }
            }
        }
    }

    @Test
    public void pushesSelectedContentPreservesOtherFilesAndUsesFixedIdentity() throws Exception {
        try (var ignored = configuration(); var access = anonymous()) {
            for (String ref : new String[]{"", "main", "refs/heads/main"}) {
                try (var destination = io.preparePush(url, ref, access)) {
                    Path staged = Files.createDirectory(destination.fetchedStudy().content().getDirectory().resolve("export"));
                    Files.createDirectory(staged.resolve("assets"));
                    Files.writeString(staged.resolve("assets/new.html"), "new");
                    var result = io.push(destination, url, access, staged, false, true, null);
                    try (var walk = new org.eclipse.jgit.revwalk.RevWalk(git.getRepository())) {
                        var commit = walk.parseCommit(org.eclipse.jgit.lib.ObjectId.fromString(result.commitId()));
                        assertEquals("JATOS", commit.getAuthorIdent().getName());
                        assertEquals("noreply@jatos.org", commit.getCommitterIdent().getEmailAddress());
                        assertEquals("Update study content", commit.getFullMessage());
                        assertNotNull(org.eclipse.jgit.treewalk.TreeWalk.forPath(git.getRepository(), "README", commit.getTree()));
                        assertNotNull(org.eclipse.jgit.treewalk.TreeWalk.forPath(git.getRepository(), "study.jas", commit.getTree()));
                        assertNull(org.eclipse.jgit.treewalk.TreeWalk.forPath(git.getRepository(), "assets/index.html", commit.getTree()));
                    }
                    assertEquals(ref.isEmpty(), result.changed());
                }
                noStaging();
            }
        }
    }

    @Test
    public void propertiesOnlyPreservesAssetsAndEmptyAssetsRemovesThem() throws Exception {
        try (var ignored = configuration(); var access = anonymous()) {
            try (var destination = io.preparePush(url, "main", access)) {
                Path staged = Files.createDirectory(destination.fetchedStudy().content().getDirectory().resolve("export"));
                Files.writeString(staged.resolve("study.jas"), "updated properties");
                io.push(destination, url, access, staged, true, false, null);
            }
            try (var fetched = io.stage(url, "main", access)) {
                assertEquals("updated properties", Files.readString(fetched.content().getProperties().orElseThrow()));
                assertEquals("first", Files.readString(fetched.content().getAssets().orElseThrow().resolve("index.html")));
            }
            try (var destination = io.preparePush(url, "main", access)) {
                Path staged = Files.createDirectory(destination.fetchedStudy().content().getDirectory().resolve("export"));
                Files.createDirectory(staged.resolve("assets"));
                assertTrue(io.push(destination, url, access, staged, false, true, null).changed());
            }
            try (var fetched = io.stage(url, "main", access)) {
                assertTrue(fetched.content().getAssets().isEmpty());
                assertEquals("updated properties", Files.readString(fetched.content().getProperties().orElseThrow()));
            }
            noStaging();
        }
    }

    @Test
    public void rejectsTagAndConcurrentRemoteChangeWithoutForcePush() throws Exception {
        git.tag().setName("release").setAnnotated(false).call();
        try (var ignored = configuration(); var access = anonymous()) {
            assertThrows(FieldValidationException.class, () -> io.preparePush(url, "release", access));
            noStaging();
            try (var destination = io.preparePush(url, "main", access)) {
                Path staged = Files.createDirectory(destination.fetchedStudy().content().getDirectory().resolve("export"));
                Files.writeString(staged.resolve("study.jas"), "new properties");
                Files.writeString(repository.resolve("README"), "concurrent update");
                String remote = commit();
                var error = assertThrows(exceptions.common.ConflictException.class,
                        () -> io.push(destination, url, access, staged, true, false, null));
                assertTrue(error.getMessage().contains("Retry"));
                assertEquals(remote, git.getRepository().resolve("refs/heads/main").name());
            }
            noStaging();
        }
    }

    @Test
    public void pushAuthenticationFailureIsSanitizedAndTemporaryFilesAreRemoved() throws Exception {
        try (var ignored = configuration(); var access = anonymous()) {
            var failing = new GitStudySourceIO() {
                @Override
                protected Transport openTransport(Repository local, String value) throws IOException {
                    throw new org.eclipse.jgit.errors.TransportException("secret-marker authentication failed");
                }
            };
            try (var destination = io.preparePush(url, "main", access)) {
                Path staged = Files.createDirectory(destination.fetchedStudy().content().getDirectory().resolve("export"));
                Files.writeString(staged.resolve("study.jas"), "new properties");
                var error = assertThrows(BadRequestException.class,
                        () -> failing.push(destination, url, access, staged, true, false, null));
                assertFalse(error.getMessage().contains("secret-marker"));
            }
            assertThrows(BadRequestException.class, () -> failing.preparePush(url, "main", access));
            noStaging();
        }
    }

    @Test
    public void stagesDefaultBranchNamedBranchAndAnnotatedTag() throws Exception {
        String first = git.getRepository().resolve("HEAD").name();
        git.tag().setName("release").setAnnotated(true).setMessage("Release").setTagger(new org.eclipse.jgit.lib.PersonIdent("Test", "test@example.org")).call();
        git.branchCreate().setName("feature").call();
        Files.writeString(repository.resolve("assets/index.html"), "second");
        String second = commit();
        try (var ignored = configuration(); var access = anonymous()) {
            for (String ref : new String[]{"", "main", "refs/heads/main", "feature", "release", "refs/tags/release"}) {
                try (var fetched = io.stage(url, ref, access)) {
                    boolean old = ref.equals("feature") || ref.contains("release");
                    assertEquals(old ? first : second, fetched.commitId());
                    assertEquals(old ? "first" : "second", Files.readString(fetched.content().getAssets().orElseThrow().resolve("index.html")));
                    assertEquals("properties", Files.readString(fetched.content().getProperties().orElseThrow()));
                    assertFalse(Files.exists(fetched.content().getAssets().orElseThrow().resolve(".git")));
                    assertFalse(Files.exists(fetched.content().getAssets().orElseThrow().resolve("README")));
                }
                noStaging();
            }
        }
    }

    @Test
    public void rejectsMissingAmbiguousAndCommitRefs() throws Exception {
        git.tag().setName("main").setAnnotated(false).call();
        try (var ignored = configuration(); var access = anonymous()) {
            for (String ref : new String[]{"missing", "main", git.getRepository().resolve("HEAD").name()})
                assertThrows(FieldValidationException.class, () -> io.stage(url, ref, access));
            noStaging();
        }
    }

    @Test
    public void rejectsMultiplePropertiesMissingContentAndSymlinks() throws Exception {
        try (var ignored = configuration(); var access = anonymous()) {
            Files.writeString(repository.resolve("second.jas"), "other");
            commit();
            assertThrows(BadRequestException.class, () -> io.stage(url, "", access));
            noStaging();
            Files.delete(repository.resolve("second.jas"));
            Files.delete(repository.resolve("study.jas"));
            Files.delete(repository.resolve("assets/index.html"));
            commit();
            assertThrows(BadRequestException.class, () -> io.stage(url, "", access));
            noStaging();
            Files.createSymbolicLink(repository.resolve("assets/link"), Path.of("../../outside"));
            commit();
            assertThrows(BadRequestException.class, () -> io.stage(url, "", access));
            noStaging();
        }
    }

    @Test
    public void transportFailuresAndTimeoutsCleanUpWithoutExposingServerDetails() throws Exception {
        try (var ignored = configuration(); var access = anonymous()) {
            for (boolean timeout : new boolean[]{false, true}) {
                var failing = new GitStudySourceIO() {
                    @Override
                    protected Transport openTransport(Repository local, String value) throws IOException {
                        throw new org.eclipse.jgit.errors.TransportException("remote-secret-marker",
                                timeout ? new java.net.SocketTimeoutException("timeout") : new IOException("unavailable"));
                    }
                };
                var error = assertThrows(BadRequestException.class, () -> failing.stage(url, "", access));
                assertFalse(error.getMessage().contains("remote-secret-marker"));
                assertEquals(timeout, error.getMessage().contains("timed out"));
                noStaging();
            }
        }
    }
}
