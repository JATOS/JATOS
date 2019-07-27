package controllers.gui;

import com.fasterxml.jackson.databind.JsonNode;
import controllers.gui.actionannotations.AuthenticationAction.Authenticated;
import controllers.gui.actionannotations.GuiAccessLoggingAction.GuiAccessLogging;
import daos.common.ComponentDao;
import daos.common.ComponentResultDao;
import daos.common.StudyDao;
import exceptions.gui.BadRequestException;
import exceptions.gui.ForbiddenException;
import exceptions.gui.JatosGuiException;
import exceptions.gui.NotFoundException;
import general.gui.RequestScopeMessaging;
import models.common.Component;
import models.common.ComponentResult;
import models.common.Study;
import models.common.User;
import play.Logger;
import play.Logger.ALogger;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.gui.*;
import utils.common.HttpUtils;
import utils.common.JsonUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

/**
 * Controller that deals with requests regarding ComponentResult.
 *
 * @author Kristian Lange
 */
@GuiAccessLogging
@Singleton
public class ComponentResults extends Controller {

    private final JatosGuiExceptionThrower jatosGuiExceptionThrower;
    private final Checker checker;
    private final AuthenticationService authenticationService;
    private final BreadcrumbsService breadcrumbsService;
    private final ResultRemover resultRemover;
    private final JsonUtils jsonUtils;
    private final StudyDao studyDao;
    private final ComponentDao componentDao;
    private final ComponentResultDao componentResultDao;

    @Inject
    ComponentResults(JatosGuiExceptionThrower jatosGuiExceptionThrower,
            Checker checker, AuthenticationService authenticationService,
            BreadcrumbsService breadcrumbsService, ResultRemover resultRemover,
            JsonUtils jsonUtils, StudyDao studyDao, ComponentDao componentDao,
            ComponentResultDao componentResultDao) {
        this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
        this.checker = checker;
        this.authenticationService = authenticationService;
        this.breadcrumbsService = breadcrumbsService;
        this.resultRemover = resultRemover;
        this.jsonUtils = jsonUtils;
        this.studyDao = studyDao;
        this.componentDao = componentDao;
        this.componentResultDao = componentResultDao;
    }

    /**
     * Shows a view with all component results of a component of a study.
     */
    @Transactional
    @Authenticated
    public Result componentResults(Long studyId, Long componentId,
            String errorMsg, int httpStatus) throws JatosGuiException {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        Component component = componentDao.findById(componentId);
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStandardForComponents(studyId, componentId, component);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwHome(e);
        }

        RequestScopeMessaging.error(errorMsg);
        String breadcrumbs = breadcrumbsService.generateForComponent(study,
                component, BreadcrumbsService.RESULTS);
        return status(httpStatus,
                views.html.gui.result.componentResults.render(loggedInUser,
                        breadcrumbs, HttpUtils.isLocalhost(), study,
                        component));
    }

    @Transactional
    @Authenticated
    public Result componentResults(Long studyId, Long componentId,
            String errorMsg) throws JatosGuiException {
        return componentResults(studyId, componentId, errorMsg, Http.Status.OK);
    }

    @Transactional
    @Authenticated
    public Result componentResults(Long studyId, Long componentId)
            throws JatosGuiException {
        return componentResults(studyId, componentId, null, Http.Status.OK);
    }

    /**
     * Ajax DELETE request
     * <p>
     * Removes all ComponentResults specified in the parameter. The parameter is
     * a comma separated list of of ComponentResult IDs as a String.
     */
    @Transactional
    @Authenticated
    public Result remove(String componentResultIds) throws JatosGuiException {
        User loggedInUser = authenticationService.getLoggedInUser();
        try {
            // Permission check is done in service for each result individually
            resultRemover.removeComponentResults(componentResultIds, loggedInUser);
        } catch (ForbiddenException | BadRequestException | NotFoundException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }
        return ok(" "); // jQuery.ajax cannot handle empty responses
    }

    /**
     * Ajax request
     * <p>
     * Removes all ComponentResults of the given component and study.
     */
    @Transactional
    @Authenticated
    public Result removeAllOfComponent(Long studyId, Long componentId) throws JatosGuiException {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        Component component = componentDao.findById(componentId);
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStandardForComponents(studyId, componentId, component);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwHome(e);
        }

        try {
            resultRemover.removeAllComponentResults(component, loggedInUser);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }
        return ok(" "); // jQuery.ajax cannot handle empty responses
    }

    /**
     * Ajax request
     * <p>
     * Returns all ComponentResults as JSON for a given component.
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

        List<ComponentResult> componentResultList =
                componentResultDao.findAllByComponent(component);
        JsonNode dataAsJson =
                jsonUtils.allComponentResultsForUI(componentResultList);
        return ok(dataAsJson);
    }

}
