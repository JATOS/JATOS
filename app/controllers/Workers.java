package controllers;

import java.io.IOException;
import java.util.Set;

import models.StudyModel;
import models.UserModel;
import models.workers.Worker;
import persistance.StudyDao;
import persistance.workers.WorkerDao;
import play.Logger;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.BreadcrumbsService;
import services.JatosGuiExceptionThrower;
import services.MessagesStrings;
import services.StudyService;
import services.UserService;
import services.WorkerService;
import utils.JsonUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import controllers.actionannotations.AuthenticationAction.Authenticated;
import controllers.actionannotations.JatosGuiAction.JatosGui;
import exceptions.BadRequestException;
import exceptions.ForbiddenException;
import exceptions.JatosGuiException;

/**
 * Controller that handles all worker actions in JATOS' GUI
 * 
 * @author Kristian Lange
 */
@JatosGui
@Authenticated
@Singleton
public class Workers extends Controller {

	private static final String CLASS_NAME = Workers.class.getSimpleName();

	private final JatosGuiExceptionThrower jatosGuiExceptionThrower;
	private final StudyService studyService;
	private final UserService userService;
	private final WorkerService workerService;
	private final BreadcrumbsService breadcrumbsService;
	private final JsonUtils jsonUtils;
	private final StudyDao studyDao;
	private final WorkerDao workerDao;

	@Inject
	Workers(JatosGuiExceptionThrower jatosGuiExceptionThrower,
			StudyService studyService, UserService userService,
			WorkerService workerService, BreadcrumbsService breadcrumbsService,
			StudyDao studyDao, JsonUtils jsonUtils, WorkerDao workerDao) {
		this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
		this.studyService = studyService;
		this.userService = userService;
		this.workerService = workerService;
		this.breadcrumbsService = breadcrumbsService;
		this.studyDao = studyDao;
		this.jsonUtils = jsonUtils;
		this.workerDao = workerDao;
	}

	/**
	 * Shows view with worker details.
	 */
	@Transactional
	public Result index(Long workerId, int httpStatus) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".index: " + "workerId " + workerId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		Worker worker = workerDao.findById(workerId);
		try {
			workerService.checkWorker(worker, workerId);
		} catch (BadRequestException e) {
			jatosGuiExceptionThrower.throwRedirect(e,
					controllers.routes.Home.home());
		}

		String breadcrumbs = breadcrumbsService.generateForWorker(worker,
				BreadcrumbsService.RESULTS);
		return status(httpStatus,
				views.html.gui.result.workersStudyResults.render(loggedInUser,
						breadcrumbs, worker));
	}

	@Transactional
	public Result index(Long workerId) throws JatosGuiException {
		return index(workerId, Http.Status.OK);
	}

	/**
	 * Ajax request
	 * 
	 * Remove a worker including its results.
	 */
	@Transactional
	public Result remove(Long workerId) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".remove: workerId " + workerId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		Worker worker = workerDao.findById(workerId);
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		try {
			workerService.checkWorker(worker, workerId);
		} catch (BadRequestException e) {
			jatosGuiExceptionThrower.throwRedirect(e,
					controllers.routes.Home.home());
		}

		try {
			workerService.checkRemovalAllowed(worker, loggedInUser);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwAjax(e);
		}
		workerDao.remove(worker);
		return ok().as("text/html");
	}

	/**
	 * Ajax request
	 * 
	 * Returns a list of workers (as JSON) that did the specified study.
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

			Set<Worker> workerSet = workerService.retrieveWorkers(study);
			dataAsJson = jsonUtils.allWorkersForUI(workerSet);
		} catch (IOException e) {
			String errorMsg = MessagesStrings.PROBLEM_GENERATING_JSON_DATA;
			jatosGuiExceptionThrower.throwAjax(errorMsg,
					Http.Status.INTERNAL_SERVER_ERROR);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwAjax(e);
		}
		return ok(dataAsJson);
	}

}
