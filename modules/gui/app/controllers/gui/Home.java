package controllers.gui;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import controllers.gui.actionannotations.AuthenticationAction.Authenticated;
import controllers.gui.actionannotations.GuiAccessLoggingAction.GuiAccessLogging;
import daos.common.StudyDao;
import exceptions.gui.JatosGuiException;
import general.common.MessagesStrings;
import models.common.Study;
import models.common.User;
import play.Logger;
import play.Logger.ALogger;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.gui.BreadcrumbsService;
import services.gui.JatosGuiExceptionThrower;
import services.gui.LogFileReader;
import services.gui.UserService;
import utils.common.HttpUtils;
import utils.common.JsonUtils;

/**
 * Controller that provides actions for the home view.
 * 
 * @author Kristian Lange
 */
@GuiAccessLogging
@Authenticated
@Singleton
public class Home extends Controller {

	private static final ALogger LOGGER = Logger.of(Home.class);

	private final JatosGuiExceptionThrower jatosGuiExceptionThrower;
	private final JsonUtils jsonUtils;
	private final UserService userService;
	private final BreadcrumbsService breadcrumbsService;
	private final StudyDao studyDao;
	private final LogFileReader logFileReader;

	@Inject
	Home(JatosGuiExceptionThrower jatosGuiExceptionThrower, JsonUtils jsonUtils,
			UserService userService, BreadcrumbsService breadcrumbsService,
			StudyDao studyDao, LogFileReader logFileReader) {
		this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
		this.jsonUtils = jsonUtils;
		this.userService = userService;
		this.breadcrumbsService = breadcrumbsService;
		this.studyDao = studyDao;
		this.logFileReader = logFileReader;
	}

	/**
	 * Shows home view
	 */
	@Transactional
	public Result home(int httpStatus) {
		LOGGER.info(".home");
		User loggedInUser = userService.retrieveLoggedInUser();
		List<Study> studyList = studyDao.findAllByUser(loggedInUser);
		String breadcrumbs = breadcrumbsService.generateForHome();
		return status(httpStatus, views.html.gui.home.render(studyList,
				loggedInUser, breadcrumbs, HttpUtils.isLocalhost()));
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
		LOGGER.info(".sidebarStudyList");
		User loggedInUser = userService.retrieveLoggedInUser();
		List<Study> studyList = studyDao.findAllByUser(loggedInUser);
		return ok(jsonUtils.sidebarStudyList(studyList));
	}

	/**
	 * Returns the content of the log file in reverse order and as
	 * 'Transfer-Encoding:chunked'. It does so only if admin is logged in. It
	 * limits the number of lines to the given lineLimit. If the log file can't
	 * be read it still returns with OK but instead of the file content with an
	 * error message.
	 */
	@Transactional
	public Result log(Integer lineLimit) throws JatosGuiException {
		LOGGER.info(".log: " + "lineLimit " + lineLimit);
		User loggedInUser = userService.retrieveLoggedInUser();
		if (!loggedInUser.getEmail().equals(UserService.ADMIN_EMAIL)) {
			jatosGuiExceptionThrower.throwHome(
					MessagesStrings.ONLY_ADMIN_CAN_SEE_LOGS,
					Http.Status.FORBIDDEN);
		}
		return ok().chunked(logFileReader.read(lineLimit));
	}
}
