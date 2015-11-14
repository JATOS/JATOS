package services.gui;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.ValidationException;

import models.common.Component;
import models.common.Study;
import models.common.User;
import play.Logger;
import play.mvc.Controller;
import play.mvc.Http.MultipartFormData.FilePart;
import utils.common.ComponentUploadUnmarshaller;
import utils.common.IOUtils;
import utils.common.JsonUtils;
import utils.common.StudyUploadUnmarshaller;
import utils.common.UploadUnmarshaller;
import utils.common.ZipUtil;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import daos.common.ComponentDao;
import daos.common.StudyDao;
import exceptions.gui.BadRequestException;
import exceptions.gui.ForbiddenException;
import general.common.MessagesStrings;
import general.gui.RequestScopeMessaging;

/**
 * Service class for JATOS Controllers (not Publix).
 * 
 * @author Kristian Lange
 */
@Singleton
public class ImportExportService {

	private static final String CLASS_NAME = ImportExportService.class
			.getSimpleName();

	public static final String COMPONENT_TITLE = "componentTitle";
	public static final String COMPONENT_EXISTS = "componentExists";
	public static final String DIR_PATH = "dirPath";
	public static final String DIR_EXISTS = "dirExists";
	public static final String STUDY_TITLE = "studyTitle";
	public static final String STUDY_EXISTS = "studyExists";
	public static final String STUDYS_DIR_CONFIRM = "studysDirConfirm";
	public static final String STUDYS_ENTITY_CONFIRM = "studysEntityConfirm";
	public static final String SESSION_UNZIPPED_STUDY_DIR = "tempStudyAssetsDir";
	public static final String SESSION_TEMP_COMPONENT_FILE = "tempComponentFile";

	private final StudyService studyService;
	private final ComponentService componentService;
	private final JsonUtils jsonUtils;
	private final StudyDao studyDao;
	private final ComponentDao componentDao;

	@Inject
	ImportExportService(StudyService studyService,
			ComponentService componentService, JsonUtils jsonUtils,
			StudyDao studyDao, ComponentDao componentDao) {
		this.studyService = studyService;
		this.componentService = componentService;
		this.jsonUtils = jsonUtils;
		this.studyDao = studyDao;
		this.componentDao = componentDao;
	}

	public ObjectNode importComponent(Study study, FilePart filePart)
			throws IOException {
		if (filePart == null) {
			throw new IOException(MessagesStrings.FILE_MISSING);
		}
		// Remember component's file name
		Controller.session(ImportExportService.SESSION_TEMP_COMPONENT_FILE,
				filePart.getFile().getName());

		// If wrong key the upload comes from the wrong form
		if (!filePart.getKey().equals(Component.COMPONENT)) {
			throw new IOException(MessagesStrings.NO_COMPONENT_UPLOAD);
		}

		Component uploadedComponent = unmarshalComponent(filePart.getFile());
		boolean componentExists = componentDao.findByUuid(
				uploadedComponent.getUuid(), study) != null;

		// Create JSON response
		ObjectNode objectNode = JsonUtils.OBJECTMAPPER.createObjectNode();
		objectNode.put(COMPONENT_EXISTS, componentExists);
		objectNode.put(COMPONENT_TITLE, uploadedComponent.getTitle());
		return objectNode;
	}

	public void importComponentConfirmed(Study study,
			String tempComponentFileName) throws IOException {
		File componentFile = getTempComponentFile(tempComponentFileName);
		if (componentFile == null) {
			Logger.warn(CLASS_NAME
					+ ".importComponentConfirmed: unzipping failed, "
					+ "couldn't find component file in temp directory");
			throw new IOException(MessagesStrings.IMPORT_OF_COMPONENT_FAILED);
		}
		Component uploadedComponent = unmarshalComponent(componentFile);
		Component currentComponent = componentDao.findByUuid(
				uploadedComponent.getUuid(), study);
		boolean componentExistsInStudy = (currentComponent != null);
		if (componentExistsInStudy) {
			componentService.updateProperties(currentComponent,
					uploadedComponent);
			RequestScopeMessaging.success(MessagesStrings
					.componentsPropertiesOverwritten(currentComponent.getId(),
							uploadedComponent.getTitle()));
		} else {
			componentDao.create(study, uploadedComponent);
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
			Controller.session().remove(
					ImportExportService.SESSION_TEMP_COMPONENT_FILE);
		}
	}

	public ObjectNode importStudy(User loggedInUser, File file)
			throws IOException, ForbiddenException {
		File tempUnzippedStudyDir = unzipUploadedFile(file);
		Study uploadedStudy = unmarshalStudy(tempUnzippedStudyDir, false);

		// Remember study assets' dir name
		Controller.session(ImportExportService.SESSION_UNZIPPED_STUDY_DIR,
				tempUnzippedStudyDir.getName());

		Study currentStudy = studyDao.findByUuid(uploadedStudy.getUuid());
		boolean studyExists = currentStudy != null;
		boolean dirExists = IOUtils.checkStudyAssetsDirExists(uploadedStudy
				.getDirName());
		checkStudyImport(loggedInUser, uploadedStudy, currentStudy,
				studyExists, dirExists);

		// Create JSON response
		ObjectNode objectNode = JsonUtils.OBJECTMAPPER.createObjectNode();
		objectNode.put(ImportExportService.STUDY_EXISTS, studyExists);
		objectNode.put(ImportExportService.STUDY_TITLE,
				uploadedStudy.getTitle());
		objectNode.put(ImportExportService.DIR_EXISTS, dirExists);
		objectNode
				.put(ImportExportService.DIR_PATH, uploadedStudy.getDirName());
		return objectNode;
	}

	public void importStudyConfirmed(User loggedInUser, JsonNode json)
			throws IOException, ForbiddenException, BadRequestException {
		if (json == null) {
			Logger.error(CLASS_NAME + ".importStudyConfirmed: JSON is null");
			throw new IOException(MessagesStrings.IMPORT_OF_STUDY_FAILED);
		}
		if (json.findPath(STUDYS_ENTITY_CONFIRM) == null
				|| json.findPath(STUDYS_DIR_CONFIRM) == null) {
			Logger.error(CLASS_NAME + ".importStudyConfirmed: "
					+ "JSON is malformed: " + STUDYS_ENTITY_CONFIRM + " or "
					+ STUDYS_DIR_CONFIRM + " missing");
			throw new IOException(MessagesStrings.IMPORT_OF_STUDY_FAILED);
		}
		Boolean studysEntityConfirm = json.findPath(STUDYS_ENTITY_CONFIRM)
				.asBoolean();
		Boolean studysDirConfirm = json.findPath(STUDYS_DIR_CONFIRM)
				.asBoolean();

		File tempUnzippedStudyDir = getUnzippedStudyDir();
		if (tempUnzippedStudyDir == null) {
			Logger.error(CLASS_NAME + ".importStudyConfirmed: "
					+ "missing unzipped study directory in temp directory");
			throw new IOException(MessagesStrings.IMPORT_OF_STUDY_FAILED);
		}
		Study importedStudy = unmarshalStudy(tempUnzippedStudyDir, true);
		Study currentStudy = studyDao.findByUuid(importedStudy.getUuid());

		boolean studyExists = (currentStudy != null);
		if (studyExists) {
			overwriteExistingStudy(loggedInUser, studysEntityConfirm,
					studysDirConfirm, tempUnzippedStudyDir, importedStudy,
					currentStudy);
		} else {
			importNewStudy(loggedInUser, tempUnzippedStudyDir, importedStudy);
		}
	}

	public void cleanupAfterStudyImport() {
		File tempUnzippedStudyDir = getUnzippedStudyDir();
		if (tempUnzippedStudyDir != null) {
			tempUnzippedStudyDir.delete();
		}
		Controller.session().remove(
				ImportExportService.SESSION_UNZIPPED_STUDY_DIR);
	}

	private void checkStudyImport(User loggedInUser, Study uploadedStudy,
			Study currentStudy, boolean studyExists, boolean dirExists)
			throws ForbiddenException {
		if (studyExists && !currentStudy.hasUser(loggedInUser)) {
			String errorMsg = MessagesStrings.studyImportNotUser(currentStudy
					.getTitle());
			throw new ForbiddenException(errorMsg);
		}
		if (dirExists
				&& (currentStudy == null || !currentStudy.getDirName().equals(
						uploadedStudy.getDirName()))) {
			String errorMsg = MessagesStrings
					.studyAssetsDirExistsBelongsToDifferentStudy(uploadedStudy
							.getDirName());
			throw new ForbiddenException(errorMsg);
		}
	}

	private void overwriteExistingStudy(User loggedInUser,
			Boolean studyEntityConfirm, Boolean studysDirConfirm,
			File tempUnzippedStudyDir, Study importedStudy, Study currentStudy)
			throws IOException, ForbiddenException, BadRequestException {
		studyService.checkStandardForStudy(currentStudy, currentStudy.getId(),
				loggedInUser);
		studyService.checkStudyLocked(currentStudy);
		if (studysDirConfirm) {
			if (studyEntityConfirm) {
				moveStudyAssetsDir(tempUnzippedStudyDir, currentStudy,
						importedStudy.getDirName());
			} else {
				// If we don't overwrite the properties, don't use the
				// updated study assets' dir name
				moveStudyAssetsDir(tempUnzippedStudyDir, currentStudy,
						currentStudy.getDirName());
			}
			RequestScopeMessaging.success(MessagesStrings
					.studyAssetsOverwritten(importedStudy.getDirName(),
							currentStudy.getId()));
		}
		if (studyEntityConfirm) {
			if (studysDirConfirm) {
				studyService.updateStudy(currentStudy, importedStudy);
			} else {
				// If we don't overwrite the current study dir with the
				// uploaded one, don't change the study dir name in the
				// properties
				studyService.updateStudyWithoutDirName(currentStudy,
						importedStudy);
			}
			updateStudysComponents(currentStudy, importedStudy);
			RequestScopeMessaging.success(MessagesStrings
					.studysPropertiesOverwritten(currentStudy.getId()));
		}
	}

	private void importNewStudy(User loggedInUser, File tempUnzippedStudyDir,
			Study importedStudy) throws IOException {
		moveStudyAssetsDir(tempUnzippedStudyDir, null,
				importedStudy.getDirName());
		studyService.createStudy(loggedInUser, importedStudy);
		RequestScopeMessaging.success(MessagesStrings.importedNewStudy(
				importedStudy.getDirName(), importedStudy.getId()));
	}

	public File createStudyExportZipFile(Study study) throws IOException {
		String studyFileName = IOUtils.generateFileName(study.getTitle());
		String studyFileSuffix = "." + IOUtils.STUDY_FILE_SUFFIX;
		File studyAsJsonFile = File.createTempFile(studyFileName,
				studyFileSuffix);
		studyAsJsonFile.deleteOnExit();
		jsonUtils.studyAsJsonForIO(study, studyAsJsonFile);
		String studyAssetsDirPath = IOUtils.generateStudyAssetsPath(study
				.getDirName());
		File zipFile = ZipUtil.zipStudy(studyAssetsDirPath, study.getDirName(),
				studyAsJsonFile.getAbsolutePath());
		studyAsJsonFile.delete();
		return zipFile;
	}

	/**
	 * Update the components of the current study with the one of the imported
	 * study.
	 */
	private void updateStudysComponents(Study currentStudy, Study updatedStudy) {
		// Clear list and rebuild it from updated study
		List<Component> currentComponentList = new ArrayList<>(
				currentStudy.getComponentList());
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
				componentService.updateProperties(currentComponent,
						updatedComponent);
				currentStudy.addComponent(currentComponent);
				currentComponentList.remove(currentComponent);
			} else {
				// If the updated component doesn't exist in the current study
				// add it.
				componentDao.create(currentStudy, updatedComponent);
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
			IOUtils.removeStudyAssetsDir(currentStudy.getDirName());
		}

		File[] dirArray = IOUtils.findDirectories(unzippedStudyDir);
		if (dirArray.length == 0) {
			// If a study assets dir is missing, create a new one.
			IOUtils.createStudyAssetsDir(studyAssetsDirName);
			RequestScopeMessaging
					.warning(MessagesStrings.NO_DIR_IN_ZIP_CREATED_NEW);
		} else if (dirArray.length == 1) {
			File studyAssetsDir = dirArray[0];
			IOUtils.moveStudyAssetsDir(studyAssetsDir, studyAssetsDirName);
		} else {
			throw new IOException(MessagesStrings.MORE_THAN_ONE_DIR_IN_ZIP);
		}
	}

	/**
	 * Get component's File object. Name is stored in session. Discard session
	 * variable afterwards.
	 */
	private File getTempComponentFile(String tempComponentFileName) {
		if (tempComponentFileName == null
				|| tempComponentFileName.trim().isEmpty()) {
			return null;
		}
		return new File(System.getProperty("java.io.tmpdir"),
				tempComponentFileName);
	}

	/**
	 * Get unzipped study dir File object stored in Java's temp directory. Name
	 * is stored in session. Discard session variable afterwards.
	 */
	private File getUnzippedStudyDir() {
		String unzippedStudyDirName = Controller
				.session(SESSION_UNZIPPED_STUDY_DIR);
		if (unzippedStudyDirName == null
				|| unzippedStudyDirName.trim().isEmpty()) {
			return null;
		}
		File unzippedStudyDir = new File(System.getProperty("java.io.tmpdir"),
				unzippedStudyDirName);
		return unzippedStudyDir;
	}

	private File unzipUploadedFile(File file) throws IOException {
		File tempDir = null;
		try {
			tempDir = ZipUtil.unzip(file);
		} catch (IOException e) {
			Logger.warn(CLASS_NAME + ".unzipUploadedFile: unzipping failed", e);
			throw new IOException(MessagesStrings.IMPORT_OF_STUDY_FAILED);
		}
		return tempDir;
	}

	private Component unmarshalComponent(File file) throws IOException {
		UploadUnmarshaller<Component> uploadUnmarshaller = new ComponentUploadUnmarshaller();
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
		File[] studyFileList = IOUtils.findFiles(tempDir, "",
				IOUtils.STUDY_FILE_SUFFIX);
		if (studyFileList.length != 1) {
			throw new IOException(MessagesStrings.STUDY_INVALID);
		}
		File studyFile = studyFileList[0];

		UploadUnmarshaller<Study> uploadUnmarshaller = new StudyUploadUnmarshaller();
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
