package controllers.gui;

import controllers.gui.actionannotations.AuthenticationAction.Authenticated;
import controllers.gui.actionannotations.GuiAccessLoggingAction.GuiAccessLogging;
import daos.common.StudyDao;
import general.common.JatosUpdater;
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
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletionStage;

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
    private final JatosUpdater jatosUpdater;

    @Inject
    Home(JsonUtils jsonUtils, AuthenticationService authenticationService,
            BreadcrumbsService breadcrumbsService, StudyDao studyDao,
            LogFileReader logFileReader, JatosUpdater jatosUpdater) {
        this.jsonUtils = jsonUtils;
        this.authenticationService = authenticationService;
        this.breadcrumbsService = breadcrumbsService;
        this.studyDao = studyDao;
        this.logFileReader = logFileReader;
        this.jatosUpdater = jatosUpdater;
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

    /**
     * Checks whether there is an JATOS update available and if yes returns the version name.
     *
     * @param allowPreUpdates If true, allows requesting of pre-releases too
     */
    @Transactional
    @Authenticated
    public CompletionStage<Result> updateInfo(Boolean allowPreUpdates) {
        LOGGER.debug(".updateInfo");
        return jatosUpdater.updateInfo(allowPreUpdates).handle((result, error) -> {
            if (error != null) {
                LOGGER.error("Couldn't request latest JATOS update info.");
                return badRequest("Couldn't request latest JATOS update info.");
            } else {
                return ok(result);
            }
        });
    }

    /**
     * Downloads the latest JATOS release into the system's tmp directory without installing it.
     *
     * @param dry Allows testing the endpoint without actually downloading anything
     */
    @Transactional
    @Authenticated(Role.ADMIN)
    public CompletionStage<Result> downloadLatestJatos(Boolean dry) {
        LOGGER.debug(".downloadLatestJatos");
        return jatosUpdater.downloadFromGitHubAndUnzip(dry).handle((result, error) -> {
            if (error != null) {
                LOGGER.error("A problem occurred while downloading a new JATOS version.", error);
                return badRequest(
                        "A problem occurred while downloading a new JATOS version.");
            } else {
                return ok(" "); // jQuery can't deal with empty POST response
            }
        });
    }

    /**
     * Moves JATOS update files from tmp directory into the current JATOS working dir into a
     * separate folder. It does not overwrite any files. Then it restarts using the laoder script
     * with the 'update' argument. The actual update of files is done in the loader script.
     *
     * @param dry Allows testing the endpoint without actually updating. Restarts happens anyway.
     */
    @Transactional
    @Authenticated(Role.ADMIN)
    public Result updateAndRestart(Boolean dry) {
        LOGGER.debug(".updateAndRestart");
        try {
            jatosUpdater.updateAndRestart(dry);
        } catch (IOException e) {
            LOGGER.error("An error occurred while updating to the new JATOS version.", e);
            return badRequest(
                    "An error occurred while updating to the new JATOS version.");
        }
        return ok(" "); // jQuery can't deal with empty POST response
    }

}
