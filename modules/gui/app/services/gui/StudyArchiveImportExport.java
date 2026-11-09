package services.gui;

import com.fasterxml.jackson.databind.JsonNode;
import daos.common.StudyDao;
import exceptions.common.*;
import general.common.ApiEnvelope.ErrorCode;
import general.common.Common;
import general.common.JpaTransactions;
import general.common.MessagesStrings;
import http.common.Http.Context;
import json.common.DomainJsonMapper;
import messaging.common.RequestScopeMessaging;
import models.common.Study;
import models.common.User;
import play.Logger;
import play.Logger.ALogger;
import utils.common.IOUtils;
import utils.common.ZipUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.ValidationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static auth.gui.AuthAction.SIGNEDIN_USER;
import static exceptions.common.JatosException.unchecked;
import static general.common.ApiEnvelope.ErrorCode.IO_ERROR;

/**
 * Handles JATOS study archive imports and exports.
 */
@Singleton
public class StudyArchiveImportExport {

    private static final ALogger LOGGER = Logger.of(StudyArchiveImportExport.class);

    public static final String SESSION_TEMP_IMPORT_STUDY_DIR = "tempImportStudyDir";

    private final JpaTransactions transactions;
    private final AuthorizationService authorizationService;
    private final StudyService studyService;
    private final DomainJsonMapper domainJsonMapper;
    private final IOUtils ioUtils;
    private final StudyDao studyDao;
    private final StudyImportService studyImportService;
    private final PendingStudyImportStorage pendingImports;

    @Inject
    public StudyArchiveImportExport(JpaTransactions transactions,
                                    AuthorizationService authorizationService,
                                    StudyService studyService,
                                    DomainJsonMapper domainJsonMapper,
                                    IOUtils ioUtils,
                                    StudyDao studyDao,
                                    StudyImportService studyImportService,
                                    PendingStudyImportStorage pendingImports) {
        this.transactions = transactions;
        this.authorizationService = authorizationService;
        this.studyService = studyService;
        this.domainJsonMapper = domainJsonMapper;
        this.ioUtils = ioUtils;
        this.studyDao = studyDao;
        this.studyImportService = studyImportService;
        this.pendingImports = pendingImports;
    }

    // @formatter:off
    /**
     * Import an uploaded JATOS study archive There are 5 possible cases: (udir - name of uploaded study asset dir, cdir
     * - name of current study asset dir)
     *
     * 1) study exists & udir exists & udir == cdir -> ask confirmation to overwrite study and/or dir
     * 2) study exists & udir exists & udir != cdir -> ask confirmation to overwrite study and/or (dir && rename to cdir)
     * 3) study exists & !udir exists -> shouldn't happen, ask confirmation to overwrite study
     * 4) !study exists & udir exists -> ask to rename dir (generate new dir name)
     * 5) !study exists & !udir exists -> new study - write both
     */
    // @formatter:on
    public Map<String, Object> importStudy(Path file) {
        cleanupAfterStudyImport();
        return pendingImports.prepare(tempUnzippedStudyDir -> {
            unzipUploadedFile(file, tempUnzippedStudyDir);
            Study uploadedStudy = deserializeStudy(tempUnzippedStudyDir);

            // Remember the study assets' dir name for the confirmation request
            Context.current().response().putSession(
                    StudyArchiveImportExport.SESSION_TEMP_IMPORT_STUDY_DIR, tempUnzippedStudyDir.getParent().getFileName().toString());

            studyImportService.checkForExistingComponents(uploadedStudy);

            Optional<Study> currentStudy = studyDao.findByUuid(uploadedStudy.getUuid());
            boolean uploadedDirExists = ioUtils.checkStudyAssetsDirExists(uploadedStudy.getDirName());
            User signedinUser = Context.current().args().get(SIGNEDIN_USER);
            if (currentStudy.isPresent() && !authorizationService.isMemberOrSuperuser(currentStudy.get(), signedinUser)) {
                throw new ForbiddenException("Import failed: the study already exists, but you are not a member and " +
                        "therefore cannot overwrite it. You may import the study into another JATOS instance, clone it " +
                        "there, then export and re-import it here.");
            }

            Map<String, Object> importInfo = new HashMap<>();
            importInfo.put("studyExists", currentStudy.isPresent());
            if (currentStudy.isPresent()) {
                importInfo.put("uuid", currentStudy.get().getUuid());
                importInfo.put("currentStudyTitle", currentStudy.get().getTitle());
                importInfo.put("currentDirName", currentStudy.get().getDirName());
            } else {
                importInfo.put("uuid", uploadedStudy.getUuid());
            }
            importInfo.put("uploadedStudyTitle", uploadedStudy.getTitle());
            importInfo.put("uploadedDirName", uploadedStudy.getDirName());
            importInfo.put("uploadedDirExists", uploadedDirExists);
            if (currentStudy.isEmpty() && uploadedDirExists) {
                String newDirName = ioUtils.findNonExistingStudyAssetsDirName(uploadedStudy.getDirName());
                importInfo.put("newDirName", newDirName);
            }
            return importInfo;
        });
    }

    /**
     * @param keepProperties        If true and the study exists already in JATOS, the current properties are kept.
     *                              Default is `false` (properties are overwritten by default). If the study doesn't
     *                              already exist, this parameter has no effect.
     * @param keepAssets            If true and the study exists already in JATOS, the current study assets directory is
     *                              kept. Default is `false` (assets are overwritten by default). If the study doesn't
     *                              already exist, this parameter has no effect.
     * @param keepCurrentAssetsName If the assets are going to be overwritten (`keepAssets=false`), this flag indicates
     *                              if the name of the currently installed assets directory should be kept. A `false`
     *                              indicates that the name should be taken from the uploaded one. Default is `true`.
     * @param renameAssets          If the study assets directory already exists in JATOS but belongs to a different
     *                              study, it cannot be overwritten. In this case you can set `renameAssets=true` to let
     *                              JATOS add a suffix to the assets directory name (original name + "_" + a number).
     *                              Default is `true`.
     */
    public Study importStudyConfirmed(boolean keepProperties, boolean keepAssets,
                                      boolean keepCurrentAssetsName, boolean renameAssets) {
        return pendingImports.use(getPendingImportDirectory(), tempUnzippedStudyDir -> transactions.withTransaction(em -> {
            Study uploadedStudy = deserializeStudy(tempUnzippedStudyDir);
            Optional<Study> currentStudy = studyDao.findByUuid(uploadedStudy.getUuid());

            // 1) study exists & udir exists & udir == cdir
            // 2) study exists & udir exists & udir != cdir
            // 3) study exists & !udir exists
            if (currentStudy.isPresent()) {
                overwriteExistingStudy(keepProperties, keepAssets,
                        keepCurrentAssetsName, tempUnzippedStudyDir, uploadedStudy, currentStudy.get());
                return currentStudy.get();
            }

            // 4) !study exists & udir exists
            // 5) !study exists & !udir exists
            return importNewStudy(tempUnzippedStudyDir, uploadedStudy, renameAssets);
        }));
    }

    /**
     * Confirms an import and builds its API representation in the same transaction, keeping lazy associations available
     * during serialization. The transaction in importStudyConfirmed joins this transaction.
     *
     * @param keepProperties        preserve the existing study properties on a UUID match
     * @param keepAssets            preserve the existing study assets on a UUID match
     * @param keepCurrentAssetsName retain the existing assets directory name when replacing assets
     * @param renameAssets          rename imported assets if their directory belongs to another study
     * @return the imported study's API properties, including component, batch and member references
     */
    public JsonNode importStudyConfirmedForApi(boolean keepProperties, boolean keepAssets,
                                               boolean keepCurrentAssetsName, boolean renameAssets) {
        return transactions.withTransaction(em -> {
            Study study = importStudyConfirmed(keepProperties, keepAssets, keepCurrentAssetsName, renameAssets);
            return domainJsonMapper.studyAsJsonForApi(study, false, false);
        });
    }

    public void cleanupAfterStudyImport() {
        Path tempUnzippedStudyDir = getPendingImportDirectory();
        try {
            pendingImports.remove(tempUnzippedStudyDir);
        } catch (Exception e) {
            LOGGER.error("cleanupAfterStudyImport: deleting temp directory failed", e);
        }
        Context.current().response().removeSession(StudyArchiveImportExport.SESSION_TEMP_IMPORT_STUDY_DIR);
    }

    private void overwriteExistingStudy(boolean keepProperties, boolean keepAssets,
                                        boolean keepCurrentAssetsName, Path tempUnzippedStudyDir, Study uploadedStudy, Study currentStudy) {
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessStudy(currentStudy, signedinUser, true);

        if (!keepAssets) {
            String dirName = keepCurrentAssetsName ? currentStudy.getDirName() : uploadedStudy.getDirName();
            unchecked(() -> moveStudyAssetsDir(tempUnzippedStudyDir, currentStudy, dirName));
            RequestScopeMessaging.success(MessagesStrings.studyAssetsOverwritten(
                    dirName, currentStudy.getId(), currentStudy.getTitle()));
        }

        if (!keepProperties) {
            if (keepCurrentAssetsName || keepAssets) {
                studyService.updateStudyWithoutDirName(currentStudy, uploadedStudy);
            } else {
                studyService.updateStudyAndRenameAssets(currentStudy, uploadedStudy);
            }
            studyImportService.updateStudysComponents(currentStudy, uploadedStudy);
            RequestScopeMessaging.success(MessagesStrings
                    .studysPropertiesOverwritten(currentStudy.getId(), currentStudy.getTitle()));
        }
    }

    private Study importNewStudy(Path tempUnzippedStudyDir, Study importedStudy, boolean renameAssets) {
        Path[] directories = unchecked(() -> ioUtils.findDirectories(tempUnzippedStudyDir));
        if (directories.length > 1)
            throw new JatosException("There are more than one directory in the ZIP file.", IO_ERROR);
        if (directories.length == 0) {
            RequestScopeMessaging.warning("There is no directory in the ZIP file - new study assets created.");
        }
        Study newStudy = studyImportService.createNewStudy(importedStudy,
                directories.length == 0 ? null : directories[0], renameAssets);
        RequestScopeMessaging.success(MessagesStrings.importedNewStudy(
                importedStudy.getDirName(), importedStudy.getId(), importedStudy.getTitle()));
        return newStudy;
    }

    /**
     * Returns a JATOS study archive packed as ZIP. It returns the File object located in the system's temp directory.
     * The JATOS study archive will contain the study assets' directory and the study's JSON data (a .jas file).
     */
    public Path createStudyExportZipFile(long studyId) {
        Study study = studyDao.findByIdWithComponentsAndBatches(studyId);

        Path studyAsJsonFile = null;
        Path zipFile = null;
        try {
            String studyFileName = ioUtils.generateFileName(study.getTitle());
            studyAsJsonFile = Files.createTempFile(studyFileName, ".jas");
            domainJsonMapper.studyAsJsonForIO(study, studyAsJsonFile);
            Path studyAssetsDir = Path.of(Common.getStudyAssetsRootPath(), study.getDirName());

            List<Path> filesToZip = new ArrayList<>();
            filesToZip.add(studyAssetsDir);
            filesToZip.add(studyAsJsonFile);

            zipFile = Files.createTempFile("jatos_study_", "." + Common.getStudyArchiveSuffix());
            ZipUtil.zipFiles(filesToZip, zipFile);
            return zipFile;
        } catch (Exception e) {
            Path finalZipFile = zipFile;
            if (zipFile != null) unchecked(() -> Files.deleteIfExists(finalZipFile));
            throw new JatosException(e.getMessage(), e, ErrorCode.IMPORT_EXPORT_ERROR);
        } finally {
            Path finalStudyAsJsonFile = studyAsJsonFile;
            if (studyAsJsonFile != null) unchecked(() -> Files.deleteIfExists(finalStudyAsJsonFile));
        }
    }

    /**
     * Deletes current study assets' dir and moves imported study assets' dir from Java's temp dir to study assets root
     * dir
     */
    private void moveStudyAssetsDir(Path unzippedStudyDir, Study currentStudy, String studyAssetsDirName) throws IOException {
        if (currentStudy != null) {
            ioUtils.removeStudyAssetsDir(currentStudy.getDirName());
        }

        Path[] dirArray = ioUtils.findDirectories(unzippedStudyDir);
        if (dirArray.length == 0) {
            // If a study assets dir is missing, create a new one.
            ioUtils.createStudyAssetsDir(studyAssetsDirName);
            RequestScopeMessaging.warning("There is no directory in the ZIP file - new study assets created.");
        } else if (dirArray.length == 1) {
            Path studyAssetsDir = dirArray[0];
            ioUtils.moveStudyAssetsDir(studyAssetsDir, studyAssetsDirName);
        } else {
            throw new JatosException("There are more than one directory in the ZIP file.", IO_ERROR);
        }
    }

    /**
     * Get unzipped study dir File object stored in Java's temp directory. Name is stored in session. Discard session
     * variable afterward.
     */
    private Path getPendingImportDirectory() {
        return pendingImports.resolve(Context.current().response().getSession(SESSION_TEMP_IMPORT_STUDY_DIR).orElse(null));
    }

    /**
     * Cancels the session's pending upload; an absent or expired upload is harmless.
     */
    public void cancelStudyImport() {
        pendingImports.remove(getPendingImportDirectory());
        Context.current().response().removeSession(SESSION_TEMP_IMPORT_STUDY_DIR);
    }

    private void unzipUploadedFile(Path file, Path destDir) {
        try {
            ZipUtil.unzip(file, destDir);
        } catch (Exception e) {
            throw new ImportExportException("Unpacking ZIP archive failed");
        }
    }

    private Study deserializeStudy(Path tempDir) {
        Path[] studyFileList = unchecked(() -> ioUtils.findFiles(tempDir, "", "jas"));
        if (studyFileList.length != 1) {
            throw new ValidationException("File is not a valid JATOS study");
        }
        Path studyFile = studyFileList[0];
        return studyImportService.deserializeStudyProperties(studyFile);
    }

}
