package controllers;

import java.util.List;

import models.StudyModel;
import models.UserModel;
import models.workers.Worker;
import play.Logger;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Security;

@Security.Authenticated(Secured.class)
public class Dashboard extends Controller {
	
	private static final String CLASS_NAME = Dashboard.class.getSimpleName();

	@Transactional
	public static Result dashboard() {
		Logger.info(CLASS_NAME + ".dashboard: "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		List<StudyModel> studyList = StudyModel.findAll();
		UserModel loggedInUser = UserModel
				.findByEmail(session(Users.COOKIE_EMAIL));
		if (loggedInUser == null) {
			return redirect(routes.Authentication.login());
		}
		List<UserModel> userList = UserModel.findAll();
		List<Worker> workerList = Worker.findAll();
		String breadcrumbs = Breadcrumbs
				.generateBreadcrumbs(Breadcrumbs.getDashboardBreadcrumb());
		return ok(views.html.mecharg.dashboard.render(studyList, loggedInUser,
				breadcrumbs, userList, workerList, null));
	}

}
