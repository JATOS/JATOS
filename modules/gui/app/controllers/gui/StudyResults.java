package controllers.gui;

import akka.stream.javadsl.Source;
import akka.util.ByteString;
import auth.gui.AuthService;
import com.google.common.base.Strings;
import auth.gui.AuthAction.Auth;
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
import models.common.workers.*;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.gui.*;
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
@SuppressWarnings("deprecation")
@GuiAccessLogging
@Singleton
public class StudyResults extends Controller {

    private final JatosGuiExceptionThrower jatosGuiExceptionThrower;
    private final Checker checker;
    private final AuthService authService;
    private final BreadcrumbsService breadcrumbsService;
    private final ResultRemover resultRemover;
    private final ResultStreamer resultStreamer;
    private final WorkerService workerService;
    private final StudyDao studyDao;
    private final BatchDao batchDao;
    private final StudyResultDao studyResultDao;
    private final GroupResultDao groupResultDao;
    private final WorkerDao workerDao;
    private final JsonUtils jsonUtils;

    @Inject
    StudyResults(JatosGuiExceptionThrower jatosGuiExceptionThrower, Checker checker, AuthService authService,
            BreadcrumbsService breadcrumbsService, ResultRemover resultRemover,
            ResultStreamer resultStreamer, WorkerService workerService, StudyDao studyDao, BatchDao batchDao,
            StudyResultDao studyResultDao, GroupResultDao groupResultDao, WorkerDao workerDao, JsonUtils jsonUtils) {
        this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
        this.checker = checker;
        this.authService = authService;
        this.breadcrumbsService = breadcrumbsService;
        this.resultRemover = resultRemover;
        this.resultStreamer = resultStreamer;
        this.workerService = workerService;
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
    @Auth
    public Result studysStudyResults(Long studyId) throws JatosGuiException {
        Study study = studyDao.findById(studyId);
        User signedinUser = authService.getSignedinUser();
        try {
            checker.checkStandardForStudy(study, studyId, signedinUser);
        } catch (ForbiddenException | NotFoundException e) {
            jatosGuiExceptionThrower.throwStudy(e, studyId);
        }

        String breadcrumbs = breadcrumbsService.generateForStudy(study, BreadcrumbsService.RESULTS);
        String dataUrl = controllers.gui.routes.StudyResults.tableDataByStudy(study.getId()).url();
        return ok(views.html.gui.result.studyResults_new.render(signedinUser, breadcrumbs, study, dataUrl));
    }

    /**
     * Shows view with all StudyResults of a batch.
     */
    @Transactional
    @Auth
    public Result batchesStudyResults(Long studyId, Long batchId, String workerType) throws JatosGuiException {
        Batch batch = batchDao.findById(batchId);
        Study study = studyDao.findById(studyId);
        User signedinUser = authService.getSignedinUser();
        try {
            checker.checkStandardForStudy(study, studyId, signedinUser);
            checker.checkStandardForBatch(batch, study, batchId);
        } catch (ForbiddenException | NotFoundException e) {
            jatosGuiExceptionThrower.throwStudy(e, studyId);
        }

        String breadcrumbsTitle = Strings.isNullOrEmpty(workerType) ? BreadcrumbsService.RESULTS
                : BreadcrumbsService.RESULTS + " of " + Worker.getUIWorkerType(workerType) + " workers";
        String breadcrumbs = breadcrumbsService.generateForBatch(study, batch, breadcrumbsTitle);
        String dataUrl = controllers.gui.routes.StudyResults.tableDataByBatch(batchId, workerType).url();
        return ok(views.html.gui.result.studyResults_new.render(signedinUser, breadcrumbs, study, dataUrl));
    }

    /**
     * Shows view with all StudyResults of a group.
     */
    @Transactional
    @Auth
    public Result groupsStudyResults(Long studyId, Long groupId) throws JatosGuiException {
        Study study = studyDao.findById(studyId);
        GroupResult groupResult = groupResultDao.findById(groupId);
        User signedinUser = authService.getSignedinUser();
        try {
            checker.checkStandardForStudy(study, studyId, signedinUser);
            checker.checkStandardForGroup(groupResult, study, groupId);
        } catch (ForbiddenException | NotFoundException e) {
            jatosGuiExceptionThrower.throwStudy(e, studyId);
        }

        String breadcrumbsTitle = BreadcrumbsService.RESULTS;
        String breadcrumbs = breadcrumbsService.generateForGroup(study, groupResult.getBatch(), groupResult,
                breadcrumbsTitle);
        String dataUrl = controllers.gui.routes.StudyResults.tableDataByGroup(groupId).url();
        return ok(views.html.gui.result.studyResults_new.render(signedinUser, breadcrumbs, study, dataUrl));
    }

    /**
     * Shows view with all StudyResults of a worker.
     */
    @Transactional
    @Auth
    public Result workersStudyResults(Long workerId) throws JatosGuiException {
        User signedinUser = authService.getSignedinUser();
        Worker worker = workerDao.findById(workerId);
        try {
            checker.checkWorker(worker, workerId);
            checker.isUserAllowedToAccessWorker(signedinUser, worker);
        } catch (BadRequestException | ForbiddenException e) {
            jatosGuiExceptionThrower.throwHome(e);
        }

        String breadcrumbs = breadcrumbsService.generateForWorker(worker, BreadcrumbsService.RESULTS);
        return ok(views.html.gui.result.workersStudyResults_new.render(signedinUser, breadcrumbs, worker));
    }

    /**
     * POST request that removes all StudyResults specified in the parameter. The parameter is a comma separated list of
     * StudyResults IDs as a String. Removing a StudyResult always removes it's ComponentResults.
     */
    @Transactional
    @Auth
    public Result remove(Http.Request request) throws ForbiddenException, BadRequestException, NotFoundException {
        User signedinUser = authService.getSignedinUser();
        if (request.body().asJson() == null) return badRequest("Malformed request body");
        if (!request.body().asJson().has("studyResultIds")) return badRequest("Malformed JSON");

        List<Long> studyResultIdList = new ArrayList<>();
        request.body().asJson().get("studyResultIds").forEach(node -> studyResultIdList.add(node.asLong()));
        resultRemover.removeStudyResults(studyResultIdList, signedinUser);

        return ok();
    }

    /**
     * GET request that returns StudyResults of a study in JSON format. It streams in chunks (reduces memory usage)
     */
    @Transactional
    @Auth
    public Result tableDataByStudy(Long studyId) throws ForbiddenException, NotFoundException {
        Study study = studyDao.findById(studyId);
        User signedinUser = authService.getSignedinUser();
        checker.checkStandardForStudy(study, studyId, signedinUser);

        Source<ByteString, ?> source = resultStreamer.streamStudyResultsByStudy(study);
        return ok().chunked(source).as("text/plain; charset=utf-8");
    }

    /**
     * GET that returns all StudyResults of a Batch in JSON format. As an additional parameter the worker type can
     * be specified and the results will only be of this type. It streams in chunks (reduces memory usage)
     */
    @Transactional
    @Auth
    public Result tableDataByBatch(Long batchId, String workerType) throws ForbiddenException, NotFoundException, BadRequestException {
        Batch batch = batchDao.findById(batchId);
        User signedinUser = authService.getSignedinUser();
        checker.checkStandardForBatch(batch, batch.getId(), signedinUser);
        workerType = workerType != null ? workerService.extractWorkerType(workerType) : null;

        Source<ByteString, ?> source = resultStreamer.streamStudyResultsByBatch(workerType, batch);
        return ok().chunked(source).as("text/plain; charset=utf-8");
    }

    /**
     * GET request that returns all StudyResults of a group in JSON format. It streams in chunks (reduces memory usage)
     */
    @Transactional
    @Auth
    public Result tableDataByGroup(Long groupResultId) throws ForbiddenException, NotFoundException {
        GroupResult groupResult = groupResultDao.findById(groupResultId);
        User signedinUser = authService.getSignedinUser();
        checker.checkStandardForGroup(groupResult, groupResultId, signedinUser);

        Source<ByteString, ?> source = resultStreamer.streamStudyResultsByGroup(groupResult);
        return ok().chunked(source).as("text/plain; charset=utf-8");
    }

    /**
     * GET request that returns all StudyResults belonging to a worker as JSON. Streams in chunks (reduces memory usage)
     */
    @Transactional
    @Auth
    public Result tableDataByWorker(Long workerId) throws BadRequestException {
        User signedinUser = authService.getSignedinUser();
        Worker worker = workerDao.findById(workerId);
        checker.checkWorker(worker, workerId);

        Source<ByteString, ?> source = resultStreamer.streamStudyResultsByWorker(signedinUser, worker);
        return ok().chunked(source).as("text/plain; charset=utf-8");
    }

    /**
     * Returns for one study result the component result's data
     */
    @Transactional
    @Auth
    public Result tableDataComponentResultsByStudyResult(Long studyResultId) throws ForbiddenException, NotFoundException {
        StudyResult studyResult = studyResultDao.findById(studyResultId);
        User signedinUser = authService.getSignedinUser();
        checker.checkStudyResult(studyResult, signedinUser, false);

        return ok(jsonUtils.getComponentResultsByStudyResult(studyResult));
    }

}
