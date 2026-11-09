package services.gui;

import exceptions.common.ImportExportException;
import general.common.Common;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class PendingStudyImportStorageTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private final PendingStudyImportStorage pending = new PendingStudyImportStorage();

    private void age(Path directory) throws Exception {
        Files.setLastModifiedTime(directory.resolve("state"), FileTime.from(Instant.now().minus(Duration.ofHours(25))));
    }

    @Test public void expiresOnlyOldInactiveImportsAndRejectsConfirmation() throws Exception {
        try (var config = mockStatic(Common.class)) {
            config.when(Common::getTmpPath).thenReturn(temporary.getRoot().toString());
            Path old = pending.prepare(p -> p).getParent();
            Path fresh = pending.prepare(p -> p).getParent();
            Path unrelated = Files.createDirectory(temporary.getRoot().toPath().resolve("unrelated"));
            age(old);
            assertThrows(ImportExportException.class, () -> pending.use(old, p -> p));
            pending.cleanupExpired();
            assertFalse(Files.exists(old));
            assertTrue(Files.exists(fresh));
            assertTrue(Files.exists(unrelated));
            assertNull(pending.resolve("../other"));
        }
    }

    @Test public void activeImportCannotBeCancelledOrExpired() throws Exception {
        try (var config = mockStatic(Common.class)) {
            config.when(Common::getTmpPath).thenReturn(temporary.getRoot().toString());
            Path directory = pending.prepare(p -> p).getParent();
            pending.use(directory, p -> {
                try { age(directory); } catch (Exception e) { throw new RuntimeException(e); }
                pending.cleanupExpired();
                assertTrue(Files.exists(directory));
                assertThrows(ImportExportException.class, () -> pending.remove(directory));
                return null;
            });
            pending.cleanupExpired();
            assertFalse(Files.exists(directory));
        }
    }

    @Test public void competingOperationsDoNotReleaseNativeLock() throws Exception {
        // Probe from another JVM: a same-JVM tryLock would only check Java's overlapping-lock table.
        Path probe = temporary.getRoot().toPath().resolve("LockProbe.java");
        Files.writeString(probe, """
                import java.nio.channels.*;
                import java.nio.file.*;
                class LockProbe {
                    public static void main(String[] args) throws Exception {
                        try (var channel = FileChannel.open(Path.of(args[0]), StandardOpenOption.WRITE);
                             var lock = channel.tryLock()) {
                            System.out.println(lock == null ? "LOCKED" : "AVAILABLE");
                        }
                    }
                }
                """);
        try (var config = mockStatic(Common.class)) {
            config.when(Common::getTmpPath).thenReturn(temporary.getRoot().toString());
            Path directory = pending.prepare(p -> p).getParent();
            PendingStudyImportStorage other = new PendingStudyImportStorage();
            pending.use(directory, p -> {
                try {
                    other.cleanupExpired();
                    assertThrows(ImportExportException.class, () -> other.remove(directory));
                    assertThrows(ImportExportException.class, () -> other.use(directory, path -> path));
                    assertEquals("LOCKED", probeLock(probe, directory));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return null;
            });
            assertEquals("AVAILABLE", probeLock(probe, directory));
            other.remove(directory);
        }
    }

    private String probeLock(Path probe, Path directory) throws Exception {
        String javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process process = new ProcessBuilder(javaExecutable, probe.toString(), directory.resolve("state").toString())
                .redirectErrorStream(true).start();
        try {
            assertTrue("Lock probe timed out", process.waitFor(30, TimeUnit.SECONDS));
            String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
            assertEquals(output, 0, process.exitValue());
            return output;
        } finally {
            process.destroyForcibly();
        }
    }

    @Test public void failedConfirmationReleasesLocalClaim() throws Exception {
        try (var config = mockStatic(Common.class)) {
            config.when(Common::getTmpPath).thenReturn(temporary.getRoot().toString());
            Path directory = pending.prepare(p -> p).getParent();
            assertThrows(IllegalStateException.class,
                    () -> pending.use(directory, p -> { throw new IllegalStateException(); }));
            pending.remove(directory);
            assertFalse(Files.exists(directory));
        }
    }

    @Test public void cleansFailedPreparationAndCancellationIsIdempotent() throws Exception {
        try (var config = mockStatic(Common.class)) {
            config.when(Common::getTmpPath).thenReturn(temporary.getRoot().toString());
            assertThrows(IllegalStateException.class, () -> pending.prepare(p -> { throw new IllegalStateException(); }));
            try (var paths = Files.list(temporary.getRoot().toPath())) { assertEquals(0, paths.count()); }
            Path directory = pending.prepare(p -> p).getParent();
            pending.remove(directory);
            pending.remove(directory);
            assertFalse(Files.exists(directory));
        }
    }

    @Test public void preparationStartsExpiryAfterFinishing() throws Exception {
        try (var config = mockStatic(Common.class)) {
            config.when(Common::getTmpPath).thenReturn(temporary.getRoot().toString());
            Path directory = pending.prepare(p -> {
                try { age(p.getParent()); } catch (Exception e) { throw new RuntimeException(e); }
                pending.cleanupExpired();
                assertTrue(Files.exists(p.getParent()));
                return p.getParent();
            });
            pending.cleanupExpired();
            assertTrue(Files.exists(directory));
        }
    }
}
