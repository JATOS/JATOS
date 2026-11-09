package models.gui;

import utils.common.IOUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Temporary source content. The caller owns this staging directory and must close it after use.
 */
public final class StagedStudySource implements AutoCloseable {

    /**
     * Path to the staging directory containing the study assets and properties
     */
    private final Path directory;

    /**
     * Path to the study property file (.jas)
     */
    private final Path properties;

    /**
     * Path to the study assets folder
     */
    private final Path assets;

    public StagedStudySource(Path directory, Path properties, Path assets) {
        this.directory = directory;
        this.properties = properties;
        this.assets = assets;
    }

    public Path getDirectory() {
        return directory;
    }

    /**
     * Empty means no properties were supplied; creation may use defaults.
     */
    public Optional<Path> getProperties() {
        return Optional.ofNullable(properties);
    }

    /**
     * Empty means preserve assets on update. A present but empty directory means replace with no assets.
     */
    public Optional<Path> getAssets() {
        return Optional.ofNullable(assets);
    }

    @Override
    public void close() throws IOException {
        IOUtils.deleteRecursivelyIfExists(directory);
    }
}
