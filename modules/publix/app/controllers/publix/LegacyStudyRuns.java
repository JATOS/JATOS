package controllers.publix;

import controllers.publix.actionannotation.PublixAccessLoggingAction.PublixAccessLogging;
import daos.common.BatchDao;
import daos.common.StudyDao;
import daos.common.StudyLinkDao;
import daos.common.worker.WorkerDao;
import exceptions.publix.BadRequestPublixException;
import models.common.Batch;
import models.common.Study;
import models.common.StudyLink;
import models.common.workers.*;
import play.Logger;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import utils.common.Helpers;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class deals with legacy style study run links and translates them to study links.
 *
 * Personal workers (PersonalSingle and PersonalMultiple):
 * e.g. /publix/1/start?batchId=1&personalSingleWorkerId=32454
 * Worker was pre-created: look up worker and batch in the database, create StudyLink, redirect to new study link
 *
 * General workers (GeneralSingle, GeneralMultiple, MT, MTSandbox):
 * e.g. /publix/1/start?batchId=1&generalSingle
 * Get StudyLink or if it does not yet exist, create it. Then redirect to new study link.
 *
 * @author Kristian Lange
 */
@Singleton
@PublixAccessLogging
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

    @Transactional
    public Result transformToStudyLink(Http.Request request, Long studyId, Long batchId)
            throws BadRequestPublixException {
        LOGGER.info(".transformToStudyLink: studyId " + studyId + ", batchId" + batchId);
        Study study = studyDao.findById(studyId);
        Batch batch = batchDao.findById(batchId);
        if (study == null || batch == null || !batch.getStudy().getId().equals(studyId)) {
            throw new BadRequestPublixException("Unknown study or batch");
        }
        String workerType = getWorkerTypeFromQuery(request);

        StudyLink studyLink;
        switch (workerType) {
            case PersonalSingleWorker.WORKER_TYPE:
            case PersonalMultipleWorker.WORKER_TYPE:
                studyLink = getStudyLink(request, batch, workerType);
                break;
            case GeneralSingleWorker.WORKER_TYPE:
            case GeneralMultipleWorker.WORKER_TYPE:
            case MTWorker.WORKER_TYPE:
            case MTSandboxWorker.WORKER_TYPE:
                studyLink = studyLinkDao.findFirstByBatchAndWorkerType(batch, workerType)
                        .orElseGet(() -> studyLinkDao.create(new StudyLink(batch, workerType)));
                break;
            default:
                throw new BadRequestPublixException("Unknown worker type");

        }
        return redirect(routes.PublixInterceptor.run(studyLink.getId()).url() + Helpers.getQueryString(request));
    }

    /**
     * Checks the request's query string which type of worker is doing the
     * study. Returns a String specifying the worker type. Before a study is
     * started the worker type is specified via a parameter in the query string.
     */
    private String getWorkerTypeFromQuery(Http.Request request) throws BadRequestPublixException {
        // Check for Personal Single Worker
        String personalSingleWorkerId = Helpers.getQueryParameter(request, "personalSingleWorkerId");
        if (personalSingleWorkerId != null) {
            return PersonalSingleWorker.WORKER_TYPE;
        }
        // Check for Personal Multiple Worker
        String pmWorkerId = Helpers.getQueryParameter(request, "personalMultipleWorkerId");
        if (pmWorkerId != null) {
            return PersonalMultipleWorker.WORKER_TYPE;
        }
        // Check for General Single Worker
        String generalSingle = Helpers.getQueryParameter(request, "generalSingle");
        if (generalSingle != null) {
            return GeneralSingleWorker.WORKER_TYPE;
        }
        // Check for General Multiple Worker
        String generalMultiple = Helpers.getQueryParameter(request, "generalMultiple");
        if (generalMultiple != null) {
            return GeneralMultipleWorker.WORKER_TYPE;
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
            throw new BadRequestPublixException("You cannot start a study from a MTurk requester preview");
        }
        throw new BadRequestPublixException("Unknown worker type");
    }

    /**
     * Returns either MTSandboxWorker.WORKER_TYPE or MTWorker.WORKER_TYPE. It
     * depends on the URL query string. If the quera has the key turkSubmitTo
     * and it's value contains 'sandbox' it returns the MTSandboxWorker one -
     * and MTWorker one otherwise.
     */
    private String retrieveMTWorkerType(Http.Request request) {
        String turkSubmitTo = request.getQueryString("turkSubmitTo");
        if (turkSubmitTo != null && turkSubmitTo.toLowerCase().contains("sandbox")) {
            return MTSandboxWorker.WORKER_TYPE;
        } else {
            return MTWorker.WORKER_TYPE;
        }
    }

    /**
     * Get StudyLink for PersonalSingle worker and PersonalMultiple worker
     */
    private StudyLink getStudyLink(Http.Request request, Batch batch, String workerType)
            throws BadRequestPublixException {
        Worker worker = fetchWorker(request, workerType);
        if (!worker.hasBatch(batch)) throw new BadRequestPublixException("Worker doesn't belong to batch");
        return studyLinkDao.findByBatchAndWorker(batch, worker).orElseGet(() -> createStudyLink(batch, worker));
    }

    /**
     * Gets Worker with the ID given in the request's URL query string from the database
     */
    private Worker fetchWorker(Http.Request request, String workerType) throws BadRequestPublixException {
        String workerIdStr;
        switch (workerType) {
            case PersonalSingleWorker.WORKER_TYPE:
                workerIdStr = Helpers.getQueryParameter(request, "personalSingleWorkerId");
                break;
            case PersonalMultipleWorker.WORKER_TYPE:
                workerIdStr = Helpers.getQueryParameter(request, "personalMultipleWorkerId");
                break;
            default:
                throw new BadRequestPublixException("Unknown worker type");
        }

        long workerId;
        try {
            workerId = Long.parseLong(workerIdStr);
        } catch (NumberFormatException e) {
            throw new BadRequestPublixException("Couldn't parse worker ID from URL query string");
        }
        Worker worker = workerDao.findById(workerId);
        if (!(worker.getWorkerType().equals(workerType))) {
            throw new BadRequestPublixException("Worker not of type " + workerType);
        }
        return worker;
    }

    private StudyLink createStudyLink(Batch batch, Worker worker) {
        StudyLink studyLink = new StudyLink(batch, worker);
        studyLinkDao.create(studyLink);
        return studyLink;
    }

}
