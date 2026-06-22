package controllers.gui;

import actions.common.AsyncAction.Async;
import actions.common.TransactionalAction.Transactional;
import http.common.Http.Context;
import actions.common.AsyncAction.Executor;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import auth.gui.AuthAction.Auth;
import com.google.common.base.Strings;
import daos.common.BatchDao;
import daos.common.GroupResultDao;
import daos.common.StudyDao;
import daos.common.StudyResultDao;
import daos.common.worker.WorkerDao;
import daos.common.worker.WorkerType;
import exceptions.common.BadRequestException;
import exceptions.common.ForbiddenException;
import exceptions.common.NotFoundException;
import models.common.*;
import models.common.workers.Worker;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.gui.*;
import json.common.JsonUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

import static auth.gui.AuthAction.SIGNEDIN_USER;
import static controllers.gui.actionannotations.SaveLastVisitedPageUrlAction.SaveLastVisitedPageUrl;
import static models.common.User.Role.USER;
import static models.common.User.Role.VIEWER;
import static messaging.common.FlashMessagingHelper.ERROR;

/**
 * Controller for actions around StudyResults in the JATOS GUI.
 */
@Singleton
public class StudyResults extends Controller {

    private final AuthorizationService authorizationService;
    private final BreadcrumbsService breadcrumbsService;
    private final ResultRemover resultRemover;
    private final ResultStreamer resultStreamer;
    private final StudyDao studyDao;
    private final BatchDao batchDao;
    private final StudyResultDao studyResultDao;
    private final GroupResultDao groupResultDao;
    private final WorkerDao workerDao;
    private final JsonUtils jsonUtils;

    @Inject
    StudyResults(AuthorizationService authorizationService,
                 BreadcrumbsService breadcrumbsService,
                 ResultRemover resultRemover,
                 ResultStreamer resultStreamer,
                 StudyDao studyDao, BatchDao batchDao,
                 StudyResultDao studyResultDao,
                 GroupResultDao groupResultDao,
                 WorkerDao workerDao,
                 JsonUtils jsonUtils) {
        this.authorizationService = authorizationService;
        this.breadcrumbsService = breadcrumbsService;
        this.resultRemover = resultRemover;
        this.resultStreamer = resultStreamer;
        this.studyDao = studyDao;
        this.batchDao = batchDao;
        this.studyResultDao = studyResultDao;
        this.groupResultDao = groupResultDao;
        this.workerDao = workerDao;
        this.jsonUtils = jsonUtils;
    }

    /**
     * Shows the study results view
     */
    @Async(Executor.IO)
    @Auth(roles = {VIEWER, USER})
    @SaveLastVisitedPageUrl
    public Result studysStudyResults(Http.Request request, Long studyId) {
        Study study = studyDao.findById(studyId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        try {
            authorizationService.canUserAccessStudy(study, signedinUser);
        } catch (ForbiddenException | NotFoundException e) {
            Context.current().response().putFlash(ERROR, e.getMessage());
            return redirect(routes.Studies.study(studyId, e.getHttpStatus()));
        }

        String breadcrumbs = breadcrumbsService.generateForStudy(study, BreadcrumbsService.RESULTS);
        String dataUrl = controllers.gui.routes.StudyResults.tableDataByStudy(study.getId()).url();
        return ok(views.html.gui.results.studyResults.render(signedinUser, breadcrumbs, study, dataUrl, request.asScala()));
    }

    /**
     * Shows view with all StudyResults of a batch.
     */
    @Async(Executor.IO)
    @Auth(roles = {VIEWER, USER})
    @SaveLastVisitedPageUrl
    public Result batchesStudyResults(Http.Request request, Long studyId, Long batchId, String workerType) {
        Batch batch = batchDao.findById(batchId);
        Study study = studyDao.findById(studyId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        try {
            authorizationService.canUserAccessStudy(study, signedinUser);
            authorizationService.canUserAccessBatch(batch, signedinUser);
        } catch (ForbiddenException | NotFoundException e) {
            Context.current().response().putFlash(ERROR, e.getMessage());
            return redirect(routes.Studies.study(studyId, e.getHttpStatus()));
        }

        String breadcrumbsTitle = Strings.isNullOrEmpty(workerType) ? BreadcrumbsService.RESULTS
                : BreadcrumbsService.RESULTS + " of type " + WorkerType.fromWireValue(workerType).uiValue();
        String breadcrumbs = breadcrumbsService.generateForBatch(study, batch, breadcrumbsTitle);
        String dataUrl = controllers.gui.routes.StudyResults.tableDataByBatch(batchId, workerType).url();
        return ok(views.html.gui.results.studyResults.render(signedinUser, breadcrumbs, study, dataUrl, request.asScala()));
    }

    /**
     * Shows view with all StudyResults of a group.
     */
    @Async(Executor.IO)
    @Auth(roles = {VIEWER, USER})
    @SaveLastVisitedPageUrl
    public Result groupsStudyResults(Http.Request request, Long studyId, Long groupId) {
        Study study = studyDao.findById(studyId);
        GroupResult groupResult = groupResultDao.findById(groupId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        try {
            authorizationService.canUserAccessStudy(study, signedinUser);
            authorizationService.canUserAccessGroupResult(groupResult, signedinUser);
        } catch (ForbiddenException | NotFoundException e) {
            Context.current().response().putFlash(ERROR, e.getMessage());
            return redirect(routes.Studies.study(studyId, e.getHttpStatus()));
        }

        String breadcrumbsTitle = BreadcrumbsService.RESULTS;
        String breadcrumbs = breadcrumbsService.generateForGroup(study, groupResult.getBatch(), groupResult,
                breadcrumbsTitle);
        String dataUrl = controllers.gui.routes.StudyResults.tableDataByGroup(groupId).url();
        return ok(views.html.gui.results.studyResults.render(signedinUser, breadcrumbs, study, dataUrl, request.asScala()));
    }

    /**
     * Shows view with all StudyResults of a worker.
     */
    @Async(Executor.IO)
    @Auth(roles = {VIEWER, USER})
    @SaveLastVisitedPageUrl
    public Result workersStudyResults(Http.Request request, Long workerId) {
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        Worker worker = workerDao.findById(workerId);
        try {
            authorizationService.canUserAccessWorker(signedinUser, worker);
        } catch (NotFoundException | ForbiddenException e) {
            Context.current().response().putFlash(ERROR, e.getMessage());
            return redirect(routes.Home.home(e.getHttpStatus()));
        }

        String breadcrumbs = breadcrumbsService.generateForWorker(worker, BreadcrumbsService.RESULTS);
        return ok(views.html.gui.results.workersStudyResults.render(signedinUser, breadcrumbs, worker, request.asScala()));
    }

    /**
     * POST request that removes all StudyResults specified in the parameter. The parameter is a comma separated list of
     * StudyResults IDs as a String. Removing a StudyResult always removes it's ComponentResults.
     */
    @Async(Executor.IO)
    @Auth(roles = USER)
    public Result remove(Http.Request request) {
        if (request.body().asJson() == null) return badRequest("Malformed request body");
        if (!request.body().asJson().has("studyResultIds")) return badRequest("Malformed JSON");

        List<Long> studyResultIdList = new ArrayList<>();
        request.body().asJson().get("studyResultIds").forEach(node -> studyResultIdList.add(node.asLong()));
        resultRemover.removeStudyResults(studyResultIdList);

        return ok();
    }

    /**
     * GET request that returns StudyResults of a study in JSON format. It streams in chunks (reduces memory usage)
     */
    @Async(Executor.IO)
    @Auth(roles = {VIEWER, USER})
    public Result tableDataByStudy(Long studyId) throws ForbiddenException, NotFoundException {
        Study study = studyDao.findById(studyId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessStudy(study, signedinUser);

        Source<ByteString, ?> source = resultStreamer.streamStudyResultsByStudy(study);
        return ok().chunked(source).as("text/plain; charset=utf-8");
    }

    /**
     * GET that returns all StudyResults of a Batch in JSON format. As an additional parameter the worker type can be
     * specified and the results will only be of this type. It streams in chunks (reduces memory usage)
     */
    @Async(Executor.IO)
    @Auth(roles = {VIEWER, USER})
    public Result tableDataByBatch(Long batchId, String workerType) throws ForbiddenException, NotFoundException, BadRequestException {
        Batch batch = batchDao.findById(batchId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessBatch(batch, signedinUser);
        WorkerType wt = WorkerType.fromWireValueInclNone(workerType);

        Source<ByteString, ?> source = resultStreamer.streamStudyResultsByBatch(wt, batch);
        return ok().chunked(source).as("text/plain; charset=utf-8");
    }

    /**
     * GET request that returns all StudyResults of a group in JSON format. It streams in chunks (reduces memory usage)
     */
    @Async(Executor.IO)
    @Auth(roles = {VIEWER, USER})
    public Result tableDataByGroup(Long groupResultId) throws ForbiddenException, NotFoundException {
        GroupResult groupResult = groupResultDao.findById(groupResultId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessGroupResult(groupResult, signedinUser);

        Source<ByteString, ?> source = resultStreamer.streamStudyResultsByGroup(groupResult);
        return ok().chunked(source).as("text/plain; charset=utf-8");
    }

    /**
     * GET request that returns all StudyResults belonging to a worker as JSON. Streams in chunks (reduces memory
     * usage)
     */
    @Async(Executor.IO)
    @Auth(roles = {VIEWER, USER})
    public Result tableDataByWorker(Long workerId) throws BadRequestException {
        Worker worker = workerDao.findById(workerId);
        if (worker == null) {
            throw new NotFoundException("Worker doesn't exist");
        }

        Source<ByteString, ?> source = resultStreamer.streamStudyResultsByWorker(worker);
        return ok().chunked(source).as("text/plain; charset=utf-8");
    }

    /**
     * Returns for one study result the component result's data
     */
    @Async(Executor.IO)
    @Transactional
    @Auth(roles = {VIEWER, USER})
    public Result tableDataComponentResultsByStudyResult(Long studyResultId) throws ForbiddenException, NotFoundException {
        StudyResult studyResult = studyResultDao.findById(studyResultId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessStudyResult(studyResult, signedinUser, false);

        return ok(jsonUtils.getComponentResultsByStudyResult(studyResult));
    }

}
