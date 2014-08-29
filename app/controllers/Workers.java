package controllers;

import java.util.ArrayList;
import java.util.List;

import models.StudyModel;
import models.UserModel;
import models.results.StudyResult;
import models.workers.MAWorker;
import models.workers.Worker;
import play.Logger;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Security;
import services.Persistance;
import exceptions.ResultException;

@Security.Authenticated(Secured.class)
public class Workers extends Controller {

	private static final String CLASS_NAME = Workers.class.getSimpleName();

	@Transactional
	public static Result index(Long workerId) throws ResultException {
		Logger.info(CLASS_NAME + ".index: " + "workerId " + workerId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		UserModel loggedInUser = UserModel
				.findByEmail(session(Users.COOKIE_EMAIL));
		Worker worker = Worker.findById(workerId);
		List<StudyModel> studyList = StudyModel.findAll();
		if (loggedInUser == null) {
			return redirect(routes.Authentication.login());
		}
		if (worker == null) {
			throw BadRequests.badRequestWorkerNotExist(workerId, loggedInUser,
					studyList);
		}

		// Generate the list of StudyResults that the logged-in user is allowed
		// to see
		List<StudyResult> allowedstudyResultList = new ArrayList<StudyResult>();
		for (StudyResult studyResult : worker.getStudyResultList()) {
			if (studyResult.getStudy().hasMember(loggedInUser)) {
				allowedstudyResultList.add(studyResult);
			}
		}

		String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
				Breadcrumbs.getDashboardBreadcrumb(), "Worker",
				Breadcrumbs.getWorkerBreadcrumb(worker));
		return ok(views.html.mecharg.worker.index
				.render(studyList, loggedInUser, breadcrumbs, null, worker,
						allowedstudyResultList));
	}

	/**
	 * HTTP Ajax request
	 */
	@Transactional
	public static Result remove(Long workerId) throws ResultException {
		Logger.info(CLASS_NAME + ".remove: workerId " + workerId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		Worker worker = Worker.findById(workerId);
		List<StudyModel> studyList = StudyModel.findAll();
		UserModel loggedInUser = UserModel
				.findByEmail(session(Users.COOKIE_EMAIL));
		if (loggedInUser == null) {
			return redirect(routes.Authentication.login());
		}
		if (worker == null) {
			throw BadRequests.badRequestWorkerNotExist(workerId, loggedInUser,
					studyList);
		}

		if (worker instanceof MAWorker) {
			MAWorker maWorker = (MAWorker) worker;
			throw BadRequests.forbiddenRemoveMAWorker(maWorker, loggedInUser,
					studyList);
		}

		// Check for every study the worker participated in whether the
		// logged-in user is member of this study and deny if not.
		for (StudyResult studyResult : worker.getStudyResultList()) {
			if (!studyResult.getStudy().hasMember(loggedInUser)) {
				throw BadRequests.forbiddenNotMember(loggedInUser,
						studyResult.getStudy(), studyList);
			}
		}

		Persistance.removeWorker(worker);
		return ok();
	}

}
