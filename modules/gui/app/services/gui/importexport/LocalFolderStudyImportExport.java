package services.gui.importexport;

import com.fasterxml.jackson.databind.JsonNode;
import exceptions.common.FieldValidationException;
import exceptions.common.BadRequestException;
import general.common.Common;
import models.gui.StudySourceSyncProperties;
import utils.common.IOUtils;
import services.gui.StudyService;

import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

import exceptions.common.ImportExportException;
import general.common.JpaTransactions;
import json.common.DomainJsonMapper;
import models.common.Study;
import models.gui.StagedStudySource;
import models.gui.StudySourceProperties;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static models.common.StudySource.Type.LOCAL_FOLDER;

/**
 * Creates studies from local folders and imports/exports content for existing studies.
 */
@Singleton
public class LocalFolderStudyImportExport {

    private final LocalFolderStudySourceIO localFolderStudySourceIO;
    private final StudyImportService studyImportService;
    private final StudyService studyService;
    private final StudySourceService studySourceService;
    private final JpaTransactions transactions;
    private final DomainJsonMapper domainJsonMapper;
    private final IOUtils ioUtils;
    private final StudySourceSyncIO studySourceSyncIO;

    @Inject
    public LocalFolderStudyImportExport(LocalFolderStudySourceIO localFolderStudySourceIO,
                                        StudyImportService studyImportService,
                                        StudyService studyService,
                                        StudySourceService studySourceService,
                                        JpaTransactions transactions,
                                        DomainJsonMapper domainJsonMapper, IOUtils ioUtils, StudySourceSyncIO studySourceSyncIO) {
        this.localFolderStudySourceIO = localFolderStudySourceIO;
        this.studyImportService = studyImportService;
        this.studyService = studyService;
        this.studySourceService = studySourceService;
        this.transactions = transactions;
        this.domainJsonMapper = domainJsonMapper;
        this.ioUtils = ioUtils;
        this.studySourceSyncIO = studySourceSyncIO;
    }

    /**
     * Created study and feedback describing the content actually imported.
     */
    public record CreationResult(JsonNode study, String message) {}

    /**
     * Copies source content to temporary storage and imports the study.
     */
    public CreationResult createStudyFromLocalFolder(String localFolderPath) {
        try (StagedStudySource staged = stageLocalFolder(localFolderPath)) {
            Study preparedStudy = studyImportService.prepareStudyFromStagedSource(staged);
            JsonNode study = createLocalFolderStudyInTransaction(staged, preparedStudy, localFolderPath);
            String imported = staged.getProperties().isPresent()
                    ? (staged.getAssets().isPresent() ? "properties and assets" : "properties only")
                    : "assets only";
            return new CreationResult(study, "Study created from local folder: imported " + imported + ".");
        } catch (IOException e) {
            throw new ImportExportException("Could not create study from study source.");
        }
    }

    private StagedStudySource stageLocalFolder(String localFolderPath) {
        try {
            return localFolderStudySourceIO.stage(localFolderPath);
        } catch (IOException e) {
            throw new FieldValidationException("localFolderPath", "Could not read the source folder: " + e.getMessage());
        }
    }

    private JsonNode createLocalFolderStudyInTransaction(StagedStudySource stagedStudySource, Study preparedStudy,
                                                         String localFolderPath) {
        try {
            return transactions.withTransaction(em -> {
                Study study = studyImportService.createStudyFromStagedSource(stagedStudySource, preparedStudy);
                StudySourceProperties props = new StudySourceProperties();
                props.setType(LOCAL_FOLDER);
                props.setLocalFolderPath(localFolderPath);
                studySourceService.save(study.getId(), props);
                JsonNode result = domainJsonMapper.studyAsJsonForApi(study, false, false);
                closeStagedSourceBeforeCommit(stagedStudySource);
                return result;
            });
        } catch (RuntimeException e) {
            // A uniqueness violation can occur at commit; query only after rollback.
            throw studyImportService.translateStudyCreationConflict(e, preparedStudy.getUuid());
        }
    }

    private void closeStagedSourceBeforeCommit(StagedStudySource staged) {
        // The outer resource block also covers failures before this point.
        try {
            staged.close();
        } catch (IOException e) {
            throw new ImportExportException("Could not clean up the staged study source.");
        }
    }

    public record ImportPreview(boolean propertiesAvailable, boolean assetsAvailable) {}

    /**
     * Stages the folder and validates its study identity without applying changes; removes staging before returning.
     */
    public ImportPreview previewImport(Study study, String folder) throws IOException {
        try (StagedStudySource staged = stage(folder)) {
            studySourceSyncIO.checkSourceOutsideAssets(folder, study.getDirName());
            extractStudyFromSource(study, staged);
            return new ImportPreview(staged.getProperties().isPresent(), staged.getAssets().isPresent());
        }
    }

    /**
     * Imports selected properties and assets into an existing study. The caller holds the synchronization lock and
     * transaction through completion, so a failure restores replaced assets along with rolled-back database changes.
     */
    public void importFromLocalFolder(Study study, String folder, StudySourceSyncProperties props) throws IOException {
        try (StagedStudySource staged = stage(folder)) {
            studySourceSyncIO.checkSourceOutsideAssets(folder, study.getDirName());
            Study imported = extractStudyFromSource(study, staged);
            if (props.isProperties() && imported == null)
                throw new BadRequestException("The source has no .jas file. Only assets can be synchronized.");
            if (props.isAssets() && staged.getAssets().isEmpty())
                throw new BadRequestException("The source has no assets/ directory. Existing assets are preserved.");

            if (props.isProperties()) {
                studyImportService.checkForExistingComponents(imported);
                //noinspection DataFlowIssue
                studyService.updateStudyWithoutDirName(study, imported);
                studyImportService.updateStudysComponents(study, imported);
            }
            if (props.isAssets())
                studySourceSyncIO.replaceAssets(staged.getAssets().orElseThrow(), study.getDirName());
        }
    }

    private StagedStudySource stage(String folder) {
        try {
            return localFolderStudySourceIO.stage(folder);
        } catch (IOException e) {
            throw new BadRequestException("Could not read the study source: " + e.getMessage());
        }
    }

    private Study extractStudyFromSource(Study target, StagedStudySource stagedSource) {
        if (stagedSource.getProperties().isEmpty()) return null;
        Study importedStudy = studyImportService.deserializeStudyProperties(stagedSource.getProperties().orElseThrow());
        if (!target.getUuid().equals(importedStudy.getUuid()))
            throw new BadRequestException("The source .jas UUID differs from this study's UUID. Synchronization was aborted.");
        return importedStudy;
    }

    /**
     * Destination validation is repeated before export; another study must never be overwritten.
     */
    public record ExportPreview(Path folder, String propertiesFilename, boolean assetsAvailable) {}

    public ExportPreview previewExport(Study study, String localFolderStr) throws IOException {
        var allowedRoots = Common.getStudySourcesLocalFolderAllowedRoots();
        if (Common.isServer() && allowedRoots.isEmpty())
            throw new BadRequestException("Local-folder sources require configured allowed roots in server mode.");
        if (!Path.of(localFolderStr).isAbsolute())
            throw new BadRequestException("Use an absolute local folder path.");

        Path localFolder = ioUtils.resolveReadableDirectoryWithinRoots(localFolderStr, allowedRoots)
                .orElseThrow(() -> new BadRequestException("The local folder must be inside a configured allowed root."));
        studySourceSyncIO.checkSourceOutsideAssets(localFolder.toString(), study.getDirName());

        if (!Files.isWritable(localFolder)) throw new BadRequestException("The local folder is not writable.");

        List<Path> jasFiles = new ArrayList<>();
        try (var entries = Files.newDirectoryStream(localFolder, "*.jas")) {
            entries.forEach(jasFiles::add);
        }
        if (jasFiles.size() > 1) throw new BadRequestException("The local folder contains more than one .jas file.");
        if (!jasFiles.isEmpty()) {
            Path jas = jasFiles.getFirst();
            if (!IOUtils.isRegularFileOrDirectory(jas, false)) {
                throw new BadRequestException("The destination .jas must be a regular file without links or junctions.");
            }
            Study existing;
            try {
                existing = studyImportService.deserializeStudyProperties(jas);
            } catch (RuntimeException e) {
                throw new BadRequestException("The destination .jas file could not be read as a valid study.");
            }
            if (!study.getUuid().equals(existing.getUuid()))
                throw new BadRequestException("The destination .jas UUID differs from this study's UUID. Export is not allowed.");
        }

        Path assets = localFolder.resolve("assets");
        if (Files.exists(assets, NOFOLLOW_LINKS) && !IOUtils.isRegularFileOrDirectory(assets, true))
            throw new BadRequestException("The destination assets must be a directory without links or junctions.");
        Path installedAssets = ioUtils.getStudyAssetsDir(study.getDirName(), false);

        return new ExportPreview(
                localFolder,
                jasFiles.size() == 1 ? jasFiles.getFirst().getFileName().toString() : "study.jas",
                Files.isDirectory(installedAssets));
    }

    /**
     * Tracks the replacement of one destination item: either the .jas file or the assets/ directory.
     */
    private static class ExportReplacement {
        final Path destination;
        final Path replacement;
        final Path backup;
        boolean oldMoved;
        boolean newMoved;

        ExportReplacement(Path destination, Path replacement, Path backup) {
            this.destination = destination;
            this.replacement = replacement;
            this.backup = backup;
        }
    }

    /**
     * Writes the selected study properties and/or assets to the configured folder, keeping backups so it can undo the
     * changes if the transaction fails. Stages selected content beside the destination, then replaces only the .jas
     * file and/or assets directory. The caller holds the study synchronization lock and transaction through
     * commit/rollback. Unrelated root files (including .git) stay untouched. Rollback restores backups; failed
     * restoration retains them for manual recovery.
     */
    public void exportToLocalFolder(Study study, String folder, StudySourceSyncProperties props) throws IOException {
        ExportPreview preview = previewExport(study, folder);
        if (props.isAssets() && !preview.assetsAvailable())
            throw new BadRequestException("The study has no assets directory. Existing destination assets are preserved.");

        Path tmp = Files.createTempDirectory(preview.folder(), ".jatos-export-");
        List<ExportReplacement> replacements = new ArrayList<>();

        transactions.afterCompletion("Finish local-folder study export",
                () -> IOUtils.deleteRecursivelyIfExists(tmp),
                () -> {
                    for (int i = replacements.size() - 1; i >= 0; i--) {
                        ExportReplacement entry = replacements.get(i);
                        if (entry.newMoved) IOUtils.deleteRecursivelyIfExists(entry.destination);
                        if (entry.oldMoved)
                            Files.move(entry.backup, entry.destination, StandardCopyOption.ATOMIC_MOVE);
                    }
                    IOUtils.deleteRecursivelyIfExists(tmp);
                });

        Path staged = Files.createDirectory(tmp.resolve("new"));
        Path backups = Files.createDirectory(tmp.resolve("old"));
        stageSelectedContent(study, props, staged, preview.propertiesFilename());

        if (props.isProperties())
            replacements.add(new ExportReplacement(
                    preview.folder().resolve(preview.propertiesFilename()),
                    staged.resolve(preview.propertiesFilename()),
                    backups.resolve(preview.propertiesFilename())));
        if (props.isAssets())
            replacements.add(new ExportReplacement(
                    preview.folder().resolve("assets"),
                    staged.resolve("assets"),
                    backups.resolve("assets")));
        for (ExportReplacement entry : replacements) {
            if (Files.exists(entry.destination, NOFOLLOW_LINKS)) {
                Files.move(entry.destination, entry.backup, StandardCopyOption.ATOMIC_MOVE);
                entry.oldMoved = true;
            }
            Files.move(entry.replacement, entry.destination, StandardCopyOption.ATOMIC_MOVE);
            entry.newMoved = true;
        }
    }

    /**
     * Uses the archive serializer, which excludes credentials, source configuration and synchronization history.
     */
    private void stageSelectedContent(Study study, StudySourceSyncProperties props, Path staged, String filename) throws IOException {
        if (props.isProperties()) domainJsonMapper.studyAsJsonForIO(study, staged.resolve(filename));
        if (props.isAssets()) {
            Path assets = ioUtils.getStudyAssetsDir(study.getDirName());
            LocalFolderStudySourceIO.copySourceDirectory(assets, assets, staged.resolve("assets"));
        }
    }

}
