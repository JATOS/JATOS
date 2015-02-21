package controllers.gui;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import models.ComponentModel;
import models.StudyModel;
import models.UserModel;
import persistance.IComponentDao;
import persistance.IStudyDao;
import play.Logger;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Http.MultipartFormData.FilePart;
import play.mvc.Result;
import play.mvc.With;
import services.RequestScopeMessaging;
import services.gui.ComponentService;
import services.gui.ImportExportService;
import services.gui.JatosGuiExceptionThrower;
import services.gui.MessagesStrings;
import services.gui.StudyService;
import services.gui.UserService;
import utils.IOUtils;
import utils.JsonUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import common.JatosGuiAction;

import exceptions.gui.JatosGuiException;

/**
 * Controller that cares for import/export of components and studies.
 * 
 * @author Kristian Lange
 */
@With(JatosGuiAction.class)
@Singleton
public class ImportExport extends Controller {

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

	private static final String CLASS_NAME = ImportExport.class.getSimpleName();

	private final JatosGuiExceptionThrower jatosGuiExceptionThrower;
	private final StudyService studyService;
	private final ComponentService componentService;
	private final UserService userService;
	private final ImportExportService importExportService;
	private final JsonUtils jsonUtils;
	private final IStudyDao studyDao;
	private final IComponentDao componentDao;

	@Inject
	ImportExport(JatosGuiExceptionThrower jatosGuiExceptionThrower,
			StudyService studyService, ComponentService componentService,
			UserService userService, ImportExportService importExportService,
			IStudyDao studyDao, IComponentDao componentDao, JsonUtils jsonUtils) {
		this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
		this.studyService = studyService;
		this.componentService = componentService;
		this.userService = userService;
		this.importExportService = importExportService;
		this.studyDao = studyDao;
		this.componentDao = componentDao;
		this.jsonUtils = jsonUtils;
	}

	/**
	 * Ajax request
	 * 
	 * Checks whether this is a legitimate study import, whether the study or
	 * its directory already exists. The actual import happens in
	 * importStudyConfirmed(). Returns JSON.
	 */
	@Transactional
	public Result importStudy() throws JatosGuiException {
		Logger.info(CLASS_NAME + ".importStudy: " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		UserModel loggedInUser = userService.retrieveLoggedInUser();

		File tempUnzippedStudyDir = importExportService.unzipUploadedFile();
		StudyModel uploadedStudy = importExportService.unmarshalStudy(
				tempUnzippedStudyDir, false);

		// Remember study assets' dir name
		session(SESSION_UNZIPPED_STUDY_DIR, tempUnzippedStudyDir.getName());

		StudyModel currentStudy = studyDao.findByUuid(uploadedStudy.getUuid());
		boolean studyExists = currentStudy != null;
		boolean dirExists = IOUtils.checkStudyAssetsDirExists(uploadedStudy
				.getDirName());
		importExportService.checkStudyImport(loggedInUser, uploadedStudy,
				currentStudy, studyExists, dirExists);

		// Create response
		Map<String, Object> response = new HashMap<String, Object>();
		response.put(STUDY_EXISTS, studyExists);
		response.put(STUDY_TITLE, uploadedStudy.getTitle());
		response.put(DIR_EXISTS, dirExists);
		response.put(DIR_PATH, uploadedStudy.getDirName());
		String asJson = JsonUtils.asJson(response);
		return ok(asJson);
	}

	/**
	 * Ajax request
	 * 
	 * Actual import of study and its study assets directory. Always subsequent
	 * of an importStudy() call.
	 */
	@Transactional
	public Result importStudyConfirmed() throws JatosGuiException {
		Logger.info(CLASS_NAME + ".importStudyConfirmed: "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		UserModel loggedInUser = userService.retrieveLoggedInUser();

		// Get confirmation: overwrite study's properties and/or study assets
		JsonNode json = request().body().asJson();
		if (json == null) {
			String errorMsg = MessagesStrings.IMPORT_OF_STUDY_FAILED;
			jatosGuiExceptionThrower.throwHome(errorMsg,
					Http.Status.BAD_REQUEST);
		}
		Boolean studysPropertiesConfirm = json.findPath(
				STUDYS_PROPERTIES_CONFIRM).asBoolean();
		Boolean studysDirConfirm = json.findPath(STUDYS_DIR_CONFIRM)
				.asBoolean();
		if (studysPropertiesConfirm == null || studysDirConfirm == null) {
			String errorMsg = MessagesStrings.IMPORT_OF_STUDY_FAILED;
			jatosGuiExceptionThrower.throwHome(errorMsg,
					Http.Status.BAD_REQUEST);
		}

		File tempUnzippedStudyDir = importExportService.getUnzippedStudyDir();
		StudyModel importedStudy = importExportService.unmarshalStudy(
				tempUnzippedStudyDir, true);
		StudyModel currentStudy = studyDao.findByUuid(importedStudy.getUuid());

		boolean studyExists = (currentStudy != null);
		if (studyExists) {
			importExportService.overwriteExistingStudy(loggedInUser,
					studysPropertiesConfirm, studysDirConfirm,
					tempUnzippedStudyDir, importedStudy, currentStudy);
		} else {
			importExportService.importNewStudy(loggedInUser,
					tempUnzippedStudyDir, importedStudy);
		}
		tempUnzippedStudyDir.delete();
		return ok(RequestScopeMessaging.getAsJson());
	}

	/**
	 * Ajax request
	 * 
	 * Export a study. Returns a .zip file that contains the study asset
	 * directory and the study as JSON as a .jas file.
	 */
	@Transactional
	public Result exportStudy(Long studyId) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".exportStudy: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		// Remove cookie of jQuery.fileDownload plugin
		response().discardCookie(JQDOWNLOAD_COOKIE_NAME);
		StudyModel study = studyDao.findById(studyId);
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		studyService.checkStandardForStudy(study, studyId, loggedInUser);

		File zipFile = importExportService.createStudyExportZipFile(studyId,
				study);

		String zipFileName = IOUtils.generateFileName(study.getTitle(),
				IOUtils.ZIP_FILE_SUFFIX);
		response().setContentType("application/x-download");
		response().setHeader("Content-disposition",
				"attachment; filename=" + zipFileName);
		// Set cookie for jQuery.fileDownload plugin
		response().setCookie(JQDOWNLOAD_COOKIE_NAME, JQDOWNLOAD_COOKIE_CONTENT);
		return ok(zipFile);
	}

	/**
	 * Ajax request
	 * 
	 * Export of a component. Returns a .jac file with the component in JSON.
	 */
	@Transactional
	public Result exportComponent(Long studyId, Long componentId)
			throws JatosGuiException {
		Logger.info(CLASS_NAME + ".exportComponent: studyId " + studyId + ", "
				+ "componentId " + componentId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		// Remove cookie of jQuery.fileDownload plugin
		response().discardCookie(JQDOWNLOAD_COOKIE_NAME);
		StudyModel study = studyDao.findById(studyId);
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		ComponentModel component = componentDao.findById(componentId);
		componentService.checkStandardForComponents(studyId, componentId,
				study, loggedInUser, component);

		String componentAsJson = null;
		try {
			componentAsJson = jsonUtils.asJsonForIO(component);
		} catch (IOException e) {
			String errorMsg = MessagesStrings
					.componentExportFailure(componentId);
			jatosGuiExceptionThrower.throwAjax(errorMsg,
					Http.Status.INTERNAL_SERVER_ERROR);
		}

		response().setContentType("application/x-download");
		String filename = IOUtils.generateFileName(component.getTitle(),
				IOUtils.COMPONENT_FILE_SUFFIX);
		response().setHeader("Content-disposition",
				"attachment; filename=" + filename);
		// Set cookie for jQuery.fileDownload plugin
		response().setCookie(JQDOWNLOAD_COOKIE_NAME, JQDOWNLOAD_COOKIE_CONTENT);
		return ok(componentAsJson);
	}

	/**
	 * Ajax request
	 * 
	 * Checks whether this is a legitimate component import. The actual import
	 * happens in importComponentConfirmed(). Returns JSON with the results.
	 */
	@Transactional
	public Result importComponent(Long studyId) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".importComponent: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		StudyModel study = studyDao.findById(studyId);
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		studyService.checkStandardForStudy(study, studyId, loggedInUser);
		studyService.checkStudyLocked(study);

		FilePart filePart = request().body().asMultipartFormData()
				.getFile(ComponentModel.COMPONENT);
		if (filePart == null) {
			String errorMsg = MessagesStrings.FILE_MISSING;
			jatosGuiExceptionThrower.throwStudies(errorMsg,
					Http.Status.BAD_REQUEST, studyId);
		}
		// If wrong key the upload comes from the wrong form
		if (!filePart.getKey().equals(ComponentModel.COMPONENT)) {
			String errorMsg = MessagesStrings.NO_COMPONENT_UPLOAD;
			jatosGuiExceptionThrower.throwStudies(errorMsg,
					Http.Status.BAD_REQUEST, studyId);
		}

		ComponentModel uploadedComponent = importExportService
				.unmarshalComponent(filePart.getFile(), study);

		// Remember component's file name
		session(SESSION_TEMP_COMPONENT_FILE, filePart.getFile().getName());

		boolean componentExists = componentDao.findByUuid(uploadedComponent
				.getUuid()) != null;

		// Create response
		Map<String, Object> response = new HashMap<String, Object>();
		response.put(COMPONENT_EXISTS, componentExists);
		response.put(COMPONENT_TITLE, uploadedComponent.getTitle());
		String asJson = JsonUtils.asJson(response);
		return ok(asJson);
	}

	/**
	 * Ajax request
	 * 
	 * Actual import of component.
	 */
	@Transactional
	public Result importComponentConfirmed(Long studyId)
			throws JatosGuiException {
		Logger.info(CLASS_NAME + ".importComponentConfirmed: " + "studyId "
				+ studyId + ", " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		StudyModel study = studyDao.findById(studyId);
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		studyService.checkStandardForStudy(study, studyId, loggedInUser);
		studyService.checkStudyLocked(study);

		File componentFile = importExportService.getTempComponentFile(study);
		ComponentModel uploadedComponent = importExportService
				.unmarshalComponent(componentFile, study);
		ComponentModel currentComponent = componentDao
				.findByUuid(uploadedComponent.getUuid());
		boolean componentExists = (currentComponent != null);
		if (componentExists) {
			componentDao.updateProperties(currentComponent, uploadedComponent);
			RequestScopeMessaging.success(MessagesStrings
					.componentsPropertiesOverwritten(currentComponent.getId()));
		} else {
			componentDao.create(study, uploadedComponent);
			RequestScopeMessaging.success(MessagesStrings
					.importedNewComponent(uploadedComponent.getId()));
		}
		return ok(RequestScopeMessaging.getAsJson());
	}

}
