package controllers;

import java.util.List;

import models.StudyModel;
import models.UserModel;
import play.Logger;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.With;
import services.Breadcrumbs;
import services.Messages;
import services.UserService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import common.JatosGuiAction;

import daos.IStudyDao;
import exceptions.JatosGuiException;

/**
 * Controller that provides actions for the home view.
 * 
 * @author Kristian Lange
 */
@With(JatosGuiAction.class)
@Singleton
public class Home extends Controller {

	private static final String CLASS_NAME = Home.class.getSimpleName();

	private final UserService userService;
	private final IStudyDao studyDao;

	@Inject
	Home(UserService userService, IStudyDao studyDao) {
		this.userService = userService;
		this.studyDao = studyDao;
	}

	/**
	 * Shows home view
	 */
	@Transactional
	public Result home(String errorMsg, int httpStatus)
			throws JatosGuiException {
		Logger.info(CLASS_NAME + ".home: " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		List<StudyModel> studyList = studyDao.findAllByUser(loggedInUser
				.getEmail());
		Messages messages = new Messages().error(errorMsg);
		Breadcrumbs breadcrumbs = Breadcrumbs.generateForHome();
		return status(httpStatus, views.html.jatos.home.render(studyList,
				loggedInUser, breadcrumbs, messages));
	}

	@Transactional
	public Result home() throws JatosGuiException {
		return home(null, Http.Status.OK);
	}

}
