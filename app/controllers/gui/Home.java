package controllers.gui;

import java.util.List;

import models.StudyModel;
import models.UserModel;
import persistance.StudyDao;
import play.Logger;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.With;
import services.gui.Breadcrumbs;
import services.gui.UserService;
import utils.JsonUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import exceptions.gui.JatosGuiException;

/**
 * Controller that provides actions for the home view.
 * 
 * @author Kristian Lange
 */
@With(JatosGuiAction.class)
@Singleton
public class Home extends Controller {

	private static final String CLASS_NAME = Home.class.getSimpleName();

	private final JsonUtils jsonUtils;
	private final UserService userService;
	private final StudyDao studyDao;

	@Inject
	Home(JsonUtils jsonUtils, UserService userService, StudyDao studyDao) {
		this.jsonUtils = jsonUtils;
		this.userService = userService;
		this.studyDao = studyDao;
	}

	/**
	 * Shows home view
	 */
	@Transactional
	public Result home(int httpStatus) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".home: " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		List<StudyModel> studyList = studyDao.findAllByUser(loggedInUser
				.getEmail());
		String breadcrumbs = Breadcrumbs.generateForHome();
		return status(httpStatus, views.html.gui.home.render(studyList,
				loggedInUser, breadcrumbs));
	}

	@Transactional
	public Result home() throws JatosGuiException {
		return home(Http.Status.OK);
	}

	/**
	 * Ajax request
	 * 
	 * Returns a list of all studies and their components belonging to the
	 * logged-in user for use in the GUI's sidebar.
	 */
	@Transactional
	public Result sidebarStudyList() throws JatosGuiException {
		Logger.info(CLASS_NAME + ".sidebarStudyList: "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		List<StudyModel> studyList = studyDao.findAllByUser(loggedInUser
				.getEmail());
		return ok(jsonUtils.sidebarStudyList(studyList));
	}

}
