package controllers.gui;

import akka.stream.javadsl.Source;
import akka.util.ByteString;
import auth.gui.AuthAction.Auth;
import auth.gui.AuthService;
import controllers.gui.actionannotations.GuiAccessLoggingAction.GuiAccessLogging;
import daos.common.ComponentDao;
import daos.common.ComponentResultDao;
import daos.common.StudyDao;
import exceptions.gui.BadRequestException;
import exceptions.gui.ForbiddenException;
import exceptions.gui.JatosGuiException;
import exceptions.gui.NotFoundException;
import models.common.Component;
import models.common.ComponentResult;
import models.common.Study;
import models.common.User;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.gui.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

import static controllers.gui.actionannotations.SaveLastVisitedPageUrlAction.SaveLastVisitedPageUrl;

/**
 * Controller that deals with requests regarding ComponentResult.
 *
 * @author Kristian Lange
 */
@SuppressWarnings("deprecation")
@GuiAccessLogging
@Singleton
public class ComponentResults extends Controller {

    private final JatosGuiExceptionThrower jatosGuiExceptionThrower;
    private final Checker checker;
    private final AuthService authService;
    private final BreadcrumbsService breadcrumbsService;
    private final ResultRemover resultRemover;
    private final ResultStreamer resultStreamer;
    private final StudyDao studyDao;
    private final ComponentDao componentDao;
    private final ComponentResultDao componentResultDao;

    @Inject
    ComponentResults(JatosGuiExceptionThrower jatosGuiExceptionThrower, Checker checker,
            AuthService authService, BreadcrumbsService breadcrumbsService,
            ResultRemover resultRemover, ResultStreamer resultStreamer, StudyDao studyDao,
            ComponentDao componentDao, ComponentResultDao componentResultDao) {
        this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
        this.checker = checker;
        this.authService = authService;
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
    @Transactional
    @Auth
    @SaveLastVisitedPageUrl
    public Result componentResults(Http.Request request, Long studyId, Long componentId) throws JatosGuiException {
        Study study = studyDao.findById(studyId);
        User signedinUser = authService.getSignedinUser();
        Component component = componentDao.findById(componentId);
        try {
            checker.checkStandardForStudy(study, studyId, signedinUser);
            checker.checkStandardForComponent(studyId, componentId, component);
        } catch (ForbiddenException | NotFoundException e) {
            jatosGuiExceptionThrower.throwHome(request, e);
        }

        String breadcrumbs = breadcrumbsService.generateForComponent(study, component, BreadcrumbsService.COMPONENT_RESULTS);
        return ok(views.html.gui.results.componentResults_new.render(request, signedinUser, breadcrumbs, study, component));
    }

    /**
     * POST request that removes all ComponentResults specified in the parameter. The parameter is
     * a comma separated list of ComponentResult IDs as a String.
     */
    @Transactional
    @Auth
    public Result remove(Http.Request request) throws ForbiddenException, BadRequestException, NotFoundException {
        User signedinUser = authService.getSignedinUser();
        if (request.body().asJson() == null) return badRequest("Malformed request body");
        if (!request.body().asJson().has("componentResultIds")) return badRequest("Malformed JSON");

        List<Long> componentResultIdList = new ArrayList<>();
        request.body().asJson().get("componentResultIds").forEach(node -> componentResultIdList.add(node.asLong()));
        // Permission check is done in service for each result individually
        resultRemover.removeComponentResults(componentResultIdList, signedinUser, false);
        return ok();
    }

    /**
     * GET request that returns all ComponentResults as JSON for a given component. It streams the data as chunks (reduces memory usage)
     */
    @Transactional
    @Auth
    public Result tableDataByComponent(Long componentId) throws ForbiddenException, NotFoundException {
        User signedinUser = authService.getSignedinUser();
        Component component = componentDao.findById(componentId);
        checker.checkStandardForComponent(componentId, component, signedinUser);

        Source<ByteString, ?> dataSource = resultStreamer.streamComponentResults(component);
        return ok().chunked(dataSource).as("application/json");
    }

    /**
     * GET result data of one component result
     */
    @Transactional
    @Auth
    public Result exportSingleResultData(Long componentResultId) throws ForbiddenException, NotFoundException {
        ComponentResult componentResult = componentResultDao.findById(componentResultId);
        User signedinUser = authService.getSignedinUser();
        checker.checkComponentResult(componentResult, signedinUser, false);
        return ok(componentResultDao.getData(componentResultId));
    }

}
