package services.gui;

import services.gui.importexport.LocalFolderStudySourceIO;
import utils.common.IOUtils;
import exceptions.common.BadRequestException;

import models.gui.StagedStudySource;

import general.common.Common;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.mockStatic;

public class LocalFolderStudySourceIOTest {

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();
    private final LocalFolderStudySourceIO io = new LocalFolderStudySourceIO(new IOUtils());
    private Path source;
    private Path staging;
    private MockedStatic<Common> common;

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Before
    public void setUp() throws IOException {
        source = temporary.newFolder("source").toPath();
        staging = temporary.newFolder("staging").toPath();
        common = mockStatic(Common.class);
        common.when(Common::getTmpPath).thenReturn(staging.toString());
    }

    @After
    public void tearDown() {
        if (common != null) common.close();
    }

    private void createSymbolicLink(Path link, Path target) {
        try {Files.createSymbolicLink(link, target);} catch (UnsupportedOperationException | IOException e) {
            org.junit.Assume.assumeNoException(e);
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private StagedStudySource stageWithRoots(String folder, List<String> roots) throws IOException {
        common.when(Common::getStudySourcesLocalFolderAllowedRoots).thenReturn(roots);
        return io.stage(folder);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Test
    public void rechecksServerModeAndRootsForEveryIngestion() throws IOException {
        Files.createDirectory(source.resolve("assets"));
        common.when(Common::isServer).thenReturn(true);
        assertThrows(BadRequestException.class, () -> stageWithRoots(source.toString(), List.of()));
        common.when(Common::isServer).thenReturn(false);
        try (var staged = stageWithRoots(source.toString(), List.of())) {
            assertTrue(Files.isDirectory(staged.getDirectory()));
        }
        for (boolean server : new boolean[]{false, true}) {
            common.when(Common::isServer).thenReturn(server);
            try (var staged = stage()) {
                assertTrue(Files.isDirectory(staged.getDirectory()));
            }
        }
        assertStagingEmpty();
    }

    private StagedStudySource stage() throws IOException {
        return stageWithRoots(source.toString(), List.of(source.toString()));
    }

    private void assertStagingEmpty() throws IOException {
        try (var entries = Files.list(staging)) {assertEquals(0, entries.count());}
    }

    @Test
    public void copiesOnlyAllowedContentAndLeavesSourceUnchanged() throws IOException {
        Files.writeString(source.resolve("study.jas"), "properties");
        Files.writeString(source.resolve("README.md"), "ignored");
        Files.createDirectory(source.resolve(".git"));
        Path assets = Files.createDirectories(source.resolve("assets/sub"));
        Files.write(assets.resolve("image.bin"), new byte[]{0, 1, -1});
        Files.createDirectories(source.resolve("assets/.git"));
        Files.writeString(source.resolve("assets/.git/config"), "ignored");
        Path result;
        try (StagedStudySource staged = stage()) {
            result = staged.getDirectory();
            assertEquals("properties", Files.readString(staged.getProperties().orElseThrow()));
            assertArrayEquals(new byte[]{0, 1, -1}, Files.readAllBytes(staged.getAssets().orElseThrow().resolve("sub/image.bin")));
            assertFalse(Files.exists(result.resolve("README.md")));
            assertFalse(Files.exists(result.resolve(".git")));
            assertFalse(Files.exists(result.resolve("assets/.git")));
            Files.writeString(staged.getProperties().orElseThrow(), "changed staged copy");
        }
        assertFalse(Files.exists(result));
        assertEquals("properties", Files.readString(source.resolve("study.jas")));
        assertEquals("ignored", Files.readString(source.resolve("assets/.git/config")));
        assertStagingEmpty();
    }

    @Test
    public void missingContentAndEmptyAssetsRemainDistinct() throws IOException {
        Files.writeString(source.resolve("study.jas"), "properties");
        try (var staged = stage()) {
            assertTrue(staged.getProperties().isPresent());
            assertTrue(staged.getAssets().isEmpty());
        }
        Files.delete(source.resolve("study.jas"));
        Files.createDirectory(source.resolve("assets"));
        try (var staged = stage()) {
            assertTrue(staged.getProperties().isEmpty());
            assertTrue(Files.isDirectory(staged.getAssets().orElseThrow()));
            try (var entries = Files.list(staged.getAssets().orElseThrow())) {assertEquals(0, entries.count());}
        }
        assertStagingEmpty();
    }

    @Test
    public void rejectsSourcesWithoutPropertiesOrAssetsAndCleansUp() throws IOException {
        IOException error = assertThrows(IOException.class, this::stage);
        assertEquals("The source folder must contain a .jas file or an assets/ directory, or both.", error.getMessage());
        assertStagingEmpty();
        Files.writeString(source.resolve("README.md"), "ignored");
        Files.createDirectory(source.resolve(".git"));
        assertThrows(IOException.class, this::stage);
        assertTrue(Files.exists(source.resolve("README.md")));
        assertStagingEmpty();
    }

    @Test
    public void multiplePropertiesFilesFailAndCleanUp() throws IOException {
        Files.writeString(source.resolve("one.jas"), "one");
        Files.writeString(source.resolve("two.jas"), "two");
        assertThrows(IOException.class, this::stage);
        assertStagingEmpty();
    }

    @Test
    public void rejectsLinkedFilesAndDirectoriesIncludingDanglingLinks() throws IOException {
        Path outside = temporary.newFolder("outside").toPath();
        Files.writeString(outside.resolve("secret"), "must not be copied");
        createSymbolicLink(source.resolve("study.jas"), outside.resolve("secret"));
        assertThrows(IOException.class, this::stage);
        Files.delete(source.resolve("study.jas"));
        createSymbolicLink(source.resolve("assets"), outside);
        assertThrows(IOException.class, this::stage);
        Files.delete(source.resolve("assets"));
        Files.createDirectory(source.resolve("assets"));
        createSymbolicLink(source.resolve("assets/link"), outside);
        assertThrows(IOException.class, this::stage);
        Files.delete(source.resolve("assets/link"));
        createSymbolicLink(source.resolve("assets/link"), outside.resolve("missing"));
        assertThrows(IOException.class, this::stage);
        assertEquals("must not be copied", Files.readString(outside.resolve("secret")));
        assertStagingEmpty();
    }

    @Test
    public void validatesAllowedRootsAndSourceAccessAtReadTime() throws IOException {
        Files.writeString(source.resolve("study.jas"), "properties");
        Path elsewhere = temporary.newFolder("elsewhere").toPath();
        assertThrows(IOException.class, () -> stageWithRoots(source.toString(), List.of(elsewhere.toString())));
        Path link = elsewhere.resolve("link");
        createSymbolicLink(link, source);
        assertThrows(IOException.class, () -> stageWithRoots(link.toString(), List.of(elsewhere.toString())));
        try (var staged = stageWithRoots(link.toString(), List.of(source.toString()))) {
            assertTrue(staged.getAssets().isEmpty());
        }
        assertThrows(IOException.class, () -> stageWithRoots("relative", List.of()));
        Files.delete(source.resolve("study.jas"));
        Files.delete(source);
        assertThrows(IOException.class, this::stage);
        assertStagingEmpty();
    }

    @Test
    public void invalidAssetsAndPropertiesTypesFailWithoutLeakingTemporaryFiles() throws IOException {
        Files.writeString(source.resolve("assets"), "not a directory");
        assertThrows(IOException.class, this::stage);
        Files.delete(source.resolve("assets"));
        Files.createDirectory(source.resolve("study.jas"));
        assertThrows(IOException.class, this::stage);
        assertStagingEmpty();
    }

    @Test
    public void stagesAreIndependentAndCloseIsIdempotent() throws IOException {
        Files.createDirectory(source.resolve("assets"));
        try (var first = stageWithRoots(source.toString(), List.of()); var second = stage()) {
            assertNotEquals(first.getDirectory(), second.getDirectory());
            first.close();
            assertTrue(Files.exists(second.getDirectory()));
        }
        assertStagingEmpty();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Test
    public void missingTemporaryDirectoryDoesNotModifySource() {
        Path missing = source.resolve("assets/tmp");
        common.when(Common::getTmpPath).thenReturn(missing.toString());
        assertThrows(IOException.class, this::stage);
        assertFalse(Files.exists(source.resolve("assets")));
    }

    @Test
    public void ignoresUnreadableGitDirectory() throws IOException {
        Path git = Files.createDirectories(source.resolve("assets/.git"));
        assumeTrue(Files.getFileStore(git).supportsFileAttributeView("posix"));
        var permissions = Files.getPosixFilePermissions(git);
        try {
            Files.setPosixFilePermissions(git, java.util.Set.of());
            try (var staged = stage()) {
                assertFalse(Files.exists(staged.getAssets().orElseThrow().resolve(".git")));
            }
            assertStagingEmpty();
        } finally {
            Files.setPosixFilePermissions(git, permissions);
        }
    }

    @Test
    public void unreadableFileFailsAndCleansUp() throws IOException {
        Path file = Files.writeString(source.resolve("study.jas"), "private");
        assumeTrue(Files.getFileStore(file).supportsFileAttributeView("posix"));
        var permissions = Files.getPosixFilePermissions(file);
        try {
            Files.setPosixFilePermissions(file, java.util.Set.of());
            // Privileged test processes can still read files with mode 000.
            assumeTrue(!Files.isReadable(file));
            assertThrows(IOException.class, this::stage);
            assertStagingEmpty();
        } finally {
            Files.setPosixFilePermissions(file, permissions);
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Test
    public void rejectsTemporaryStorageInsideAssets() throws IOException {
        Path assets = Files.createDirectory(source.resolve("assets"));
        common.when(Common::getTmpPath).thenReturn(assets.toString());
        assertThrows(IOException.class, this::stage);
        try (var entries = Files.list(assets)) {assertEquals(0, entries.count());}
    }
}
