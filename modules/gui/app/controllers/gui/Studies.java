package controllers.gui;

import actions.common.AsyncAction.Async;
import actions.common.AsyncAction.Executor;
import actions.common.TransactionalAction.Transactional;
import auth.gui.AuthAction.Auth;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import daos.common.*;
import exceptions.common.ForbiddenException;
import exceptions.common.IOException;
import exceptions.common.NotFoundException;
import general.common.Common;
import general.common.Http.Context;
import general.common.StudyLogger;
import models.common.*;
import models.gui.StudyProperties;
import play.data.Form;
import play.data.FormFactory;
import play.data.validation.ValidationError;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.gui.*;
import utils.common.IOUtils;
import utils.common.JsonUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

import static auth.gui.AuthAction.SIGNEDIN_USER;
import static controllers.gui.actionannotations.SaveLastVisitedPageUrlAction.SaveLastVisitedPageUrl;
import static messaging.common.FlashScopeMessaging.*;

/**
 * Controller for all actions regarding studies within the JATOS GUI.
 *
 * @author Kristian Lange
 */
@Singleton
public class Studies extends Controller {

    private final Checker checker;
    private final StudyService studyService;
    private final UserService userService;
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
    Studies(Checker checker,
            StudyService studyService,
            UserService userService,
            BreadcrumbsService breadcrumbsService,
            BatchService batchService,
            StudyDao studyDao,
            ComponentDao componentDao,
            StudyResultDao studyResultDao,
            UserDao userDao,
            ComponentResultDao componentResultDao,
            StudyLinkDao studyLinkDao,
            JsonUtils jsonUtils,
            IOUtils ioUtils,
            FormFactory formFactory,
            StudyLogger studyLogger) {
        this.checker = checker;
        this.studyService = studyService;
        this.userService = userService;
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
     * Shows the study view with details of a study and their components.
     */
    @Async(Executor.IO)
    @Auth
    @SaveLastVisitedPageUrl
    public Result study(Http.Request request, Long studyId, int httpStatus) {
        Study study = studyDao.findById(studyId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        try {
            checker.checkStandardForStudy(study, studyId, signedinUser);
        } catch (ForbiddenException | NotFoundException e) {
            return redirect(routes.Home.home(e.getHttpStatus())).flashing(ERROR, e.getMessage());
        }

        String breadcrumbs = breadcrumbsService.generateForStudy(study);
        int studyResultCount = studyResultDao.countByStudy(study);
        return status(httpStatus, views.html.gui.study.study
                .render(signedinUser, breadcrumbs, study, studyResultCount, request.asScala()));
    }

    public Result study(Http.Request request, Long studyId) {
        return study(request, studyId, Http.Status.OK);
    }

    /**
     * POST request to create a new study.
     */
    @Async(Executor.IO)
    @Auth
    public Result submitCreated(Http.Request request) {
        Form<StudyProperties> form = formFactory.form(StudyProperties.class).bindFromRequest(request);
        if (form.hasErrors()) return badRequest(form.errorsAsJson());

        StudyProperties studyProperties = form.get();
        Study study = studyService.createAndPersistStudy(studyProperties);
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
    @Async(Executor.IO)
    @Auth
    public Result properties(Long studyId) {
        Study study = studyDao.findById(studyId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        checker.checkStandardForStudy(study, studyId, signedinUser);

        StudyProperties studyProperties = studyService.bindToProperties(study);
        return ok(JsonUtils.asJsonNode(studyProperties));
    }

    /**
     * POST request to update study properties
     */
    @Async(Executor.IO)
    @Auth
    @Transactional
    public Result submitEdited(Http.Request request, Long studyId) {
        Study study = studyDao.findById(studyId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        checker.checkStandardForStudy(study, studyId, signedinUser);
        checker.checkStudyLocked(study);

        Form<StudyProperties> form = formFactory.form(StudyProperties.class).bindFromRequest(request);
        if (form.hasErrors()) return badRequest(form.errorsAsJson());

        StudyProperties studyProperties = form.get();
        try {
            studyService.renameStudyAssetsDir(study, studyProperties.getDirName());
        } catch (IOException e) {
            return badRequest(form.withError(StudyProperties.DIR_NAME, e.getMessage()).errorsAsJson());
        }

        studyService.updateStudy(study, studyProperties);
        return ok();
    }

    /**
     * POST request to update study properties
     */
    @Async(Executor.IO)
    @Auth
    public Result submitDescription(Http.Request request, Long studyId) {
        Study study = studyDao.findById(studyId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        checker.checkStandardForStudy(study, studyId, signedinUser);
        checker.checkStudyLocked(study);

        String description = request.body().asText();

        StudyProperties sp = new StudyProperties();
        sp.setDescription(description);
        List<ValidationError> errors = sp.validateDescription();
        if (!errors.isEmpty()) return badRequest(errors.get(0).message());

        studyService.updateDescription(study, description);
        return ok();
    }

    /**
     * POST request to swap the locked field of a study.
     */
    @Async(Executor.IO)
    @Auth
    public Result toggleLock(Long studyId) {
        Study study = studyDao.findById(studyId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        checker.checkStandardForStudy(study, studyId, signedinUser);

        study.setLocked(!study.isLocked());
        studyDao.merge(study);
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
    @Async(Executor.IO)
    @Auth(User.Role.ADMIN)
    public Result toggleActive(Long studyId, Boolean active) {
        Study study = studyDao.findById(studyId);
        study.setActive(active);
        studyDao.merge(study);
        return ok();
    }

    /**
     * GET request to clones a study.
     */
    @Async(Executor.IO)
    @Auth
    public Result cloneStudy(Long studyId) {
        Study study = studyDao.findByIdWithComponents(studyId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        checker.checkStandardForStudy(study, studyId, signedinUser);

        Study clone = studyService.clone(study);
        clone = studyService.createAndPersistStudy(signedinUser, clone);
        return ok(Json.toJson(ImmutableMap.of("id", clone.getId(), "title", clone.getTitle())));
    }

    /**
     * GET request that gets all users and whether they are admin of this study as a JSON array.
     */
    @Async(Executor.IO)
    @Auth
    public Result memberUsers(Long studyId) {
        Study study = studyDao.findById(studyId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        checker.checkStandardForStudy(study, studyId, signedinUser);

        List<User> userList = userDao.findAll();
        return ok(jsonUtils.memberUserArrayOfStudy(userList, study));
    }

    /**
     * POST request that adds or removes a member user from a study
     */
    @Async(Executor.IO)
    @Auth
    public Result toggleMemberUser(Long studyId, String username, boolean isMember) {
        Study study = studyDao.findById(studyId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        String normalizedUsername = User.normalizeUsername(username);
        checker.checkStandardForStudy(study, studyId, signedinUser);
        User userToChange = userService.retrieveUser(normalizedUsername);
        studyService.changeUserMember(study, userToChange, isMember);

        return ok(jsonUtils.memberUserOfStudy(userToChange, study));
    }

    /**
     * POST request that adds all users as members to a study
     */
    @Async(Executor.IO)
    @Auth
    public Result addAllMemberUsers(Long studyId) {
        Study study = studyDao.findById(studyId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
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
    @Async(Executor.IO)
    @Auth
    public Result removeAllMemberUsers(Long studyId) {
        Study study = studyDao.findById(studyId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        checker.checkStandardForStudy(study, studyId, signedinUser);

        studyService.removeAllUserMembers(study);
        return ok();
    }

    /**
     * POST request to change the order of components within a study.
     */
    @Async(Executor.IO)
    @Auth
    public Result changeComponentOrder(Long studyId, Long componentId, String newPosition) {
        Study study = studyDao.findById(studyId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        Component component = componentDao.findById(componentId);
        checker.checkStandardForStudy(study, studyId, signedinUser);
        checker.checkStudyLocked(study);
        checker.checkStandardForComponent(studyId, componentId, component);
        studyService.changeComponentPosition(newPosition, study, component);

        return ok();
    }

    /**
     * Runs the whole study. Can run the study in multiple frames in parallel. Uses a JatosWorker and the given batch.
     * Redirects to /publix/runx.
     */
    @Async(Executor.IO)
    @Auth
    public Result runStudy(Http.Request request, Long studyId, Long batchId, Long frames, Long hSplit, Long vSplit) {
        Study study = studyDao.findById(studyId);
        Batch batch = batchService.fetchBatch(batchId, study);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        checker.checkStandardForStudy(study, studyId, signedinUser);

        // Get StudyLink and redirect to jatos-publix to start the study
        StudyLink studyLink = studyLinkDao.findByBatchAndWorker(batch, signedinUser.getWorker())
                .orElseGet(() -> studyLinkDao.persist(new StudyLink(batch, signedinUser.getWorker())));
        String runUrl = Common.getJatosUrlBasePath() + "publix/runx?code=" + studyLink.getStudyCode()
                + "&frames=" + frames + "&hSplit=" + hSplit + "&vSplit=" + vSplit;
        return redirect(runUrl).addingToSession(request, "jatos_run", "RUN_STUDY");
    }

    /**
     * GET request that returns all component data of the given study as JSON.
     */
    @Async(Executor.IO)
    @Auth
    public Result tableDataByStudy(Long studyId) {
        Study study = studyDao.findByIdWithComponents(studyId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        checker.checkStandardForStudy(study, studyId, signedinUser);

        List<Component> componentList = study.getComponentList();
        List<Integer> resultCountList = new ArrayList<>();
        componentList.forEach(component -> resultCountList.add(componentResultDao.countByComponent(component)));
        JsonNode dataAsJson = jsonUtils.allComponentsForUI(study.getComponentList(), resultCountList);
        return ok(dataAsJson);
    }

}
