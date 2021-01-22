package services.gui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import daos.common.ComponentDao;
import daos.common.StudyDao;
import exceptions.gui.BadRequestException;
import exceptions.gui.ForbiddenException;
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
import play.mvc.Http.MultipartFormData.FilePart;
import utils.common.IOUtils;
import utils.common.JsonUtils;
import utils.common.ZipUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.ValidationException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service class for JATOS Controllers (not Publix).
 *
 * @author Kristian Lange
 */
@Singleton
public class ImportExportService {

    private static final ALogger LOGGER = Logger.of(ImportExportService.class);

    public static final String SESSION_UNZIPPED_STUDY_DIR = "tempStudyAssetsDir";
    public static final String SESSION_TEMP_COMPONENT_FILE = "tempComponentFile";

    private final Application app;
    private final Checker checker;
    private final StudyService studyService;
    private final ComponentService componentService;
    private final JsonUtils jsonUtils;
    private final IOUtils ioUtils;
    private final StudyDao studyDao;
    private final ComponentDao componentDao;

    @Inject
    ImportExportService(Application app, Checker checker,
            StudyService studyService, ComponentService componentService,
            JsonUtils jsonUtils, IOUtils ioUtils,
            StudyDao studyDao,
            ComponentDao componentDao) {
        this.app = app;
        this.checker = checker;
        this.studyService = studyService;
        this.componentService = componentService;
        this.jsonUtils = jsonUtils;
        this.ioUtils = ioUtils;
        this.studyDao = studyDao;
        this.componentDao = componentDao;
    }

    public ObjectNode importComponent(Study study, FilePart<?> filePart)
            throws IOException {
        if (filePart == null) {
            throw new IOException(MessagesStrings.FILE_MISSING);
        }
        File file = (File) filePart.getFile();

        // Remember component's file name
        Controller.session(ImportExportService.SESSION_TEMP_COMPONENT_FILE, file.getName());

        // If wrong key the upload comes from the wrong form
        if (!filePart.getKey().equals(Component.COMPONENT)) {
            throw new IOException(MessagesStrings.NO_COMPONENT_UPLOAD);
        }

        Component uploadedComponent = unmarshalComponent(file);
        boolean componentExists =
                componentDao.findByUuid(uploadedComponent.getUuid(), study).isPresent();

        // Move uploaded component file to Java's tmp folder
        Path source = file.toPath();
        Path target = getTempComponentFile(file.getName()).toPath();
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);

        // Create JSON response
        ObjectNode objectNode = Json.mapper().createObjectNode();
        objectNode.put("componentExists", componentExists);
        objectNode.put("componentTitle", uploadedComponent.getTitle());
        return objectNode;
    }

    public void importComponentConfirmed(Study study, String tempComponentFileName)
            throws IOException {
        File componentFile = getTempComponentFile(tempComponentFileName);
        if (componentFile == null) {
            LOGGER.warn(".importComponentConfirmed: unzipping failed, "
                    + "couldn't find component file in temp directory");
            throw new IOException(MessagesStrings.IMPORT_OF_COMPONENT_FAILED);
        }
        Component uploadedComponent = unmarshalComponent(componentFile);
        Optional<Component> currentComponent =
                componentDao.findByUuid(uploadedComponent.getUuid(), study);
        if (currentComponent.isPresent()) {
            componentService.updateProperties(currentComponent.get(), uploadedComponent);
            RequestScopeMessaging.success(MessagesStrings.componentsPropertiesOverwritten(
                    currentComponent.get().getId(), uploadedComponent.getTitle()));
        } else {
            componentService.createAndPersistComponent(study, uploadedComponent);
            RequestScopeMessaging.success(MessagesStrings.importedNewComponent(
                    uploadedComponent.getId(), uploadedComponent.getTitle()));
        }
    }

    public void cleanupAfterComponentImport() {
        String tempComponentFileName = Controller
                .session(ImportExportService.SESSION_TEMP_COMPONENT_FILE);
        if (tempComponentFileName != null) {
            File componentFile = getTempComponentFile(tempComponentFileName);
            if (componentFile != null) {
                componentFile.delete();
            }
            Controller.session().remove(ImportExportService.SESSION_TEMP_COMPONENT_FILE);
        }
    }

    /**
     * Import a uploaded study: there are 5 possible cases:
     * (udir - name of uploaded study asset dir, cdir - name of current study asset dir)
     * <p>
     * 1) study exists  -  udir exists - udir == cdir : ask confirmation to overwrite study and/or dir
     * 2) study exists  -  udir exists - udir != cdir : ask confirmation to overwrite study and/or (dir && rename to cdir)
     * 3) study exists  - !udir exists : shouldn't happen, ask confirmation to overwrite study
     * 4) !study exists -  udir exists : ask to rename dir (generate new dir name)
     * 5) !study exists - !udir exists : new study - write both
     */
    public ObjectNode importStudy(User loggedInUser, File file)
            throws IOException, ForbiddenException {
        File tempUnzippedStudyDir = unzipUploadedFile(file);
        Study uploadedStudy = unmarshalStudy(tempUnzippedStudyDir, false);

        // Remember study assets' dir name
        Controller.session(ImportExportService.SESSION_UNZIPPED_STUDY_DIR,
                tempUnzippedStudyDir.getName());

        Optional<Study> currentStudy = studyDao.findByUuid(uploadedStudy.getUuid());
        boolean uploadedDirExists = ioUtils.checkStudyAssetsDirExists(uploadedStudy.getDirName());
        if (currentStudy.isPresent() && !currentStudy.get().hasUser(loggedInUser)) {
            String errorMsg = MessagesStrings.studyImportNotUser(uploadedStudy.getTitle());
            throw new ForbiddenException(errorMsg);
        }

        // Create JSON response
        ObjectNode responseJson = Json.mapper().createObjectNode();
        responseJson.put("studyExists", currentStudy.isPresent());
        if (currentStudy.isPresent()) {
            responseJson.put("currentStudyTitle", currentStudy.get().getTitle());
            responseJson.put("currentStudyUuid", currentStudy.get().getUuid());
            responseJson.put("currentDirName", currentStudy.get().getDirName());
        }
        responseJson.put("uploadedStudyTitle", uploadedStudy.getTitle());
        responseJson.put("uploadedStudyUuid", uploadedStudy.getUuid());
        responseJson.put("uploadedDirName", uploadedStudy.getDirName());
        responseJson.put("uploadedDirExists", uploadedDirExists);
        if (!currentStudy.isPresent() && uploadedDirExists) {
            String newDirName =
                    ioUtils.findNonExistingStudyAssetsDirName(uploadedStudy.getDirName());
            responseJson.put("newDirName", newDirName);
        }
        return responseJson;
    }

    public void importStudyConfirmed(User loggedInUser, JsonNode json)
            throws IOException, ForbiddenException, BadRequestException {
        if (json == null || json.findPath("overwriteStudysProperties") == null ||
                json.findPath("overwriteStudysDir") == null) {
            LOGGER.error(".importStudyConfirmed: " + "JSON is malformed");
            throw new IOException(MessagesStrings.IMPORT_OF_STUDY_FAILED);
        }
        Boolean overwriteStudysProperties = json.findPath("overwriteStudysProperties").asBoolean();
        Boolean overwriteStudysDir = json.findPath("overwriteStudysDir").asBoolean();
        boolean keepCurrentDirName = json.findPath("keepCurrentDirName").booleanValue();
        boolean renameDir = json.findPath("renameDir").booleanValue();

        File tempUnzippedStudyDir = getUnzippedStudyDir();
        if (tempUnzippedStudyDir == null) {
            LOGGER.error(".importStudyConfirmed: "
                    + "missing unzipped study directory in temp directory");
            throw new IOException(MessagesStrings.IMPORT_OF_STUDY_FAILED);
        }
        Study uploadedStudy = unmarshalStudy(tempUnzippedStudyDir, true);
        Optional<Study> currentStudy = studyDao.findByUuid(uploadedStudy.getUuid());

        // 1) study exists  -  udir exists - udir == cdir
        // 2) study exists  -  udir exists - udir != cdir
        // 3) study exists  - !udir exists
        if (currentStudy.isPresent()) {
            overwriteExistingStudy(loggedInUser, overwriteStudysProperties, overwriteStudysDir,
                    keepCurrentDirName, tempUnzippedStudyDir, uploadedStudy, currentStudy.get());
            return;
        }

        // 4) !study exists -  udir exists
        // 5) !study exists - !udir exists
        if (overwriteStudysProperties && overwriteStudysDir) {
            boolean uploadedDirExists =
                    ioUtils.checkStudyAssetsDirExists(uploadedStudy.getDirName());
            if (uploadedDirExists && !renameDir) return;
            if (renameDir) {
                String newDirName =
                        ioUtils.findNonExistingStudyAssetsDirName(uploadedStudy.getDirName());
                uploadedStudy.setDirName(newDirName);
            }
            importNewStudy(loggedInUser, tempUnzippedStudyDir, uploadedStudy);
        }
    }

    public void cleanupAfterStudyImport() {
        File tempUnzippedStudyDir = getUnzippedStudyDir();
        if (tempUnzippedStudyDir != null) {
            tempUnzippedStudyDir.delete();
        }
        Controller.session().remove(ImportExportService.SESSION_UNZIPPED_STUDY_DIR);
    }

    private void overwriteExistingStudy(User loggedInUser,
            boolean overwriteStudysProperties, boolean overwriteStudysDir,
            boolean keepCurrentDirName,
            File tempUnzippedStudyDir, Study uploadedStudy, Study currentStudy)
            throws IOException, ForbiddenException, BadRequestException {
        checker.checkStandardForStudy(currentStudy, currentStudy.getId(), loggedInUser);
        checker.checkStudyLocked(currentStudy);

        if (overwriteStudysDir) {
            String dirName =
                    keepCurrentDirName ? currentStudy.getDirName() : uploadedStudy.getDirName();
            moveStudyAssetsDir(tempUnzippedStudyDir, currentStudy, dirName);
            RequestScopeMessaging.success(MessagesStrings.studyAssetsOverwritten(
                    dirName, currentStudy.getId(), currentStudy.getTitle()));
        }

        if (overwriteStudysProperties) {
            if (keepCurrentDirName || !overwriteStudysDir) {
                studyService.updateStudyWithoutDirName(currentStudy, uploadedStudy, loggedInUser);
            } else {
                studyService.updateStudy(currentStudy, uploadedStudy, loggedInUser);
            }
            updateStudysComponents(currentStudy, uploadedStudy);
            RequestScopeMessaging.success(MessagesStrings
                    .studysPropertiesOverwritten(currentStudy.getId(), currentStudy.getTitle()));
        }
    }

    private void importNewStudy(User loggedInUser, File tempUnzippedStudyDir,
            Study importedStudy) throws IOException {
        moveStudyAssetsDir(tempUnzippedStudyDir, null, importedStudy.getDirName());
        studyService.createAndPersistStudy(loggedInUser, importedStudy);
        RequestScopeMessaging.success(MessagesStrings.importedNewStudy(
                importedStudy.getDirName(), importedStudy.getId(), importedStudy.getTitle()));
    }

    /**
     * Zips a study. It returns a File object with the name 'study.zip' within
     * the system's temp directory. The zip file will contain the study assets'
     * directory and the study's JSON data (a .jas file).
     */
    public File createStudyExportZipFile(Study study) throws IOException {
        String studyFileName = ioUtils.generateFileName(study.getTitle());
        String studyFileSuffix = "." + IOUtils.STUDY_FILE_SUFFIX;
        File studyAsJsonFile = File.createTempFile(studyFileName, studyFileSuffix);
        studyAsJsonFile.deleteOnExit();
        jsonUtils.studyAsJsonForIO(study, studyAsJsonFile);
        Path studyAssetsDir = Paths.get(ioUtils.generateStudyAssetsPath(study.getDirName()));

        List<Path> filesToZip = new ArrayList<>();
        filesToZip.add(studyAssetsDir);
        filesToZip.add(studyAsJsonFile.toPath());
        String zipFileName = ioUtils.generateFileName(study.getTitle());
        File zipFile = File.createTempFile(zipFileName, "." + IOUtils.JZIP_FILE_SUFFIX);
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
                if (tempComponent.getUuid()
                        .equals(updatedComponent.getUuid())) {
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
            RequestScopeMessaging.warning(MessagesStrings.NO_DIR_IN_ZIP_CREATED_NEW);
        } else if (dirArray.length == 1) {
            File studyAssetsDir = dirArray[0];
            ioUtils.moveStudyAssetsDir(studyAssetsDir, studyAssetsDirName);
        } else {
            throw new IOException(MessagesStrings.MORE_THAN_ONE_DIR_IN_ZIP);
        }
    }

    /**
     * Get component's File object. Name is stored in session. Discard session
     * variable afterwards.
     */
    private File getTempComponentFile(String tempComponentFileName) {
        if (tempComponentFileName == null || tempComponentFileName.trim().isEmpty()) {
            return null;
        }
        return new File(IOUtils.TMP_DIR, tempComponentFileName);
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
        File unzippedStudyDir = new File(IOUtils.TMP_DIR, unzippedStudyDirName);
        return unzippedStudyDir;
    }

    private File unzipUploadedFile(File file) throws IOException {
        File destDir;
        try {
            destDir = new File(IOUtils.TMP_DIR, "JatosImport_" + UUID.randomUUID().toString());
            ZipUtil.unzip(file, destDir);
        } catch (IOException e) {
            LOGGER.warn(".unzipUploadedFile: unzipping failed", e);
            throw new IOException(MessagesStrings.IMPORT_OF_STUDY_FAILED);
        }
        return destDir;
    }

    private Component unmarshalComponent(File file) throws IOException {
        UploadUnmarshaller<Component> uploadUnmarshaller =
                app.injector().instanceOf(ComponentUploadUnmarshaller.class);
        Component component = uploadUnmarshaller.unmarshalling(file);
        try {
            componentService.validate(component);
        } catch (ValidationException e) {
            throw new IOException(e);
        }
        return component;
    }

    private Study unmarshalStudy(File tempDir, boolean deleteAfterwards)
            throws IOException {
        File[] studyFileList = ioUtils.findFiles(tempDir, "", IOUtils.STUDY_FILE_SUFFIX);
        if (studyFileList.length != 1) {
            throw new IOException(MessagesStrings.STUDY_INVALID);
        }
        File studyFile = studyFileList[0];

        UploadUnmarshaller<Study> uploadUnmarshaller =
                app.injector().instanceOf(StudyUploadUnmarshaller.class);
        Study study = uploadUnmarshaller.unmarshalling(studyFile);

        try {
            studyService.validate(study);
        } catch (ValidationException e) {
            throw new IOException(e);
        }

        if (deleteAfterwards) {
            studyFile.delete();
        }
        return study;
    }

}
