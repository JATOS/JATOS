package controllers;

import java.util.List;

import models.StudyModel;
import models.UserModel;
import play.Logger;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.Breadcrumbs;
import services.Messages;

import com.google.inject.Inject;

import exceptions.ResultException;

/**
 * Controller that provides actions for the home view.
 * 
 * @author Kristian Lange
 */
public class Home extends Controller {

	private static final String CLASS_NAME = Home.class.getSimpleName();

	private final ControllerUtils controllerUtils;

	@Inject
	public Home(ControllerUtils controllerUtils) {
		this.controllerUtils = controllerUtils;
	}

	/**
	 * Shows home view
	 */
	@Transactional
	public Result home(String errorMsg, int httpStatus) throws ResultException {
		Logger.info(CLASS_NAME + ".home: " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		UserModel loggedInUser = controllerUtils.retrieveLoggedInUser();
		List<StudyModel> studyList = StudyModel.findAllByUser(loggedInUser
				.getEmail());
		Messages messages = new Messages().error(errorMsg);
		Breadcrumbs breadcrumbs = Breadcrumbs.generateForHome();
		return status(httpStatus, views.html.jatos.home.render(studyList,
				loggedInUser, breadcrumbs, messages));
	}

	@Transactional
	public Result home() throws ResultException {
		return home(null, Http.Status.OK);
	}

}
