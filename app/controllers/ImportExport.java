package controllers;

import java.io.File;
import java.io.IOException;
import java.util.Date;

import models.ComponentModel;
import models.StudyModel;
import models.UserModel;
import models.workers.Worker;
import persistance.ComponentDao;
import persistance.StudyDao;
import persistance.workers.WorkerDao;
import play.Logger;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Http.MultipartFormData.FilePart;
import play.mvc.Result;
import services.ComponentService;
import services.ImportExportService;
import services.JatosGuiExceptionThrower;
import services.MessagesStrings;
import services.ResultDataStringGenerator;
import services.StudyService;
import services.UserService;
import services.WorkerService;
import utils.DateUtils;
import utils.IOUtils;
import utils.JsonUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import common.RequestScopeMessaging;
import controllers.actionannotations.AuthenticationAction.Authenticated;
import controllers.actionannotations.JatosGuiAction.JatosGui;
import exceptions.BadRequestException;
import exceptions.ForbiddenException;
import exceptions.JatosGuiException;
import exceptions.NotFoundException;

/**
 * Controller that cares for import/export of components and studies.
 * 
 * @author Kristian Lange
 */
@JatosGui
@Authenticated
@Singleton
public class ImportExport extends Controller {

	private static final String CLASS_NAME = ImportExport.class.getSimpleName();
	public static final String JQDOWNLOAD_COOKIE_NAME = "fileDownload";
	public static final String JQDOWNLOAD_COOKIE_CONTENT = "true";

	private final JatosGuiExceptionThrower jatosGuiExceptionThrower;
	private final StudyService studyService;
	private final ComponentService componentService;
	private final UserService userService;
	private final ImportExportService importExportService;
	private final ResultDataStringGenerator resultDataStringGenerator;
	private final WorkerService workerService;
	private final JsonUtils jsonUtils;
	private final StudyDao studyDao;
	private final ComponentDao componentDao;
	private final WorkerDao workerDao;

	@Inject
	ImportExport(JatosGuiExceptionThrower jatosGuiExceptionThrower,
			JsonUtils jsonUtils, StudyService studyService,
			ComponentService componentService, UserService userService,
			ImportExportService importExportService,
			ResultDataStringGenerator resultDataStringGenerator,
			WorkerService workerService, StudyDao studyDao,
			ComponentDao componentDao, WorkerDao workerDao) {
		this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
		this.jsonUtils = jsonUtils;
		this.studyService = studyService;
		this.componentService = componentService;
		this.userService = userService;
		this.importExportService = importExportService;
		this.resultDataStringGenerator = resultDataStringGenerator;
		this.workerService = workerService;
		this.studyDao = studyDao;
		this.componentDao = componentDao;
		this.workerDao = workerDao;
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

		if (filePart == null) {
			jatosGuiExceptionThrower.throwAjax(MessagesStrings.FILE_MISSING,
					Http.Status.BAD_REQUEST);
		}
		if (!filePart.getKey().equals(StudyModel.STUDY)) {
			// If wrong key the upload comes from wrong form
			jatosGuiExceptionThrower.throwAjax(MessagesStrings.NO_STUDY_UPLOAD,
					Http.Status.BAD_REQUEST);
		}

		JsonNode jsonNode = null;
		try {
			jsonNode = importExportService.importStudy(loggedInUser,
					filePart.getFile());
		} catch (ForbiddenException | IOException e) {
			importExportService.cleanupAfterStudyImport();
			jatosGuiExceptionThrower.throwAjax(e);
		}
		return ok(jsonNode);
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
		try {
			importExportService.importStudyConfirmed(loggedInUser, json);
		} catch (ForbiddenException | IOException | BadRequestException e) {
			jatosGuiExceptionThrower.throwHome(e);
		} finally {
			importExportService.cleanupAfterStudyImport();
		}
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
		StudyModel study = studyDao.findById(studyId);
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		try {
			studyService.checkStandardForStudy(study, studyId, loggedInUser);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwAjax(e);
		}

		File zipFile = null;
		try {
			zipFile = importExportService.createStudyExportZipFile(study);
		} catch (IOException e) {
			String errorMsg = MessagesStrings.studyExportFailure(studyId);
			jatosGuiExceptionThrower.throwAjax(errorMsg,
					Http.Status.INTERNAL_SERVER_ERROR);
		}

		String zipFileName = IOUtils.generateFileName(study.getTitle(),
				IOUtils.ZIP_FILE_SUFFIX);
		response().setContentType("application/x-download");
		response().setHeader("Content-disposition",
				"attachment; filename=" + zipFileName);
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
		StudyModel study = studyDao.findById(studyId);
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		ComponentModel component = componentDao.findById(componentId);
		try {
			studyService.checkStandardForStudy(study, studyId, loggedInUser);
			componentService.checkStandardForComponents(studyId, componentId,
					loggedInUser, component);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwAjax(e);
		}

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
		ObjectNode json = null;
		try {
			studyService.checkStandardForStudy(study, studyId, loggedInUser);
			studyService.checkStudyLocked(study);

			FilePart filePart = request().body().asMultipartFormData()
					.getFile(ComponentModel.COMPONENT);
			json = importExportService.importComponent(study, filePart);
		} catch (ForbiddenException | BadRequestException | IOException e) {
			importExportService.cleanupAfterComponentImport(study);
			jatosGuiExceptionThrower.throwStudyIndex(e, study.getId());
		}
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

		try {
			studyService.checkStandardForStudy(study, studyId, loggedInUser);
			studyService.checkStudyLocked(study);
			String tempComponentFileName = session(ImportExportService.SESSION_TEMP_COMPONENT_FILE);
			importExportService.importComponentConfirmed(study,
					tempComponentFileName);
		} catch (ForbiddenException | IOException | BadRequestException e) {
			jatosGuiExceptionThrower.throwStudyIndex(e, study.getId());
		} finally {
			importExportService.cleanupAfterComponentImport(study);
		}
		return ok(RequestScopeMessaging.getAsJson());
	}

	/**
	 * Ajax request
	 * 
	 * Returns all result data of ComponentResults belonging to StudyResults
	 * specified in the given string of study result IDs. Returns the results as
	 * text.
	 */
	@Transactional
	public Result exportDataOfStudyResults(String studyResultIds)
			throws JatosGuiException {
		Logger.info(CLASS_NAME + ".exportResultData: studyResultIds "
				+ studyResultIds + ", " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		// Remove cookie of johnculviner's jQuery.fileDownload plugin (just to
		// be sure, in case it's still there)
		response().discardCookie(JQDOWNLOAD_COOKIE_NAME);
		UserModel loggedInUser = userService.retrieveLoggedInUser();

		String resultDataAsStr = null;
		try {
			resultDataAsStr = resultDataStringGenerator
					.fromListOfStudyResultIds(studyResultIds, loggedInUser);
		} catch (ForbiddenException | BadRequestException | NotFoundException e) {
			jatosGuiExceptionThrower.throwAjax(e);
		}
		prepareResponseForExport();
		return ok(resultDataAsStr);
	}

	/**
	 * Ajax request
	 * 
	 * Returns all result data of ComponentResults belonging to StudyResults
	 * belonging to the given study.
	 */
	@Transactional
	public Result exportDataOfAllStudyResults(Long studyId)
			throws JatosGuiException {
		Logger.info(CLASS_NAME + ".exportAllData: logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		// Remove cookie of johnculviner's jQuery.fileDownload plugin (just to
		// be sure, in case it's still there)
		response().discardCookie(JQDOWNLOAD_COOKIE_NAME);
		StudyModel study = studyDao.findById(studyId);
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		try {
			studyService.checkStandardForStudy(study, studyId, loggedInUser);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwAjax(e);
		}

		String resultDataAsStr = null;
		try {
			resultDataAsStr = resultDataStringGenerator.forStudy(loggedInUser,
					study);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwAjax(e);
		}
		prepareResponseForExport();
		return ok(resultDataAsStr);
	}

	/**
	 * Ajax request
	 * 
	 * Returns all result data of ComponentResults specified in the given string
	 * of study component result IDs. Returns the results as text.
	 */
	@Transactional
	public Result exportDataOfComponentResults(String componentResultIds)
			throws JatosGuiException {
		Logger.info(CLASS_NAME + ".exportResultData: componentResultIds "
				+ componentResultIds + ", " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		// Remove cookie of johnculviner's jQuery.fileDownload plugin (just to
		// be sure, in case it's still there)
		response().discardCookie(ImportExport.JQDOWNLOAD_COOKIE_NAME);
		UserModel loggedInUser = userService.retrieveLoggedInUser();

		String resultDataAsStr = null;
		try {
			resultDataAsStr = resultDataStringGenerator
					.fromListOfComponentResultIds(componentResultIds,
							loggedInUser);
		} catch (ForbiddenException | BadRequestException | NotFoundException e) {
			jatosGuiExceptionThrower.throwAjax(e);
		}
		prepareResponseForExport();
		return ok(resultDataAsStr);
	}

	/**
	 * Ajax request
	 * 
	 * Returns all result data of ComponentResults belonging to the given
	 * component and study.
	 */
	@Transactional
	public Result exportDataOfAllComponentResults(Long studyId, Long componentId)
			throws JatosGuiException {
		Logger.info(CLASS_NAME + ".exportDataOfAllComponentResults: studyId "
				+ studyId + ", " + "componentId " + componentId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		// Remove cookie of johnculviner's jQuery.fileDownload plugin (just to
		// be sure, in case it's still there)
		response().discardCookie(JQDOWNLOAD_COOKIE_NAME);
		StudyModel study = studyDao.findById(studyId);
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		ComponentModel component = componentDao.findById(componentId);
		try {
			studyService.checkStandardForStudy(study, studyId, loggedInUser);
			componentService.checkStandardForComponents(studyId, componentId,
					loggedInUser, component);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwAjax(e);
		}

		String resultDataAsStr = null;
		try {
			resultDataAsStr = resultDataStringGenerator.forComponent(
					loggedInUser, component);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwAjax(e);
		}
		prepareResponseForExport();
		return ok(resultDataAsStr);
	}

	/**
	 * Ajax request
	 * 
	 * Returns all result data of ComponentResults belonging to the given
	 * worker's StudyResults.
	 */
	@Transactional
	public Result exportAllResultDataOfWorker(Long workerId)
			throws JatosGuiException {
		Logger.info(CLASS_NAME + ".exportAllResultDataOfWorker: workerId "
				+ workerId + ", " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		// Remove cookie of johnculviner's jQuery.fileDownload plugin (just to
		// be sure, in case it's still there)
		response().discardCookie(JQDOWNLOAD_COOKIE_NAME);
		Worker worker = workerDao.findById(workerId);
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		try {
			workerService.checkWorker(worker, workerId);
		} catch (BadRequestException e) {
			jatosGuiExceptionThrower.throwRedirect(e,
					controllers.routes.Home.home());
		}

		String resultDataAsStr = null;
		try {
			resultDataAsStr = resultDataStringGenerator.forWorker(loggedInUser,
					worker);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwAjax(e);
		}
		prepareResponseForExport();
		return ok(resultDataAsStr);
	}

	/**
	 * Prepares the response for a download with the johnculviner's
	 * jQuery.fileDownload plugin. This plugin is merely used to detect a failed
	 * download. If the response isn't OK and it doesn't have this cookie then
	 * the plugin regards it as a fail.
	 */
	private void prepareResponseForExport() {
		response().setContentType("application/x-download");
		String filename = "results_" + DateUtils.getDateForFile(new Date())
				+ "." + IOUtils.TXT_FILE_SUFFIX;
		response().setHeader("Content-disposition",
				"attachment; filename=" + filename);
		response().setCookie(JQDOWNLOAD_COOKIE_NAME, JQDOWNLOAD_COOKIE_CONTENT);
	}

}
