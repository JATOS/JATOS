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
import general.common.UpdateJatos;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

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
    private final UpdateJatos updateJatos;

    @Inject
    Home(JsonUtils jsonUtils, AuthenticationService authenticationService,
            BreadcrumbsService breadcrumbsService, StudyDao studyDao,
            LogFileReader logFileReader, UpdateJatos updateJatos) {
        this.jsonUtils = jsonUtils;
        this.authenticationService = authenticationService;
        this.breadcrumbsService = breadcrumbsService;
        this.studyDao = studyDao;
        this.logFileReader = logFileReader;
        this.updateJatos = updateJatos;
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

    @Transactional
    @Authenticated
    public CompletionStage<Result> getLatestJatosInfo() {
        LOGGER.debug(".checkUpdatable");
        return updateJatos.checkUpdatable().handle((result, error) -> {
            if (error != null) {
                Logger.error("Couldn't request latest JATOS info.");
                return status(503, "Couldn't request latest JATOS info.");
            } else {
                return ok(result);
            }
        });
    }

    /**
     * Downloads the latest JATOS release into the tmp directory without installing it
     */
    @Transactional
    @Authenticated(Role.ADMIN)
    public Result downloadLatestJatos(Boolean dry) {
        LOGGER.debug(".downloadLatestJatos");
        if (UpdateJatos.isOsUx()) {
            Logger.error("Tried to update JATOS on a system other than Linux or MacOS.");
            return forbidden("Can only update JATOS on a Linux or MacOS system.");
        }

        try {
            if (dry) {
                TimeUnit.SECONDS.sleep(5);
            } else {
                updateJatos.download();
            }
        } catch (IOException | InterruptedException e) {
            LOGGER.error("An error occurred while downloading the new JATOS version.");
            return internalServerError(
                    "An error occurred while downloading the new JATOS version.");
        }
        Logger.info("Successfully downloaded JATOS update.");
        return ok(" "); // jQuery can't deal with empty POST response
    }

    /**
     * Moves JATOS update files from tmp directory into the current JATOS working dir
     */
    @Transactional
    @Authenticated(Role.ADMIN)
    public Result updateJatosFiles(Boolean dry) {
        LOGGER.debug(".updateJatosFiles");
        if (UpdateJatos.isOsUx()) {
            Logger.error("Tried to update JATOS on a system other than Linux or MacOS.");
            return forbidden("Can only update JATOS on a Linux or MacOS system.");
        }

        try {
            if (dry) {
                TimeUnit.SECONDS.sleep(2);
            } else {
                updateJatos.updateFiles();
            }
        } catch (IOException | InterruptedException e) {
            LOGGER.error("An error occurred while updating to the new JATOS version.");
            return internalServerError(
                    "An error occurred while updating to the new JATOS version.");
        }
        Logger.info("Successfully moved files for JATOS update.");
        return ok(" "); // jQuery can't deal with empty POST response
    }

    @Transactional
    @Authenticated(Role.ADMIN)
    public Result restartJatos() {
        LOGGER.debug(".restartJatos");
        if (UpdateJatos.isOsUx()) {
            Logger.error("Tried to restart JATOS on a system other than Linux or MacOS.");
            return forbidden("Can only restart JATOS on a Linux or MacOS system.");
        }

        updateJatos.restartJatos();
        return ok(" "); // jQuery can't deal with empty POST response
    }

}
