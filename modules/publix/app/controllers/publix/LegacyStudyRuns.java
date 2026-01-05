package controllers.publix;

import actions.common.AsyncAction.Async;
import actions.common.AsyncAction.Executor;
import daos.common.BatchDao;
import daos.common.StudyDao;
import daos.common.StudyLinkDao;
import daos.common.worker.WorkerDao;
import daos.common.worker.WorkerType;
import exceptions.common.BadRequestException;
import models.common.Batch;
import models.common.Study;
import models.common.StudyLink;
import models.common.workers.Worker;
import play.Logger;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import utils.common.Helpers;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;

/**
 * This class deals with legacy style study run links and translates them to study links.
 *
 * Personal workers (PersonalSingle and PersonalMultiple): e.g. /publix/1/start?batchId=1&personalSingleWorkerId=32454
 * Worker was pre-created: look up worker and batch in the database, create StudyLink, redirect to the new study link
 *
 * General workers (GeneralSingle, GeneralMultiple, MT, MTSandbox): e.g. /publix/1/start?batchId=1&generalSingle Get
 * StudyLink or if it does not yet exist, create it. Then redirect to the new study link.
 *
 * @author Kristian Lange
 */
@Singleton
public class LegacyStudyRuns extends Controller {

    private static final Logger.ALogger LOGGER = Logger.of(LegacyStudyRuns.class);

    private final StudyDao studyDao;
    private final BatchDao batchDao;
    private final WorkerDao workerDao;
    private final StudyLinkDao studyLinkDao;

    @Inject
    public LegacyStudyRuns(StudyDao studyDao, BatchDao batchDao, WorkerDao workerDao, StudyLinkDao studyLinkDao) {
        this.studyDao = studyDao;
        this.batchDao = batchDao;
        this.workerDao = workerDao;
        this.studyLinkDao = studyLinkDao;
    }

    @Async(Executor.IO)
    public Result transformToStudyLink(Http.Request request, Long studyId, Long batchId) {
        LOGGER.info(".transformToStudyLink: studyId " + studyId + ", batchId " + batchId);
        Study study = studyDao.findById(studyId);
        Batch batch = batchDao.findById(batchId);
        if (study == null || batch == null || !batch.getStudy().getId().equals(studyId)) {
            throw new BadRequestException("Unknown study or batch");
        }
        WorkerType workerType = getWorkerTypeFromQuery(request);

        StudyLink studyLink;
        switch (workerType) {
            case PERSONAL_SINGLE:
            case PERSONAL_MULTIPLE:
                studyLink = getStudyLink(request, batch, workerType);
                break;
            case GENERAL_SINGLE:
            case GENERAL_MULTIPLE:
            case MT:
            case MT_SANDBOX:
                studyLink = studyLinkDao.findFirstByBatchAndWorkerType(batch, workerType)
                        .orElseGet(() -> studyLinkDao.persist(new StudyLink(batch, workerType)));
                break;
            default:
                throw new BadRequestException("Unknown worker type");

        }
        return redirect(routes.PublixInterceptor.run(studyLink.getStudyCode()).url() + Helpers.getQueryString(request));
    }

    /**
     * Checks the request's query string which type of worker is doing the study. Returns a String specifying the worker
     * type. Before a study is started, the worker type is specified via a parameter in the query string.
     */
    private WorkerType getWorkerTypeFromQuery(Http.Request request) {
        // Check for Personal Single Worker
        String personalSingleWorkerId = Helpers.getQueryParameter(request, "personalSingleWorkerId");
        if (personalSingleWorkerId != null) {
            return WorkerType.PERSONAL_SINGLE;
        }
        // Check for Personal Multiple Worker
        String pmWorkerId = Helpers.getQueryParameter(request, "personalMultipleWorkerId");
        if (pmWorkerId != null) {
            return WorkerType.PERSONAL_MULTIPLE;
        }
        // Check for General Single Worker
        String generalSingle = Helpers.getQueryParameter(request, "generalSingle");
        if (generalSingle != null) {
            return WorkerType.GENERAL_SINGLE;
        }
        // Check for General Multiple Worker
        String generalMultiple = Helpers.getQueryParameter(request, "generalMultiple");
        if (generalMultiple != null) {
            return WorkerType.GENERAL_MULTIPLE;
        }
        // Check for MT worker and MT Sandbox worker
        String mtWorkerId = Helpers.getQueryParameter(request, "workerId");
        String mtAssignmentId = Helpers.getQueryParameter(request, "assignmentId");
        if (mtWorkerId != null && mtAssignmentId != null) {
            return retrieveMTWorkerType(request);
        }
        // Check for MT (sandbox) requester preview link
        String mtMode = Helpers.getQueryParameter(request, "mode");
        if ("requester-preview".equals(mtMode)) {
            throw new BadRequestException("You cannot start a study from a MTurk requester preview");
        }
        throw new BadRequestException("Unknown worker type");
    }

    /**
     * Returns either MTSandboxWorker.WORKER_TYPE or MTWorker.WORKER_TYPE. It depends on the URL query string. If the
     * query has the key turkSubmitTo and its value contains 'sandbox,' it returns the MTSandboxWorker one - and
     * MTWorker one otherwise.
     */
    private WorkerType retrieveMTWorkerType(Http.Request request) {
        Optional<String> turkSubmitTo = request.queryString("turkSubmitTo");
        if (turkSubmitTo.isPresent() && turkSubmitTo.get().toLowerCase().contains("sandbox")) {
            return WorkerType.MT_SANDBOX;
        } else {
            return WorkerType.MT;
        }
    }

    /**
     * Get StudyLink for PersonalSingle worker and PersonalMultiple worker
     */
    private StudyLink getStudyLink(Http.Request request, Batch batch, WorkerType workerType) {
        Worker worker = fetchWorker(request, workerType);
        if (!worker.hasBatch(batch)) throw new BadRequestException("Worker doesn't belong to batch");
        return studyLinkDao.findByBatchAndWorker(batch, worker).orElseGet(() -> createStudyLink(batch, worker));
    }

    /**
     * Gets Worker with the ID given in the request's URL query string from the database
     */
    private Worker fetchWorker(Http.Request request, WorkerType workerType) {
        String workerIdStr;
        switch (workerType) {
            case PERSONAL_SINGLE:
                workerIdStr = Helpers.getQueryParameter(request, "personalSingleWorkerId");
                break;
            case PERSONAL_MULTIPLE:
                workerIdStr = Helpers.getQueryParameter(request, "personalMultipleWorkerId");
                break;
            default:
                throw new BadRequestException("Unknown worker type");
        }

        long workerId;
        try {
            workerId = Long.parseLong(workerIdStr);
        } catch (NumberFormatException e) {
            throw new BadRequestException("Couldn't parse worker ID from URL query string");
        }
        Worker worker = workerDao.findById(workerId);
        if (worker.getWorkerType() != workerType) {
            throw new BadRequestException("Worker not of type " + workerType);
        }
        return worker;
    }

    private StudyLink createStudyLink(Batch batch, Worker worker) {
        StudyLink studyLink = new StudyLink(batch, worker);
        studyLinkDao.persist(studyLink);
        return studyLink;
    }

}
