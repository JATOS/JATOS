package controllers.gui;

import auth.gui.AuthAction.Auth;
import controllers.gui.actionannotations.GuiAccessLoggingAction.GuiAccessLogging;
import general.common.JatosUpdater;
import models.common.User.Role;
import play.Logger;
import play.Logger.ALogger;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Result;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.concurrent.CompletionStage;

/**
 * Controller class for everything around JATOS' auto-update
 *
 * @author Kristian Lange
 */
@SuppressWarnings("deprecation")
@GuiAccessLogging
@Singleton
public class Updates extends Controller {

    private static final ALogger LOGGER = Logger.of(Updates.class);

    private final JatosUpdater jatosUpdater;

    @Inject
    Updates(JatosUpdater jatosUpdater) {
        this.jatosUpdater = jatosUpdater;
    }

    /**
     * Checks whether there is an JATOS update available and if yes returns ReleaseInfo as JSON
     * Example URL to enforce update to a certain version: example.com/jatos?version=v3.5.1
     *
     * @param version          Can be used to enforce a certain version. If not set the latest version is used.
     * @param allowPreReleases If true, allows requesting of pre-releases too
     */
    @Transactional
    @Auth(Role.ADMIN)
    public CompletionStage<Result> getReleaseInfo(String version, Boolean allowPreReleases) {
        return jatosUpdater.getReleaseInfo(version, allowPreReleases).handle((releaseInfo, error) -> {
            if (error != null) {
                LOGGER.error("Couldn't request JATOS update info.");
                return status(503, "Couldn't request JATOS update info.");
            } else {
                return ok(releaseInfo);
            }
        });
    }

    @Transactional
    @Auth(Role.ADMIN)
    public Result cancelUpdate() {
        jatosUpdater.cancelUpdate();
        return ok();
    }

    /**
     * Downloads the latest JATOS release into the system's tmp directory without installing it.
     *
     * @param dry Allows testing the endpoint without actually downloading anything
     */
    @Transactional
    @Auth(Role.ADMIN)
    public CompletionStage<Result> downloadJatos(Boolean dry) {
        return jatosUpdater.downloadFromGitHubAndUnzip(dry).handle((result, error) -> {
            if (error != null) {
                LOGGER.error("A problem occurred while downloading a new JATOS release.", error);
                return badRequest("A problem occurred while downloading a new JATOS release.");
            } else {
                return ok(" "); // jQuery can't deal with empty POST response
            }
        });
    }

    /**
     * Initializes the actual JATOS update and subsequent restart.
     *
     * @param backupAll If true, everything in the JATOS directory will be copied into a backup folder.
     *                  If false, only the conf directory and the loader scripts.
     */
    @Transactional
    @Auth(Role.ADMIN)
    public Result updateAndRestart(Boolean backupAll) {
        try {
            jatosUpdater.updateAndRestart(backupAll);
        } catch (IOException e) {
            LOGGER.error("An error occurred while updating to the new JATOS release.", e);
            return badRequest("An error occurred while updating to the new JATOS release.");
        }
        return ok(" "); // jQuery can't deal with empty POST response
    }

}
