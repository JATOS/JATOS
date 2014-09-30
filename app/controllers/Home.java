package controllers;

import java.util.List;

import exceptions.ResultException;
import models.StudyModel;
import models.UserModel;
import models.workers.Worker;
import play.Logger;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.Security;

@Security.Authenticated(Secured.class)
public class Home extends Controller {

	private static final String CLASS_NAME = Home.class.getSimpleName();

	@Transactional
	public static Result home(String errorMsg, int httpStatus)
			throws ResultException {
		Logger.info(CLASS_NAME + ".home: " + "logged-in user's email "
				+ session(Users.COOKIE_EMAIL));
		List<StudyModel> studyList = StudyModel.findAll();
		UserModel loggedInUser = Users.getLoggedInUser();
		List<UserModel> userList = UserModel.findAll();
		List<Worker> workerList = Worker.findAll();
		String breadcrumbs = Breadcrumbs.generateBreadcrumbs(Breadcrumbs
				.getHomeBreadcrumb());
		return status(httpStatus, views.html.mecharg.home.render(studyList,
				loggedInUser, breadcrumbs, userList, workerList, errorMsg));
	}

	@Transactional
	public static Result home() throws ResultException {
		return home(null, Http.Status.OK);
	}

}
