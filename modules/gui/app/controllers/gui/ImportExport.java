package controllers.gui;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import controllers.gui.actionannotations.AuthenticationAction.Authenticated;
import controllers.gui.actionannotations.GuiExceptionAction.GuiExceptionCatching;
import controllers.gui.actionannotations.GuiAccessLoggingAction.GuiAccessLogging;
import daos.common.ComponentDao;
import daos.common.StudyDao;
import daos.common.worker.WorkerDao;
import exceptions.gui.BadRequestException;
import exceptions.gui.ForbiddenException;
import exceptions.gui.JatosGuiException;
import exceptions.gui.NotFoundException;
import general.common.MessagesStrings;
import general.gui.RequestScopeMessaging;
import models.common.Component;
import models.common.Study;
import models.common.User;
import models.common.workers.Worker;
import play.Logger;
import play.Logger.ALogger;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Http.MultipartFormData.FilePart;
import play.mvc.Result;
import services.gui.Checker;
import services.gui.ImportExportService;
import services.gui.JatosGuiExceptionThrower;
import services.gui.ResultDataStringGenerator;
import services.gui.UserService;
import utils.common.IOUtils;
import utils.common.JsonUtils;

/**
 * Controller that cares for import/export of components and studies.
 * 
 * @author Kristian Lange
 */
@GuiExceptionCatching
@GuiAccessLogging
@Authenticated
@Singleton
public class ImportExport extends Controller {

	private static final ALogger LOGGER = Logger.of(ImportExport.class);

	public static final String JQDOWNLOAD_COOKIE_NAME = "fileDownload";
	public static final String JQDOWNLOAD_COOKIE_CONTENT = "true";
	public static final String DATE_FORMAT_FILE = "yyyyMMddHHmmss";
	public static final SimpleDateFormat DATE_FORMATER_FILE = new SimpleDateFormat(
			DATE_FORMAT_FILE);

	private final JatosGuiExceptionThrower jatosGuiExceptionThrower;
	private final Checker checker;
	private final UserService userService;
	private final ImportExportService importExportService;
	private final ResultDataStringGenerator resultDataStringGenerator;
	private final IOUtils ioUtils;
	private final JsonUtils jsonUtils;
	private final StudyDao studyDao;
	private final ComponentDao componentDao;
	private final WorkerDao workerDao;

	@Inject
	ImportExport(JatosGuiExceptionThrower jatosGuiExceptionThrower,
			Checker checker, IOUtils ioUtils, JsonUtils jsonUtils,
			UserService userService, ImportExportService importExportService,
			ResultDataStringGenerator resultDataStringGenerator,
			StudyDao studyDao, ComponentDao componentDao, WorkerDao workerDao) {
		this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
		this.checker = checker;
		this.jsonUtils = jsonUtils;
		this.ioUtils = ioUtils;
		this.userService = userService;
		this.importExportService = importExportService;
		this.resultDataStringGenerator = resultDataStringGenerator;
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
		LOGGER.info(".importStudy");
		User loggedInUser = userService.retrieveLoggedInUser();

		// Get file from request
		FilePart filePart = request().body().asMultipartFormData()
				.getFile(Study.STUDY);

		if (filePart == null) {
			jatosGuiExceptionThrower.throwAjax(MessagesStrings.FILE_MISSING,
					Http.Status.BAD_REQUEST);
		}
		if (!filePart.getKey().equals(Study.STUDY)) {
			// If wrong key the upload comes from wrong form
			jatosGuiExceptionThrower.throwAjax(MessagesStrings.NO_STUDY_UPLOAD,
					Http.Status.BAD_REQUEST);
		}

		JsonNode responseJson = null;
		try {
			responseJson = importExportService.importStudy(loggedInUser,
					filePart.getFile());
		} catch (ForbiddenException | IOException e) {
			importExportService.cleanupAfterStudyImport();
			jatosGuiExceptionThrower.throwAjax(e);
		}
		return ok(responseJson);
	}

	/**
	 * Ajax request
	 * 
	 * Actual import of study and its study assets directory. Always subsequent
	 * of an importStudy() call.
	 */
	@Transactional
	public Result importStudyConfirmed() throws JatosGuiException {
		LOGGER.info(".importStudyConfirmed");
		User loggedInUser = userService.retrieveLoggedInUser();

		// Get confirmation: overwrite study's properties and/or study assets
		JsonNode json = request().body().asJson();
		try {
			importExportService.importStudyConfirmed(loggedInUser, json);
		} catch (ForbiddenException | IOException | BadRequestException e) {
			jatosGuiExceptionThrower.throwHome(e);
		} catch (Exception e) {
			// Unexpected exception, but we have to clean up
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
		LOGGER.info(".exportStudy: studyId " + studyId);
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		try {
			checker.checkStandardForStudy(study, studyId, loggedInUser);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwAjax(e);
		}

		File zipFile = null;
		try {
			zipFile = importExportService.createStudyExportZipFile(study);
		} catch (IOException e) {
			String errorMsg = MessagesStrings.studyExportFailure(studyId);
			LOGGER.error(".exportStudy: " + errorMsg, e);
			jatosGuiExceptionThrower.throwAjax(errorMsg,
					Http.Status.INTERNAL_SERVER_ERROR);
		}

		String zipFileName = ioUtils.generateFileName(study.getTitle(),
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
		LOGGER.info(".exportComponent: studyId " + studyId + ", "
				+ "componentId " + componentId);
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		Component component = componentDao.findById(componentId);
		try {
			checker.checkStandardForStudy(study, studyId, loggedInUser);
			checker.checkStandardForComponents(studyId, componentId, component);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwAjax(e);
		}

		JsonNode componentAsJson = null;
		try {
			componentAsJson = jsonUtils.componentAsJsonForIO(component);
		} catch (IOException e) {
			String errorMsg = MessagesStrings
					.componentExportFailure(componentId);
			jatosGuiExceptionThrower.throwAjax(errorMsg,
					Http.Status.INTERNAL_SERVER_ERROR);
		}

		response().setContentType("application/x-download");
		String filename = ioUtils.generateFileName(component.getTitle(),
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
		LOGGER.info(".importComponent: studyId " + studyId);
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		ObjectNode json = null;
		try {
			checker.checkStandardForStudy(study, studyId, loggedInUser);
			checker.checkStudyLocked(study);

			FilePart filePart = request().body().asMultipartFormData()
					.getFile(Component.COMPONENT);
			json = importExportService.importComponent(study, filePart);
		} catch (ForbiddenException | BadRequestException | IOException e) {
			importExportService.cleanupAfterComponentImport();
			jatosGuiExceptionThrower.throwStudy(e, study.getId());
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
		LOGGER.info(".importComponentConfirmed: " + "studyId " + studyId);
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();

		try {
			checker.checkStandardForStudy(study, studyId, loggedInUser);
			checker.checkStudyLocked(study);
			String tempComponentFileName = session(
					ImportExportService.SESSION_TEMP_COMPONENT_FILE);
			importExportService.importComponentConfirmed(study,
					tempComponentFileName);
		} catch (ForbiddenException | IOException | BadRequestException e) {
			jatosGuiExceptionThrower.throwStudy(e, study.getId());
		} catch (Exception e) {
			// Unexpected exception, but we have to clean up
			jatosGuiExceptionThrower.throwStudy(e, study.getId());
		} finally {
			importExportService.cleanupAfterComponentImport();
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
		LOGGER.info(
				".exportDataOfStudyResults: studyResultIds " + studyResultIds);
		// Remove cookie of johnculviner's jQuery.fileDownload plugin (just to
		// be sure, in case it's still there)
		response().discardCookie(JQDOWNLOAD_COOKIE_NAME);
		User loggedInUser = userService.retrieveLoggedInUser();

		String resultDataAsStr = null;
		try {
			resultDataAsStr = resultDataStringGenerator
					.fromListOfStudyResultIds(studyResultIds, loggedInUser);
		} catch (ForbiddenException | BadRequestException
				| NotFoundException e) {
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
		LOGGER.info(".exportDataOfAllStudyResults");
		// Remove cookie of johnculviner's jQuery.fileDownload plugin (just to
		// be sure, in case it's still there)
		response().discardCookie(JQDOWNLOAD_COOKIE_NAME);
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		try {
			checker.checkStandardForStudy(study, studyId, loggedInUser);
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
		LOGGER.info(".exportDataOfComponentResults: componentResultIds "
				+ componentResultIds);
		// Remove cookie of johnculviner's jQuery.fileDownload plugin (just to
		// be sure, in case it's still there)
		response().discardCookie(ImportExport.JQDOWNLOAD_COOKIE_NAME);
		User loggedInUser = userService.retrieveLoggedInUser();

		String resultDataAsStr = null;
		try {
			resultDataAsStr = resultDataStringGenerator
					.fromListOfComponentResultIds(componentResultIds,
							loggedInUser);
		} catch (ForbiddenException | BadRequestException
				| NotFoundException e) {
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
	public Result exportDataOfAllComponentResults(Long studyId,
			Long componentId) throws JatosGuiException {
		LOGGER.info(".exportDataOfAllComponentResults: studyId " + studyId
				+ ", " + "componentId " + componentId);
		// Remove cookie of johnculviner's jQuery.fileDownload plugin (just to
		// be sure, in case it's still there)
		response().discardCookie(JQDOWNLOAD_COOKIE_NAME);
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		Component component = componentDao.findById(componentId);
		try {
			checker.checkStandardForStudy(study, studyId, loggedInUser);
			checker.checkStandardForComponents(studyId, componentId, component);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwAjax(e);
		}

		String resultDataAsStr = null;
		try {
			resultDataAsStr = resultDataStringGenerator
					.forComponent(loggedInUser, component);
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
		LOGGER.info(".exportAllResultDataOfWorker: workerId " + workerId);
		// Remove cookie of johnculviner's jQuery.fileDownload plugin (just to
		// be sure, in case it's still there)
		response().discardCookie(JQDOWNLOAD_COOKIE_NAME);
		Worker worker = workerDao.findById(workerId);
		User loggedInUser = userService.retrieveLoggedInUser();
		try {
			checker.checkWorker(worker, workerId);
		} catch (BadRequestException e) {
			jatosGuiExceptionThrower.throwRedirect(e,
					controllers.gui.routes.Home.home());
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
		String dateForFile = DATE_FORMATER_FILE.format(new Date());
		String filename = "results_" + dateForFile + "."
				+ IOUtils.TXT_FILE_SUFFIX;
		response().setHeader("Content-disposition",
				"attachment; filename=" + filename);
		response().setCookie(JQDOWNLOAD_COOKIE_NAME, JQDOWNLOAD_COOKIE_CONTENT);
	}

}
