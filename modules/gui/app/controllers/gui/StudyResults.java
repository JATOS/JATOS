package controllers.gui;

import akka.stream.javadsl.Source;
import akka.util.ByteString;
import controllers.gui.actionannotations.AuthenticationAction.Authenticated;
import controllers.gui.actionannotations.GuiAccessLoggingAction.GuiAccessLogging;
import daos.common.BatchDao;
import daos.common.GroupResultDao;
import daos.common.StudyDao;
import daos.common.StudyResultDao;
import daos.common.worker.WorkerDao;
import exceptions.gui.BadRequestException;
import exceptions.gui.ForbiddenException;
import exceptions.gui.JatosGuiException;
import exceptions.gui.NotFoundException;
import models.common.*;
import models.common.workers.Worker;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Result;
import scala.Option;
import services.gui.*;
import utils.common.Helpers;
import utils.common.JsonUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

/**
 * Controller for actions around StudyResults in the JATOS GUI.
 *
 * @author Kristian Lange
 */
@GuiAccessLogging
@Singleton
public class StudyResults extends Controller {

    private final JatosGuiExceptionThrower jatosGuiExceptionThrower;
    private final Checker checker;
    private final AuthenticationService authenticationService;
    private final BreadcrumbsService breadcrumbsService;
    private final ResultRemover resultRemover;
    private final ResultService resultService;
    private final StudyDao studyDao;
    private final BatchDao batchDao;
    private final StudyResultDao studyResultDao;
    private final GroupResultDao groupResultDao;
    private final WorkerDao workerDao;
    private final JsonUtils jsonUtils;

    @Inject
    StudyResults(JatosGuiExceptionThrower jatosGuiExceptionThrower,
            Checker checker, AuthenticationService authenticationService,
            BreadcrumbsService breadcrumbsService, ResultRemover resultRemover,
            ResultService resultService, StudyDao studyDao, BatchDao batchDao,
            StudyResultDao studyResultDao, GroupResultDao groupResultDao, WorkerDao workerDao, JsonUtils jsonUtils) {
        this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
        this.checker = checker;
        this.authenticationService = authenticationService;
        this.breadcrumbsService = breadcrumbsService;
        this.resultRemover = resultRemover;
        this.resultService = resultService;
        this.studyDao = studyDao;
        this.batchDao = batchDao;
        this.studyResultDao = studyResultDao;
        this.groupResultDao = groupResultDao;
        this.workerDao = workerDao;
        this.jsonUtils = jsonUtils;
    }

    /**
     * Shows view with all StudyResults of a study.
     */
    @Transactional
    @Authenticated
    public Result studysStudyResults(Long studyId) throws JatosGuiException {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwStudy(e, studyId);
        }

        String breadcrumbs = breadcrumbsService.generateForStudy(study, BreadcrumbsService.RESULTS);
        String dataUrl = controllers.gui.routes.StudyResults.tableDataByStudy(study.getId()).url();
        return ok(views.html.gui.result.studyResults
                .render(loggedInUser, breadcrumbs, Helpers.isLocalhost(), study, dataUrl));
    }

    /**
     * Shows view with all StudyResults of a batch.
     */
    @Transactional
    @Authenticated
    public Result batchesStudyResults(Long studyId, Long batchId, Option<String> workerType) throws JatosGuiException {
        Batch batch = batchDao.findById(batchId);
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStandardForBatch(batch, study, batchId);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwStudy(e, studyId);
        }

        String breadcrumbsTitle = workerType.isEmpty() ? BreadcrumbsService.RESULTS
                : BreadcrumbsService.RESULTS + " of " + Worker.getUIWorkerType(workerType.get()) + " workers";
        String breadcrumbs = breadcrumbsService.generateForBatch(study, batch, breadcrumbsTitle);
        String dataUrl = controllers.gui.routes.StudyResults.tableDataByBatch(studyId, batchId, workerType).url();
        return ok(views.html.gui.result.studyResults.render(loggedInUser,
                breadcrumbs, Helpers.isLocalhost(), study, dataUrl));
    }

    /**
     * Shows view with all StudyResults of a group.
     */
    @Transactional
    @Authenticated
    public Result groupsStudyResults(Long studyId, Long groupId) throws JatosGuiException {
        Study study = studyDao.findById(studyId);
        GroupResult groupResult = groupResultDao.findById(groupId);
        User loggedInUser = authenticationService.getLoggedInUser();
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStandardForGroup(groupResult, study, groupId);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwStudy(e, studyId);
        }

        String breadcrumbsTitle = BreadcrumbsService.RESULTS;
        String breadcrumbs = breadcrumbsService.generateForGroup(study, groupResult.getBatch(), groupResult,
                breadcrumbsTitle);
        String dataUrl = controllers.gui.routes.StudyResults.tableDataByGroup(studyId, groupId).url();
        return ok(views.html.gui.result.studyResults.render(loggedInUser,
                breadcrumbs, Helpers.isLocalhost(), study, dataUrl));
    }

    /**
     * Shows view with all StudyResults of a worker.
     */
    @Transactional
    @Authenticated
    public Result workersStudyResults(Long workerId) throws JatosGuiException {
        User loggedInUser = authenticationService.getLoggedInUser();
        Worker worker = workerDao.findById(workerId);
        try {
            checker.checkWorker(worker, workerId);
        } catch (BadRequestException e) {
            jatosGuiExceptionThrower.throwRedirect(e, controllers.gui.routes.Home.home());
        }

        String breadcrumbs = breadcrumbsService.generateForWorker(worker, BreadcrumbsService.RESULTS);
        return ok(views.html.gui.result.workersStudyResults.render(loggedInUser,
                breadcrumbs, Helpers.isLocalhost(), worker));
    }

    /**
     * POST request that removes all StudyResults specified in the parameter. The parameter is a comma separated list of
     * StudyResults IDs as a String. Removing a StudyResult always removes it's ComponentResults.
     */
    @Transactional
    @Authenticated
    public Result remove() throws JatosGuiException {
        User loggedInUser = authenticationService.getLoggedInUser();
        List<Long> studyResultIdList = new ArrayList<>();
        request().body().asJson().get("resultIds").forEach(node -> studyResultIdList.add(node.asLong()));
        try {
            resultRemover.removeStudyResults(studyResultIdList, loggedInUser);
        } catch (ForbiddenException | BadRequestException | NotFoundException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }
        return ok(" "); // jQuery.ajax cannot handle empty responses
    }

    /**
     * GET request that returns StudyResults of a study in JSON format. It streams in chunks (reduces memory usage)
     */
    @Transactional
    @Authenticated
    public Result tableDataByStudy(Long studyId) throws JatosGuiException {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }

        Source<ByteString, ?> source = resultService.streamStudyResultsByStudy(study);
        return ok().chunked(source).as("text/html; charset=utf-8");
    }

    /**
     * GET that returns all StudyResults of a Batch in JSON format. As an additional parameter the worker type can
     * be specified and the results will only be of this type. It streams in chunks (reduces memory usage)
     */
    @Transactional
    @Authenticated
    public Result tableDataByBatch(Long studyId, Long batchId, Option<String> workerType) throws JatosGuiException {
        Study study = studyDao.findById(studyId);
        Batch batch = batchDao.findById(batchId);
        User loggedInUser = authenticationService.getLoggedInUser();
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStandardForBatch(batch, study, batchId);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }

        Source<ByteString, ?> source = resultService.streamStudyResultsByBatch(workerType, batch);
        return ok().chunked(source).as("text/html; charset=utf-8");
    }

    /**
     * GET request that returns all StudyResults of a group in JSON format. It streams in chunks (reduces memory usage)
     */
    @Transactional
    @Authenticated
    public Result tableDataByGroup(Long studyId, Long groupResultId) throws JatosGuiException {
        Study study = studyDao.findById(studyId);
        GroupResult groupResult = groupResultDao.findById(groupResultId);
        User loggedInUser = authenticationService.getLoggedInUser();
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStandardForGroup(groupResult, study, groupResultId);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }

        Source<ByteString, ?> source = resultService.streamStudyResultsByGroup(groupResult);
        return ok().chunked(source).as("text/html; charset=utf-8");
    }

    /**
     * GET request that returns all StudyResults belonging to a worker as JSON. Streams in chunks (reduces memory usage)
     */
    @Transactional
    @Authenticated
    public Result tableDataByWorker(Long workerId) throws JatosGuiException {
        User loggedInUser = authenticationService.getLoggedInUser();
        Worker worker = workerDao.findById(workerId);
        try {
            checker.checkWorker(worker, workerId);
        } catch (BadRequestException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }

        Source<ByteString, ?> source = resultService.streamStudyResultsByWorker(loggedInUser, worker);
        return ok().chunked(source).as("text/html; charset=utf-8");
    }

    /**
     * Returns for one study result the component result's data
     */
    @Transactional
    @Authenticated
    public Result tableDataComponentResultByStudyResult(Long studyResultId) throws JatosGuiException {
        StudyResult studyResult = studyResultDao.findById(studyResultId);
        Study study = studyResult.getStudy();
        User loggedInUser = authenticationService.getLoggedInUser();
        try {
            checker.checkStandardForStudy(study, study.getId(), loggedInUser);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }

        return ok(jsonUtils.getComponentResultsByStudyResult(studyResult));
    }

}
