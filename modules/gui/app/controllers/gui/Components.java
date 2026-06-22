package controllers.gui;

import actions.common.AsyncAction.Async;
import actions.common.TransactionalAction.Transactional;
import auth.gui.AuthAction.Auth;
import daos.common.ComponentDao;
import daos.common.StudyDao;
import daos.common.StudyLinkDao;
import exceptions.common.JatosException;
import general.common.Common;
import http.common.Http.Context;
import general.common.MessagesStrings;
import json.common.DefaultJson;
import messaging.common.RequestScopeMessaging;
import models.common.*;
import models.gui.ComponentProperties;
import play.data.Form;
import play.data.FormFactory;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.gui.AuthorizationService;
import services.gui.BatchService;
import services.gui.ComponentService;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.io.IOException;

import static actions.common.AsyncAction.Executor;
import static auth.gui.AuthAction.SIGNEDIN_USER;
import static models.common.User.Role.USER;
import static models.common.User.Role.VIEWER;

/**
 * Controller that deals with all requests regarding Components within the JATOS GUI.
 */
@Singleton
public class Components extends Controller {

    private final AuthorizationService authorizationService;
    private final ComponentService componentService;
    private final BatchService batchService;
    private final StudyDao studyDao;
    private final StudyLinkDao studyLinkDao;
    private final ComponentDao componentDao;
    private final FormFactory formFactory;
    private final DefaultJson defaultJson;

    @Inject
    Components(AuthorizationService authorizationService,
               ComponentService componentService,
               BatchService batchService,
               StudyDao studyDao,
               StudyLinkDao studyLinkDao,
               ComponentDao componentDao,
               FormFactory formFactory,
               DefaultJson defaultJson) {
        this.authorizationService = authorizationService;
        this.componentService = componentService;
        this.batchService = batchService;
        this.studyDao = studyDao;
        this.studyLinkDao = studyLinkDao;
        this.componentDao = componentDao;
        this.formFactory = formFactory;
        this.defaultJson = defaultJson;
    }

    /**
     * Runs a single component (in opposite to the whole study). Can run the component in multiple frames in parallel.
     * Uses a JatosWorker and the given batch. Redirects to /publix/runx.
     */
    @Async(Executor.IO)
    @Auth(roles = USER)
    public Result runComponent(Long studyId, Long componentId, Long batchId, Long frames, Long hSplit, Long vSplit) {
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        Study study = studyDao.findById(studyId);
        Batch batch = batchService.fetchBatch(batchId, study);
        Component component = componentDao.findById(componentId);

        authorizationService.canUserAccessStudy(study, signedinUser);
        authorizationService.canUserAccessBatch(batch, signedinUser);
        authorizationService.canUserAccessComponent(component, signedinUser);

        if (component.getHtmlFilePath() == null || component.getHtmlFilePath().trim().isEmpty()) {
            String errorMsg = MessagesStrings.htmlFilePathEmpty(componentId);
            return badRequest(errorMsg);
        }

        // Get a StudyLink, generate run URL, specify a component in session and redirect to jatos-publix to start the study
        StudyLink studyLink = studyLinkDao.findByBatchAndWorker(batch, signedinUser.getWorker())
                .orElseGet(() -> studyLinkDao.persist(new StudyLink(batch, signedinUser.getWorker())));
        String runUrl = Common.getJatosUrlBasePath() + "publix/runx?code=" + studyLink.getStudyCode()
                + "&frames=" + frames + "&hSplit=" + hSplit + "&vSplit=" + vSplit;
        Context.current().response().putSession("jatos_run", "RUN_COMPONENT_START");
        Context.current().response().putSession("run_component_uuid", component.getUuid());
        return redirect(runUrl);
    }

    /**
     * POST request: Handles the POST request of the form to create a new Component.
     */
    @Async(Executor.IO)
    @Auth(roles = USER)
    public Result submitCreated(Http.Request request, Long studyId) {
        Study study = studyDao.findByIdWithComponents(studyId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessStudy(study, signedinUser, true);

        Form<ComponentProperties> form = formFactory.form(ComponentProperties.class).bindFromRequest(request);
        if (form.hasErrors()) return badRequest(form.errorsAsJson());

        ComponentProperties componentProperties = form.get();
        Component component = componentService.createAndPersistComponent(study, componentProperties);
        return ok(component.getId().toString());
    }

    /**
     * GET requests for getting the properties of a Component.
     */
    @Async(Executor.IO)
    @Auth(roles = {VIEWER, USER})
    public Result properties(Long studyId, Long componentId) {
        Study study = studyDao.findById(studyId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        Component component = componentDao.findByIdWithStudy(componentId);
        authorizationService.canUserAccessStudy(study, signedinUser);
        authorizationService.canUserAccessComponent(component, signedinUser);

        ComponentProperties p = componentService.bindToProperties(component);
        return ok(defaultJson.objAsJsonNode(p));
    }

    /**
     * POST request that handles update of component properties
     */
    @Async(Executor.IO)
    @Auth(roles = USER)
    @Transactional
    public Result submitEdited(Http.Request request, Long studyId, Long componentId) {
        Study study = studyDao.findById(studyId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        Component component = componentDao.findById(componentId);
        authorizationService.canUserAccessStudy(study, signedinUser, true);
        authorizationService.canUserAccessComponent(component, signedinUser);

        Form<ComponentProperties> form = formFactory.form(ComponentProperties.class).bindFromRequest(request);
        if (form.hasErrors()) return badRequest(form.errorsAsJson());

        ComponentProperties properties = form.get();
        componentService.updateComponentAfterEdit(component, properties);
        try {
            componentService.renameHtmlFilePath(component, properties.getHtmlFilePath(), properties.isHtmlFileRename());
        } catch (JatosException e) {
            return badRequest(form.withError(ComponentProperties.HTML_FILE_PATH, e.getMessage()).errorsAsJson());
        }
        return ok(component.getId().toString());
    }

    /**
     * GET request to clone a component.
     */
    @Transactional
    @Auth(roles = USER)
    public Result cloneComponent(Long studyId, Long componentId) {
        Study study = studyDao.findById(studyId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        Component component = componentDao.findById(componentId);
        authorizationService.canUserAccessStudy(study, signedinUser, true);
        authorizationService.canUserAccessComponent(component, signedinUser);

        Component clone = componentService.cloneWholeComponent(component);
        componentService.createAndPersistComponent(study, clone);
        return ok(RequestScopeMessaging.asJson());
    }

    /**
     * DELETE request to remove a component.
     */
    @Async(Executor.IO)
    @Auth(roles = USER)
    public Result remove(Long studyId, Long componentId) {
        Study study = studyDao.findByIdWithComponents(studyId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        Component component = componentDao.findByIdWithStudy(componentId);
        authorizationService.canUserAccessStudy(study, signedinUser, true);
        authorizationService.canUserAccessComponent(component, signedinUser);

        componentService.remove(component);
        RequestScopeMessaging.success(MessagesStrings.COMPONENT_DELETED_BUT_FILES_NOT);
        return ok(RequestScopeMessaging.asJson());
    }

}
