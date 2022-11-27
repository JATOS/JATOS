package controllers.gui;

import akka.stream.javadsl.Source;
import akka.util.ByteString;
import controllers.gui.actionannotations.AuthenticationAction.Authenticated;
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
import utils.common.Helpers;
import utils.common.JsonUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

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
    private final AuthenticationService authenticationService;
    private final BreadcrumbsService breadcrumbsService;
    private final ResultRemover resultRemover;
    private final ResultService resultService;
    private final StudyDao studyDao;
    private final ComponentDao componentDao;
    private final ComponentResultDao componentResultDao;
    private final JsonUtils jsonUtils;

    @Inject
    ComponentResults(JatosGuiExceptionThrower jatosGuiExceptionThrower, Checker checker,
            AuthenticationService authenticationService, BreadcrumbsService breadcrumbsService,
            ResultRemover resultRemover, ResultService resultService, StudyDao studyDao,
            ComponentDao componentDao, ComponentResultDao componentResultDao, JsonUtils jsonUtils) {
        this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
        this.checker = checker;
        this.authenticationService = authenticationService;
        this.breadcrumbsService = breadcrumbsService;
        this.resultRemover = resultRemover;
        this.resultService = resultService;
        this.studyDao = studyDao;
        this.componentDao = componentDao;
        this.componentResultDao = componentResultDao;
        this.jsonUtils = jsonUtils;
    }

    /**
     * Shows a view with all component results of a component of a study.
     */
    @Transactional
    @Authenticated
    public Result componentResults(Long studyId, Long componentId) throws JatosGuiException {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        Component component = componentDao.findById(componentId);
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStandardForComponents(studyId, componentId, component);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwHome(e);
        }

        String breadcrumbs = breadcrumbsService.generateForComponent(study, component, BreadcrumbsService.RESULTS);
        return ok(views.html.gui.result.componentResults
                .render(loggedInUser, breadcrumbs, Helpers.isLocalhost(), study, component));
    }

    /**
     * POST request that removes all ComponentResults specified in the parameter. The parameter is
     * a comma separated list of ComponentResult IDs as a String.
     */
    @Transactional
    @Authenticated
    public Result remove(Http.Request request) throws JatosGuiException {
        User loggedInUser = authenticationService.getLoggedInUser();
        List<Long> componentResultIdList = new ArrayList<>();
        request.body().asJson().get("resultIds").forEach(node -> componentResultIdList.add(node.asLong()));
        try {
            // Permission check is done in service for each result individually
            resultRemover.removeComponentResults(componentResultIdList, loggedInUser);
        } catch (ForbiddenException | BadRequestException | NotFoundException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }
        return ok(" "); // jQuery.ajax cannot handle empty responses
    }

    /**
     * GET request that returns all ComponentResults as JSON for a given component. It streams the data as chunks (reduces memory usage)
     */
    @Transactional
    @Authenticated
    public Result tableDataByComponent(Long studyId, Long componentId) throws JatosGuiException {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        Component component = componentDao.findById(componentId);
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStandardForComponents(studyId, componentId, component);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }

        Source<ByteString, ?> dataSource = resultService.streamComponentResults(component);
        return ok().chunked(dataSource).as("text/html; charset=utf-8");
    }

    /**
     * GET result data of one component result
     */
    @Transactional
    @Authenticated
    public Result tableDataComponentResultData(Long componentResultId) throws JatosGuiException {
        ComponentResult componentResult = componentResultDao.findById(componentResultId);
        Study study = componentResult.getStudyResult().getStudy();
        User loggedInUser = authenticationService.getLoggedInUser();
        try {
            checker.checkStandardForStudy(study, study.getId(), loggedInUser);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }

        return ok(jsonUtils.componentResultDataForUI(componentResult));
    }


}
