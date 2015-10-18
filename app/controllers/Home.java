package controllers;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import models.StudyModel;
import models.UserModel;
import persistance.StudyDao;
import play.Logger;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.BreadcrumbsService;
import services.JatosGuiExceptionThrower;
import services.UserService;
import utils.IOUtils;
import utils.JsonUtils;
import utils.MessagesStrings;
import controllers.actionannotations.AuthenticationAction.Authenticated;
import controllers.actionannotations.JatosGuiAction.JatosGui;
import exceptions.JatosGuiException;

/**
 * Controller that provides actions for the home view.
 * 
 * @author Kristian Lange
 */
@JatosGui
@Authenticated
@Singleton
public class Home extends Controller {

	private static final String CLASS_NAME = Home.class.getSimpleName();

	private final JatosGuiExceptionThrower jatosGuiExceptionThrower;
	private final JsonUtils jsonUtils;
	private final UserService userService;
	private final BreadcrumbsService breadcrumbsService;
	private final StudyDao studyDao;

	@Inject
	Home(JatosGuiExceptionThrower jatosGuiExceptionThrower,
			JsonUtils jsonUtils, UserService userService,
			BreadcrumbsService breadcrumbsService, StudyDao studyDao) {
		this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
		this.jsonUtils = jsonUtils;
		this.userService = userService;
		this.breadcrumbsService = breadcrumbsService;
		this.studyDao = studyDao;
	}

	/**
	 * Shows home view
	 */
	@Transactional
	public Result home(int httpStatus) {
		Logger.info(CLASS_NAME + ".home: " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		List<StudyModel> studyList = studyDao.findAllByUser(loggedInUser
				.getEmail());
		String breadcrumbs = breadcrumbsService.generateForHome();
		return status(httpStatus, views.html.gui.home.render(studyList,
				loggedInUser, breadcrumbs));
	}

	@Transactional
	public Result home() {
		return home(Http.Status.OK);
	}

	/**
	 * Ajax request
	 * 
	 * Returns a list of all studies and their components belonging to the
	 * logged-in user for use in the GUI's sidebar.
	 */
	@Transactional
	public Result sidebarStudyList() {
		Logger.info(CLASS_NAME + ".sidebarStudyList: "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		List<StudyModel> studyList = studyDao.findAllByUser(loggedInUser
				.getEmail());
		return ok(jsonUtils.sidebarStudyList(studyList));
	}

	/**
	 * Returns a Chunks<String> with the content of the log file only if admin
	 * is logged in. It limits the number of lines to the given lineLimit.
	 */
	@Transactional
	public Result log(Integer lineLimit) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".log: " + "lineLimit " + lineLimit + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		if (!loggedInUser.getEmail().equals(UserService.ADMIN_EMAIL)) {
			jatosGuiExceptionThrower.throwHome(
					MessagesStrings.ONLY_ADMIN_CAN_SEE_LOGS,
					Http.Status.FORBIDDEN);
		}
		return ok(IOUtils.readApplicationLog(lineLimit));
	}
}
