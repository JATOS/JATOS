package services.gui;

import daos.common.ComponentDao;
import daos.common.StudyDao;
import exceptions.common.ForbiddenException;
import exceptions.common.ImportExportException;
import exceptions.common.JatosException;
import general.common.ApiEnvelope.ErrorCode;
import general.common.Common;
import http.common.Http.Context;
import general.common.MessagesStrings;
import json.common.JsonUtils;
import messaging.common.RequestScopeMessaging;
import models.common.Component;
import models.common.Study;
import models.common.User;
import play.Logger;
import play.Logger.ALogger;
import play.db.jpa.JPAApi;
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

/**
 * Service class for everything related to Import and Export.
 */
@Singleton
public class ImportExportService {

    private static final ALogger LOGGER = Logger.of(ImportExportService.class);

    public static final String SESSION_TEMP_IMPORT_STUDY_DIR = "tempImportStudyDir";

    private final JPAApi jpa;
    private final AuthorizationService authorizationService;
    private final StudyService studyService;
    private final BatchService batchService;
    private final ComponentService componentService;
    private final JsonUtils jsonUtils;
    private final IOUtils ioUtils;
    private final StudyDao studyDao;
    private final ComponentDao componentDao;
    private final StudyDeserializer studyDeserializer;

    @Inject
    ImportExportService(JPAApi jpa,
                        AuthorizationService authorizationService,
                        StudyService studyService,
                        BatchService batchService,
                        ComponentService componentService,
                        JsonUtils jsonUtils,
                        IOUtils ioUtils,
                        StudyDao studyDao,
                        ComponentDao componentDao,
                        StudyDeserializer studyDeserializer) {
        this.jpa = jpa;
        this.authorizationService = authorizationService;
        this.studyService = studyService;
        this.batchService = batchService;
        this.componentService = componentService;
        this.jsonUtils = jsonUtils;
        this.ioUtils = ioUtils;
        this.studyDao = studyDao;
        this.componentDao = componentDao;
        this.studyDeserializer = studyDeserializer;
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
        Path tempUnzippedStudyDir = unzipUploadedFile(file);
        Study uploadedStudy = deserializeStudy(tempUnzippedStudyDir);

        // Remember the study assets' dir name for the confirmation request
        Context.current().response().putSession(
                ImportExportService.SESSION_TEMP_IMPORT_STUDY_DIR, tempUnzippedStudyDir.getFileName().toString());

        checkForExistingComponents(uploadedStudy);

        Optional<Study> currentStudy = studyDao.findByUuid(uploadedStudy.getUuid());
        boolean uploadedDirExists = ioUtils.checkStudyAssetsDirExists(uploadedStudy.getDirName());
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        if (currentStudy.isPresent() && !currentStudy.get().hasUser(signedinUser)) {
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
    }

    /**
     * Checks if any of the imported study's components already exists in the database but are from a different study.
     * Something like this can only happen if the .jas file (the file describing the study in a JATOS study archive) was
     * not generated by JATOS.
     */
    private void checkForExistingComponents(Study uploadedStudy) {
        for (Component c : uploadedStudy.getComponentList()) {
            Optional<Component> existingComponent = componentDao.findByUuid(c.getUuid());
            if (existingComponent.isPresent()
                    && !existingComponent.get().getStudy().getUuid().equals(uploadedStudy.getUuid())) {
                throw new ForbiddenException("An component of the imported study has the same UUID (" + c.getUuid()
                        + ") as an existing component.");
            }
        }
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
        return jpa.withTransaction(em -> {
            Path tempUnzippedStudyDir = getUnzippedStudyDir();
            if (tempUnzippedStudyDir == null) {
                throw new JatosException("Missing unzipped study directory in tmp directory", ErrorCode.IO_ERROR);
            }
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
            boolean uploadedDirExists = ioUtils.checkStudyAssetsDirExists(uploadedStudy.getDirName());
            if (uploadedDirExists) {
                if (renameAssets) {
                    String newDirName = ioUtils.findNonExistingStudyAssetsDirName(uploadedStudy.getDirName());
                    uploadedStudy.setDirName(newDirName);
                } else {
                    throw new ForbiddenException("Cannot import study: a study assets directory with the same name exists already, but 'renameAssets' is set to false.");
                }
            }
            return importNewStudy(tempUnzippedStudyDir, uploadedStudy);
        });
    }

    public void cleanupAfterStudyImport() {
        Path tempUnzippedStudyDir = getUnzippedStudyDir();
        try {
            IOUtils.deleteRecursivelyIfExists(tempUnzippedStudyDir);
        } catch (Exception e) {
            LOGGER.error("cleanupAfterStudyImport: deleting temp directory failed", e);
        }
        Context.current().response().removeSession(ImportExportService.SESSION_TEMP_IMPORT_STUDY_DIR);
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
            updateStudysComponents(currentStudy, uploadedStudy);
            RequestScopeMessaging.success(MessagesStrings
                    .studysPropertiesOverwritten(currentStudy.getId(), currentStudy.getTitle()));
        }
    }

    private Study importNewStudy(Path tempUnzippedStudyDir, Study importedStudy) {
        unchecked(() -> moveStudyAssetsDir(tempUnzippedStudyDir, null, importedStudy.getDirName()));
        Study newStudy = studyService.createAndPersistStudy(importedStudy);
        RequestScopeMessaging.success(MessagesStrings.importedNewStudy(
                importedStudy.getDirName(), importedStudy.getId(), importedStudy.getTitle()));
        return newStudy;
    }

    /**
     * Returns a JATOS study archive packed as ZIP. It returns the File object located in the system's temp directory.
     * The JATOS study archive will contain the study assets' directory and the study's JSON data (a .jas file).
     */
    public Path createStudyExportZipFile(Study study) {
        Path studyAsJsonFile = null;
        Path zipFile = null;
        try {
            String studyFileName = ioUtils.generateFileName(study.getTitle());
            studyAsJsonFile = Files.createTempFile(studyFileName, ".jas");
            jsonUtils.studyAsJsonForIO(study, studyAsJsonFile);
            Path studyAssetsDir = IOUtils.generateStudyAssetsPath(study.getDirName());

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
     * Update the components of the current study with the one of the imported study.
     */
    private void updateStudysComponents(Study currentStudy, Study updatedStudy) {
        // Clear the list and rebuild it from the updated study
        List<Component> currentComponentList = new ArrayList<>(currentStudy.getComponentList());
        currentStudy.getComponentList().clear();

        for (Component updatedComponent : updatedStudy.getComponentList()) {
            Component currentComponent = null;
            // Find both matching components with the same UUID
            for (Component tempComponent : currentComponentList) {
                if (tempComponent.getUuid().equals(updatedComponent.getUuid())) {
                    currentComponent = tempComponent;
                    break;
                }
            }
            if (currentComponent != null) {
                componentService.updateProperties(currentComponent, updatedComponent);
                currentStudy.addComponent(currentComponent);
                currentComponentList.remove(currentComponent);
            } else {
                // If the updated component doesn't exist in the current study, add it.
                componentService.createAndPersistComponent(currentStudy, updatedComponent);
            }
        }

        // Check whether any component from the current study is left that isn't in the updated study. Add them to the
        // end of the list and put them into inactive (we don't remove them because they could be associated with results)
        for (Component currentComponent : currentComponentList) {
            currentComponent.setActive(false);
            currentStudy.addComponent(currentComponent);
        }

        studyDao.merge(currentStudy);
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
            throw new JatosException("There are more than one directory in the ZIP file.", ErrorCode.IO_ERROR);
        }
    }

    /**
     * Get unzipped study dir File object stored in Java's temp directory. Name is stored in session. Discard session
     * variable afterward.
     */
    private Path getUnzippedStudyDir() {
        Optional<String> unzippedStudyDirName = Context.current().response().session().get(SESSION_TEMP_IMPORT_STUDY_DIR);
        if (unzippedStudyDirName.isEmpty() || unzippedStudyDirName.get().trim().isEmpty()) {
            return null;
        }
        return IOUtils.tmpDir().resolve(unzippedStudyDirName.get());
    }

    private Path unzipUploadedFile(Path file) {
        Path destDir;
        try {
            destDir = IOUtils.tmpDir().resolve("JatosImport_" + UUID.randomUUID());
            ZipUtil.unzip(file, destDir);
        } catch (Exception e) {
            throw new ImportExportException("Unpacking ZIP archive failed");
        }
        return destDir;
    }

    private Study deserializeStudy(Path tempDir) {
        Path[] studyFileList = unchecked(() -> ioUtils.findFiles(tempDir, "", "jas"));
        if (studyFileList.length != 1) {
            throw new ValidationException("File is not a valid JATOS study");
        }
        Path studyFile = studyFileList[0];
        Study study = studyDeserializer.deserialize(studyFile);

        studyService.validate(study);
        study.getComponentList().forEach(componentService::validate);
        study.getBatchList().forEach(batchService::validate);

        return study;
    }

}
