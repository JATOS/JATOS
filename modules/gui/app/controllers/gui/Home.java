package controllers.gui;

import controllers.gui.actionannotations.AuthenticationAction.Authenticated;
import controllers.gui.actionannotations.GuiAccessLoggingAction.GuiAccessLogging;
import daos.common.StudyDao;
import models.common.Study;
import models.common.User;
import models.common.User.Role;
import play.Logger;
import play.Logger.ALogger;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.gui.AuthenticationService;
import services.gui.BreadcrumbsService;
import services.gui.LogFileReader;
import utils.common.HttpUtils;
import utils.common.JsonUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

/**
 * Controller that provides actions for the home view.
 *
 * @author Kristian Lange
 */
@GuiAccessLogging
@Singleton
public class Home extends Controller {

    private static final ALogger LOGGER = Logger.of(Home.class);

    private final JsonUtils jsonUtils;
    private final AuthenticationService authenticationService;
    private final BreadcrumbsService breadcrumbsService;
    private final StudyDao studyDao;
    private final LogFileReader logFileReader;

    @Inject
    Home(JsonUtils jsonUtils, AuthenticationService authenticationService,
            BreadcrumbsService breadcrumbsService, StudyDao studyDao,
            LogFileReader logFileReader) {
        this.jsonUtils = jsonUtils;
        this.authenticationService = authenticationService;
        this.breadcrumbsService = breadcrumbsService;
        this.studyDao = studyDao;
        this.logFileReader = logFileReader;
    }

    /**
     * Shows home view
     */
    @Transactional
    @Authenticated
    public Result home(int httpStatus) {
        LOGGER.debug(".home");
        User loggedInUser = authenticationService.getLoggedInUser();
        List<Study> studyList = studyDao.findAllByUser(loggedInUser);
        String breadcrumbs = breadcrumbsService.generateForHome();
        return status(httpStatus, views.html.gui.home.render(studyList,
                loggedInUser, breadcrumbs, HttpUtils.isLocalhost()));
    }

    @Transactional
    @Authenticated
    public Result home() {
        return home(Http.Status.OK);
    }

    /**
     * Ajax request
     * <p>
     * Returns a list of all studies and their components belonging to the
     * logged-in user for use in the GUI's sidebar.
     */
    @Transactional
    @Authenticated
    public Result sidebarStudyList() {
        LOGGER.debug(".sidebarStudyList");
        User loggedInUser = authenticationService.getLoggedInUser();
        List<Study> studyList = studyDao.findAllByUser(loggedInUser);
        return ok(jsonUtils.sidebarStudyList(studyList));
    }

    /**
     * Returns the content of the log file in reverse order and as
     * 'Transfer-Encoding:chunked'. It does so only if an user with Role ADMIN
     * is logged in. It limits the number of lines to the given lineLimit. If
     * the log file can't be read it still returns with OK but instead of the
     * file content with an error message.
     */
    @Transactional
    @Authenticated(Role.ADMIN)
    public Result log(Integer lineLimit) {
        LOGGER.debug(".log: " + "lineLimit " + lineLimit);
        return ok().chunked(logFileReader.read("application.log", lineLimit))
                .as("text/plain; charset=utf-8");
    }
}
