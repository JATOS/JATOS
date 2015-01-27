package controllers;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import models.StudyModel;
import models.UserModel;
import models.results.StudyResult;
import models.workers.JatosWorker;
import models.workers.Worker;
import play.Logger;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.Security;
import services.Breadcrumbs;
import services.ErrorMessages;
import services.JsonUtils;
import services.Messages;
import services.PersistanceUtils;
import exceptions.ResultException;

@Security.Authenticated(Secured.class)
public class Workers extends Controller {

	private static final String CLASS_NAME = Workers.class.getSimpleName();

	/**
	 * Shows view with worker details.
	 */
	@Transactional
	public static Result index(Long workerId, String errorMsg, int httpStatus)
			throws ResultException {
		Logger.info(CLASS_NAME + ".index: " + "workerId " + workerId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		List<StudyModel> studyList = StudyModel.findAllByUser(loggedInUser
				.getEmail());
		Worker worker = Worker.findById(workerId);
		ControllerUtils.checkWorker(worker, workerId);

		Messages messages = new Messages().error(errorMsg);
		Breadcrumbs breadcrumbs = Breadcrumbs.generateForWorker(worker,
				Breadcrumbs.RESULTS);
		return status(httpStatus,
				views.html.jatos.result.workersStudyResults.render(studyList,
						loggedInUser, breadcrumbs, messages, worker));
	}

	@Transactional
	public static Result index(Long workerId, String errorMsg)
			throws ResultException {
		return index(workerId, errorMsg, Http.Status.OK);
	}

	@Transactional
	public static Result index(Long workerId) throws ResultException {
		return index(workerId, null, Http.Status.OK);
	}

	/**
	 * HTTP Ajax request
	 * 
	 * Remove a worker including its results.
	 */
	@Transactional
	public static Result remove(Long workerId) throws ResultException {
		Logger.info(CLASS_NAME + ".remove: workerId " + workerId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		Worker worker = Worker.findById(workerId);
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		ControllerUtils.checkWorker(worker, workerId);

		checkRemoval(worker, loggedInUser);
		PersistanceUtils.removeWorker(worker);
		return ok();
	}

	private static void checkRemoval(Worker worker, UserModel loggedInUser)
			throws ResultException {
		// JatosWorker associated to a JATOS user must not be removed
		if (worker instanceof JatosWorker) {
			JatosWorker maWorker = (JatosWorker) worker;
			String errorMsg = ErrorMessages.removeJatosWorkerNotAllowed(worker
					.getId(), maWorker.getUser().getName(), maWorker.getUser()
					.getEmail());
			ControllerUtils.throwAjaxResultException(errorMsg,
					Http.Status.FORBIDDEN);
		}

		// Check for every study if removal is allowed
		for (StudyResult studyResult : worker.getStudyResultList()) {
			StudyModel study = studyResult.getStudy();
			ControllerUtils.checkStandardForStudy(study, study.getId(),
					loggedInUser);
			ControllerUtils.checkStudyLocked(study);
		}
	}

	/**
	 * HTTP Ajax request
	 * 
	 * Returns a list of workers (as JSON) that did the specified study.
	 */
	@Transactional
	public static Result tableDataByStudy(Long studyId) throws ResultException {
		Logger.info(CLASS_NAME + ".tableDataByStudy: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		ControllerUtils.checkStandardForStudy(study, studyId, loggedInUser);

		String dataAsJson = null;
		try {
			Set<Worker> workerSet = ControllerUtils.retrieveWorkers(study);
			dataAsJson = JsonUtils.allWorkersForUI(workerSet);
		} catch (IOException e) {
			String errorMsg = ErrorMessages.PROBLEM_GENERATING_JSON_DATA;
			ControllerUtils.throwAjaxResultException(errorMsg,
					Http.Status.INTERNAL_SERVER_ERROR);
		}
		return ok(dataAsJson);
	}

}
