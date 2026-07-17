package controllers.gui;

import actions.common.AsyncAction.Async;
import actions.common.AsyncAction.Executor;
import actions.common.TransactionalAction.Transactional;
import auth.gui.AuthAction.Auth;
import com.google.common.base.Strings;
import daos.common.StudyDao;
import general.common.Common;
import http.common.Http.Context;
import json.common.DomainJsonMapper;
import models.common.Study;
import models.common.User;
import play.libs.ws.WSClient;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.gui.AuthorizationService;
import services.gui.BreadcrumbsService;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static actions.common.TransactionalAction.Mode.READ_ONLY;
import static auth.gui.AuthAction.SIGNEDIN_USER;
import static controllers.gui.actionannotations.SaveLastVisitedPageUrlAction.SaveLastVisitedPageUrl;
import static models.common.User.Role.USER;
import static models.common.User.Role.VIEWER;

/**
 * Controller that provides actions for the home page
 */
@Singleton
public class Home extends Controller {

    private final DomainJsonMapper domainJsonMapper;
    private final BreadcrumbsService breadcrumbsService;
    private final StudyDao studyDao;
    private final AuthorizationService authorizationService;
    private final WSClient ws;

    @Inject
    Home(DomainJsonMapper domainJsonMapper,
         BreadcrumbsService breadcrumbsService,
         StudyDao studyDao,
         AuthorizationService authorizationService,
         WSClient ws) {
        this.domainJsonMapper = domainJsonMapper;
        this.breadcrumbsService = breadcrumbsService;
        this.studyDao = studyDao;
        this.authorizationService = authorizationService;
        this.ws = ws;
    }

    /**
     * Shows home view
     */
    @Async(Executor.IO)
    @Auth(roles = {VIEWER, USER})
    @SaveLastVisitedPageUrl
    public Result home(Http.Request request, int httpStatus) {
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        String breadcrumbs = breadcrumbsService.generateForHome();
        boolean freshlySignedin = signedinUser.getLastLogin() != null &&
                Duration.between(signedinUser.getLastLogin().toInstant(), Instant.now())
                        .minusSeconds(30)
                        .isNegative();
        return status(httpStatus, views.html.gui.home.render(freshlySignedin, signedinUser, breadcrumbs, request.asScala()));
    }

    public Result home(Http.Request request) {
        return home(request, Http.Status.OK);
    }

    /**
     * Tries to loads some static HTML that will be shown on the home page instead of the default welcome message
     */
    @Async(Executor.IO)
    @Auth(roles = {VIEWER, USER})
    public CompletionStage<Result> branding() {
        Context context = Context.current();
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        if (Strings.isNullOrEmpty(Common.getBrandingUrl())) return CompletableFuture.completedFuture(noContent());
        return ws.url(Common.getBrandingUrl()).get().thenApply(r -> Context.withContext(context, () -> {
            String branding = r.getBody()
                    .replaceAll("@JATOS_VERSION", Common.getJatosVersion())
                    .replaceAll("@USER_NAME", signedinUser.getName())
                    .replaceAll("@USER_USERNAME", signedinUser.getUsername());
            if (r.getStatus() == 404 || branding.startsWith("404")) return notFound();
            Context.current().response().setHeader("Cache-Control", "max-age=3600");
            return ok(branding);
        }));
    }

    /**
     * GET request that returns the data needed to draw the sidebar (e.g. a list of all studies that belong to the
     * signed-in user).
     */
    @Async(Executor.IO)
    @Auth(roles = {VIEWER, USER})
    @Transactional(READ_ONLY)
    public Result sidebarData() {
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        List<Study> studyList = authorizationService.isAllowedSuperuser(signedinUser)
                ? studyDao.findAll()
                : studyDao.findAllByUser(signedinUser);
        return ok(domainJsonMapper.sidebarData(studyList));
    }

}
