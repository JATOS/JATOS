package controllers.gui;

import java.io.File;
import java.io.IOException;

import models.ComponentModel;
import models.StudyModel;
import models.UserModel;
import persistance.ComponentDao;
import persistance.StudyDao;
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
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import exceptions.gui.JatosGuiException;

/**
 * Controller that cares for import/export of components and studies.
 * 
 * @author Kristian Lange
 */
@With(JatosGuiAction.class)
@Singleton
public class ImportExport extends Controller {

	private static final String CLASS_NAME = ImportExport.class.getSimpleName();

	private final JatosGuiExceptionThrower jatosGuiExceptionThrower;
	private final StudyService studyService;
	private final ComponentService componentService;
	private final UserService userService;
	private final ImportExportService importExportService;
	private final JsonUtils jsonUtils;
	private final StudyDao studyDao;
	private final ComponentDao componentDao;

	@Inject
	ImportExport(JatosGuiExceptionThrower jatosGuiExceptionThrower,
			StudyService studyService, ComponentService componentService,
			UserService userService, ImportExportService importExportService,
			StudyDao studyDao, ComponentDao componentDao, JsonUtils jsonUtils) {
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

		// Get file from request
		FilePart filePart = request().body().asMultipartFormData()
				.getFile(StudyModel.STUDY);

		return ok(importExportService.importStudy(loggedInUser, filePart));
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
				ImportExportService.STUDYS_PROPERTIES_CONFIRM).asBoolean();
		Boolean studysDirConfirm = json.findPath(
				ImportExportService.STUDYS_DIR_CONFIRM).asBoolean();
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
		response().discardCookie(ImportExportService.JQDOWNLOAD_COOKIE_NAME);
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
		response().setCookie(ImportExportService.JQDOWNLOAD_COOKIE_NAME,
				ImportExportService.JQDOWNLOAD_COOKIE_CONTENT);
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
		response().discardCookie(ImportExportService.JQDOWNLOAD_COOKIE_NAME);
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
		response().setCookie(ImportExportService.JQDOWNLOAD_COOKIE_NAME,
				ImportExportService.JQDOWNLOAD_COOKIE_CONTENT);
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
		ObjectNode json = null;
		try {
			json = importExportService.importComponent(study, filePart);
		} catch (IOException e) {
			jatosGuiExceptionThrower.throwStudies(e.getMessage(),
					Http.Status.BAD_REQUEST, study.getId());
		}
		// Remember component's file name
		session(ImportExportService.SESSION_TEMP_COMPONENT_FILE, filePart
				.getFile().getName());
		return ok(json);
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

		try {
			String tempComponentFileName = session(ImportExportService.SESSION_TEMP_COMPONENT_FILE);
			importExportService.importComponentConfirmed(study,
					tempComponentFileName);
		} catch (IOException e) {
			jatosGuiExceptionThrower.throwStudies(e.getMessage(),
					Http.Status.BAD_REQUEST, study.getId());
		} finally {
			session().remove(ImportExportService.SESSION_TEMP_COMPONENT_FILE);
		}
		return ok(RequestScopeMessaging.getAsJson());
	}

}
