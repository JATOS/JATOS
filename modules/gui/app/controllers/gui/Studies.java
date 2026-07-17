package controllers.gui;

import actions.common.AsyncAction.Async;
import actions.common.AsyncAction.Executor;
import actions.common.TransactionalAction.Transactional;
import auth.gui.AuthAction.Auth;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import daos.common.*;
import exceptions.common.ForbiddenException;
import exceptions.common.NotFoundException;
import general.common.Common;
import general.common.StudyLogger;
import http.common.Http.Context;
import json.common.DefaultJson;
import json.common.DomainJsonMapper;
import models.common.*;
import models.gui.StudyProperties;
import play.data.Form;
import play.data.FormFactory;
import play.data.validation.ValidationError;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.gui.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static auth.gui.AuthAction.SIGNEDIN_USER;
import static controllers.gui.actionannotations.SaveLastVisitedPageUrlAction.SaveLastVisitedPageUrl;
import static messaging.common.FlashMessagingHelper.ERROR;
import static models.common.User.Role.USER;
import static models.common.User.Role.VIEWER;

/**
 * Controller for all actions regarding studies within the JATOS GUI.
 */
@Singleton
public class Studies extends Controller {

    private final AuthorizationService authorizationService;
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
    private final FormFactory formFactory;
    private final StudyLogger studyLogger;
    private final DefaultJson defaultJson;
    private final DomainJsonMapper domainJsonMapper;

    @Inject
    Studies(AuthorizationService authorizationService,
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
            FormFactory formFactory,
            StudyLogger studyLogger,
            DefaultJson defaultJson,
            DomainJsonMapper domainJsonMapper) {
        this.authorizationService = authorizationService;
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
        this.formFactory = formFactory;
        this.studyLogger = studyLogger;
        this.defaultJson = defaultJson;
        this.domainJsonMapper = domainJsonMapper;
    }

    /**
     * Shows the study view with details of a study and their components.
     */
    @Async(Executor.IO)
    @Auth(roles = {VIEWER, USER})
    @SaveLastVisitedPageUrl
    public Result study(Http.Request request, Long studyId, int httpStatus) {
        Study study = studyDao.findById(studyId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        try {
            authorizationService.canUserAccessStudy(study, signedinUser);
        } catch (ForbiddenException | NotFoundException e) {
            Context.current().response().putFlash(ERROR, e.getMessage());
            return redirect(routes.Home.home(e.getHttpStatus()));
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
    @Auth(roles = USER)
    public Result submitCreated(Http.Request request) {
        Form<StudyProperties> form = formFactory.form(StudyProperties.class).bindFromRequest(request);
        if (form.hasErrors()) return badRequest(form.errorsAsJson());

        StudyProperties studyProperties = form.get();
        Study study = studyService.createAndPersistStudyAndAssetsDir(studyProperties, false);
        return ok(String.valueOf(study.getId()));
    }

    /**
     * GET request that returns the study properties as JSON.
     */
    @Async(Executor.IO)
    @Auth(roles = {VIEWER, USER})
    public Result properties(Long studyId) {
        Study study = studyDao.findById(studyId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessStudy(study, signedinUser);

        StudyProperties studyProperties = studyService.bindToProperties(study);
        return ok(defaultJson.objAsJsonNode(studyProperties));
    }

    /**
     * POST request to update study properties
     */
    @Async(Executor.IO)
    @Auth(roles = USER)
    @Transactional
    public Result submitEdited(Http.Request request, Long studyId) {
        Study study = studyDao.findById(studyId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessStudy(study, signedinUser, true);

        Form<StudyProperties> form = formFactory.form(StudyProperties.class).bindFromRequest(request);
        if (form.hasErrors()) return badRequest(form.errorsAsJson());

        StudyProperties studyProperties = form.get();
        try {
            studyService.updateStudyAndRenameAssets(study, studyProperties);
        } catch (IOException e) {
            return badRequest(form.withError(StudyProperties.DIR_NAME, e.getMessage()).errorsAsJson());
        }
        return ok();
    }

    /**
     * POST request to update study properties
     */
    @Async(Executor.IO)
    @Auth(roles = USER)
    public Result submitDescription(Http.Request request, Long studyId) {
        Study study = studyDao.findById(studyId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessStudy(study, signedinUser, true);

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
    @Auth(roles = USER)
    public Result toggleLock(Long studyId) {
        Study study = studyDao.findById(studyId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessStudy(study, signedinUser);

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
     * GET request to clone a study.
     */
    @Async(Executor.IO)
    @Auth(roles = USER)
    @Transactional
    public Result cloneStudy(Long studyId) {
        Study study = studyDao.findByIdWithComponents(studyId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessStudy(study, signedinUser);

        Study clone = studyService.clone(study);
        clone = studyService.createAndPersistStudy(clone);

        JsonNode json = defaultJson.objAsJsonNode(ImmutableMap.of("id", clone.getId(), "title", clone.getTitle()));
        return ok(json);
    }

    /**
     * GET request that gets all users and whether they are admin of this study as a JSON array.
     */
    @Async(Executor.IO)
    @Auth(roles = {VIEWER, USER})
    public Result memberUsers(Long studyId) {
        Study study = studyDao.findById(studyId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessStudy(study, signedinUser);

        List<User> userList = userDao.findAllByStudy(study);
        return ok(domainJsonMapper.memberUserArrayOfStudy(userList));
    }

    /**
     * POST request that adds or removes a member user from a study
     */
    @Async(Executor.IO)
    @Auth(roles = USER)
    @Transactional
    public Result toggleMemberUser(Long studyId, String username, boolean isMember) {
        Study study = studyDao.findById(studyId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        String normalizedUsername = User.normalizeUsername(username);
        authorizationService.canUserAccessStudy(study, signedinUser);

        User userToChange = userService.retrieveUser(normalizedUsername);
        studyService.changeUserMember(study, userToChange, isMember);

        return ok(domainJsonMapper.memberUserOfStudy(userToChange, study));
    }

    /**
     * POST request that adds all users as members to a study
     */
    @Async(Executor.IO)
    @Auth(roles = USER)
    @Transactional
    public Result addAllMemberUsers(Long studyId) {
        Study study = studyDao.findById(studyId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessStudy(study, signedinUser);

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
    @Auth(roles = USER)
    @Transactional
    public Result removeAllMemberUsers(Long studyId) {
        Study study = studyDao.findById(studyId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessStudy(study, signedinUser);

        studyService.removeAllUserMembers(study);
        return ok();
    }

    /**
     * POST request to change the order of components within a study.
     */
    @Async(Executor.IO)
    @Auth(roles = USER)
    @Transactional
    public Result changeComponentOrder(Long studyId, Long componentId, int newPosition) {
        Study study = studyDao.findByIdWithComponents(studyId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        Component component = componentDao.findById(componentId);
        authorizationService.canUserAccessStudy(study, signedinUser, true);
        authorizationService.canUserAccessComponent(component, signedinUser);

        studyService.changeComponentPosition(newPosition, study, component);

        return ok();
    }

    /**
     * Runs the whole study. Can run the study in multiple frames in parallel. Uses a JatosWorker and the given batch.
     * Redirects to /publix/runx.
     */
    @Async(Executor.IO)
    @Auth(roles = USER)
    public Result runStudy(Long studyId, Long batchId, Long frames, Long hSplit, Long vSplit) {
        Study study = studyDao.findById(studyId);
        Batch batch = batchService.fetchBatch(batchId, study);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessStudy(study, signedinUser);
        authorizationService.canUserAccessBatch(batch, signedinUser);

        // Get StudyLink and redirect to jatos-publix to start the study
        StudyLink studyLink = studyLinkDao.findByBatchAndWorker(batch, signedinUser.getWorker())
                .orElseGet(() -> studyLinkDao.persist(new StudyLink(batch, signedinUser.getWorker())));
        String runUrl = Common.getJatosUrlBasePath() + "publix/runx?code=" + studyLink.getStudyCode()
                + "&frames=" + frames + "&hSplit=" + hSplit + "&vSplit=" + vSplit;
        Context.current().response().putSession("jatos_run", "RUN_STUDY");
        return redirect(runUrl);
    }

    /**
     * GET request that returns all component data of the given study as JSON.
     */
    @Async(Executor.IO)
    @Auth(roles = {VIEWER, USER})
    public Result tableDataByStudy(Long studyId) {
        Study study = studyDao.findByIdWithComponents(studyId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessStudy(study, signedinUser);

        Map<Long, Integer> resultCountsByComponentId = componentResultDao.countByStudyComponents(study);
        JsonNode dataAsJson = domainJsonMapper.allComponentsForUI(study.getComponentList(), resultCountsByComponentId);
        return ok(dataAsJson);
    }

}
