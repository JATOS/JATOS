package controllers.gui;

import com.google.common.base.Strings;
import auth.gui.AuthAction.Auth;
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
import auth.gui.AuthService;
import services.gui.BreadcrumbsService;
import utils.common.Helpers;
import utils.common.JsonUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Controller that provides actions for the home page
 *
 * @author Kristian Lange
 */
@SuppressWarnings("deprecation")
@GuiAccessLogging
@Singleton
public class Home extends Controller {

    private final JsonUtils jsonUtils;
    private final AuthService authenticationService;
    private final BreadcrumbsService breadcrumbsService;
    private final StudyDao studyDao;
    private final WSClient ws;

    @Inject
    Home(JsonUtils jsonUtils, AuthService authenticationService,
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
    @Auth
    public Result home(int httpStatus) {
        User loggedInUser = authenticationService.getLoggedInUser();
        List<Study> studyList = studyDao.findAllByUser(loggedInUser);
        String breadcrumbs = breadcrumbsService.generateForHome();
        boolean freshlyLoggedIn = loggedInUser.getLastLogin() != null &&
                Duration.between(loggedInUser.getLastLogin().toInstant(), Instant.now())
                        .minusSeconds(30)
                        .isNegative();
        return status(httpStatus, views.html.gui.home.render(studyList, freshlyLoggedIn, loggedInUser, breadcrumbs));
    }

    @Transactional
    @Auth
    public Result home() {
        return home(Http.Status.OK);
    }

    /**
     * Tries to loads some static HTML that will be shown on the home page instead of the default welcome message
     */
    @Transactional
    @Auth
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
     * GET request that returns a list of all studies and their components belonging to the
     * logged-in user for use in the GUI's sidebar.
     */
    @Transactional
    @Auth
    public Result sidebarStudyList() {
        User loggedInUser = authenticationService.getLoggedInUser();
        List<Study> studyList = Helpers.isAllowedSuperuser(loggedInUser)
                ? studyDao.findAll()
                : studyDao.findAllByUser(loggedInUser);
        return ok(jsonUtils.sidebarStudyList(studyList));
    }

}
