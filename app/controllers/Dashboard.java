package controllers;

import java.util.List;

import models.StudyModel;
import models.UserModel;
import play.data.Form;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Security;
import controllers.routes;
import controllers.Authentication.Login;

public class Dashboard extends Controller {

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result dashboard() {
		List<StudyModel> studyList = StudyModel.findAll();
		UserModel loggedInUser = UserModel
				.findByEmail(session(Users.COOKIE_EMAIL));
		if (loggedInUser == null) {
			return redirect(routes.Authentication.login());
		}
		List<UserModel> userList = UserModel.findAll();
		String breadcrumbs = Breadcrumbs
				.generateBreadcrumbs(Breadcrumbs.getDashboardBreadcrumb());
		return ok(views.html.mecharg.dashboard.render(studyList, loggedInUser,
				breadcrumbs, userList, null));
	}

}
