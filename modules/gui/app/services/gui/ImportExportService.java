package services.gui;

import com.fasterxml.jackson.databind.node.ObjectNode;
import daos.common.ComponentDao;
import daos.common.StudyDao;
import exceptions.gui.ForbiddenException;
import exceptions.gui.NotFoundException;
import general.common.Common;
import general.common.MessagesStrings;
import general.gui.RequestScopeMessaging;
import models.common.Component;
import models.common.Study;
import models.common.User;
import play.Logger;
import play.Logger.ALogger;
import play.api.Application;
import play.libs.Json;
import play.mvc.Controller;
import utils.common.IOUtils;
import utils.common.JsonUtils;
import utils.common.ZipUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.ValidationException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service class for JATOS Controllers (not Publix).
 *
 * @author Kristian Lange
 */
@SuppressWarnings({"ResultOfMethodCallIgnored", "deprecation"})
@Singleton
public class ImportExportService {

    private static final ALogger LOGGER = Logger.of(ImportExportService.class);

    public static final String SESSION_UNZIPPED_STUDY_DIR = "tempStudyAssetsDir";

    private final Application app;
    private final Checker checker;
    private final StudyService studyService;
    private final BatchService batchService;
    private final ComponentService componentService;
    private final JsonUtils jsonUtils;
    private final IOUtils ioUtils;
    private final StudyDao studyDao;
    private final ComponentDao componentDao;

    @Inject
    ImportExportService(Application app, Checker checker,
            StudyService studyService, BatchService batchService, ComponentService componentService,
            JsonUtils jsonUtils, IOUtils ioUtils,
            StudyDao studyDao,
            ComponentDao componentDao) {
        this.app = app;
        this.checker = checker;
        this.studyService = studyService;
        this.batchService = batchService;
        this.componentService = componentService;
        this.jsonUtils = jsonUtils;
        this.ioUtils = ioUtils;
        this.studyDao = studyDao;
        this.componentDao = componentDao;
    }

    /**
     * Import a uploaded JATOS study archive
     * There are 5 possible cases:
     * (udir - name of uploaded study asset dir, cdir - name of current study asset dir)
     * <p>
     * 1) study exists  -  udir exists - udir == cdir : ask confirmation to overwrite study and/or dir
     * 2) study exists  -  udir exists - udir != cdir : ask confirmation to overwrite study and/or (dir && rename to cdir)
     * 3) study exists  - !udir exists : shouldn't happen, ask confirmation to overwrite study
     * 4) !study exists -  udir exists : ask to rename dir (generate new dir name)
     * 5) !study exists - !udir exists : new study - write both
     */
    public ObjectNode importStudy(User signedinUser, File file) throws IOException, ForbiddenException {
        File tempUnzippedStudyDir = unzipUploadedFile(file);
        Study uploadedStudy = unmarshalStudy(tempUnzippedStudyDir, false);

        // Remember study assets' dir name
        Controller.session(ImportExportService.SESSION_UNZIPPED_STUDY_DIR, tempUnzippedStudyDir.getName());

        checkForExistingComponents(uploadedStudy);

        Optional<Study> currentStudy = studyDao.findByUuid(uploadedStudy.getUuid());
        boolean uploadedDirExists = ioUtils.checkStudyAssetsDirExists(uploadedStudy.getDirName());
        if (currentStudy.isPresent() && !currentStudy.get().hasUser(signedinUser)) {
            throw new ForbiddenException(MessagesStrings.studyImportNotUser());
        }

        // Create JSON response
        ObjectNode responseJson = Json.mapper().createObjectNode();
        responseJson.put("studyExists", currentStudy.isPresent());
        if (currentStudy.isPresent()) {
            responseJson.put("uuid", currentStudy.get().getUuid());
            responseJson.put("currentStudyTitle", currentStudy.get().getTitle());
            responseJson.put("currentDirName", currentStudy.get().getDirName());
        } else {
            responseJson.put("uuid", uploadedStudy.getUuid());
        }
        responseJson.put("uploadedStudyTitle", uploadedStudy.getTitle());
        responseJson.put("uploadedDirName", uploadedStudy.getDirName());
        responseJson.put("uploadedDirExists", uploadedDirExists);
        if (!currentStudy.isPresent() && uploadedDirExists) {
            String newDirName = ioUtils.findNonExistingStudyAssetsDirName(uploadedStudy.getDirName());
            responseJson.put("newDirName", newDirName);
        }
        return responseJson;
    }

    /**
     * Checks if any of the imported study's components already exists in the database but are from a different study.
     * Something like this can only happen if the .jas file (the file describing the study in a JATOS study archive)
     * was not generated by JATOS.
     */
    private void checkForExistingComponents(Study uploadedStudy) throws ForbiddenException {
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
     * @param signedinUser          The signed-in user.
     * @param keepProperties        If true and the study exists already in JATOS the current properties are kept.
     *                              Default is `false` (properties are overwritten by default). If the study doesn't
     *                              already exist, this parameter has no effect.
     * @param keepAssets            If true and the study exists already in JATOS the current study assets directory is
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
    public Long importStudyConfirmed(User signedinUser, boolean keepProperties, boolean keepAssets,
            boolean keepCurrentAssetsName, boolean renameAssets) throws IOException, ForbiddenException, NotFoundException {
        File tempUnzippedStudyDir = getUnzippedStudyDir();
        if (tempUnzippedStudyDir == null) {
            LOGGER.error(".importStudyConfirmed: missing unzipped study directory in temp directory");
            throw new IOException("Missing unzipped study directory in tmp directory");
        }
        Study uploadedStudy = unmarshalStudy(tempUnzippedStudyDir, true);
        Optional<Study> currentStudy = studyDao.findByUuid(uploadedStudy.getUuid());

        // 1) study exists  -  udir exists - udir == cdir
        // 2) study exists  -  udir exists - udir != cdir
        // 3) study exists  - !udir exists
        if (currentStudy.isPresent()) {
            overwriteExistingStudy(signedinUser, keepProperties, keepAssets,
                    keepCurrentAssetsName, tempUnzippedStudyDir, uploadedStudy, currentStudy.get());
            return currentStudy.get().getId();
        }

        // 4) !study exists -  udir exists
        // 5) !study exists - !udir exists
        else if (!keepProperties && !keepAssets) {
            boolean uploadedDirExists = ioUtils.checkStudyAssetsDirExists(uploadedStudy.getDirName());
            if (uploadedDirExists && !renameAssets) {
                throw new ForbiddenException("Study assets directory already exists but doesn't belong to the study and 'renameAssets' is set to false.");
            }
            if (renameAssets) {
                String newDirName = ioUtils.findNonExistingStudyAssetsDirName(uploadedStudy.getDirName());
                uploadedStudy.setDirName(newDirName);
            }
            Study newStudy = importNewStudy(signedinUser, tempUnzippedStudyDir, uploadedStudy);
            return newStudy.getId();
        }

        else {
            throw new IOException("Impossible to import study: no new study and the existing study is not allowed to be overwritten");
        }
    }

    public void cleanupAfterStudyImport() {
        File tempUnzippedStudyDir = getUnzippedStudyDir();
        if (tempUnzippedStudyDir != null) {
            tempUnzippedStudyDir.delete();
        }
        Controller.session().remove(ImportExportService.SESSION_UNZIPPED_STUDY_DIR);
    }

    private void overwriteExistingStudy(User signedinUser, boolean keepProperties, boolean keepAssets,
            boolean keepCurrentAssetsName, File tempUnzippedStudyDir, Study uploadedStudy, Study currentStudy)
            throws IOException, ForbiddenException, NotFoundException {
        checker.checkStandardForStudy(currentStudy, currentStudy.getId(), signedinUser);
        checker.checkStudyLocked(currentStudy);

        if (!keepAssets) {
            String dirName = keepCurrentAssetsName ? currentStudy.getDirName() : uploadedStudy.getDirName();
            moveStudyAssetsDir(tempUnzippedStudyDir, currentStudy, dirName);
            RequestScopeMessaging.success(MessagesStrings.studyAssetsOverwritten(
                    dirName, currentStudy.getId(), currentStudy.getTitle()));
        }

        if (!keepProperties) {
            if (keepCurrentAssetsName || keepAssets) {
                studyService.updateStudyWithoutDirName(currentStudy, uploadedStudy, signedinUser);
            } else {
                studyService.updateStudy(currentStudy, uploadedStudy, signedinUser);
            }
            updateStudysComponents(currentStudy, uploadedStudy);
            RequestScopeMessaging.success(MessagesStrings
                    .studysPropertiesOverwritten(currentStudy.getId(), currentStudy.getTitle()));
        }
    }

    private Study importNewStudy(User signedinUser, File tempUnzippedStudyDir, Study importedStudy) throws IOException {
        moveStudyAssetsDir(tempUnzippedStudyDir, null, importedStudy.getDirName());
        Study newStudy = studyService.createAndPersistStudy(signedinUser, importedStudy);
        RequestScopeMessaging.success(MessagesStrings.importedNewStudy(
                importedStudy.getDirName(), importedStudy.getId(), importedStudy.getTitle()));
        return newStudy;
    }

    /**
     * Returns a JATOS study archive packed as ZIP. It returns the File object located in the system's temp directory.
     * The JATOS study archive will contain the study assets' directory and the study's JSON data (a .jas file).
     */
    public File createStudyExportZipFile(Study study) throws IOException {
        String studyFileName = ioUtils.generateFileName(study.getTitle());
        File studyAsJsonFile = File.createTempFile(studyFileName, ".jas");
        studyAsJsonFile.deleteOnExit();
        jsonUtils.studyAsJsonForIO(study, studyAsJsonFile);
        Path studyAssetsDir = Paths.get(ioUtils.generateStudyAssetsPath(study.getDirName()));

        List<Path> filesToZip = new ArrayList<>();
        filesToZip.add(studyAssetsDir);
        filesToZip.add(studyAsJsonFile.toPath());
        File zipFile = File.createTempFile("jatos_study_", "." + Common.getStudyArchiveSuffix());
        zipFile.deleteOnExit();
        ZipUtil.zipFiles(filesToZip, zipFile);

        studyAsJsonFile.delete();
        return zipFile;
    }

    /**
     * Update the components of the current study with the one of the imported
     * study.
     */
    private void updateStudysComponents(Study currentStudy, Study updatedStudy) {
        // Clear list and rebuild it from updated study
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
                // If the updated component doesn't exist in the current study
                // add it.
                componentService.createAndPersistComponent(currentStudy, updatedComponent);
            }
        }

        // Check whether any component from the current study are left that
        // aren't in the updated study. Add them to the end of the list and
        // put them into inactive (we don't remove them, because they could be
        // associated with results)
        for (Component currentComponent : currentComponentList) {
            currentComponent.setActive(false);
            currentStudy.addComponent(currentComponent);
        }

        studyDao.update(currentStudy);
    }

    /**
     * Deletes current study assets' dir and moves imported study assets' dir
     * from Java's temp dir to study assets root dir
     */
    private void moveStudyAssetsDir(File unzippedStudyDir, Study currentStudy,
            String studyAssetsDirName) throws IOException {
        if (currentStudy != null) {
            ioUtils.removeStudyAssetsDir(currentStudy.getDirName());
        }

        File[] dirArray = ioUtils.findDirectories(unzippedStudyDir);
        if (dirArray.length == 0) {
            // If a study assets dir is missing, create a new one.
            ioUtils.createStudyAssetsDir(studyAssetsDirName);
            RequestScopeMessaging.warning("There is no directory in the ZIP file - new study assets created.");
        } else if (dirArray.length == 1) {
            File studyAssetsDir = dirArray[0];
            ioUtils.moveStudyAssetsDir(studyAssetsDir, studyAssetsDirName);
        } else {
            throw new IOException("There are more than one directory in the ZIP file.");
        }
    }

    /**
     * Get unzipped study dir File object stored in Java's temp directory. Name
     * is stored in session. Discard session variable afterwards.
     */
    private File getUnzippedStudyDir() {
        String unzippedStudyDirName = Controller.session(SESSION_UNZIPPED_STUDY_DIR);
        if (unzippedStudyDirName == null || unzippedStudyDirName.trim().isEmpty()) {
            return null;
        }
        return new File(IOUtils.TMP_DIR, unzippedStudyDirName);
    }

    private File unzipUploadedFile(File file) throws IOException {
        File destDir;
        try {
            destDir = new File(IOUtils.TMP_DIR, "JatosImport_" + UUID.randomUUID());
            ZipUtil.unzip(file, destDir);
        } catch (IOException e) {
            LOGGER.warn(".unzipUploadedFile: unzipping failed");
            throw new IOException("Unpacking ZIP archive failed");
        }
        return destDir;
    }

    private Study unmarshalStudy(File tempDir, boolean deleteAfterwards) throws IOException {
        File[] studyFileList = ioUtils.findFiles(tempDir, "", "jas");
        if (studyFileList.length != 1) {
            throw new IOException("Study is invalid");
        }
        File studyFile = studyFileList[0];

        UploadUnmarshaller<Study> uploadUnmarshaller = app.injector().instanceOf(StudyUploadUnmarshaller.class);
        Study study = uploadUnmarshaller.unmarshalling(studyFile);

        try {
            studyService.validate(study);
            study.getComponentList().forEach(componentService::validate);
            study.getBatchList().forEach(batchService::validate);
        } catch (ValidationException e) {
            throw new IOException(e);
        }

        if (deleteAfterwards) {
            studyFile.delete();
        }
        return study;
    }

}
