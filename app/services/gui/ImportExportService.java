package services.gui;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import models.ComponentModel;
import models.StudyModel;
import models.UserModel;
import persistance.ComponentDao;
import persistance.StudyDao;
import play.mvc.Controller;
import play.mvc.Http.MultipartFormData.FilePart;
import services.RequestScopeMessaging;
import utils.IOUtils;
import utils.JsonUtils;
import utils.JsonUtils.UploadUnmarshaller;
import utils.ZipUtil;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import exceptions.BadRequestException;
import exceptions.ForbiddenException;

/**
 * Service class for JATOS Controllers (not Publix).
 * 
 * @author Kristian Lange
 */
@Singleton
public class ImportExportService extends Controller {

	public static final String COMPONENT_TITLE = "componentTitle";
	public static final String COMPONENT_EXISTS = "componentExists";
	public static final String DIR_PATH = "dirPath";
	public static final String DIR_EXISTS = "dirExists";
	public static final String STUDY_TITLE = "studyTitle";
	public static final String STUDY_EXISTS = "studyExists";
	public static final String STUDYS_DIR_CONFIRM = "studysDirConfirm";
	public static final String STUDYS_PROPERTIES_CONFIRM = "studysPropertiesConfirm";
	public static final String SESSION_UNZIPPED_STUDY_DIR = "tempStudyAssetsDir";
	public static final String SESSION_TEMP_COMPONENT_FILE = "tempComponentFile";
	public static final String JQDOWNLOAD_COOKIE_NAME = "Set-Cookie";
	public static final String JQDOWNLOAD_COOKIE_CONTENT = "fileDownload=true; path=/";

	private final StudyService studyService;
	private final JsonUtils jsonUtils;
	private final StudyDao studyDao;
	private final ComponentDao componentDao;

	@Inject
	ImportExportService(StudyService studyService, JsonUtils jsonUtils,
			StudyDao studyDao, ComponentDao componentDao) {
		this.studyService = studyService;
		this.jsonUtils = jsonUtils;
		this.studyDao = studyDao;
		this.componentDao = componentDao;
	}

	public ObjectNode importComponent(StudyModel study, FilePart filePart)
			throws IOException {
		if (filePart == null) {
			throw new IOException(MessagesStrings.FILE_MISSING);
		}
		// Remember component's file name
		session(ImportExportService.SESSION_TEMP_COMPONENT_FILE, filePart
				.getFile().getName());

		// If wrong key the upload comes from the wrong form
		if (!filePart.getKey().equals(ComponentModel.COMPONENT)) {
			throw new IOException(MessagesStrings.NO_COMPONENT_UPLOAD);
		}

		ComponentModel uploadedComponent = unmarshalComponent(
				filePart.getFile(), study);

		boolean componentExists = componentDao.findByUuid(
				uploadedComponent.getUuid(), study) != null;

		// Create JSON response
		ObjectNode objectNode = JsonUtils.OBJECTMAPPER.createObjectNode();
		objectNode.put(COMPONENT_EXISTS, componentExists);
		objectNode.put(COMPONENT_TITLE, uploadedComponent.getTitle());
		return objectNode;
	}

	public void importComponentConfirmed(StudyModel study,
			String tempComponentFileName) throws IOException {
		File componentFile = getTempComponentFile(study, tempComponentFileName);
		if (componentFile == null) {
			throw new IOException(MessagesStrings.IMPORT_OF_COMPONENT_FAILED);
		}
		ComponentModel uploadedComponent = unmarshalComponent(componentFile,
				study);
		ComponentModel currentComponent = componentDao.findByUuid(
				uploadedComponent.getUuid(), study);
		boolean componentExists = (currentComponent != null);
		if (componentExists) {
			componentDao.updateProperties(currentComponent, uploadedComponent);
			RequestScopeMessaging.success(MessagesStrings
					.componentsPropertiesOverwritten(currentComponent.getId(),
							uploadedComponent.getTitle()));
		} else {
			componentDao.create(study, uploadedComponent);
			RequestScopeMessaging.success(MessagesStrings.importedNewComponent(
					uploadedComponent.getId(), uploadedComponent.getTitle()));
		}
	}

	public void cleanupAfterComponentImport(StudyModel study) {
		String tempComponentFileName = session(ImportExportService.SESSION_TEMP_COMPONENT_FILE);
		if (tempComponentFileName != null) {
			File componentFile = getTempComponentFile(study,
					tempComponentFileName);
			if (componentFile != null) {
				componentFile.delete();
			}
			session().remove(ImportExportService.SESSION_TEMP_COMPONENT_FILE);
		}
	}

	public ObjectNode importStudy(UserModel loggedInUser, File file)
			throws IOException, ForbiddenException {
		File tempUnzippedStudyDir = unzipUploadedFile(file);
		StudyModel uploadedStudy = unmarshalStudy(tempUnzippedStudyDir, false);

		// Remember study assets' dir name
		session(ImportExportService.SESSION_UNZIPPED_STUDY_DIR,
				tempUnzippedStudyDir.getName());

		StudyModel currentStudy = studyDao.findByUuid(uploadedStudy.getUuid());
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

	public void importStudyConfirmed(UserModel loggedInUser, JsonNode json)
			throws IOException, ForbiddenException, BadRequestException {
		if (json == null) {
			throw new IOException(MessagesStrings.IMPORT_OF_STUDY_FAILED);
		}
		Boolean studysPropertiesConfirm = json.findPath(
				STUDYS_PROPERTIES_CONFIRM).asBoolean();
		Boolean studysDirConfirm = json.findPath(STUDYS_DIR_CONFIRM)
				.asBoolean();
		if (studysPropertiesConfirm == null || studysDirConfirm == null) {
			throw new IOException(MessagesStrings.IMPORT_OF_STUDY_FAILED);
		}

		File tempUnzippedStudyDir = getUnzippedStudyDir();
		if (tempUnzippedStudyDir == null) {
			throw new IOException(MessagesStrings.IMPORT_OF_STUDY_FAILED);
		}
		StudyModel importedStudy = unmarshalStudy(tempUnzippedStudyDir, true);
		StudyModel currentStudy = studyDao.findByUuid(importedStudy.getUuid());

		boolean studyExists = (currentStudy != null);
		if (studyExists) {
			overwriteExistingStudy(loggedInUser, studysPropertiesConfirm,
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
		session().remove(ImportExportService.SESSION_UNZIPPED_STUDY_DIR);
	}

	private void checkStudyImport(UserModel loggedInUser,
			StudyModel uploadedStudy, StudyModel currentStudy,
			boolean studyExists, boolean dirExists) throws IOException,
			ForbiddenException {
		if (studyExists && !currentStudy.hasMember(loggedInUser)) {
			String errorMsg = MessagesStrings.studyImportNotMember(currentStudy
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

	private void overwriteExistingStudy(UserModel loggedInUser,
			Boolean studysPropertiesConfirm, Boolean studysDirConfirm,
			File tempUnzippedStudyDir, StudyModel importedStudy,
			StudyModel currentStudy) throws IOException, ForbiddenException,
			BadRequestException {
		studyService.checkStandardForStudy(currentStudy, currentStudy.getId(),
				loggedInUser);
		studyService.checkStudyLocked(currentStudy);
		if (studysDirConfirm) {
			if (studysPropertiesConfirm) {
				moveStudyAssetsDir(tempUnzippedStudyDir, currentStudy,
						importedStudy.getDirName(), loggedInUser);
			} else {
				// If we don't overwrite the properties, don't use the
				// updated study assets' dir name
				moveStudyAssetsDir(tempUnzippedStudyDir, currentStudy,
						currentStudy.getDirName(), loggedInUser);
			}
			RequestScopeMessaging.success(MessagesStrings
					.studyAssetsOverwritten(importedStudy.getDirName(),
							currentStudy.getId()));
		}
		if (studysPropertiesConfirm) {
			if (studysDirConfirm) {
				studyDao.updateProperties(currentStudy, importedStudy);
			} else {
				// If we don't overwrite the current study dir with the
				// uploaded one, don't change the study dir name in the
				// properties
				studyDao.updatePropertiesWODirName(currentStudy, importedStudy);
			}
			updateStudysComponents(currentStudy, importedStudy);
			RequestScopeMessaging.success(MessagesStrings
					.studysPropertiesOverwritten(currentStudy.getId()));
		}
	}

	private void importNewStudy(UserModel loggedInUser,
			File tempUnzippedStudyDir, StudyModel importedStudy)
			throws IOException {
		moveStudyAssetsDir(tempUnzippedStudyDir, null,
				importedStudy.getDirName(), loggedInUser);
		studyDao.create(importedStudy, loggedInUser);
		RequestScopeMessaging.success(MessagesStrings.importedNewStudy(
				importedStudy.getDirName(), importedStudy.getId()));
	}

	public File createStudyExportZipFile(StudyModel study) throws IOException {
		File zipFile = null;
		String studyFileName = IOUtils.generateFileName(study.getTitle());
		String studyFileSuffix = "." + IOUtils.STUDY_FILE_SUFFIX;
		File studyAsJsonFile = File.createTempFile(studyFileName,
				studyFileSuffix);
		studyAsJsonFile.deleteOnExit();
		jsonUtils.asJsonForIO(study, studyAsJsonFile);
		String studyAssetsDirPath = IOUtils.generateStudyAssetsPath(study
				.getDirName());
		zipFile = ZipUtil.zipStudy(studyAssetsDirPath, study.getDirName(),
				studyAsJsonFile.getAbsolutePath());
		studyAsJsonFile.delete();
		return zipFile;
	}

	/**
	 * Update the components of the current study with the one of the imported
	 * study.
	 */
	private void updateStudysComponents(StudyModel currentStudy,
			StudyModel updatedStudy) {
		// Clear list and rebuild it from updated study
		List<ComponentModel> currentComponentList = new ArrayList<ComponentModel>(
				currentStudy.getComponentList());
		currentStudy.getComponentList().clear();

		for (ComponentModel updatedComponent : updatedStudy.getComponentList()) {
			ComponentModel currentComponent = null;
			// Find both matching components with the same UUID
			for (ComponentModel tempComponent : currentComponentList) {
				if (tempComponent.getUuid().equals(updatedComponent.getUuid())) {
					currentComponent = tempComponent;
					break;
				}
			}
			if (currentComponent != null) {
				componentDao.updateProperties(currentComponent,
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
		for (ComponentModel currentComponent : currentComponentList) {
			currentComponent.setActive(false);
			currentStudy.addComponent(currentComponent);
		}

		studyDao.update(currentStudy);
	}

	/**
	 * Deletes current study assets' dir and moves imported study assets' dir
	 * from Java's temp dir to study assets root dir
	 */
	private void moveStudyAssetsDir(File unzippedStudyDir,
			StudyModel currentStudy, String studyAssetsDirName,
			UserModel loggedInUser) throws IOException {
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
	 * 
	 * @throws IOException
	 */
	private File getTempComponentFile(StudyModel study,
			String tempComponentFileName) {
		if (tempComponentFileName == null
				|| tempComponentFileName.trim().isEmpty()) {
			return null;
		}
		File tempComponentFile = new File(System.getProperty("java.io.tmpdir"),
				tempComponentFileName);
		return tempComponentFile;
	}

	/**
	 * Get unzipped study dir File object stored in Java's temp directory. Name
	 * is stored in session. Discard session variable afterwards.
	 */
	private File getUnzippedStudyDir() {
		String unzippedStudyDirName = session(SESSION_UNZIPPED_STUDY_DIR);
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
			throw new IOException(MessagesStrings.IMPORT_OF_STUDY_FAILED);
		}
		return tempDir;
	}

	private ComponentModel unmarshalComponent(File file, StudyModel study)
			throws IOException {
		ComponentModel component = new JsonUtils.UploadUnmarshaller()
				.unmarshalling(file, ComponentModel.class);
		if (component == null) {
			throw new IOException(MessagesStrings.NO_COMPONENT_UPLOAD);
		}
		if (component.validate() != null) {
			throw new IOException(MessagesStrings.COMPONENT_INVALID);
		}
		return component;
	}

	private StudyModel unmarshalStudy(File tempDir, boolean deleteAfterwards)
			throws IOException {
		File[] studyFileList = IOUtils.findFiles(tempDir, "",
				IOUtils.STUDY_FILE_SUFFIX);
		if (studyFileList.length != 1) {
			throw new IOException(MessagesStrings.STUDY_INVALID);
		}
		File studyFile = studyFileList[0];
		UploadUnmarshaller uploadUnmarshaller = new JsonUtils.UploadUnmarshaller();
		StudyModel study = uploadUnmarshaller.unmarshalling(studyFile,
				StudyModel.class);
		if (study == null) {
			throw new IOException(uploadUnmarshaller.getErrorMsg());
		}
		if (study.validate() != null) {
			throw new IOException(MessagesStrings.STUDY_INVALID);
		}
		if (deleteAfterwards) {
			studyFile.delete();
		}
		return study;
	}

}
