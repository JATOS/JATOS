package controllers.gui;

import akka.stream.javadsl.FileIO;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import controllers.gui.actionannotations.AuthenticationAction.Authenticated;
import controllers.gui.actionannotations.GuiAccessLoggingAction.GuiAccessLogging;
import daos.common.*;
import exceptions.gui.BadRequestException;
import exceptions.gui.ForbiddenException;
import exceptions.gui.JatosGuiException;
import exceptions.gui.NotFoundException;
import general.common.Common;
import general.common.StudyLogger;
import general.gui.RequestScopeMessaging;
import models.common.*;
import models.common.workers.Worker;
import models.gui.StudyProperties;
import play.data.Form;
import play.data.FormFactory;
import play.db.jpa.Transactional;
import play.http.HttpEntity;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.ResponseHeader;
import play.mvc.Result;
import services.gui.*;
import utils.common.Helpers;
import utils.common.IOUtils;
import utils.common.JsonUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

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
    private final AuthenticationService authenticationService;
    private final WorkerService workerService;
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
            UserService userService, AuthenticationService authenticationService, WorkerService workerService,
            BreadcrumbsService breadcrumbsService, BatchService batchService, StudyDao studyDao,
            ComponentDao componentDao, StudyResultDao studyResultDao, UserDao userDao,
            ComponentResultDao componentResultDao, StudyLinkDao studyLinkDao, JsonUtils jsonUtils,
            IOUtils ioUtils, FormFactory formFactory, StudyLogger studyLogger) {
        this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
        this.checker = checker;
        this.studyService = studyService;
        this.userService = userService;
        this.authenticationService = authenticationService;
        this.workerService = workerService;
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
    @Authenticated
    public Result study(Long studyId, int httpStatus) throws JatosGuiException {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        checkStandardForStudy(studyId, study, loggedInUser);
        if (!study.isActive()) {
            RequestScopeMessaging.warning(
                    "This study was deactivated by an admin. Although you can still edit this study, it can't be run "
                            + "by you nor by a participant. Please contact your admin.");
        }
        String breadcrumbs = breadcrumbsService.generateForStudy(study);
        int studyResultCount = studyResultDao.countByStudy(study);
        return status(httpStatus, views.html.gui.study.study
                .render(loggedInUser, breadcrumbs, Helpers.isLocalhost(), study, studyResultCount));
    }

    @Transactional
    @Authenticated
    public Result study(Long studyId) throws JatosGuiException {
        return study(studyId, Http.Status.OK);
    }

    /**
     * POST request to create a new study.
     */
    @Transactional
    @Authenticated
    public Result submitCreated() {
        User loggedInUser = authenticationService.getLoggedInUser();

        Form<StudyProperties> form = formFactory.form(StudyProperties.class).bindFromRequest();
        if (form.hasErrors()) return badRequest(form.errorsAsJson());

        StudyProperties studyProperties = form.get();

        try {
            ioUtils.createStudyAssetsDir(studyProperties.getDirName());
        } catch (IOException e) {
            return badRequest(form.withError(StudyProperties.DIR_NAME, e.getMessage()).errorsAsJson());
        }

        Study study = studyService.createAndPersistStudy(loggedInUser, studyProperties);
        return ok(study.getId().toString());
    }

    /**
     * GET request that returns the study properties as JSON.
     */
    @Transactional
    @Authenticated
    public Result properties(Long studyId) {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
        } catch (ForbiddenException e) {
            return forbidden(e.getMessage());
        } catch (NotFoundException e) {
            return notFound(e.getMessage());
        }

        StudyProperties studyProperties = studyService.bindToProperties(study);
        return ok(jsonUtils.asJsonNode(studyProperties));
    }

    /**
     * POST request to update study properties
     */
    @Transactional
    @Authenticated
    public Result submitEdited(Long studyId) {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStudyLocked(study);
        } catch (ForbiddenException e) {
            return forbidden(e.getMessage());
        } catch (NotFoundException e) {
            return notFound(e.getMessage());
        }

        Form<StudyProperties> form = formFactory.form(StudyProperties.class).bindFromRequest();
        if (form.hasErrors()) return badRequest(form.errorsAsJson());

        StudyProperties studyProperties = form.get();
        try {
            studyService.renameStudyAssetsDir(study, studyProperties.getDirName(), studyProperties.isDirRename());
        } catch (IOException e) {
            return badRequest(form.withError(StudyProperties.DIR_NAME, e.getMessage()).errorsAsJson());
        }

        studyService.updateStudy(study, studyProperties, loggedInUser);
        return ok(" "); // jQuery.ajax cannot handle empty responses
    }

    /**
     * POST request to swap the locked field of a study.
     */
    @Transactional
    @Authenticated
    public Result toggleLock(Long studyId) throws JatosGuiException {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        checkStandardForStudy(studyId, study, loggedInUser);

        study.setLocked(!study.isLocked());
        studyDao.update(study);
        if (study.isLocked()) {
            studyLogger.log(study, loggedInUser, "Locked study");
        } else {
            studyLogger.log(study, loggedInUser, "Unlocked study");
        }
        return ok(String.valueOf(study.isLocked()));
    }

    /**
     * POST request to activate or deactivate a study. Can be done only by an admin.
     */
    @Transactional
    @Authenticated(User.Role.ADMIN)
    public Result toggleActive(Long studyId, Boolean active) {
        Study study = studyDao.findById(studyId);
        study.setActive(active);
        studyDao.update(study);
        return ok();
    }

    /**
     * DELETE request to remove a study
     */
    @Transactional
    @Authenticated
    public Result remove(Long studyId) throws JatosGuiException {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStudyLocked(study);
        } catch (ForbiddenException | NotFoundException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }

        try {
            studyService.removeStudyInclAssets(study, loggedInUser);
        } catch (IOException e) {
            String errorMsg = e.getMessage();
            return internalServerError(errorMsg);
        }
        return ok(" "); // jQuery.ajax cannot handle empty responses
    }

    /**
     * GET request to clones a study.
     */
    @Transactional
    @Authenticated
    public Result cloneStudy(Long studyId) throws JatosGuiException {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
        } catch (ForbiddenException | NotFoundException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }

        Study clone = null;
        try {
            clone = studyService.clone(study);
            studyService.createAndPersistStudy(loggedInUser, clone);
        } catch (IOException e) {
            jatosGuiExceptionThrower.throwAjax(e.getMessage(), Http.Status.INTERNAL_SERVER_ERROR);
        }
        return ok(Json.toJson(ImmutableMap.of("id", clone.getId(), "title", clone.getTitle())));
    }

    /**
     * GET request that gets all users and whether they are admin of this study as a JSON array.
     */
    @Transactional
    @Authenticated
    public Result memberUsers(Long studyId) throws JatosGuiException {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
        } catch (ForbiddenException | NotFoundException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }
        List<User> userList = userDao.findAll();
        return ok(jsonUtils.memberUserArrayOfStudy(userList, study));
    }

    /**
     * POST request that adds or removes a member user from a study
     */
    @Transactional
    @Authenticated
    public Result toggleMemberUser(Long studyId, String username, boolean isMember) throws JatosGuiException {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        User userToChange = null;
        String normalizedUsername = User.normalizeUsername(username);
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            userToChange = userService.retrieveUser(normalizedUsername);
            studyService.changeUserMember(study, userToChange, isMember);
        } catch (ForbiddenException | NotFoundException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }

        return ok(jsonUtils.memberUserOfStudy(userToChange, study));
    }

    /**
     * POST request that adds all users as members to a study
     */
    @Transactional
    @Authenticated
    public Result addAllMemberUsers(Long studyId) throws JatosGuiException {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
        } catch (ForbiddenException | NotFoundException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }
        if (!Common.isStudyMembersAllowedToAddAllUsers()) {
            return forbidden("It's not allowed to add all users at once in this JATOS.");
        }

        studyService.addAllUserMembers(study);
        return ok(" "); // jQuery.ajax cannot handle empty responses
    }

    /**
     * DELETE request that removes all member users from a study
     */
    @Transactional
    @Authenticated
    public Result removeAllMemberUsers(Long studyId) throws JatosGuiException {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
        } catch (ForbiddenException | NotFoundException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }
        studyService.removeAllUserMembers(study);
        return ok(" "); // jQuery.ajax cannot handle empty responses
    }

    /**
     * POST request to change the oder of components within a study.
     */
    @Transactional
    @Authenticated
    public Result changeComponentOrder(Long studyId, Long componentId, String newPosition) throws JatosGuiException {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        Component component = componentDao.findById(componentId);
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStudyLocked(study);
            checker.checkStandardForComponents(studyId, componentId, component);
            studyService.changeComponentPosition(newPosition, study, component);
        } catch (ForbiddenException | BadRequestException | NotFoundException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }
        return ok(" "); // jQuery.ajax cannot handle empty responses
    }

    /**
     * Runs the whole study. Uses a JatosWorker and the given batch. Redirects to /publix/studyCode.
     */
    @Transactional
    @Authenticated
    public Result runStudy(Http.Request request, Long studyId, Long batchId)
            throws JatosGuiException, NotFoundException {
        Study study = studyDao.findById(studyId);
        Batch batch = batchService.fetchBatch(batchId, study);
        User loggedInUser = authenticationService.getLoggedInUser();
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStandardForBatch(batch, study, batchId);
        } catch (ForbiddenException | NotFoundException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }

        // Get StudyLink and redirect to jatos-publix: start study
        StudyLink studyLink = studyLinkDao.findByBatchAndWorker(batch, loggedInUser.getWorker())
                .orElseGet(() -> studyLinkDao.create(new StudyLink(batch, loggedInUser.getWorker())));
        String runUrl = Common.getPlayHttpContext() + "publix/"  + studyLink.getStudyCode();
        return redirect(runUrl).addingToSession(request, "jatos_run", "RUN_STUDY");
    }

    /**
     * GET request that returns all component data of the given study as JSON.
     */
    @Transactional
    @Authenticated
    public Result tableDataByStudy(Long studyId) throws JatosGuiException {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        checkStandardForStudy(studyId, study, loggedInUser);

        List<Component> componentList = study.getComponentList();
        List<Integer> resultCountList = new ArrayList<>();
        componentList.forEach(component -> resultCountList.add(componentResultDao.countByComponent(component)));
        JsonNode dataAsJson = jsonUtils.allComponentsForUI(study.getComponentList(), resultCountList);
        return ok(dataAsJson);
    }

    /**
     * GET request
     *
     * @param studyId    study's ID
     * @param entryLimit It cuts the log after the number of lines given in entryLimit
     * @param download   If true streams the whole study log file - if not only until entryLimit
     * @return Depending on 'download' flag returns the whole study log file - or only part of it (until entryLimit) in
     * reverse order and 'Transfer-Encoding:chunked'
     */
    @Transactional
    @Authenticated
    public Result studyLog(Long studyId, int entryLimit, boolean download) {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
        } catch (ForbiddenException e) {
            return forbidden(e.getMessage());
        } catch (NotFoundException e) {
            return notFound(e.getMessage());
        }

        if (download) {
            Path studyLogPath = Paths.get(studyLogger.getPath(study));
            if (Files.notExists(studyLogPath)) {
                return notFound();
            }
            Source<ByteString, ?> source = FileIO.fromPath(studyLogPath);
            Optional<Long> contentLength = Optional.of(studyLogPath.toFile().length());
            return new Result(new ResponseHeader(200, Collections.emptyMap()),
                    new HttpEntity.Streamed(source, contentLength, Optional.of("application/octet-stream")));
        } else {
            return ok().chunked(studyLogger.readLogFile(study, entryLimit));
        }
    }

    /**
     * GET request that returns all worker data that belong to this study as JSON
     */
    @Transactional
    @Authenticated
    public Result allWorkers(Long studyId) {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();

        JsonNode dataAsJson;
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);

            Set<Worker> workerSet = workerService.retrieveAllWorkers(study);
            dataAsJson = jsonUtils.workersForTableData(workerSet, study);
        } catch (ForbiddenException e) {
            return forbidden(e.getMessage());
        } catch (NotFoundException e) {
            return notFound(e.getMessage());
        }
        return ok(dataAsJson);
    }

    private void checkStandardForStudy(Long studyId, Study study, User loggedInUser) throws JatosGuiException {
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
        } catch (ForbiddenException | NotFoundException e) {
            jatosGuiExceptionThrower.throwHome(e);
        }
    }

}
