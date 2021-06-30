package controllers.gui;

import com.google.common.base.Strings;
import controllers.gui.actionannotations.AuthenticationAction.Authenticated;
import controllers.gui.actionannotations.GuiAccessLoggingAction.GuiAccessLogging;
import daos.common.StudyDao;
import general.common.Common;
import models.common.Study;
import models.common.User;
import play.db.jpa.Transactional;
import play.libs.ws.WSClient;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.gui.AuthenticationService;
import services.gui.BreadcrumbsService;
import services.gui.LogFileReader;
import utils.common.Helpers;
import utils.common.Helpers;
import utils.common.JsonUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Controller that provides actions for the home page
 *
 * @author Kristian Lange
 */
@GuiAccessLogging
@Singleton
public class Home extends Controller {

    private final JsonUtils jsonUtils;
    private final AuthenticationService authenticationService;
    private final BreadcrumbsService breadcrumbsService;
    private final StudyDao studyDao;
    private final WSClient ws;

    @Inject
    Home(JsonUtils jsonUtils, AuthenticationService authenticationService,
            BreadcrumbsService breadcrumbsService, StudyDao studyDao, WSClient ws) {
        this.jsonUtils = jsonUtils;
        this.authenticationService = authenticationService;
        this.breadcrumbsService = breadcrumbsService;
        this.studyDao = studyDao;
        this.ws = ws;
    }

    /**
     * Shows home view
     */
    @Transactional
    @Authenticated
    public Result home(int httpStatus) {
        User loggedInUser = authenticationService.getLoggedInUser();
        List<Study> studyList = studyDao.findAllByUser(loggedInUser);
        String breadcrumbs = breadcrumbsService.generateForHome();
        return status(httpStatus,
                views.html.gui.home.render(studyList, loggedInUser, breadcrumbs, Helpers.isLocalhost()));
    }

    @Transactional
    @Authenticated
    public Result home() {
        return home(Http.Status.OK);
    }

    /**
     * Tries to loads some static HTML that will be shown on the home page instead of the default welcome message
     */
    @Transactional
    @Authenticated
    public CompletionStage<Result> branding() {
        User loggedInUser = authenticationService.getLoggedInUser();
        if (Strings.isNullOrEmpty(Common.getBrandingUrl())) return CompletableFuture.completedFuture(noContent());
        return ws.url(Common.getBrandingUrl()).get().thenApply(r -> {
            String branding = r.getBody()
                    .replaceAll("@JATOS_VERSION", Common.getJatosVersion())
                    .replaceAll("@USER_NAME", loggedInUser.getName())
                    .replaceAll("@USER_USERNAME", loggedInUser.getUsername());
            if (r.getStatus() == 404 || branding.startsWith("404")) return notFound();
            return ok(branding).withHeader("Cache-Control", "max-age=3600");
        });
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
        User loggedInUser = authenticationService.getLoggedInUser();
        List<Study> studyList = studyDao.findAllByUser(loggedInUser);
        return ok(jsonUtils.sidebarStudyList(studyList));
    }

}
