package services.gui.importexport;

import exceptions.common.BadRequestException;
import exceptions.common.HttpException;
import general.common.Common;
import general.common.JpaTransactions;
import utils.common.IOUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Filesystem operations for source synchronization, including recovery of replaced assets.
 */
@Singleton
public class StudySourceSyncIO {

    private static final Set<Path> ACTIVE = ConcurrentHashMap.newKeySet();

    private final JpaTransactions transactions;
    private final IOUtils ioUtils;

    @Inject
    public StudySourceSyncIO(JpaTransactions transactions, IOUtils ioUtils) {
        this.transactions = transactions;
        this.ioUtils = ioUtils;
    }

    public record SyncLock(Path file, FileChannel channel, FileLock lock) implements AutoCloseable {
        @SuppressWarnings("EmptyTryBlock")
        @Override
        public void close() throws IOException {
            try (channel; lock) { /* Close channels before releasing the in-process claim. */ } finally {
                ACTIVE.remove(file);
            }
        }
    }

    /**
     * Locks a study to prevent simultaneous synchronization. FileLock coordinates processes; ACTIVE prevents threads in
     * this JVM from opening a second channel. On some systems, closing a second channel could release the first
     * channel's lock.
     */
    public SyncLock lock(long studyId) throws IOException {
        Path locks = IOUtils.tmpDir().resolve("study-sync-locks");
        Files.createDirectories(locks);
        Path file = locks.resolve(studyId + ".lock").toAbsolutePath().normalize();
        if (!ACTIVE.add(file)) throw busy();
        FileChannel channel = null;
        try {
            channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
            FileLock lock = channel.tryLock(0, 1, false);
            if (lock == null) throw busy();
            return new SyncLock(file, channel, lock);
        } catch (IOException | RuntimeException e) {
            try {if (channel != null) channel.close();} finally {ACTIVE.remove(file);}
            throw e;
        }
    }

    private HttpException busy() {
        return new HttpException(409, "This study is already being synchronized. Try again shortly.");
    }

    /**
     * Rejects source folders that overlap the installed study assets.
     */
    public void checkSourceOutsideAssets(String localFolderPath, String dirName) throws IOException {
        Path source = Path.of(localFolderPath).toRealPath();
        Path target = ioUtils.getStudyAssetsDir(dirName, false);
        if (!Files.exists(target)) return;
        target = target.toRealPath();
        if (source.startsWith(target) || target.startsWith(source.resolve("assets")))
            throw new BadRequestException("The source folder and installed study assets must not overlap.");
    }

    private static class ReplacementState {
        boolean oldMoved;
        boolean newMoved;
    }

    /**
     * Copies incoming assets into a temporary "new" directory and keeps the previous assets in "old" for rollback. The
     * temporary directory stays inside the assets root so replacement and restoration can use atomic moves. Commit
     * deletes the backup; rollback restores it. Failed restoration retains the backup for manual recovery.
     */
    public void replaceAssets(Path source, String studyAssetsDirName) throws IOException {
        Path destination = ioUtils.getStudyAssetsDir(studyAssetsDirName, false);
        // This temporary directory has to be in the same filesystem as the destination.
        Path tmp = Files.createTempDirectory(Path.of(Common.getStudyAssetsRootPath()), ".study-sync-");
        Path replacement = tmp.resolve("new");
        Path backup = tmp.resolve("old");
        // Mutable state captured by the transaction callbacks.
        ReplacementState state = new ReplacementState();
        transactions.afterCompletion("Finish synchronized study assets",
                () -> IOUtils.deleteRecursivelyIfExists(tmp),
                () -> {
                    if (state.newMoved) IOUtils.deleteRecursivelyIfExists(destination);
                    if (state.oldMoved) Files.move(backup, destination, StandardCopyOption.ATOMIC_MOVE);
                    // Keep the backup for manual recovery if restoring it fails.
                    IOUtils.deleteRecursivelyIfExists(tmp);
                });
        IOUtils.copyRecursively(source, replacement);
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
            Files.move(destination, backup, StandardCopyOption.ATOMIC_MOVE);
            state.oldMoved = true;
        }
        Files.move(replacement, destination, StandardCopyOption.ATOMIC_MOVE);
        state.newMoved = true;
    }
}
