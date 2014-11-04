package controllers;

import java.util.List;

import models.StudyModel;
import models.UserModel;
import models.results.StudyResult;
import models.workers.MAWorker;
import models.workers.Worker;
import play.Logger;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.Security;
import play.mvc.SimpleResult;
import services.ErrorMessages;
import services.PersistanceUtils;
import exceptions.ResultException;

@Security.Authenticated(Secured.class)
public class Workers extends Controller {

	private static final String CLASS_NAME = Workers.class.getSimpleName();

	@Transactional
	public static Result index(Long workerId, String errorMsg, int httpStatus)
			throws ResultException {
		Logger.info(CLASS_NAME + ".index: " + "workerId " + workerId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		Worker worker = Worker.findById(workerId);
		List<StudyModel> studyList = StudyModel.findAllByUser(loggedInUser
				.getEmail());
		ControllerUtils.checkWorker(worker, workerId);

		String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
				Breadcrumbs.getHomeBreadcrumb(),
				Breadcrumbs.getWorkerBreadcrumb(worker));
		return status(httpStatus,
				views.html.mecharg.result.workersStudyResults.render(studyList,
						loggedInUser, breadcrumbs, worker, errorMsg));
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
	 */
	@Transactional
	public static Result remove(Long workerId) throws ResultException {
		Logger.info(CLASS_NAME + ".remove: workerId " + workerId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		Worker worker = Worker.findById(workerId);
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		ControllerUtils.checkWorker(worker, workerId);

		if (worker instanceof MAWorker) {
			MAWorker maWorker = (MAWorker) worker;
			String errorMsg = ErrorMessages
					.removeMAWorker(worker.getId(), maWorker.getUser()
							.getName(), maWorker.getUser().getEmail());
			SimpleResult result = forbidden(errorMsg);
			throw new ResultException(result, errorMsg);
		}

		// Check for every study if removal is allowed
		StudyModel study;
		for (StudyResult studyResult : worker.getStudyResultList()) {
			study = studyResult.getStudy();
			ControllerUtils.checkStandardForStudy(study, study.getId(),
					loggedInUser);
			ControllerUtils.checkStudyLocked(study);
		}

		PersistanceUtils.removeWorker(worker);
		return ok();
	}

}
