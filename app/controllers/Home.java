package controllers;

import java.util.List;

import models.StudyModel;
import models.UserModel;
import play.Logger;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.Security;
import services.Breadcrumbs;
import services.Messages;
import exceptions.ResultException;

@Security.Authenticated(Secured.class)
public class Home extends Controller {

	private static final String CLASS_NAME = Home.class.getSimpleName();

	@Transactional
	public static Result home(String errorMsg, int httpStatus)
			throws ResultException {
		Logger.info(CLASS_NAME + ".home: " + "logged-in user's email "
				+ session(Users.COOKIE_EMAIL));
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		List<StudyModel> studyList = StudyModel.findAllByUser(loggedInUser
				.getEmail());
		Messages messages = new Messages().error(errorMsg);
		Breadcrumbs breadcrumbs = Breadcrumbs.generateForHome();
		return status(httpStatus, views.html.mecharg.home.render(studyList,
				loggedInUser, breadcrumbs, messages));
	}

	@Transactional
	public static Result home() throws ResultException {
		return home(null, Http.Status.OK);
	}

}
