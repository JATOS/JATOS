package services.gui;

import general.common.Common;
import utils.common.IOUtils;
import general.common.JpaTransactions;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import services.gui.importexport.StudySourceSyncIO;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.mock;

public class StudySourceSyncIOTest {

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void failedCompetingLockDoesNotReleaseNativeLock() throws Exception {
        Path probe = temporary.getRoot().toPath().resolve("LockProbe.java");
        Files.writeString(probe, """
                import java.nio.channels.*;
                import java.nio.file.*;
                class LockProbe {
                    public static void main(String[] args) throws Exception {
                        try (var channel = FileChannel.open(Path.of(args[0]), StandardOpenOption.WRITE);
                             var lock = channel.tryLock(0, 1, false)) {
                            System.out.println(lock == null ? "LOCKED" : "AVAILABLE");
                        }
                    }
                }
                """);
        try (var common = mockStatic(Common.class)) {
            common.when(Common::getTmpPath).thenReturn(temporary.getRoot().toString());
            try (var lock = new StudySourceSyncIO(mock(JpaTransactions.class), new IOUtils()).lock(42)) {
                assertThrows(exceptions.common.HttpException.class, () -> new StudySourceSyncIO(mock(JpaTransactions.class), new IOUtils()).lock(42));
                assertEquals("LOCKED", probe(probe, lock.file()));
            }
            assertEquals("AVAILABLE", probe(probe, temporary.getRoot().toPath().resolve("study-sync-locks/42.lock")));
        }
    }

    @Test
    public void rejectsOverlappingAndUnsafeAssetDestinations() throws Exception {
        Path root = temporary.newFolder("assets-root").toPath();
        Path source = temporary.newFolder("source").toPath();
        Files.createDirectory(root.resolve("study"));
        try (var common = mockStatic(Common.class)) {
            common.when(Common::getStudyAssetsRootPath).thenReturn(root.toString());
            StudySourceSyncIO io = new StudySourceSyncIO(mock(JpaTransactions.class), new IOUtils());
            io.checkSourceOutsideAssets(source.toString(), "study");
            assertThrows(exceptions.common.BadRequestException.class,
                    () -> io.checkSourceOutsideAssets(root.resolve("study").toString(), "study"));
            assertThrows(java.io.IOException.class, () -> io.checkSourceOutsideAssets(source.toString(), ".."));
            assertThrows(java.io.IOException.class, () -> io.checkSourceOutsideAssets(source.toString(), "../outside"));
        }
    }

    private String probe(Path program, Path file) throws Exception {
        var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                program.toString(), file.toString()).redirectErrorStream(true).start();
        try {
            assertTrue(process.waitFor(30, TimeUnit.SECONDS));
            String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
            assertEquals(output, 0, process.exitValue());
            return output;
        } finally {process.destroyForcibly();}
    }
}
