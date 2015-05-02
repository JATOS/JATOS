package controllers.gui;

import java.io.IOException;
import java.util.Date;
import java.util.List;

import models.StudyModel;
import models.StudyResult;
import models.UserModel;
import models.workers.Worker;
import persistance.StudyDao;
import persistance.workers.WorkerDao;
import play.Logger;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.RequestScopeMessaging;
import services.gui.Breadcrumbs;
import services.gui.ImportExportService;
import services.gui.JatosGuiExceptionThrower;
import services.gui.MessagesStrings;
import services.gui.ResultService;
import services.gui.StudyService;
import services.gui.UserService;
import services.gui.WorkerService;
import utils.DateUtils;
import utils.IOUtils;
import utils.JsonUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import controllers.gui.actionannotations.AuthenticationAction.Authenticated;
import controllers.gui.actionannotations.JatosGuiAction.JatosGui;
import exceptions.BadRequestException;
import exceptions.ForbiddenException;
import exceptions.NotFoundException;
import exceptions.gui.JatosGuiException;

/**
 * Controller for actions around StudyResults in the JATOS GUI.
 * 
 * @author Kristian Lange
 */
@JatosGui
@Authenticated
@Singleton
public class StudyResults extends Controller {

	private static final String CLASS_NAME = StudyResults.class.getSimpleName();

	private final JatosGuiExceptionThrower jatosGuiExceptionThrower;
	private final JsonUtils jsonUtils;
	private final StudyService studyService;
	private final UserService userService;
	private final WorkerService workerService;
	private final ResultService resultService;
	private final StudyDao studyDao;
	private final WorkerDao workerDao;

	@Inject
	StudyResults(JatosGuiExceptionThrower jatosGuiExceptionThrower,
			StudyService studyService, UserService userService,
			WorkerService workerService, ResultService resultService,
			StudyDao studyDao, JsonUtils jsonUtils, WorkerDao workerDao) {
		this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
		this.studyService = studyService;
		this.userService = userService;
		this.workerService = workerService;
		this.resultService = resultService;
		this.studyDao = studyDao;
		this.jsonUtils = jsonUtils;
		this.workerDao = workerDao;
	}

	/**
	 * Shows view with all StudyResults of a study.
	 */
	@Transactional
	public Result index(Long studyId, String errorMsg, int httpStatus)
			throws JatosGuiException {
		Logger.info(CLASS_NAME + ".index: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		StudyModel study = studyDao.findById(studyId);
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		try {
			studyService.checkStandardForStudy(study, studyId, loggedInUser);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwStudyIndex(e, study.getId());
		}

		RequestScopeMessaging.error(errorMsg);
		String breadcrumbs = Breadcrumbs.generateForStudy(study,
				Breadcrumbs.RESULTS);
		return status(httpStatus,
				views.html.gui.result.studysStudyResults.render(loggedInUser,
						breadcrumbs, study));
	}

	@Transactional
	public Result index(Long studyId, String errorMsg) throws JatosGuiException {
		return index(studyId, errorMsg, Http.Status.OK);
	}

	@Transactional
	public Result index(Long studyId) throws JatosGuiException {
		return index(studyId, null, Http.Status.OK);
	}

	/**
	 * Ajax request
	 * 
	 * Takes a string with a list of StudyResults and removes them all.
	 */
	@Transactional
	public Result remove(String studyResultIds) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".remove: studyResultIds " + studyResultIds
				+ ", " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		try {
			resultService.removeAllStudyResults(studyResultIds, loggedInUser);
		} catch (ForbiddenException | BadRequestException | NotFoundException e) {
			jatosGuiExceptionThrower.throwAjax(e);
		}
		return ok().as("text/html");
	}

	/**
	 * Ajax request
	 * 
	 * Returns all StudyResults of a study in JSON format.
	 */
	@Transactional
	public Result tableDataByStudy(Long studyId) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".tableDataByStudy: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		StudyModel study = studyDao.findById(studyId);
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		String dataAsJson = null;
		try {
			studyService.checkStandardForStudy(study, studyId, loggedInUser);
			dataAsJson = jsonUtils.allStudyResultsForUI(study);
		} catch (IOException e) {
			String errorMsg = MessagesStrings.PROBLEM_GENERATING_JSON_DATA;
			jatosGuiExceptionThrower.throwAjax(errorMsg,
					Http.Status.INTERNAL_SERVER_ERROR);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwAjax(e);
		}
		return ok(dataAsJson);
	}

	/**
	 * Ajax request
	 * 
	 * Returns all StudyResults belonging to a worker as JSON.
	 */
	@Transactional
	public Result tableDataByWorker(Long workerId) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".tableDataByWorker: workerId " + workerId
				+ ", " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		Worker worker = workerDao.findById(workerId);
		try {
			workerService.checkWorker(worker, workerId);
		} catch (BadRequestException e) {
			jatosGuiExceptionThrower.throwAjax(e);
		}

		List<StudyResult> allowedStudyResultList = resultService
				.getAllowedStudyResultList(loggedInUser, worker);
		String dataAsJson = null;
		try {
			dataAsJson = jsonUtils.allStudyResultsForUI(allowedStudyResultList);
		} catch (IOException e) {
			String errorMsg = MessagesStrings.PROBLEM_GENERATING_JSON_DATA;
			jatosGuiExceptionThrower.throwAjax(errorMsg,
					Http.Status.INTERNAL_SERVER_ERROR);
		}
		return ok(dataAsJson);
	}

	/**
	 * Ajax request
	 * 
	 * Returns all result data of ComponentResults belonging to StudyResults
	 * specified in the given string as text.
	 */
	@Transactional
	public Result exportData(String studyResultIds) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".exportData: studyResultIds "
				+ studyResultIds + ", " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		// Remove cookie of jQuery.fileDownload plugin
		response().discardCookie(ImportExportService.JQDOWNLOAD_COOKIE_NAME);
		UserModel loggedInUser = userService.retrieveLoggedInUser();

		String studyResultDataAsStr = null;
		try {
			studyResultDataAsStr = resultService.generateStudyResultStr(
					studyResultIds, loggedInUser);
		} catch (ForbiddenException | BadRequestException | NotFoundException e) {
			jatosGuiExceptionThrower.throwAjax(e);
		}

		response().setContentType("application/x-download");
		String filename = "results_" + DateUtils.getDateForFile(new Date())
				+ "." + IOUtils.TXT_FILE_SUFFIX;
		response().setHeader("Content-disposition",
				"attachment; filename=" + filename);
		// Set cookie for jQuery.fileDownload plugin
		response().setCookie(ImportExportService.JQDOWNLOAD_COOKIE_NAME,
				ImportExportService.JQDOWNLOAD_COOKIE_CONTENT);
		return ok(studyResultDataAsStr);
	}

}
