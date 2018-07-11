package controllers.gui;

import akka.stream.javadsl.FileIO;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import com.fasterxml.jackson.databind.JsonNode;
import controllers.gui.actionannotations.AuthenticationAction.Authenticated;
import controllers.gui.actionannotations.GuiAccessLoggingAction.GuiAccessLogging;
import daos.common.*;
import exceptions.gui.BadRequestException;
import exceptions.gui.ForbiddenException;
import exceptions.gui.JatosGuiException;
import exceptions.gui.NotFoundException;
import general.common.Common;
import general.common.StudyLogger;
import models.common.Component;
import models.common.Study;
import models.common.User;
import models.common.workers.Worker;
import models.gui.StudyProperties;
import play.Logger;
import play.Logger.ALogger;
import play.api.mvc.ResponseHeader;
import play.data.Form;
import play.data.FormFactory;
import play.data.validation.ValidationError;
import play.db.jpa.Transactional;
import play.http.HttpEntity;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import scala.Option;
import services.gui.*;
import utils.common.HttpUtils;
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
@GuiAccessLogging
@Singleton
public class Studies extends Controller {

    private static final ALogger LOGGER = Logger.of(Studies.class);

    private final JatosGuiExceptionThrower jatosGuiExceptionThrower;
    private final Checker checker;
    private final StudyService studyService;
    private final UserService userService;
    private final AuthenticationService authenticationService;
    private final WorkerService workerService;
    private final BreadcrumbsService breadcrumbsService;
    private final UserDao userDao;
    private final StudyDao studyDao;
    private final ComponentDao componentDao;
    private final StudyResultDao studyResultDao;
    private final ComponentResultDao componentResultDao;
    private final JsonUtils jsonUtils;
    private final IOUtils ioUtils;
    private final FormFactory formFactory;
    private final StudyLogger studyLogger;

    @Inject
    Studies(JatosGuiExceptionThrower jatosGuiExceptionThrower, Checker checker,
            StudyService studyService, UserService userService,
            AuthenticationService authenticationService,
            WorkerService workerService, BreadcrumbsService breadcrumbsService,
            StudyDao studyDao, ComponentDao componentDao,
            StudyResultDao studyResultDao, UserDao userDao,
            ComponentResultDao componentResultDao, JsonUtils jsonUtils,
            IOUtils ioUtils, FormFactory formFactory, StudyLogger studyLogger) {
        this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
        this.checker = checker;
        this.studyService = studyService;
        this.userService = userService;
        this.authenticationService = authenticationService;
        this.workerService = workerService;
        this.breadcrumbsService = breadcrumbsService;
        this.studyDao = studyDao;
        this.componentDao = componentDao;
        this.studyResultDao = studyResultDao;
        this.componentResultDao = componentResultDao;
        this.userDao = userDao;
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
        LOGGER.debug(".study: studyId " + studyId);
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        checkStandardForStudy(studyId, study, loggedInUser);

        String breadcrumbs = breadcrumbsService.generateForStudy(study);
        int studyResultCount = studyResultDao.countByStudy(study);
        return status(httpStatus,
                views.html.gui.study.study.render(loggedInUser, breadcrumbs,
                        HttpUtils.isLocalhost(), study, studyResultCount));
    }

    @Transactional
    @Authenticated
    public Result study(Long studyId) throws JatosGuiException {
        return study(studyId, Http.Status.OK);
    }

    /**
     * Ajax POST request of the form to create a new study.
     */
    @Transactional
    @Authenticated
    public Result submitCreated() {
        LOGGER.debug(".submitCreated");
        User loggedInUser = authenticationService.getLoggedInUser();

        Form<StudyProperties> form = formFactory.form(StudyProperties.class)
                .bindFromRequest();
        if (form.hasErrors()) {
            return badRequest(form.errorsAsJson());
        }
        StudyProperties studyProperties = form.get();

        try {
            ioUtils.createStudyAssetsDir(studyProperties.getDirName());
        } catch (IOException e) {
            form.reject(new ValidationError(StudyProperties.DIRNAME,
                    e.getMessage()));
            return badRequest(form.errorsAsJson());
        }

        Study study = studyService.createAndPersistStudy(loggedInUser,
                studyProperties);
        return ok(study.getId().toString());
    }

    /**
     * Ajax GET request that gets the study properties as JSON.
     */
    @Transactional
    @Authenticated
    public Result properties(Long studyId) throws JatosGuiException {
        LOGGER.debug(".properties: studyId " + studyId);
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        checkStandardForStudy(studyId, study, loggedInUser);

        StudyProperties studyProperties = studyService.bindToProperties(study);
        return ok(jsonUtils.asJsonNode(studyProperties));
    }

    /**
     * Ajax POST request of the edit form to change the properties of a study.
     */
    @Transactional
    @Authenticated
    public Result submitEdited(Long studyId) throws JatosGuiException {
        LOGGER.debug(".submitEdited: studyId " + studyId);
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStudyLocked(study);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }

        Form<StudyProperties> form = formFactory.form(StudyProperties.class)
                .bindFromRequest();
        if (form.hasErrors()) {
            return badRequest(form.errorsAsJson());
        }
        StudyProperties studyProperties = form.get();
        try {
            studyService.renameStudyAssetsDir(study,
                    studyProperties.getDirName());
        } catch (IOException e) {
            form.reject(new ValidationError(StudyProperties.DIRNAME,
                    e.getMessage()));
            return badRequest(form.errorsAsJson());
        }

        studyService.updateStudy(study, studyProperties);
        return ok(" "); // jQuery.ajax cannot handle empty responses
    }

    /**
     * Ajax POST request
     * <p>
     * Swap the locked field of a study.
     */
    @Transactional
    @Authenticated
    public Result toggleLock(Long studyId) throws JatosGuiException {
        LOGGER.debug(".toggleLock: studyId " + studyId);
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        checkStandardForStudy(studyId, study, loggedInUser);

        study.setLocked(!study.isLocked());
        studyDao.update(study);
        if (study.isLocked()) {
            studyLogger.log(study, "Locked study");
        } else {
            studyLogger.log(study, "Unlocked study");
        }
        return ok(String.valueOf(study.isLocked()));
    }

    /**
     * Ajax DELETE request
     * <p>
     * Remove a study
     */
    @Transactional
    @Authenticated
    public Result remove(Long studyId) throws JatosGuiException {
        LOGGER.debug(".remove: studyId " + studyId);
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStudyLocked(study);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }

        try {
            studyService.removeStudyInclAssets(study);
        } catch (IOException e) {
            String errorMsg = e.getMessage();
            return internalServerError(errorMsg);
        }
        return ok(" "); // jQuery.ajax cannot handle empty responses
    }

    /**
     * Ajax request
     * <p>
     * Clones a study.
     */
    @Transactional
    @Authenticated
    public Result cloneStudy(Long studyId) throws JatosGuiException {
        LOGGER.debug(".cloneStudy: studyId " + studyId);
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }

        Study clone = null;
        try {
            clone = studyService.clone(study);
            studyService.createAndPersistStudy(loggedInUser, clone);
        } catch (IOException e) {
            jatosGuiExceptionThrower.throwAjax(e.getMessage(),
                    Http.Status.INTERNAL_SERVER_ERROR);
        }
        return ok(clone.getTitle());
    }

    /**
     * Ajax GET request that gets all users and whether they are admin of this
     * study as a JSON array.
     */
    @Transactional
    @Authenticated
    public Result memberUsers(Long studyId) throws JatosGuiException {
        LOGGER.debug(".memberUsers: studyId " + studyId);
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }
        List<User> userList = userDao.findAll();
        return ok(jsonUtils.memberUserArrayOfStudy(userList, study));
    }

    /**
     * Ajax POST request that adds or removes a member user from a study
     */
    @Transactional
    @Authenticated
    public Result toggleMemberUser(Long studyId, String email, boolean isMember)
            throws JatosGuiException {
        LOGGER.debug(".toggleMemberUser: studyId " + studyId + ", email " + email
                + ", isMember " + isMember);
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        User userToChange = null;
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            userToChange = userService.retrieveUser(email);
            studyService.changeUserMember(study, userToChange, isMember);
        } catch (ForbiddenException | NotFoundException | BadRequestException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }

        return ok(jsonUtils.memberUserOfStudy(userToChange, study));
    }

    /**
     * Ajax POST request that adds all users as members to a study
     */
    @Transactional
    @Authenticated
    public Result addAllMemberUsers(Long studyId) throws JatosGuiException {
        LOGGER.debug(".addAllMemberUsers: studyId " + studyId);
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }

        studyService.addAllUserMembers(study);
        return ok(" "); // jQuery.ajax cannot handle empty responses
    }

    /**
     * Ajax DELETE request that removes all member users from a study
     */
    @Transactional
    @Authenticated
    public Result removeAllMemberUsers(Long studyId) throws JatosGuiException {
        LOGGER.debug(".removeAllMemberUsers: studyId " + studyId);
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }
        studyService.removeAllUserMembers(study);
        return ok(" "); // jQuery.ajax cannot handle empty responses
    }


    /**
     * Ajax POST request
     * <p>
     * Change the oder of components within a study.
     */
    @Transactional
    @Authenticated
    public Result changeComponentOrder(Long studyId, Long componentId,
            String newPosition) throws JatosGuiException {
        LOGGER.debug(".changeComponentOrder: studyId " + studyId);
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        Component component = componentDao.findById(componentId);
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStudyLocked(study);
            checker.checkStandardForComponents(studyId, componentId, component);
            studyService.changeComponentPosition(newPosition, study, component);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }
        return ok(" "); // jQuery.ajax cannot handle empty responses
    }

    /**
     * Actually runs the study with the given study ID, in the batch with the
     * given batch ID while using a JatosWorker. It redirects to
     * Publix.startStudy() action.
     */
    @Transactional
    @Authenticated
    public Result runStudy(Long studyId, Long batchId)
            throws JatosGuiException {
        LOGGER.debug(
                ".runStudy: studyId " + studyId + ", " + "batch " + batchId);
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        checkStandardForStudy(studyId, study, loggedInUser);

        session("jatos_run", "RUN_STUDY");
        String startStudyUrl = Common.getPlayHttpContext()
                + "publix/" + study.getId() + "/start?"
                + "batchId" + "=" + batchId + "&" + "jatosWorkerId" + "="
                + loggedInUser.getWorker().getId();
        return redirect(startStudyUrl);
    }

    /**
     * Ajax request
     * <p>
     * Returns all Components of the given study as JSON.
     */
    @Transactional
    @Authenticated
    public Result tableDataByStudy(Long studyId) throws JatosGuiException {
        LOGGER.debug(".tableDataByStudy: studyId " + studyId);
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        checkStandardForStudy(studyId, study, loggedInUser);

        List<Component> componentList = study.getComponentList();
        List<Integer> resultCountList = new ArrayList<>();
        componentList.forEach(component -> resultCountList
                .add(componentResultDao.countByComponent(component)));
        JsonNode dataAsJson = jsonUtils
                .allComponentsForUI(study.getComponentList(), resultCountList);
        return ok(dataAsJson);
    }

    /**
     * Ajax request
     *
     * @param studyId    study's ID
     * @param entryLimit It cuts the log after the number of lines given in entryLimit
     * @param download   If true it prepares the Response for johnculviner's jQuery.fileDownload
     * @return Returns the content of the study log file in reverse order and as 'Transfer-Encoding:chunked'.
     * @throws JatosGuiException
     */
    @Transactional
    @Authenticated
    public Result studyLog(Long studyId, int entryLimit, boolean download)
            throws JatosGuiException {
        LOGGER.debug(".studyLog: studyId " + studyId + ", entryLimit " + entryLimit
                + ", download " + download);
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        checkStandardForStudy(studyId, study, loggedInUser);

        if (download) {
            // Prepares the response for a download with the johnculviner's
            // jQuery.fileDownload plugin. This plugin is merely used to detect a failed
            // download. If the response isn't OK and it doesn't have this cookie then
            // the plugin regards it as a fail.
            Path studyLogPath = Paths.get(studyLogger.getPath(study));
            if (Files.notExists(studyLogPath)) {
                return notFound();
            }
            String filename = "jatos_studylog_" + study.getUuid() + ".log";
            response().setHeader("Content-disposition", "attachment; filename=" + filename);
            // Set transient cookie with no domain or path constraints
            Http.Cookie cookie = new Http.Cookie("fileDownload", "true", null,
                    Common.getPlayHttpContext(), null, false, false);
            response().setCookie(cookie);
            Source<ByteString, ?> source = FileIO.fromPath(studyLogPath);
            return new Result(
                    new ResponseHeader(200, Collections.emptyMap(), Option.apply("")),
                    new HttpEntity.Streamed(source, Optional.empty(), Optional.of("text/plain"))
            );
        } else {
            return ok().chunked(studyLogger.readLogFile(study, entryLimit));
        }
    }

    /**
     * Ajax GET request
     * <p>
     * Returns a list of all workers as JSON that belong to this study.
     */
    @Transactional
    @Authenticated
    public Result allWorkers(Long studyId) throws JatosGuiException {
        LOGGER.debug(".allWorkersTableData: studyId " + studyId);
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();

        JsonNode dataAsJson = null;
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);

            Set<Worker> workerSet = workerService.retrieveAllWorkers(study);
            dataAsJson = jsonUtils.workersForTableData(workerSet, study);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }
        return ok(dataAsJson);
    }

    private void checkStandardForStudy(Long studyId, Study study,
            User loggedInUser) throws JatosGuiException {
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwHome(e);
        }
    }

}
