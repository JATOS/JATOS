package controllers.gui;

import actions.common.AsyncAction.Async;
import actions.common.TransactionalAction.Transactional;
import auth.gui.AuthAction.Auth;
import daos.common.ComponentDao;
import daos.common.StudyDao;
import daos.common.StudyLinkDao;
import exceptions.common.IOException;
import general.common.Common;
import general.common.Http.Context;
import general.common.MessagesStrings;
import messaging.common.RequestScopeMessaging;
import models.common.*;
import models.gui.ComponentProperties;
import play.data.Form;
import play.data.FormFactory;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.gui.BatchService;
import services.gui.Checker;
import services.gui.ComponentService;
import utils.common.JsonUtils;

import javax.inject.Inject;
import javax.inject.Singleton;

import static actions.common.AsyncAction.Executor;
import static auth.gui.AuthAction.SIGNEDIN_USER;

/**
 * Controller that deals with all requests regarding Components within the JATOS GUI.
 *
 * @author Kristian Lange
 */
@Singleton
public class Components extends Controller {

    private final Checker checker;
    private final ComponentService componentService;
    private final BatchService batchService;
    private final StudyDao studyDao;
    private final StudyLinkDao studyLinkDao;
    private final ComponentDao componentDao;
    private final FormFactory formFactory;

    @Inject
    Components(Checker checker,
               ComponentService componentService,
               BatchService batchService,
               StudyDao studyDao,
               StudyLinkDao studyLinkDao,
               ComponentDao componentDao,
               FormFactory formFactory) {
        this.checker = checker;
        this.componentService = componentService;
        this.batchService = batchService;
        this.studyDao = studyDao;
        this.studyLinkDao = studyLinkDao;
        this.componentDao = componentDao;
        this.formFactory = formFactory;
    }

    /**
     * Runs a single component (in opposite to the whole study). Can run the component in multiple frames in parallel.
     * Uses a JatosWorker and the given batch. Redirects to /publix/runx.
     */
    @Async(Executor.IO)
    @Auth
    public Result runComponent(Http.Request request, Long studyId, Long componentId, Long batchId,
                               Long frames, Long hSplit, Long vSplit) {
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        Study study = studyDao.findById(studyId);
        Batch batch = batchService.fetchBatch(batchId, study);
        Component component = componentDao.findById(componentId);

        checker.checkStandardForStudy(study, studyId, signedinUser);
        checker.checkStandardForBatch(batch, study, batchId);
        checker.checkStandardForComponent(studyId, componentId, component);

        if (component.getHtmlFilePath() == null || component.getHtmlFilePath().trim().isEmpty()) {
            String errorMsg = MessagesStrings.htmlFilePathEmpty(componentId);
            return badRequest(errorMsg);
        }

        // Get a StudyLink, generate run URL, specify a component in session and redirect to jatos-publix to start the study
        StudyLink studyLink = studyLinkDao.findByBatchAndWorker(batch, signedinUser.getWorker())
                .orElseGet(() -> studyLinkDao.persist(new StudyLink(batch, signedinUser.getWorker())));
        String runUrl = Common.getJatosUrlBasePath() + "publix/runx?code=" + studyLink.getStudyCode()
                + "&frames=" + frames + "&hSplit=" + hSplit + "&vSplit=" + vSplit;
        return redirect(runUrl)
                .addingToSession(request, "jatos_run", "RUN_COMPONENT_START")
                .addingToSession(request, "run_component_uuid", component.getUuid());
    }

    /**
     * POST request: Handles the POST request of the form to create a new Component.
     */
    @Async(Executor.IO)
    @Auth
    public Result submitCreated(Http.Request request, Long studyId) {
        Study study = studyDao.findByIdWithComponents(studyId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        checker.checkStandardForStudy(study, studyId, signedinUser);
        checker.checkStudyLocked(study);

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
    @Auth
    public Result properties(Long studyId, Long componentId) {
        Study study = studyDao.findById(studyId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        Component component = componentDao.findByIdWithStudy(componentId);
        checker.checkStandardForStudy(study, studyId, signedinUser);
        checker.checkStandardForComponent(studyId, componentId, component);

        ComponentProperties p = componentService.bindToProperties(component);
        return ok(JsonUtils.asJsonNode(p));
    }

    /**
     * POST request that handles update of component properties
     */
    @Async(Executor.IO)
    @Auth
    @Transactional
    public Result submitEdited(Http.Request request, Long studyId, Long componentId) {
        Study study = studyDao.findById(studyId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        Component component = componentDao.findById(componentId);
        checker.checkStandardForStudy(study, studyId, signedinUser);
        checker.checkStudyLocked(study);
        checker.checkStandardForComponent(studyId, componentId, component);

        Form<ComponentProperties> form = formFactory.form(ComponentProperties.class).bindFromRequest(request);
        if (form.hasErrors()) return badRequest(form.errorsAsJson());

        ComponentProperties properties = form.get();
        componentService.updateComponentAfterEdit(component, properties);
        try {
            componentService.renameHtmlFilePath(component, properties.getHtmlFilePath(), properties.isHtmlFileRename());
        } catch (IOException e) {
            return badRequest(form.withError(ComponentProperties.HTML_FILE_PATH, e.getMessage()).errorsAsJson());
        }
        return ok(component.getId().toString());
    }

    /**
     * POST Request to change the property 'active' of a component.
     */
    @Async(Executor.IO)
    @Auth
    public Result toggleActive(Long studyId, Long componentId, Boolean active) {
        Study study = studyDao.findById(studyId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        Component component = componentDao.findById(componentId);
        checker.checkStandardForStudy(study, studyId, signedinUser);
        checker.checkStudyLocked(study);
        checker.checkStandardForComponent(studyId, componentId, component);

        if (active != null) {
            componentDao.changeActive(component, active);
        }
        return ok(JsonUtils.asJsonNode(component.isActive()));
    }

    /**
     * GET request to clone a component.
     */
    @Async(Executor.IO)
    @Auth
    public Result cloneComponent(Long studyId, Long componentId) {
        Study study = studyDao.findByIdWithComponents(studyId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        Component component = componentDao.findByIdWithStudy(componentId);
        checker.checkStandardForStudy(study, studyId, signedinUser);
        checker.checkStudyLocked(study);
        checker.checkStandardForComponent(studyId, componentId, component);

        Component clone = componentService.cloneWholeComponent(component);
        componentService.createAndPersistComponent(study, clone);
        return ok(RequestScopeMessaging.asJson());
    }

    /**
     * DELETE request to remove a component.
     */
    @Async(Executor.IO)
    @Auth
    public Result remove(Long studyId, Long componentId) {
        Study study = studyDao.findByIdWithComponents(studyId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        Component component = componentDao.findByIdWithStudy(componentId);
        checker.checkStandardForStudy(study, studyId, signedinUser);
        checker.checkStudyLocked(study);
        checker.checkStandardForComponent(studyId, componentId, component);

        componentService.remove(component);
        RequestScopeMessaging.success(MessagesStrings.COMPONENT_DELETED_BUT_FILES_NOT);
        return ok(RequestScopeMessaging.asJson());
    }

}