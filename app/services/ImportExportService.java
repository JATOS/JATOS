package services;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import models.ComponentModel;
import models.StudyModel;
import models.UserModel;
import persistance.IComponentDao;
import persistance.IStudyDao;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Http.MultipartFormData.FilePart;
import utils.IOUtils;
import utils.JsonUtils;
import utils.JsonUtils.UploadUnmarshaller;
import utils.ZipUtil;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import controllers.ImportExport;
import exceptions.JatosGuiException;

/**
 * Utility class for all JATOS Controllers (not Publix).
 * 
 * @author Kristian Lange
 */
@Singleton
public class ImportExportService extends Controller {

	private final StudyService studyService;
	private final JsonUtils jsonUtils;
	private final JatosGuiExceptionThrower jatosGuiExceptionThrower;
	private final IStudyDao studyDao;
	private final IComponentDao componentDao;

	@Inject
	ImportExportService(StudyService studyService, JsonUtils jsonUtils,
			JatosGuiExceptionThrower jatosGuiExceptionThrower,
			IStudyDao studyDao, IComponentDao componentDao) {
		this.studyService = studyService;
		this.jsonUtils = jsonUtils;
		this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
		this.studyDao = studyDao;
		this.componentDao = componentDao;
	}

	public void checkStudyImport(UserModel loggedInUser,
			StudyModel uploadedStudy, StudyModel currentStudy,
			boolean studyExists, boolean dirExists) throws JatosGuiException {
		if (studyExists && !currentStudy.hasMember(loggedInUser)) {
			String errorMsg = MessagesStrings.studyImportNotMember(currentStudy
					.getTitle());
			jatosGuiExceptionThrower.throwHome(errorMsg, Http.Status.FORBIDDEN);
		}
		if (dirExists
				&& (currentStudy == null || !currentStudy.getDirName().equals(
						uploadedStudy.getDirName()))) {
			String errorMsg = MessagesStrings
					.studyAssetsDirExistsBelongsToDifferentStudy(uploadedStudy
							.getDirName());
			jatosGuiExceptionThrower.throwHome(errorMsg, Http.Status.FORBIDDEN);
		}
	}

	public void overwriteExistingStudy(UserModel loggedInUser,
			Boolean studysPropertiesConfirm, Boolean studysDirConfirm,
			File tempUnzippedStudyDir, StudyModel importedStudy,
			StudyModel currentStudy) throws JatosGuiException {
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
			RequestScope.getMessages().success(
					MessagesStrings.studyAssetsOverwritten(
							importedStudy.getDirName(), currentStudy.getId()));
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
			RequestScope.getMessages().success(
					MessagesStrings.studysPropertiesOverwritten(currentStudy
							.getId()));
		}
	}

	public void importNewStudy(UserModel loggedInUser,
			File tempUnzippedStudyDir, StudyModel importedStudy)
			throws JatosGuiException {
		moveStudyAssetsDir(tempUnzippedStudyDir, null,
				importedStudy.getDirName(), loggedInUser);
		studyDao.create(importedStudy, loggedInUser);
		RequestScope.getMessages().success(
				MessagesStrings.importedNewStudy(importedStudy.getDirName(),
						importedStudy.getId()));
	}

	public File createStudyExportZipFile(Long studyId, StudyModel study)
			throws JatosGuiException {
		File zipFile = null;
		try {
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
		} catch (IOException e) {
			String errorMsg = MessagesStrings.studyExportFailure(studyId);
			jatosGuiExceptionThrower.throwAjax(errorMsg,
					Http.Status.INTERNAL_SERVER_ERROR);
		}
		return zipFile;
	}

	/**
	 * Update the components of the current study with the one of the imported
	 * study.
	 */
	public void updateStudysComponents(StudyModel currentStudy,
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
	public void moveStudyAssetsDir(File unzippedStudyDir,
			StudyModel currentStudy, String studyAssetsDirName,
			UserModel loggedInUser) throws JatosGuiException {
		try {
			if (currentStudy != null) {
				IOUtils.removeStudyAssetsDir(currentStudy.getDirName());
			}

			File[] dirArray = IOUtils.findDirectories(unzippedStudyDir);
			if (dirArray.length == 0) {
				// If a study assets dir is missing, create a new one.
				IOUtils.createStudyAssetsDir(studyAssetsDirName);
				RequestScope.getMessages().warning(
						MessagesStrings.NO_DIR_IN_ZIP_CREATED_NEW);
			} else if (dirArray.length == 1) {
				File studyAssetsDir = dirArray[0];
				IOUtils.moveStudyAssetsDir(studyAssetsDir, studyAssetsDirName);
			} else {
				String errorMsg = MessagesStrings.MORE_THAN_ONE_DIR_IN_ZIP;
				jatosGuiExceptionThrower.throwHome(errorMsg,
						Http.Status.BAD_REQUEST);
			}
		} catch (IOException e) {
			String errorMsg = "Study not imported: " + e.getMessage();
			jatosGuiExceptionThrower.throwHome(errorMsg,
					Http.Status.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * Get component's File object. Name is stored in session. Discard session
	 * variable afterwards.
	 */
	public File getTempComponentFile(StudyModel study) throws JatosGuiException {
		String tempComponentFileName = session(ImportExport.SESSION_TEMP_COMPONENT_FILE);
		session().remove(ImportExport.SESSION_TEMP_COMPONENT_FILE);
		if (tempComponentFileName == null || tempComponentFileName.isEmpty()) {
			String errorMsg = MessagesStrings.IMPORT_OF_COMPONENT_FAILED;
			jatosGuiExceptionThrower.throwStudies(errorMsg,
					Http.Status.BAD_REQUEST, study.getId());
		}
		File tempComponentFile = new File(System.getProperty("java.io.tmpdir"),
				tempComponentFileName);
		return tempComponentFile;
	}

	/**
	 * Get unzipped study dir File object stored in Java's temp directory. Name
	 * is stored in session. Discard session variable afterwards.
	 */
	public File getUnzippedStudyDir() throws JatosGuiException {
		String unzippedStudyDirName = session(ImportExport.SESSION_UNZIPPED_STUDY_DIR);
		session().remove(ImportExport.SESSION_UNZIPPED_STUDY_DIR);
		if (unzippedStudyDirName == null || unzippedStudyDirName.isEmpty()) {
			String errorMsg = MessagesStrings.IMPORT_OF_STUDY_FAILED;
			jatosGuiExceptionThrower.throwHome(errorMsg,
					Http.Status.BAD_REQUEST);
		}
		File unzippedStudyDir = new File(System.getProperty("java.io.tmpdir"),
				unzippedStudyDirName);
		return unzippedStudyDir;
	}

	public File unzipUploadedFile() throws JatosGuiException {
		// Get file from request
		FilePart filePart = request().body().asMultipartFormData()
				.getFile(StudyModel.STUDY);
		if (filePart == null) {
			String errorMsg = MessagesStrings.FILE_MISSING;
			jatosGuiExceptionThrower.throwHome(errorMsg,
					Http.Status.BAD_REQUEST);
		}
		if (!filePart.getKey().equals(StudyModel.STUDY)) {
			// If wrong key the upload comes from wrong form
			String errorMsg = MessagesStrings.NO_STUDY_UPLOAD;
			jatosGuiExceptionThrower.throwHome(errorMsg,
					Http.Status.BAD_REQUEST);
		}

		File tempDir = null;
		try {
			tempDir = ZipUtil.unzip(filePart.getFile());
		} catch (IOException e1) {
			String errorMsg = MessagesStrings.IMPORT_OF_STUDY_FAILED;
			jatosGuiExceptionThrower.throwHome(errorMsg,
					Http.Status.INTERNAL_SERVER_ERROR);
		}
		return tempDir;
	}

	public ComponentModel unmarshalComponent(File file, StudyModel study)
			throws JatosGuiException {
		ComponentModel component = new JsonUtils.UploadUnmarshaller()
				.unmarshalling(file, ComponentModel.class);
		if (component == null) {
			String errorMsg = MessagesStrings.NO_COMPONENT_UPLOAD;
			jatosGuiExceptionThrower.throwStudies(errorMsg,
					Http.Status.BAD_REQUEST, study.getId());
		}
		if (component.validate() != null) {
			String errorMsg = MessagesStrings.COMPONENT_INVALID;
			jatosGuiExceptionThrower.throwStudies(errorMsg,
					Http.Status.BAD_REQUEST, study.getId());
		}
		return component;
	}

	public StudyModel unmarshalStudy(File tempDir, boolean deleteAfterwards)
			throws JatosGuiException {
		File[] studyFileList = IOUtils.findFiles(tempDir, "",
				IOUtils.STUDY_FILE_SUFFIX);
		if (studyFileList.length != 1) {
			String errorMsg = MessagesStrings.STUDY_INVALID;
			jatosGuiExceptionThrower.throwHome(errorMsg,
					Http.Status.BAD_REQUEST);
		}
		File studyFile = studyFileList[0];
		UploadUnmarshaller uploadUnmarshaller = new JsonUtils.UploadUnmarshaller();
		StudyModel study = uploadUnmarshaller.unmarshalling(studyFile,
				StudyModel.class);
		if (study == null) {
			jatosGuiExceptionThrower.throwHome(
					uploadUnmarshaller.getErrorMsg(), Http.Status.BAD_REQUEST);
		}
		if (study.validate() != null) {
			String errorMsg = MessagesStrings.STUDY_INVALID;
			jatosGuiExceptionThrower.throwHome(errorMsg,
					Http.Status.BAD_REQUEST);
		}
		if (deleteAfterwards) {
			studyFile.delete();
		}
		return study;
	}

}
