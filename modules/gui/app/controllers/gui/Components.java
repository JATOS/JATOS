package controllers.gui;

import auth.gui.AuthAction.Auth;
import auth.gui.AuthService;
import controllers.gui.actionannotations.GuiAccessLoggingAction.GuiAccessLogging;
import daos.common.ComponentDao;
import daos.common.StudyDao;
import daos.common.StudyLinkDao;
import exceptions.gui.ForbiddenException;
import exceptions.gui.JatosGuiException;
import exceptions.gui.NotFoundException;
import general.common.Common;
import general.common.MessagesStrings;
import general.gui.RequestScopeMessaging;
import models.common.*;
import models.gui.ComponentProperties;
import play.data.Form;
import play.data.FormFactory;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.gui.*;
import utils.common.JsonUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;

/**
 * Controller that deals with all requests regarding Components within the JATOS GUI.
 *
 * @author Kristian Lange
 */
@SuppressWarnings("deprecation")
@GuiAccessLogging
@Singleton
public class Components extends Controller {

    public static final String EDIT_SUBMIT_NAME = "action";
    public static final String EDIT_SAVE = "save";

    private final JatosGuiExceptionThrower jatosGuiExceptionThrower;
    private final Checker checker;
    private final ComponentService componentService;
    private final AuthService authenticationService;
    private final BatchService batchService;
    private final StudyDao studyDao;
    private final StudyLinkDao studyLinkDao;
    private final ComponentDao componentDao;
    private final FormFactory formFactory;

    @Inject
    Components(JatosGuiExceptionThrower jatosGuiExceptionThrower, Checker checker, ComponentService componentService,
            AuthService authenticationService, BatchService batchService, StudyDao studyDao,
            StudyLinkDao studyLinkDao, ComponentDao componentDao, FormFactory formFactory) {
        this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
        this.checker = checker;
        this.componentService = componentService;
        this.authenticationService = authenticationService;
        this.batchService = batchService;
        this.studyDao = studyDao;
        this.studyLinkDao = studyLinkDao;
        this.componentDao = componentDao;
        this.formFactory = formFactory;
    }

    /**
     * Runs a single component (in opposite to the whole study). Uses a JatosWorker and the given batch. Redirects
     * to /publix/studyCode.
     */
    @Transactional
    @Auth
    public Result runComponent(Http.Request request, Long studyId, Long componentId, Long batchId)
            throws JatosGuiException, NotFoundException {
        User loggedInUser = authenticationService.getLoggedInUser();
        Study study = studyDao.findById(studyId);
        Batch batch = batchService.fetchBatch(batchId, study);
        Component component = componentDao.findById(componentId);
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStandardForBatch(batch, study, batchId);
        } catch (ForbiddenException | NotFoundException e) {
            jatosGuiExceptionThrower.throwHome(e);
        }
        try {
            checker.checkStandardForComponent(studyId, componentId, component);
        } catch (ForbiddenException | NotFoundException e) {
            jatosGuiExceptionThrower.throwStudy(e, studyId);
        }
        if (component.getHtmlFilePath() == null || component.getHtmlFilePath().trim().isEmpty()) {
            String errorMsg = MessagesStrings.htmlFilePathEmpty(componentId);
            jatosGuiExceptionThrower.throwStudy(errorMsg, Http.Status.BAD_REQUEST, studyId);
        }

        // Get a StudyLink, generate run URL, specify component in session and redirect to jatos-publix: start study
        StudyLink studyLink = studyLinkDao.findByBatchAndWorker(batch, loggedInUser.getWorker())
                .orElseGet(() -> studyLinkDao.create(new StudyLink(batch, loggedInUser.getWorker())));
        String runUrl = Common.getJatosUrlBasePath() + "publix/" + studyLink.getStudyCode();
        return redirect(runUrl)
                .addingToSession(request, "jatos_run", "RUN_COMPONENT_START")
                .addingToSession(request, "run_component_uuid", component.getUuid());
    }

    /**
     * POST request: Handles the post request of the form to create a new Component.
     */
    @Transactional
    @Auth
    public Result submitCreated(Long studyId) throws JatosGuiException {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        checkStudyAndLocked(studyId, study, loggedInUser);

        Form<ComponentProperties> form = formFactory.form(ComponentProperties.class).bindFromRequest();
        if (form.hasErrors()) return badRequest(form.errorsAsJson());

        ComponentProperties componentProperties = form.get();

        Component component = componentService.createAndPersistComponent(study, componentProperties);
        return ok(component.getId().toString());
    }

    /**
     * GET requests for getting the properties of a Component.
     */
    @Transactional
    @Auth
    public Result properties(Long studyId, Long componentId) throws ForbiddenException, NotFoundException {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        Component component = componentDao.findById(componentId);
        checker.checkStandardForStudy(study, studyId, loggedInUser);
        checker.checkStandardForComponent(studyId, componentId, component);

        ComponentProperties p = componentService.bindToProperties(component);
        return ok(JsonUtils.asJsonNode(p));
    }

    /**
     * POST request that handles update of component properties
     */
    @Transactional
    @Auth
    public Result submitEdited(Long studyId, Long componentId) throws JatosGuiException {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        Component component = componentDao.findById(componentId);
        checkStudyAndLockedAndComponent(studyId, componentId, study, loggedInUser, component);

        Form<ComponentProperties> form = formFactory.form(ComponentProperties.class).bindFromRequest();
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
    @Transactional
    @Auth
    public Result toggleActive(Long studyId, Long componentId, Boolean active) throws JatosGuiException {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        Component component = componentDao.findById(componentId);
        checkStudyAndLockedAndComponent(studyId, componentId, study, loggedInUser, component);

        if (active != null) {
            componentDao.changeActive(component, active);
        }
        return ok(JsonUtils.asJsonNode(component.isActive()));
    }

    /**
     * GET request to clone a component.
     */
    @Transactional
    @Auth
    public Result cloneComponent(Long studyId, Long componentId) throws JatosGuiException {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        Component component = componentDao.findById(componentId);
        checkStudyAndLockedAndComponent(studyId, componentId, study, loggedInUser, component);

        Component clone = componentService.cloneWholeComponent(component);
        componentService.createAndPersistComponent(study, clone);
        return ok(RequestScopeMessaging.getAsJson());
    }

    /**
     * DELETE request to remove a component.
     */
    @Transactional
    @Auth
    public Result remove(Long studyId, Long componentId) throws JatosGuiException {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        Component component = componentDao.findById(componentId);
        checkStudyAndLockedAndComponent(studyId, componentId, study, loggedInUser, component);

        componentService.remove(component, loggedInUser);
        RequestScopeMessaging.success(MessagesStrings.COMPONENT_DELETED_BUT_FILES_NOT);
        return ok(RequestScopeMessaging.getAsJson());
    }

    private void checkStudyAndLocked(Long studyId, Study study, User loggedInUser) throws JatosGuiException {
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStudyLocked(study);
        } catch (ForbiddenException | NotFoundException e) {
            jatosGuiExceptionThrower.throwStudy(e, studyId);
        }
    }

    private void checkStudyAndLockedAndComponent(Long studyId, Long componentId, Study study, User loggedInUser,
            Component component) throws JatosGuiException {
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStudyLocked(study);
            checker.checkStandardForComponent(studyId, componentId, component);
        } catch (ForbiddenException | NotFoundException e) {
            jatosGuiExceptionThrower.throwStudy(e, studyId);
        }
    }
}
