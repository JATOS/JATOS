package controllers.gui;

import auth.gui.AuthAction.Auth;
import auth.gui.AuthService;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import controllers.gui.actionannotations.GuiAccessLoggingAction.GuiAccessLogging;
import daos.common.*;
import exceptions.gui.BadRequestException;
import exceptions.gui.ForbiddenException;
import exceptions.gui.JatosGuiException;
import exceptions.gui.NotFoundException;
import general.common.Common;
import general.common.StudyLogger;
import models.common.*;
import models.gui.StudyProperties;
import play.data.Form;
import play.data.FormFactory;
import play.data.validation.ValidationError;
import play.db.jpa.Transactional;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.gui.*;
import utils.common.IOUtils;
import utils.common.JsonUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static controllers.gui.actionannotations.SaveLastVisitedPageUrlAction.SaveLastVisitedPageUrl;

/**
 * Controller for all actions regarding studies within the JATOS GUI.
 *
 * @author Kristian Lange
 */
@SuppressWarnings("deprecation")
@GuiAccessLogging
@Singleton
public class Studies extends Controller {

    private final JatosGuiExceptionThrower jatosGuiExceptionThrower;
    private final Checker checker;
    private final StudyService studyService;
    private final UserService userService;
    private final AuthService authService;
    private final BreadcrumbsService breadcrumbsService;
    private final BatchService batchService;
    private final StudyDao studyDao;
    private final ComponentDao componentDao;
    private final StudyResultDao studyResultDao;
    private final UserDao userDao;
    private final ComponentResultDao componentResultDao;
    private final StudyLinkDao studyLinkDao;
    private final JsonUtils jsonUtils;
    private final IOUtils ioUtils;
    private final FormFactory formFactory;
    private final StudyLogger studyLogger;

    @Inject
    Studies(JatosGuiExceptionThrower jatosGuiExceptionThrower, Checker checker, StudyService studyService,
            UserService userService, AuthService authService,
            BreadcrumbsService breadcrumbsService, BatchService batchService, StudyDao studyDao,
            ComponentDao componentDao, StudyResultDao studyResultDao, UserDao userDao,
            ComponentResultDao componentResultDao, StudyLinkDao studyLinkDao, JsonUtils jsonUtils,
            IOUtils ioUtils, FormFactory formFactory, StudyLogger studyLogger) {
        this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
        this.checker = checker;
        this.studyService = studyService;
        this.userService = userService;
        this.authService = authService;
        this.breadcrumbsService = breadcrumbsService;
        this.batchService = batchService;
        this.studyDao = studyDao;
        this.componentDao = componentDao;
        this.studyResultDao = studyResultDao;
        this.userDao = userDao;
        this.componentResultDao = componentResultDao;
        this.studyLinkDao = studyLinkDao;
        this.jsonUtils = jsonUtils;
        this.ioUtils = ioUtils;
        this.formFactory = formFactory;
        this.studyLogger = studyLogger;
    }

    /**
     * Shows the study view with details of a study components and so on.
     */
    @Transactional
    @Auth
    @SaveLastVisitedPageUrl
    public Result study(Http.Request request, Long studyId, int httpStatus) throws JatosGuiException {
        Study study = studyDao.findById(studyId);
        User signedinUser = authService.getSignedinUser();
        checkStandardForStudy(request, studyId, study, signedinUser);
        String breadcrumbs = breadcrumbsService.generateForStudy(study);
        int studyResultCount = studyResultDao.countByStudy(study);
        return status(httpStatus, views.html.gui.study.study
                .render(request, signedinUser, breadcrumbs, study, studyResultCount));
    }

    @Transactional
    @Auth
    @SaveLastVisitedPageUrl
    public Result study(Http.Request request, Long studyId) throws JatosGuiException {
        return study(request, studyId, Http.Status.OK);
    }

    /**
     * POST request to create a new study.
     */
    @Transactional
    @Auth
    public Result submitCreated() {
        User signedinUser = authService.getSignedinUser();

        Form<StudyProperties> form = formFactory.form(StudyProperties.class).bindFromRequest();
        if (form.hasErrors()) return badRequest(form.errorsAsJson());

        StudyProperties studyProperties = form.get();
        Study study = studyService.createAndPersistStudy(signedinUser, studyProperties);
        try {
            ioUtils.createStudyAssetsDir(study.getDirName());
        } catch (IOException e) {
            return badRequest(form.withError(StudyProperties.DIR_NAME, e.getMessage()).errorsAsJson());
        }
        return ok(study.getId().toString());
    }

    /**
     * GET request that returns the study properties as JSON.
     */
    @Transactional
    @Auth
    public Result properties(Long studyId) throws ForbiddenException, NotFoundException {
        Study study = studyDao.findById(studyId);
        User signedinUser = authService.getSignedinUser();
        checker.checkStandardForStudy(study, studyId, signedinUser);

        StudyProperties studyProperties = studyService.bindToProperties(study);
        return ok(JsonUtils.asJsonNode(studyProperties));
    }

    /**
     * POST request to update study properties
     */
    @Transactional
    @Auth
    public Result submitEdited(Long studyId) throws ForbiddenException, NotFoundException {
        Study study = studyDao.findById(studyId);
        User signedinUser = authService.getSignedinUser();
        checker.checkStandardForStudy(study, studyId, signedinUser);
        checker.checkStudyLocked(study);

        Form<StudyProperties> form = formFactory.form(StudyProperties.class).bindFromRequest();
        if (form.hasErrors()) return badRequest(form.errorsAsJson());

        StudyProperties studyProperties = form.get();
        try {
            studyService.renameStudyAssetsDir(study, studyProperties.getDirName());
        } catch (IOException e) {
            return badRequest(form.withError(StudyProperties.DIR_NAME, e.getMessage()).errorsAsJson());
        }

        studyService.updateStudy(study, studyProperties, signedinUser);
        return ok();
    }

    /**
     * POST request to update study properties
     */
    @Transactional
    @Auth
    public Result submitDescription(Http.Request request, Long studyId) throws ForbiddenException, NotFoundException {
        Study study = studyDao.findById(studyId);
        User signedinUser = authService.getSignedinUser();
        checker.checkStandardForStudy(study, studyId, signedinUser);
        checker.checkStudyLocked(study);

        String description = request.body().asText();

        StudyProperties sp = new StudyProperties();
        sp.setDescription(description);
        List<ValidationError> errors =  sp.validateDescription();
        if (!errors.isEmpty()) return badRequest(errors.get(0).message());

        studyService.updateDescription(study, description, signedinUser);
        return ok();
    }

    /**
     * POST request to swap the locked field of a study.
     */
    @Transactional
    @Auth
    public Result toggleLock(Long studyId) throws JatosGuiException, ForbiddenException, NotFoundException {
        Study study = studyDao.findById(studyId);
        User signedinUser = authService.getSignedinUser();
        checker.checkStandardForStudy(study, studyId, signedinUser);

        study.setLocked(!study.isLocked());
        studyDao.update(study);
        if (study.isLocked()) {
            studyLogger.log(study, signedinUser, "Locked study");
        } else {
            studyLogger.log(study, signedinUser, "Unlocked study");
        }
        return ok(String.valueOf(study.isLocked()));
    }

    /**
     * POST request to activate or deactivate a study. Can be done only by an admin.
     */
    @Transactional
    @Auth(User.Role.ADMIN)
    public Result toggleActive(Long studyId, Boolean active) {
        Study study = studyDao.findById(studyId);
        study.setActive(active);
        studyDao.update(study);
        return ok();
    }

     /**
     * GET request to clones a study.
     */
    @Transactional
    @Auth
    public Result cloneStudy(Long studyId) throws JatosGuiException, ForbiddenException, NotFoundException, IOException {
        Study study = studyDao.findById(studyId);
        User signedinUser = authService.getSignedinUser();
        checker.checkStandardForStudy(study, studyId, signedinUser);

        Study clone = studyService.clone(study);
        studyService.createAndPersistStudy(signedinUser, clone);
        return ok(Json.toJson(ImmutableMap.of("id", clone.getId(), "title", clone.getTitle())));
    }

    /**
     * GET request that gets all users and whether they are admin of this study as a JSON array.
     */
    @Transactional
    @Auth
    public Result memberUsers(Long studyId) throws JatosGuiException, ForbiddenException, NotFoundException {
        Study study = studyDao.findById(studyId);
        User signedinUser = authService.getSignedinUser();
        checker.checkStandardForStudy(study, studyId, signedinUser);

        List<User> userList = userDao.findAll();
        return ok(jsonUtils.memberUserArrayOfStudy(userList, study));
    }

    /**
     * POST request that adds or removes a member user from a study
     */
    @Transactional
    @Auth
    public Result toggleMemberUser(Long studyId, String username, boolean isMember) throws ForbiddenException, NotFoundException {
        Study study = studyDao.findById(studyId);
        User signedinUser = authService.getSignedinUser();
        String normalizedUsername = User.normalizeUsername(username);
        checker.checkStandardForStudy(study, studyId, signedinUser);
        User userToChange = userService.retrieveUser(normalizedUsername);
        studyService.changeUserMember(study, userToChange, isMember);

        return ok(jsonUtils.memberUserOfStudy(userToChange, study));
    }

    /**
     * POST request that adds all users as members to a study
     */
    @Transactional
    @Auth
    public Result addAllMemberUsers(Long studyId) throws ForbiddenException, NotFoundException {
        Study study = studyDao.findById(studyId);
        User signedinUser = authService.getSignedinUser();
        checker.checkStandardForStudy(study, studyId, signedinUser);

        if (!Common.isStudyMembersAllowedToAddAllUsers()) {
            return forbidden("It's not allowed to add all users at once in this JATOS.");
        }

        studyService.addAllUserMembers(study);
        return ok();
    }

    /**
     * DELETE request that removes all member users from a study
     */
    @Transactional
    @Auth
    public Result removeAllMemberUsers(Long studyId) throws ForbiddenException, NotFoundException {
        Study study = studyDao.findById(studyId);
        User signedinUser = authService.getSignedinUser();
        checker.checkStandardForStudy(study, studyId, signedinUser);

        studyService.removeAllUserMembers(study);
        return ok();
    }

    /**
     * POST request to change the oder of components within a study.
     */
    @Transactional
    @Auth
    public Result changeComponentOrder(Long studyId, Long componentId, String newPosition)
            throws JatosGuiException, ForbiddenException, NotFoundException, BadRequestException {
        Study study = studyDao.findById(studyId);
        User signedinUser = authService.getSignedinUser();
        Component component = componentDao.findById(componentId);
        checker.checkStandardForStudy(study, studyId, signedinUser);
        checker.checkStudyLocked(study);
        checker.checkStandardForComponent(studyId, componentId, component);
        studyService.changeComponentPosition(newPosition, study, component);

        return ok();
    }

    /**
     * Runs the whole study. Can run the study in mulitple frames in parallel. Uses a JatosWorker and the given batch.
     * Redirects to /publix/runx.
     */
    @Transactional
    @Auth
    public Result runStudy(Http.Request request, Long studyId, Long batchId, Long frames)
            throws JatosGuiException, NotFoundException, ForbiddenException {
        Study study = studyDao.findById(studyId);
        Batch batch = batchService.fetchBatch(batchId, study);
        User signedinUser = authService.getSignedinUser();
        checker.checkStandardForStudy(study, studyId, signedinUser);
        checker.checkStandardForBatch(batch, study, batchId);

        // Get StudyLink and redirect to jatos-publix to start the study
        StudyLink studyLink = studyLinkDao.findByBatchAndWorker(batch, signedinUser.getWorker())
                .orElseGet(() -> studyLinkDao.create(new StudyLink(batch, signedinUser.getWorker())));
        String runUrl = Common.getJatosUrlBasePath() + "publix/runx?code=" + studyLink.getStudyCode()
                + "&frames=" + frames;
        return redirect(runUrl).addingToSession(request, "jatos_run", "RUN_STUDY");
    }

    /**
     * GET request that returns all component data of the given study as JSON.
     */
    @Transactional
    @Auth
    public Result tableDataByStudy(Long studyId) throws JatosGuiException, ForbiddenException, NotFoundException {
        Study study = studyDao.findById(studyId);
        User signedinUser = authService.getSignedinUser();
        checker.checkStandardForStudy(study, studyId, signedinUser);

        List<Component> componentList = study.getComponentList();
        List<Integer> resultCountList = new ArrayList<>();
        componentList.forEach(component -> resultCountList.add(componentResultDao.countByComponent(component)));
        JsonNode dataAsJson = jsonUtils.allComponentsForUI(study.getComponentList(), resultCountList);
        return ok(dataAsJson);
    }

    private void checkStandardForStudy(Http.Request request, Long studyId, Study study, User signedinUser) throws JatosGuiException {
        try {
            checker.checkStandardForStudy(study, studyId, signedinUser);
        } catch (ForbiddenException | NotFoundException e) {
            jatosGuiExceptionThrower.throwHome(request, e);
        }
    }

}
