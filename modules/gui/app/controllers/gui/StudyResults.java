package controllers.gui;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.Status;
import akka.stream.OverflowStrategy;
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
import models.common.Batch;
import models.common.GroupResult;
import models.common.Study;
import models.common.User;
import models.common.workers.MTSandboxWorker;
import models.common.workers.MTWorker;
import models.common.workers.Worker;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Result;
import scala.Option;
import services.gui.*;
import utils.common.HttpUtils;
import utils.common.JsonUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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
    private final GroupResultDao groupResultDao;
    private final WorkerDao workerDao;
    private final StudyResultDao studyResultDao;

    @Inject
    StudyResults(JatosGuiExceptionThrower jatosGuiExceptionThrower,
            Checker checker, AuthenticationService authenticationService,
            BreadcrumbsService breadcrumbsService, ResultRemover resultRemover,
            ResultService resultService, StudyDao studyDao, BatchDao batchDao,
            GroupResultDao groupResultDao, WorkerDao workerDao, StudyResultDao studyResultDao) {
        this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
        this.checker = checker;
        this.authenticationService = authenticationService;
        this.breadcrumbsService = breadcrumbsService;
        this.resultRemover = resultRemover;
        this.resultService = resultService;
        this.studyDao = studyDao;
        this.batchDao = batchDao;
        this.groupResultDao = groupResultDao;
        this.workerDao = workerDao;
        this.studyResultDao = studyResultDao;
    }

    /**
     * Shows view with all StudyResults of a study. Allows to specify the max number of results.
     */
    @Transactional
    @Authenticated
    public Result studysStudyResults(Long studyId, Option<Integer> max) throws JatosGuiException {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwStudy(e, study.getId());
        }

        String breadcrumbs = breadcrumbsService.generateForStudy(study, BreadcrumbsService.RESULTS);
        String dataUrl = controllers.gui.routes.StudyResults.tableDataByStudy(study.getId(), max).url();
        return ok(views.html.gui.result.studyResults
                .render(loggedInUser, breadcrumbs, HttpUtils.isLocalhost(), study, dataUrl));
    }

    /**
     * Shows view with all StudyResults of a batch. Allows to specify the max number of results.
     */
    @Transactional
    @Authenticated
    public Result batchesStudyResults(Long studyId, Long batchId, Option<String> workerType, Option<Integer> max)
            throws JatosGuiException {
        Batch batch = batchDao.findById(batchId);
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStandardForBatch(batch, study, batchId);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwStudy(e, study.getId());
        }

        String breadcrumbsTitle = workerType.isEmpty() ? BreadcrumbsService.RESULTS
                : BreadcrumbsService.RESULTS + " of " + Worker.getUIWorkerType(workerType.get()) + " workers";
        String breadcrumbs = breadcrumbsService.generateForBatch(study, batch, breadcrumbsTitle);
        String dataUrl = controllers.gui.routes.StudyResults.tableDataByBatch(study.getId(), batch.getId(), workerType,
                max).url();
        return ok(views.html.gui.result.studyResults.render(loggedInUser,
                breadcrumbs, HttpUtils.isLocalhost(), study, dataUrl));
    }

    /**
     * Shows view with all StudyResults of a group. Allows to specify the max number of results.
     */
    @Transactional
    @Authenticated
    public Result groupsStudyResults(Long studyId, Long groupId, Option<Integer> max) throws JatosGuiException {
        Study study = studyDao.findById(studyId);
        GroupResult groupResult = groupResultDao.findById(groupId);
        User loggedInUser = authenticationService.getLoggedInUser();
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStandardForGroup(groupResult, study, groupId);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwStudy(e, study.getId());
        }

        String breadcrumbsTitle = BreadcrumbsService.RESULTS;
        String breadcrumbs = breadcrumbsService.generateForGroup(study, groupResult.getBatch(), groupResult,
                breadcrumbsTitle);
        String dataUrl = controllers.gui.routes.StudyResults.tableDataByGroup(study.getId(), groupResult.getId(), max)
                .url();
        return ok(views.html.gui.result.studyResults.render(loggedInUser,
                breadcrumbs, HttpUtils.isLocalhost(), study, dataUrl));
    }

    /**
     * Shows view with all StudyResults of a worker. Allows to specify the max number of results.
     */
    @Transactional
    @Authenticated
    public Result workersStudyResults(Long workerId, Option<Integer> max) throws JatosGuiException {
        User loggedInUser = authenticationService.getLoggedInUser();
        Worker worker = workerDao.findById(workerId);
        try {
            checker.checkWorker(worker, workerId);
        } catch (BadRequestException e) {
            jatosGuiExceptionThrower.throwRedirect(e, controllers.gui.routes.Home.home());
        }

        String breadcrumbs = breadcrumbsService.generateForWorker(worker, BreadcrumbsService.RESULTS);
        return ok(views.html.gui.result.workersStudyResults.render(loggedInUser,
                breadcrumbs, HttpUtils.isLocalhost(), worker, max));
    }

    /**
     * Ajax POST request
     * <p>
     * Removes all StudyResults specified in the parameter. The parameter is a comma separated list of StudyResults
     * IDs as a String. Removing a StudyResult always removes it's ComponentResults.
     */
    @Transactional
    @Authenticated
    public Result remove() throws JatosGuiException {
        User loggedInUser = authenticationService.getLoggedInUser();
        List<Long> studyResultIdList = new ArrayList<>();
        request().body().asJson().get("resultIds").forEach(node -> studyResultIdList.add(node.asLong()));
        try {
            resultRemover.removeStudyResults(studyResultIdList, loggedInUser);
        } catch (ForbiddenException | BadRequestException | NotFoundException | IOException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }
        return ok(" "); // jQuery.ajax cannot handle empty responses
    }

    /**
     * Ajax request with chunked streaming (reduces memory usage)
     *
     * Returns StudyResults of a study in JSON format. It gets up to 'max' results - or if 'max' is undefined it
     * gets all. If their is a problem during retrieval it returns nothing.
     */
    @Transactional
    @Authenticated
    public Result tableDataByStudy(Long studyId, Option<Integer> max) throws JatosGuiException {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }

        int resultCount = studyResultDao.countByStudy(study);
        int bufferSize = max.isDefined() ? max.get() : resultCount;
        Source<ByteString, ?> source = Source.<ByteString>actorRef(bufferSize, OverflowStrategy.fail())
                .mapMaterializedValue(sourceActor -> {
                    CompletableFuture.runAsync(() -> {
                        resultService.fetchStudyResultsAndWriteIntoActor(sourceActor, loggedInUser, max,
                                () -> studyResultDao.findAllByStudyScrollable(study));
                        sourceActor.tell(new Status.Success(NotUsed.getInstance()), ActorRef.noSender());
                    });
                    return sourceActor;
                });
        return ok().chunked(source).as("text/html; charset=utf-8");
    }

    /**
     * Ajax request with chunked streaming (reduces memory usage)
     *
     * Returns all StudyResults of a batch in JSON format. As an additional parameter the worker type can
     * be specified and the results will only be of this type. It gets up to 'max' results - or if 'max' is undefined it
     * gets all. If their is a problem during retrieval it returns nothing.
     */
    @Transactional
    @Authenticated
    public Result tableDataByBatch(Long studyId, Long batchId, Option<String> workerType, Option<Integer> max)
            throws JatosGuiException {
        Study study = studyDao.findById(studyId);
        Batch batch = batchDao.findById(batchId);
        User loggedInUser = authenticationService.getLoggedInUser();
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStandardForBatch(batch, study, batchId);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }

        Source<ByteString, ?> source;
        if (workerType.isEmpty()) {
            int resultCount = studyResultDao.countByBatch(batch);
            int bufferSize = max.isDefined() ? max.get() : resultCount;
            source = Source.<ByteString>actorRef(bufferSize, OverflowStrategy.fail())
                    .mapMaterializedValue(sourceActor -> {
                        CompletableFuture.runAsync(() -> {
                            resultService.fetchStudyResultsAndWriteIntoActor(sourceActor, loggedInUser, max,
                                    () -> studyResultDao.findAllByBatchScrollable(batch));

                            sourceActor.tell(new Status.Success(NotUsed.getInstance()), ActorRef.noSender());
                        });
                        return sourceActor;
                    });
        } else {
            int resultCount = studyResultDao.countByBatchAndWorkerType(batch, workerType.get());
            int bufferSize = max.isDefined() ? max.get() : resultCount;
            source = Source.<ByteString>actorRef(bufferSize, OverflowStrategy.fail())
                    .mapMaterializedValue(sourceActor -> {
                        CompletableFuture.runAsync(() -> {
                            resultService.fetchStudyResultsAndWriteIntoActor(sourceActor, loggedInUser, max,
                                    () -> studyResultDao
                                            .findAllByBatchAndWorkerTypeScrollable(batch, workerType.get()));
                            // If worker type is MT then add MTSandbox on top
                            if (MTWorker.WORKER_TYPE.equals(workerType.get())) {
                                resultService.fetchStudyResultsAndWriteIntoActor(sourceActor, loggedInUser, max,
                                        () -> studyResultDao.findAllByBatchAndWorkerTypeScrollable(batch,
                                                MTSandboxWorker.WORKER_TYPE));
                            }
                            sourceActor.tell(new Status.Success(NotUsed.getInstance()), ActorRef.noSender());
                        });
                        return sourceActor;
                    });
        }

        return ok().chunked(source).as("text/html; charset=utf-8");
    }

    /**
     * Ajax request with chunked streaming (reduces memory usage)
     *
     * Returns all StudyResults of a group in JSON format. It gets up to 'max' results - or if 'max' is undefined it
     * gets all. If their is a problem during retrieval it returns nothing.
     */
    @Transactional
    @Authenticated
    public Result tableDataByGroup(Long studyId, Long groupResultId, Option<Integer> max) throws JatosGuiException {
        Study study = studyDao.findById(studyId);
        GroupResult groupResult = groupResultDao.findById(groupResultId);
        User loggedInUser = authenticationService.getLoggedInUser();
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStandardForGroup(groupResult, study, groupResultId);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }

        int resultCount = studyResultDao.countByGroup(groupResult);
        int bufferSize = max.isDefined() ? max.get() : resultCount;
        Source<ByteString, ?> source = Source.<ByteString>actorRef(bufferSize, OverflowStrategy.fail())
                .mapMaterializedValue(sourceActor -> {
                    CompletableFuture.runAsync(() -> {
                        resultService.fetchStudyResultsAndWriteIntoActor(sourceActor, loggedInUser, max,
                                () -> studyResultDao.findAllByGroupScrollable(groupResult));
                        sourceActor.tell(new Status.Success(NotUsed.getInstance()), ActorRef.noSender());
                    });
                    return sourceActor;
                });
        return ok().chunked(source).as("text/html; charset=utf-8");
    }

    /**
     * Ajax request with chunked streaming (reduces memory usage)
     *
     * Returns all StudyResults belonging to a worker as JSON. It gets up to 'max' results - or if 'max' is undefined it
     * gets all. If their is a problem during retrieval it returns nothing.
     */
    @Transactional
    @Authenticated
    public Result tableDataByWorker(Long workerId, Option<Integer> max) throws JatosGuiException {
        User loggedInUser = authenticationService.getLoggedInUser();
        Worker worker = workerDao.findById(workerId);
        try {
            checker.checkWorker(worker, workerId);
        } catch (BadRequestException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }

        int resultCount = studyResultDao.countByWorker(worker);
        int bufferSize = max.isDefined() ? max.get() : resultCount;
        Source<ByteString, ?> source = Source.<ByteString>actorRef(bufferSize, OverflowStrategy.fail())
                .mapMaterializedValue(sourceActor -> {
                    CompletableFuture.runAsync(() -> {
                        resultService.fetchStudyResultsAndWriteIntoActor(sourceActor, loggedInUser, max,
                                () -> studyResultDao.findAllByWorkerScrollable(worker));

                        sourceActor.tell(new Status.Success(NotUsed.getInstance()), ActorRef.noSender());
                    });
                    return sourceActor;
                });
        return ok().chunked(source).as("text/html; charset=utf-8");
    }

    /**
     * Ajax request
     *
     * Returns the last 5 finished and unfinished StudyResultStatus as JSON
     */
    @Transactional
    @Authenticated(User.Role.ADMIN)
    public Result status() {
        return ok(JsonUtils.asJson(resultService.getStudyResultStatus()));
    }



}
