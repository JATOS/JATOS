package controllers;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import models.StudyModel;
import models.StudyResult;
import models.UserModel;
import models.workers.JatosWorker;
import models.workers.Worker;
import play.Logger;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.With;
import services.Breadcrumbs;
import services.ErrorMessages;
import services.JatosGuiExceptionThrower;
import services.Messages;
import services.StudyService;
import services.UserService;
import services.WorkerService;
import utils.JsonUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import common.JatosGuiAction;

import daos.IStudyDao;
import daos.workers.IWorkerDao;
import exceptions.JatosGuiException;

/**
 * Controller that handles all worker actions in JATOS' GUI
 * 
 * @author Kristian Lange
 */
@With(JatosGuiAction.class)
@Singleton
public class Workers extends Controller {

	private static final String CLASS_NAME = Workers.class.getSimpleName();

	private final JatosGuiExceptionThrower jatosGuiExceptionThrower;
	private final StudyService studyService;
	private final UserService userService;
	private final WorkerService workerService;
	private final JsonUtils jsonUtils;
	private final IStudyDao studyDao;
	private final IWorkerDao workerDao;

	@Inject
	public Workers(JatosGuiExceptionThrower jatosGuiExceptionThrower,
			StudyService studyService, UserService userService,
			WorkerService workerService, IStudyDao studyDao,
			JsonUtils jsonUtils, IWorkerDao workerDao) {
		this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
		this.studyService = studyService;
		this.userService = userService;
		this.workerService = workerService;
		this.studyDao = studyDao;
		this.jsonUtils = jsonUtils;
		this.workerDao = workerDao;
	}

	/**
	 * Shows view with worker details.
	 */
	@Transactional
	public Result index(Long workerId, String errorMsg, int httpStatus)
			throws JatosGuiException {
		Logger.info(CLASS_NAME + ".index: " + "workerId " + workerId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		List<StudyModel> studyList = studyDao.findAllByUser(loggedInUser
				.getEmail());
		Worker worker = workerDao.findById(workerId);
		workerService.checkWorker(worker, workerId);

		Messages messages = new Messages().error(errorMsg);
		Breadcrumbs breadcrumbs = Breadcrumbs.generateForWorker(worker,
				Breadcrumbs.RESULTS);
		return status(httpStatus,
				views.html.jatos.result.workersStudyResults.render(studyList,
						loggedInUser, breadcrumbs, messages, worker));
	}

	@Transactional
	public Result index(Long workerId, String errorMsg)
			throws JatosGuiException {
		return index(workerId, errorMsg, Http.Status.OK);
	}

	@Transactional
	public Result index(Long workerId) throws JatosGuiException {
		return index(workerId, null, Http.Status.OK);
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
		workerService.checkWorker(worker, workerId);

		checkRemoval(worker, loggedInUser);
		workerDao.remove(worker);
		return ok();
	}

	private void checkRemoval(Worker worker, UserModel loggedInUser)
			throws JatosGuiException {
		// JatosWorker associated to a JATOS user must not be removed
		if (worker instanceof JatosWorker) {
			JatosWorker maWorker = (JatosWorker) worker;
			String errorMsg = ErrorMessages.removeJatosWorkerNotAllowed(worker
					.getId(), maWorker.getUser().getName(), maWorker.getUser()
					.getEmail());
			jatosGuiExceptionThrower.throwAjax(errorMsg, Http.Status.FORBIDDEN);
		}

		// Check for every study if removal is allowed
		for (StudyResult studyResult : worker.getStudyResultList()) {
			StudyModel study = studyResult.getStudy();
			studyService.checkStandardForStudy(study, study.getId(),
					loggedInUser);
			studyService.checkStudyLocked(study);
		}
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
		studyService.checkStandardForStudy(study, studyId, loggedInUser);

		String dataAsJson = null;
		try {
			Set<Worker> workerSet = workerService.retrieveWorkers(study);
			dataAsJson = jsonUtils.allWorkersForUI(workerSet);
		} catch (IOException e) {
			String errorMsg = ErrorMessages.PROBLEM_GENERATING_JSON_DATA;
			jatosGuiExceptionThrower.throwAjax(errorMsg,
					Http.Status.INTERNAL_SERVER_ERROR);
		}
		return ok(dataAsJson);
	}

}
