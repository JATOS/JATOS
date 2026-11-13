package services.gui.importexport;

import exceptions.common.ImportExportException;
import exceptions.common.JatosException;
import play.Logger;
import utils.common.IOUtils;

import javax.inject.Singleton;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static general.common.ApiEnvelope.ErrorCode.IO_ERROR;

/**
 * Manages temporary study archive uploads between preparation and confirmation. It handles their lifetime and prevents
 * confirmation and cleanup from using the same upload simultaneously. It doesn’t validate or import studies itself;
 * callers provide those operations as callbacks. The usual lifecycle is prepare → wait for confirmation → use → remove
 * or prepare → wait for confirmation → cancel → remove. In case of abandonment, cleanupExpired is called by a
 * scheduler.
 */
@Singleton
public class StudyArchiveImportStorage {

    private static final Logger.ALogger LOGGER = Logger.of(StudyArchiveImportStorage.class);

    // JVM-wide: closing a second channel to the same file can release another channel's native lock.
    private static final Set<Path> ACTIVE_IMPORTS = ConcurrentHashMap.newKeySet();

    private static final String DIR_PREFIX = "study-import-";
    private static final String STATE_FILE_NAME = "state";
    private static final Duration EXPIRY = Duration.ofHours(24);
    private static final String EXPIRED_ERROR_MSG = "The pending import has expired. Upload the study archive again.";

    /**
     * Creates the temporary directory and state file, acquires a lock, and calls action with the "content/" path. The
     * caller extracts and validates the archive there. On success, it marks the import ready and starts the 24-hour
     * expiry period. On failure, it attempts cleanup and propagates the error.
     */
    public <T> T prepare(Function<Path, T> action) {
        Path directory;
        try {
            directory = Files.createDirectory(IOUtils.tmpDir().resolve(DIR_PREFIX + UUID.randomUUID()));
            Files.createFile(directory.resolve(STATE_FILE_NAME));
        } catch (IOException e) {
            throw new JatosException("Could not create temporary study import storage.", e, IO_ERROR);
        }
        try (ImportClaim ignored1 = claim(directory);
             FileChannel channel = openState(directory); FileLock ignored = channel.lock()) {
            T result = action.apply(directory.resolve("content"));
            //noinspection ResultOfMethodCallIgnored
            channel.write(ByteBuffer.wrap(new byte[]{1}));
            Files.setLastModifiedTime(directory.resolve(STATE_FILE_NAME), FileTime.from(Instant.now()));
            return result;
        } catch (IOException | RuntimeException e) {
            try {remove(directory);} catch (RuntimeException cleanup) {e.addSuppressed(cleanup);}
            if (e instanceof RuntimeException runtime) throw runtime;
            throw new JatosException("Could not prepare the study import.", e, IO_ERROR);
        }
    }

    /**
     * Resolves only directory names issued by this service; session values never become arbitrary paths.
     */
    public Path resolve(String name) {
        if (name == null || !name.matches("study-import-[0-9a-fA-F-]{36}")) return null;
        return IOUtils.tmpDir().resolve(name);
    }

    /**
     * Acquires the import’s lock, checks that preparation succeeded and the import hasn’t expired, then executes action
     * with the "content/" path. Confirmation uses this method. It releases the lock afterward but does not delete the
     * upload or extend its expiry.
     */
    public <T> T use(Path directory, Function<Path, T> action) {
        if (directory == null) throw new ImportExportException(EXPIRED_ERROR_MSG);
        try (ImportClaim ignored = claim(directory);
             FileChannel channel = openState(directory); FileLock lock = tryLock(channel)) {
            if (lock == null)
                throw new ImportExportException("The pending import is currently in use. Try again shortly.");
            if (channel.size() == 0 || expired(directory.resolve(STATE_FILE_NAME)))
                throw new ImportExportException(EXPIRED_ERROR_MSG);
            return action.apply(directory.resolve("content"));
        } catch (NoSuchFileException e) {
            throw new ImportExportException(EXPIRED_ERROR_MSG);
        } catch (IOException e) {
            throw new JatosException("Could not access the pending study import.", e, IO_ERROR);
        }
    }

    public void remove(Path directory) {
        if (directory == null || !Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return;
        try {
            if (!remove(directory, false))
                throw new ImportExportException("The pending import is currently in use. Try again shortly.");
        } catch (IOException e) {
            throw new JatosException("Could not remove the pending study import.", e, IO_ERROR);
        }
    }

    /**
     * Called hourly and on startup. Fresh, active, and unrelated temporary directories are left alone.
     */
    public void cleanupExpired() {
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(IOUtils.tmpDir(), DIR_PREFIX + "*")) {
            for (Path directory : entries) {
                if (resolve(directory.getFileName().toString()) == null || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS))
                    continue;
                try {remove(directory, true);} catch (IOException | RuntimeException e) {
                    LOGGER.warn("Could not clean up expired study import " + directory, e);
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Could not scan pending study imports", e);
        }
    }

    /**
     * Requests immediate deletion, regardless of age, for cancellation or completed imports. Does nothing if the
     * directory is absent. Reports an error if another operation currently holds its lock.
     */
    private boolean remove(Path directory, boolean onlyExpired) throws IOException {
        try (ImportClaim claim = tryClaim(directory)) {
            if (claim == null) return false;
            Path state = directory.resolve(STATE_FILE_NAME);
            if (!Files.exists(state, LinkOption.NOFOLLOW_LINKS)) {
                // Also recover a process crash between directory and state-file creation.
                if (!onlyExpired || expired(directory)) IOUtils.deleteRecursivelyIfExists(directory);
                return true;
            }
            try (FileChannel channel = openState(directory); FileLock lock = tryLock(channel)) {
                if (lock == null) return false;
                if (onlyExpired && !expired(state)) return true;
                IOUtils.deleteRecursivelyIfExists(directory.resolve("content"));
                // Invalidate before releasing the lock: a waiting confirmer must not use deleted content.
                channel.truncate(0);
            } catch (NoSuchFileException e) {
                return true; // Another cleaner already removed it.
            }
            // Close the lock file before deletion, which is required on Windows.
            Files.deleteIfExists(state);
            Files.deleteIfExists(directory);
            return true;
        }
    }

    private static ImportClaim claim(Path directory) {
        ImportClaim claim = tryClaim(directory);
        if (claim == null)
            throw new ImportExportException("The pending import is currently in use. Try again shortly.");
        return claim;
    }

    private static ImportClaim tryClaim(Path directory) {
        Path key = directory.toAbsolutePath().normalize();
        return ACTIVE_IMPORTS.add(key) ? new ImportClaim(key) : null;
    }

    /**
     * Released after the file channel closes, including when an operation fails.
     */
    private record ImportClaim(Path directory) implements AutoCloseable {
        @Override
        public void close() {
            ACTIVE_IMPORTS.remove(directory);
        }
    }

    /**
     * Opens the existing state file for writing and locking. It doesn’t create a missing file and refuses to follow a
     * symbolic link for that file.
     */
    private static FileChannel openState(Path directory) throws IOException {
        return FileChannel.open(directory.resolve(STATE_FILE_NAME), StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
    }

    /**
     * Attempts to acquire the lock without waiting. Returns null if another operation holds it. It also converts Java’s
     * same-process OverlappingFileLockException into that same result.
     */
    private static FileLock tryLock(FileChannel channel) throws IOException {
        try {return channel.tryLock();} catch (OverlappingFileLockException e) {return null;}
    }

    private static boolean expired(Path path) throws IOException {
        return Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toInstant().isBefore(Instant.now().minus(EXPIRY));
    }
}
