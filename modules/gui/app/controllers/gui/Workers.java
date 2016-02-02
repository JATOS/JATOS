package controllers.gui;

import java.io.IOException;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

import controllers.gui.actionannotations.AuthenticationAction.Authenticated;
import controllers.gui.actionannotations.JatosGuiAction.JatosGui;
import daos.common.StudyDao;
import daos.common.worker.WorkerDao;
import exceptions.gui.BadRequestException;
import exceptions.gui.ForbiddenException;
import exceptions.gui.JatosGuiException;
import general.common.MessagesStrings;
import models.common.Study;
import models.common.User;
import models.common.workers.Worker;
import play.Logger;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.gui.Checker;
import services.gui.JatosGuiExceptionThrower;
import services.gui.UserService;
import services.gui.WorkerService;
import utils.common.JsonUtils;

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
	private final Checker checker;
	private final UserService userService;
	private final WorkerService workerService;
	private final JsonUtils jsonUtils;
	private final StudyDao studyDao;
	private final WorkerDao workerDao;

	@Inject
	Workers(JatosGuiExceptionThrower jatosGuiExceptionThrower, Checker checker,
			UserService userService, WorkerService workerService,
			StudyDao studyDao, JsonUtils jsonUtils, WorkerDao workerDao) {
		this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
		this.checker = checker;
		this.userService = userService;
		this.workerService = workerService;
		this.studyDao = studyDao;
		this.jsonUtils = jsonUtils;
		this.workerDao = workerDao;
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
		User loggedInUser = userService.retrieveLoggedInUser();
		try {
			checker.checkWorker(worker, workerId);
		} catch (BadRequestException e) {
			jatosGuiExceptionThrower.throwRedirect(e,
					controllers.gui.routes.Home.home());
		}

		try {
			checker.checkRemovalAllowed(worker, loggedInUser);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwAjax(e);
		}
		workerDao.remove(worker);
		return ok();
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
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();

		String dataAsJson = null;
		try {
			checker.checkStandardForStudy(study, studyId, loggedInUser);

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
