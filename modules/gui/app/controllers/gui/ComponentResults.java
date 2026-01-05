package controllers.gui;

import actions.common.AsyncAction.Async;
import actions.common.AsyncAction.Executor;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import auth.gui.AuthAction.Auth;
import daos.common.ComponentDao;
import daos.common.ComponentResultDao;
import daos.common.StudyDao;
import exceptions.common.BadRequestException;
import exceptions.common.ForbiddenException;
import exceptions.common.NotFoundException;
import general.common.Http.Context;
import models.common.Component;
import models.common.ComponentResult;
import models.common.Study;
import models.common.User;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.gui.BreadcrumbsService;
import services.gui.Checker;
import services.gui.ResultRemover;
import services.gui.ResultStreamer;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

import static auth.gui.AuthAction.SIGNEDIN_USER;
import static controllers.gui.actionannotations.SaveLastVisitedPageUrlAction.SaveLastVisitedPageUrl;
import static messaging.common.FlashScopeMessaging.*;

/**
 * Controller that deals with requests regarding ComponentResult.
 *
 * @author Kristian Lange
 */
@Singleton
public class ComponentResults extends Controller {

    private final Checker checker;
    private final BreadcrumbsService breadcrumbsService;
    private final ResultRemover resultRemover;
    private final ResultStreamer resultStreamer;
    private final StudyDao studyDao;
    private final ComponentDao componentDao;
    private final ComponentResultDao componentResultDao;

    @Inject
    ComponentResults(Checker checker,
                     BreadcrumbsService breadcrumbsService,
                     ResultRemover resultRemover,
                     ResultStreamer resultStreamer,
                     StudyDao studyDao,
                     ComponentDao componentDao,
                     ComponentResultDao componentResultDao) {
        this.checker = checker;
        this.breadcrumbsService = breadcrumbsService;
        this.resultRemover = resultRemover;
        this.resultStreamer = resultStreamer;
        this.studyDao = studyDao;
        this.componentDao = componentDao;
        this.componentResultDao = componentResultDao;
    }

    /**
     * Shows a view with all component results of a component of a study.
     */
    @Async(Executor.IO)
    @Auth
    @SaveLastVisitedPageUrl
    public Result componentResults(Http.Request request, Long studyId, Long componentId) {
        Study study = studyDao.findById(studyId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        Component component = componentDao.findById(componentId);
        try {
            checker.checkStandardForStudy(study, studyId, signedinUser);
            checker.checkStandardForComponent(studyId, componentId, component);
        } catch (ForbiddenException | NotFoundException e) {
            return redirect(routes.Home.home(e.getHttpStatus())).flashing(ERROR, e.getMessage());
        }

        String breadcrumbs = breadcrumbsService.generateForComponent(study, component, BreadcrumbsService.COMPONENT_RESULTS);
        return ok(views.html.gui.results.componentResults.render(signedinUser, breadcrumbs, study, component, request.asScala()));
    }

    /**
     * POST request that removes all ComponentResults specified in the parameter. The parameter is a comma separated
     * list of ComponentResult IDs as a String.
     */
    @Async(Executor.IO)
    @Auth
    public Result remove(Http.Request request) throws ForbiddenException, BadRequestException, NotFoundException {
        if (request.body().asJson() == null) return badRequest("Malformed request body");
        if (!request.body().asJson().has("componentResultIds")) return badRequest("Malformed JSON");

        List<Long> componentResultIdList = new ArrayList<>();
        request.body().asJson().get("componentResultIds").forEach(node -> componentResultIdList.add(node.asLong()));
        // Permission check is done in service for each result individually
        resultRemover.removeComponentResults(componentResultIdList, false);
        return ok();
    }

    /**
     * GET request that returns all ComponentResults as JSON for a given component. It streams the data as chunks
     * (reduces memory usage)
     */
    @Async(Executor.IO)
    @Auth
    public Result tableDataByComponent(Long componentId) throws ForbiddenException, NotFoundException {
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        Component component = componentDao.findById(componentId);
        checker.checkStandardForComponent(componentId, component, signedinUser);

        Source<ByteString, ?> dataSource = resultStreamer.streamComponentResults(component);
        return ok().chunked(dataSource).as("application/json");
    }

    /**
     * GET result data of one component result
     */
    @Async(Executor.IO)
    @Auth
    public Result exportSingleResultData(Long componentResultId) throws ForbiddenException, NotFoundException {
        ComponentResult componentResult = componentResultDao.findByIdWithComponent(componentResultId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        checker.checkComponentResult(componentResult, signedinUser, false);
        return ok(componentResultDao.getData(componentResultId));
    }

}
