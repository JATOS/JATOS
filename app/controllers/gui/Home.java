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
import services.gui.Breadcrumbs;
import services.gui.JatosGuiExceptionThrower;
import services.gui.MessagesStrings;
import services.gui.UserService;
import utils.IOUtils;
import utils.JsonUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import controllers.gui.actionannotations.AuthenticationAction.Authenticated;
import controllers.gui.actionannotations.JatosGuiAction.JatosGui;
import exceptions.gui.JatosGuiException;

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
	private final StudyDao studyDao;

	@Inject
	Home(JatosGuiExceptionThrower jatosGuiExceptionThrower,
			JsonUtils jsonUtils, UserService userService, StudyDao studyDao) {
		this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
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
