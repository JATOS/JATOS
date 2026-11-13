package services.gui.importexport;

import exceptions.common.BadRequestException;
import general.common.Common;
import utils.common.IOUtils;
import models.gui.StagedStudySource;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

import static general.common.ApiEnvelope.ErrorCode.CONFIG_ERROR;

/**
 * Stages local folder content for study import.
 */
@Singleton
public class LocalFolderStudySourceIO {

    private final IOUtils ioUtils;

    @Inject
    public LocalFolderStudySourceIO(IOUtils ioUtils) {
        this.ioUtils = ioUtils;
    }

    /**
     * Rechecks server policy on every ingestion. The caller must close the returned staging directory.
     */
    public StagedStudySource stage(String folder) throws IOException {
        var roots = Common.getStudySourcesAllowedRoots();
        if (Common.isServer() && roots.isEmpty()) {
            throw new BadRequestException("Local-folder sources require configured allowed roots in server mode.", CONFIG_ERROR);
        }
        return stageLocalStudySource(folder, roots);
    }

    /**
     * Copies only one root .jas file and assets/ into private temporary storage. Missing content stays absent. Source
     * content is never modified. Links and special files within selected content are rejected.
     */
    private StagedStudySource stageLocalStudySource(String sourceDir, List<String> allowedRoots) throws IOException {
        if (!Path.of(sourceDir).isAbsolute()) throw new IOException("Use an absolute source folder path.");
        Path source = ioUtils.resolveReadableDirectoryWithinRoots(sourceDir, allowedRoots)
                .orElseThrow(() -> new IOException("The source folder must be inside one of the configured allowed roots."));
        Path stagingParent = IOUtils.tmpDir().toRealPath();
        if (stagingParent.startsWith(source.resolve("assets")))
            throw new IOException("The temporary directory must be outside the source assets directory.");

        Path staging = Files.createTempDirectory(stagingParent, "study-source-");
        try (DirectoryStream<Path> input = Files.newDirectoryStream(source)) {
            Path properties = null;
            Path assets = null;
            for (Path entry : input) {
                Path name = entry.getFileName();
                String filename = name.toString();
                if (filename.endsWith(".jas")) {
                    if (properties != null)
                        throw new IOException("The source folder must contain at most one .jas file.");
                    copySourceFile(source, entry, staging.resolve(filename));
                    properties = staging.resolve(filename);
                } else if (filename.equals("assets")) {
                    assets = staging.resolve("assets");
                    copySourceDirectory(source, entry, assets);
                }
            }
            if (properties == null && assets == null)
                throw new IOException("The source folder must contain a .jas file or an assets/ directory, or both.");
            return new StagedStudySource(staging, properties, assets);
        } catch (IOException | RuntimeException e) {
            try {
                IOUtils.deleteRecursively(staging);
            } catch (IOException cleanup) {
                e.addSuppressed(cleanup);
            }
            throw e;
        }
    }

    /**
     * Reject existing links and junctions. Source folders must be trusted against concurrent replacement.
     */
    private static BasicFileAttributes sourceAttributes(Path root, Path path) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        Path real = path.toRealPath();
        if (attributes.isSymbolicLink() || attributes.isOther() || !real.startsWith(root)
                || !real.equals(path.toAbsolutePath().normalize()))
            throw new IOException("Source content must contain only regular files and directories, without symbolic links or junctions.");
        return attributes;
    }

    private static void copySourceDirectory(Path root, Path source, Path target) throws IOException {
        if (!sourceAttributes(root, source).isDirectory())
            throw new IOException("Source assets must be a directory.");
        // No FOLLOW_LINKS: links are rejected rather than traversed. Ignore .git at every depth.
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                if (directory.getFileName().toString().equals(".git")) return FileVisitResult.SKIP_SUBTREE;
                sourceAttributes(root, directory);
                Files.createDirectory(target.resolve(source.relativize(directory)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                if (!file.getFileName().toString().equals(".git")) {
                    copySourceFile(root, file, target.resolve(source.relativize(file)));
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException error) throws IOException {
                if (file.getFileName().toString().equals(".git")) return FileVisitResult.CONTINUE;
                throw error;
            }
        });
    }

    private static void copySourceFile(Path root, Path source, Path target) throws IOException {
        if (!sourceAttributes(root, source).isRegularFile())
            throw new IOException("Source content must contain only regular files and directories.");
        try (var input = Files.newInputStream(source, LinkOption.NOFOLLOW_LINKS)) {
            Files.copy(input, target);
        }
    }

}
