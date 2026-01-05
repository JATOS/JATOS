package controllers.gui;

import actions.common.AsyncAction.Async;
import general.common.Http.Context;
import actions.common.AsyncAction.Executor;
import auth.gui.AuthAction.Auth;
import com.fasterxml.jackson.databind.JsonNode;
import daos.common.*;
import daos.common.worker.WorkerDao;
import daos.common.worker.WorkerType;
import exceptions.common.BadRequestException;
import exceptions.common.ForbiddenException;
import messaging.common.RequestScopeMessaging;
import models.common.*;
import models.common.GroupResult.GroupState;
import models.common.workers.Worker;
import models.gui.BatchProperties;
import models.gui.BatchSession;
import models.gui.GroupSession;
import play.data.Form;
import play.data.FormFactory;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.gui.*;
import utils.common.JsonUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static auth.gui.AuthAction.SIGNEDIN_USER;
import static controllers.gui.actionannotations.SaveLastVisitedPageUrlAction.SaveLastVisitedPageUrl;

/**
 * Controller for all actions regarding study links, batches, and workers within the JATOS GUI.
 *
 * @author Kristian Lange
 */
@Singleton
public class StudyLinks extends Controller {

    private final Checker checker;
    private final JsonUtils jsonUtils;
    private final WorkerService workerService;
    private final BatchService batchService;
    private final GroupService groupService;
    private final BreadcrumbsService breadcrumbsService;
    private final StudyDao studyDao;
    private final BatchDao batchDao;
    private final WorkerDao workerDao;
    private final StudyResultDao studyResultDao;
    private final GroupResultDao groupResultDao;
    private final StudyLinkDao studyLinkDao;
    private final FormFactory formFactory;

    @Inject
    StudyLinks(Checker checker,
               JsonUtils jsonUtils,
               WorkerService workerService,
               BatchService batchService,
               GroupService groupService,
               BreadcrumbsService breadcrumbsService,
               StudyDao studyDao,
               BatchDao batchDao,
               WorkerDao workerDao,
               StudyResultDao studyResultDao,
               GroupResultDao groupResultDao,
               StudyLinkDao studyLinkDao,
               FormFactory formFactory) {
        this.checker = checker;
        this.jsonUtils = jsonUtils;
        this.workerService = workerService;
        this.batchService = batchService;
        this.groupService = groupService;
        this.breadcrumbsService = breadcrumbsService;
        this.studyDao = studyDao;
        this.batchDao = batchDao;
        this.workerDao = workerDao;
        this.studyResultDao = studyResultDao;
        this.groupResultDao = groupResultDao;
        this.studyLinkDao = studyLinkDao;
        this.formFactory = formFactory;
    }

    /**
     * GET request to get the Study Links page
     */
    @Async(Executor.IO)
    @Auth
    @SaveLastVisitedPageUrl
    public Result studyLinks(Http.Request request, Long studyId) {
        Study study = studyDao.findById(studyId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        checker.checkStandardForStudy(study, studyId, signedinUser);

        String breadcrumbs = breadcrumbsService.generateForStudy(study, BreadcrumbsService.STUDY_LINKS);
        return ok(views.html.gui.studyLinks.studyLinks.render(signedinUser, breadcrumbs, study, request.asScala()));
    }

    /**
     * GET request that returns Batch data belonging to the given study as JSON. It includes the count of its
     * StudyResults and GroupResults.
     */
    @Async(Executor.IO)
    @Auth
    public Result batchById(Long studyId, Long batchId) {
        Study study = studyDao.findById(studyId);
        Batch batch = batchDao.findById(batchId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        checker.checkStandardForStudy(study, studyId, signedinUser);
        checker.checkStandardForBatch(batch, study, batchId);

        Integer resultCount = studyResultDao.countByBatch(batch, WorkerType.JATOS);
        Integer groupCount = groupResultDao.countByBatch(batch);
        return ok(jsonUtils.getBatchByStudyForUI(batch, resultCount, groupCount));
    }

    /**
     * GET request that returns the data of all Batches of the given study as JSON. It includes the count of their
     * StudyResults, the count of their GroupResults, and the count of their Workers.
     */
    @Async(Executor.IO)
    @Auth
    public Result batchesByStudy(Long studyId) {
        Study study = studyDao.findByIdWithBatches(studyId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        checker.checkStandardForStudy(study, studyId, signedinUser);

        List<Batch> batchList = study.getBatchList();
        List<Integer> resultCountList = new ArrayList<>();
        batchList.forEach(batch -> resultCountList.add(studyResultDao.countByBatch(batch, WorkerType.JATOS)));
        List<Integer> groupCountList = new ArrayList<>();
        batchList.forEach(batch -> groupCountList.add(groupResultDao.countByBatch(batch)));
        return ok(jsonUtils.allBatchesByStudyForUI(batchList, resultCountList, groupCountList));
    }

    /**
     * GET request that returns data of all groups that belong to the given batch as JSON
     */
    @Async(Executor.IO)
    @Auth
    public Result groupsByBatch(Long studyId, Long batchId) {
        Study study = studyDao.findById(studyId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        Batch batch = batchDao.findById(batchId);
        checker.checkStandardForStudy(study, studyId, signedinUser);
        checker.checkStandardForBatch(batch, study, batchId);

        JsonNode dataAsJson = jsonUtils.allGroupResultsForUI(groupResultDao.findAllByBatch(batch));
        return ok(dataAsJson);
    }

    /**
     * POST request to submit a newly created Batch
     */
    @Async(Executor.IO)
    @Auth
    public Result submitCreatedBatch(Http.Request request, Long studyId) {
        Study study = studyDao.findById(studyId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        checker.checkStandardForStudy(study, studyId, signedinUser);
        checker.checkStudyLocked(study);

        Form<BatchProperties> form = formFactory.form(BatchProperties.class).bindFromRequest(request);
        if (form.hasErrors()) return badRequest(form.errorsAsJson());

        BatchProperties batchProperties = form.get();
        Batch batch = batchService.bindToBatch(batchProperties);

        batchService.createAndPersistBatch(batch, study);
        return ok(batch.getId().toString());
    }

    /**
     * GET request that returns the batch session data as String
     */
    @Async(Executor.IO)
    @Auth
    public Result batchSessionData(Long studyId, Long batchId) {
        Study study = studyDao.findById(studyId);
        Batch batch = batchDao.findById(batchId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        checker.checkStandardForStudy(study, studyId, signedinUser);
        checker.checkStandardForBatch(batch, study, batchId);

        BatchSession batchSession = batchService.bindToBatchSession(batch);
        return ok(JsonUtils.asJsonNode(batchSession));
    }

    /**
     * GET request that returns the group session data as String
     */
    @Async(Executor.IO)
    @Auth
    public Result groupSessionData(Long studyId, Long groupResultId) {
        Study study = studyDao.findById(studyId);
        GroupResult groupResult = groupResultDao.findById(groupResultId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        checker.checkStandardForStudy(study, studyId, signedinUser);
        checker.checkStandardForGroup(groupResult, study, groupResultId);

        GroupSession groupSession = groupService.bindToGroupSession(groupResult);
        return ok(JsonUtils.asJsonNode(groupSession));
    }

    /**
     * POST request to submit changed batch session data
     */
    @Async(Executor.IO)
    @Auth
    public Result submitEditedBatchSessionData(Http.Request request, Long studyId, Long batchId) {
        Study study = studyDao.findById(studyId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        Batch batch = batchDao.findById(batchId);
        checker.checkStandardForStudy(study, studyId, signedinUser);
        checker.checkStudyLocked(study);
        checker.checkStandardForBatch(batch, study, batchId);

        Form<BatchSession> form = formFactory.form(BatchSession.class).bindFromRequest(request);
        if (form.hasErrors()) return badRequest(form.errorsAsJson());

        BatchSession batchSession = form.get();
        boolean success = batchService.updateBatchSession(batch.getId(), batchSession);
        if (!success) {
            return forbidden("The Batch Session has been updated since you " +
                    "loaded this page. Reload before trying to save again.");
        }
        return ok();
    }

    /**
     * POST request to submit changed group session data
     */
    @Async(Executor.IO)
    @Auth
    public Result submitEditedGroupSessionData(Http.Request request, Long studyId, Long groupResultId) {
        Study study = studyDao.findById(studyId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        GroupResult groupResult = groupResultDao.findById(groupResultId);
        checker.checkStandardForStudy(study, studyId, signedinUser);
        checker.checkStudyLocked(study);
        checker.checkStandardForGroup(groupResult, study, groupResultId);

        Form<GroupSession> form = formFactory.form(GroupSession.class).bindFromRequest(request);
        if (form.hasErrors()) return badRequest(form.errorsAsJson());

        GroupSession groupSession = form.get();
        boolean success = groupService.updateGroupSession(groupResult.getId(), groupSession);
        if (!success) {
            return forbidden("The Group Session has been updated since you " +
                    "loaded this page. Reload before trying to save again.");
        }
        return ok();
    }

    /**
     * GET request to toggle the group state FIXED / STARTED
     */
    @Async(Executor.IO)
    @Auth
    public Result toggleGroupFixed(Long studyId, Long groupResultId, boolean fixed) {
        Study study = studyDao.findById(studyId);
        GroupResult groupResult = groupResultDao.findById(groupResultId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        checker.checkStandardForStudy(study, studyId, signedinUser);
        checker.checkStandardForGroup(groupResult, study, groupResultId);

        GroupState result = groupService.toggleGroupFixed(groupResult, fixed);
        return ok(JsonUtils.asJsonNode(result));
    }

    /**
     * GET request that returns Batch properties as JSON
     */
    @Async(Executor.IO)
    @Auth
    public Result batchProperties(Long studyId, Long batchId) {
        Study study = studyDao.findById(studyId);
        Batch batch = batchDao.findById(batchId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        checker.checkStandardForStudy(study, studyId, signedinUser);
        checker.checkStandardForBatch(batch, study, batchId);

        BatchProperties batchProperties = batchService.bindToProperties(batch);
        return ok(JsonUtils.asJsonNode(batchProperties));
    }

    /**
     * POST request to submit changed Batch properties
     */
    @Async(Executor.IO)
    @Auth
    public Result submitEditedBatchProperties(Http.Request request, Long studyId, Long batchId) {
        Study study = studyDao.findById(studyId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        Batch currentBatch = batchDao.findById(batchId);
        checker.checkStandardForStudy(study, studyId, signedinUser);
        checker.checkStudyLocked(study);
        checker.checkStandardForBatch(currentBatch, study, batchId);

        Form<BatchProperties> form = formFactory.form(BatchProperties.class).bindFromRequest(request);
        if (form.hasErrors()) return badRequest(form.errorsAsJson());

        BatchProperties batchProperties = form.get();
        // Have to bind ALLOWED_WORKER_TYPES from checkboxes by hand
        String[] allowedWorkerArray = request.body().asFormUrlEncoded().get(BatchProperties.ALLOWED_WORKER_TYPES);
        if (allowedWorkerArray != null) {
            Arrays.stream(allowedWorkerArray)
                    .map(WorkerType::fromWireValue)
                    .forEach(batchProperties::addAllowedWorkerType);
        }

        batchService.updateBatch(currentBatch, batchProperties);
        return ok();
    }

    /**
     * POST request to toggle the property 'active' of the given batch.
     */
    @Async(Executor.IO)
    @Auth
    public Result toggleBatchActive(Long studyId, Long batchId, Boolean active) {
        Study study = studyDao.findById(studyId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        Batch batch = batchDao.findById(batchId);
        checker.checkStandardForStudy(study, studyId, signedinUser);
        checker.checkStudyLocked(study);
        checker.checkStandardForBatch(batch, study, batchId);

        if (active != null) {
            batch.setActive(active);
            batchDao.merge(batch);
        }
        return ok(JsonUtils.asJsonNode(batch.isActive()));
    }

    /**
     * POST request to allow or deny a worker type in a batch.
     */
    @Async(Executor.IO)
    @Auth
    public Result toggleAllowedWorkerType(Long studyId, Long batchId, String workerType,
                                          Boolean allow) {
        Study study = studyDao.findById(studyId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        Batch batch = batchDao.findById(batchId);
        checker.checkStandardForStudy(study, studyId, signedinUser);
        checker.checkStudyLocked(study);
        checker.checkStandardForBatch(batch, study, batchId);

        WorkerType standardizedWorkerType = WorkerType.fromWireValue(workerType);
        if (allow == null) return badRequest();

        if (allow) {
            batch.addAllowedWorkerType(standardizedWorkerType);
        } else {
            batch.removeAllowedWorkerType(standardizedWorkerType);
        }
        batchDao.merge(batch);
        return ok(JsonUtils.asJsonNode(batch.getAllowedWorkerTypes()));
    }

    /**
     * DELETE request to remove a Batch
     */
    @Async(Executor.IO)
    @Auth
    public Result removeBatch(Http.Request request, Long studyId, Long batchId) {
        Study study = studyDao.findById(studyId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        Batch batch = batchDao.findById(batchId);
        checker.checkStandardForStudy(study, studyId, signedinUser);
        checker.checkStudyLocked(study);
        checker.checkStandardForBatch(batch, study, batchId);
        checker.checkDefaultBatch(batch);

        batchService.remove(batch);
        return ok(RequestScopeMessaging.asJson());
    }

    /**
     * GET request that returns a JSON object use in the Study Links page with a list of data aggregated from StudyLink,
     * Worker, and Batch
     */
    @Async(Executor.IO)
    @Auth
    public Result studyLinksSetupData(Long studyId, Long batchId) {
        Study study = studyDao.findById(studyId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        Batch batch = batchDao.findByIdWithStudy(batchId);
        checker.checkStandardForStudy(study, studyId, signedinUser);
        checker.checkStandardForBatch(batch, study, batchId);

        Map<String, Integer> studyResultCountsPerWorker = workerService.retrieveStudyResultCountsPerWorker(batch);
        Integer personalSingleLinkCount = studyLinkDao.countByBatchAndWorkerType(batch, WorkerType.PERSONAL_SINGLE);
        Integer personalMultipleLinkCount = studyLinkDao
                .countByBatchAndWorkerType(batch, WorkerType.PERSONAL_MULTIPLE);
        JsonNode studyLinksSetupData = jsonUtils.studyLinksSetupData(batch, studyResultCountsPerWorker,
                personalSingleLinkCount, personalMultipleLinkCount);
        return ok(studyLinksSetupData);
    }

    /**
     * GET request that returns a JSON object used in the Study Links page to fill the study links table for the
     * Personal type workers
     */
    @Async(Executor.IO)
    @Auth
    public Result studyLinksData(Long studyId, Long batchId, String workerType) {
        Study study = studyDao.findById(studyId);
        Batch batch = batchDao.findById(batchId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        checker.checkStandardForStudy(study, studyId, signedinUser);
        checker.checkStandardForBatch(batch, study, batchId);
        WorkerType standardizedWorkerType = WorkerType.fromWireValue(workerType);

        List<StudyLink> studyLinkList = studyLinkDao.findAllByBatchAndWorkerType(batch, standardizedWorkerType);
        return ok(jsonUtils.studyLinksData(studyLinkList));
    }

    /**
     * POST request to change the property 'active' of a StudyLink.
     */
    @Async(Executor.IO)
    @Auth
    public Result toggleStudyLinkActive(Long studyId, Long batchId, String studyCode, Boolean active) {
        Study study = studyDao.findById(studyId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        Batch batch = batchDao.findById(batchId);
        StudyLink studyLink = studyLinkDao.findByStudyCode(studyCode);
        checker.checkStandardForStudy(study, studyId, signedinUser);
        checker.checkStandardForBatch(batch, study, batchId);

        if (!batch.equals(studyLink.getBatch())) {
            return forbidden("Not allowed to change this study link.");
        }

        studyLink.setActive(active);
        studyLinkDao.merge(studyLink);
        return ok(JsonUtils.asJsonNode(studyLink.isActive()));
    }

    /**
     * POST request to change a Worker's comment. Traditionally, comments are stored with the Worker and not with the
     * StudyLink.
     */
    @Async(Executor.IO)
    @Auth
    public Result editWorkerComment(Http.Request request, Long workerId) {
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        Worker worker = workerDao.findById(workerId);
        try {
            checker.checkWorker(worker, workerId);
            checker.isUserAllowedToAccessWorker(signedinUser, worker);
        } catch (BadRequestException | ForbiddenException e) {
            return forbidden("User is not allowed to access this Worker");
        }
        if (!request.body().asFormUrlEncoded().containsKey("comment")) {
            return badRequest("No comment provided.");
        }

        String comment = request.body().asFormUrlEncoded().get("comment")[0];
        worker.setComment(comment);
        workerService.validateWorker(worker);
        workerDao.merge(worker);
        return ok();
    }

}
