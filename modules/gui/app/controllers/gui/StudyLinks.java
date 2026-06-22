package controllers.gui;

import actions.common.AsyncAction.Async;
import exceptions.common.NotFoundException;
import http.common.Http.Context;
import actions.common.AsyncAction.Executor;
import auth.gui.AuthAction.Auth;
import com.fasterxml.jackson.databind.JsonNode;
import daos.common.*;
import daos.common.worker.WorkerDao;
import daos.common.worker.WorkerType;
import exceptions.common.ForbiddenException;
import messaging.common.RequestScopeMessaging;
import models.common.*;
import models.common.GroupResult.GroupState;
import models.common.workers.Worker;
import models.gui.BatchProperties;
import play.data.Form;
import play.data.FormFactory;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.gui.*;
import json.common.DefaultJson;
import json.common.JsonUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static auth.gui.AuthAction.SIGNEDIN_USER;
import static controllers.gui.actionannotations.SaveLastVisitedPageUrlAction.SaveLastVisitedPageUrl;
import static models.common.User.Role.USER;
import static models.common.User.Role.VIEWER;

/**
 * Controller for all actions regarding study links, batches, and workers within the JATOS GUI.
 */
@Singleton
public class StudyLinks extends Controller {

    private final AuthorizationService authorizationService;
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
    private final DefaultJson defaultJson;
    private final JsonUtils jsonUtils;

    @Inject
    StudyLinks(AuthorizationService authorizationService,
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
               FormFactory formFactory,
               DefaultJson defaultJson,
               JsonUtils jsonUtils) {
        this.authorizationService = authorizationService;
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
        this.defaultJson = defaultJson;
        this.jsonUtils = jsonUtils;
    }

    /**
     * GET request to get the Study Links page
     */
    @Async(Executor.IO)
    @Auth(roles = {VIEWER, USER})
    @SaveLastVisitedPageUrl
    public Result studyLinks(Http.Request request, Long studyId) {
        Study study = studyDao.findById(studyId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessStudy(study, signedinUser);

        String breadcrumbs = breadcrumbsService.generateForStudy(study, BreadcrumbsService.STUDY_LINKS);
        return ok(views.html.gui.studyLinks.studyLinks.render(signedinUser, breadcrumbs, study, request.asScala()));
    }

    /**
     * GET request that returns Batch data belonging to the given study as JSON. It includes the count of its
     * StudyResults and GroupResults.
     */
    @Async(Executor.IO)
    @Auth(roles = {VIEWER, USER})
    public Result batchById(Long studyId, Long batchId) {
        Study study = studyDao.findById(studyId);
        Batch batch = batchDao.findById(batchId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessStudy(study, signedinUser);
        authorizationService.canUserAccessBatch(batch, signedinUser);

        Integer resultCount = studyResultDao.countByBatch(batch, WorkerType.JATOS);
        Integer groupCount = groupResultDao.countByBatch(batch);
        return ok(jsonUtils.getBatchByStudyForUI(batch, resultCount, groupCount));
    }

    /**
     * GET request that returns the data of all Batches of the given study as JSON. It includes the count of their
     * StudyResults, the count of their GroupResults, and the count of their Workers.
     */
    @Async(Executor.IO)
    @Auth(roles = {VIEWER, USER})
    public Result batchesByStudy(Long studyId) {
        Study study = studyDao.findByIdWithBatches(studyId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessStudy(study, signedinUser);

        List<Batch> batchList = study.getBatchList();
        List<Integer> resultCountList = new ArrayList<>();
        batchList.forEach(batch -> resultCountList.add(studyResultDao.countByBatch(batch, WorkerType.JATOS)));
        List<Integer> groupCountList = new ArrayList<>();
        batchList.forEach(batch -> groupCountList.add(groupResultDao.countByBatch(batch)));
        return ok(jsonUtils.allBatchesByStudyForUI(batchList, resultCountList, groupCountList));
    }

    /**
     * POST request to submit a newly created Batch
     */
    @Async(Executor.IO)
    @Auth(roles = USER)
    public Result submitCreatedBatch(Http.Request request, Long studyId) {
        Study study = studyDao.findById(studyId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessStudy(study, signedinUser, true);

        Form<BatchProperties> form = formFactory.form(BatchProperties.class).bindFromRequest(request);
        if (form.hasErrors()) return badRequest(form.errorsAsJson());

        BatchProperties batchProperties = form.get();
        Batch batch = batchService.bindToBatch(batchProperties);

        batchService.addDefaultAllowedWorkerTypes(batch);
        batchService.initAndPersistBatch(batch, study);
        return ok(batch.getId().toString());
    }

    /**
     * GET request to toggle the group state FIXED / STARTED
     */
    @Async(Executor.IO)
    @Auth(roles = USER)
    @SuppressWarnings("unused")
    public Result toggleGroupFixed(Long studyId, Long groupResultId, boolean fixed) {
        GroupResult groupResult = groupResultDao.findById(groupResultId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessGroupResult(groupResult, signedinUser);

        GroupState result = groupService.toggleGroupFixed(groupResult, fixed);
        return ok(defaultJson.objAsJsonNode(result));
    }

    /**
     * GET request that returns Batch properties as JSON
     */
    @Async(Executor.IO)
    @Auth(roles = {VIEWER, USER})
    public Result batchProperties(Long studyId, Long batchId) {
        Study study = studyDao.findById(studyId);
        Batch batch = batchDao.findById(batchId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessStudy(study, signedinUser);
        authorizationService.canUserAccessBatch(batch, signedinUser);

        BatchProperties batchProperties = batchService.bindToProperties(batch);
        return ok(defaultJson.objAsJsonNode(batchProperties));
    }

    /**
     * POST request to submit changed Batch properties
     */
    @Async(Executor.IO)
    @Auth(roles = USER)
    public Result submitEditedBatchProperties(Http.Request request, Long studyId, Long batchId) {
        Study study = studyDao.findById(studyId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        Batch currentBatch = batchDao.findById(batchId);
        authorizationService.canUserAccessStudy(study, signedinUser, true);
        authorizationService.canUserAccessBatch(currentBatch, signedinUser);

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
     * POST request to allow or deny a worker type in a batch.
     */
    @Async(Executor.IO)
    @Auth(roles = USER)
    public Result toggleAllowedWorkerType(Long studyId, Long batchId, String workerType,
                                          Boolean allow) {
        Study study = studyDao.findById(studyId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        Batch batch = batchDao.findById(batchId);
        authorizationService.canUserAccessStudy(study, signedinUser, true);
        authorizationService.canUserAccessBatch(batch, signedinUser);
        if (allow == null)  return badRequest();

        WorkerType standardizedWorkerType = WorkerType.fromWireValue(workerType);

        if (allow) {
            batch.addAllowedWorkerType(standardizedWorkerType);
        } else {
            batch.removeAllowedWorkerType(standardizedWorkerType);
        }
        batchDao.merge(batch);
        return ok(defaultJson.objAsJsonNode(batch.getAllowedWorkerTypes()));
    }

    /**
     * DELETE request to remove a Batch
     */
    @Async(Executor.IO)
    @Auth(roles = USER)
    public Result removeBatch(Long studyId, Long batchId) {
        Study study = studyDao.findById(studyId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        Batch batch = batchDao.findById(batchId);
        authorizationService.canUserAccessStudy(study, signedinUser, true);
        authorizationService.canUserAccessBatch(batch, signedinUser);
        authorizationService.checkNotDefaultBatch(batch);

        batchService.remove(batch);
        return ok(RequestScopeMessaging.asJson());
    }

    /**
     * GET request that returns a JSON object use in the Study Links page with a list of data aggregated from StudyLink,
     * Worker, and Batch
     */
    @Async(Executor.IO)
    @Auth(roles = {VIEWER, USER})
    public Result studyLinksSetupData(Long studyId, Long batchId) {
        Study study = studyDao.findById(studyId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        Batch batch = batchDao.findByIdWithStudy(batchId);
        authorizationService.canUserAccessStudy(study, signedinUser);
        authorizationService.canUserAccessBatch(batch, signedinUser);

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
    @Auth(roles = {VIEWER, USER})
    public Result studyLinksData(Long studyId, Long batchId, String workerType) {
        Study study = studyDao.findById(studyId);
        Batch batch = batchDao.findById(batchId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        authorizationService.canUserAccessStudy(study, signedinUser);
        authorizationService.canUserAccessBatch(batch, signedinUser);
        WorkerType standardizedWorkerType = WorkerType.fromWireValue(workerType);

        List<StudyLink> studyLinkList = studyLinkDao.findAllByBatchAndWorkerType(batch, standardizedWorkerType);
        return ok(jsonUtils.studyLinksData(studyLinkList));
    }

    /**
     * POST request to change the property 'active' of a StudyLink.
     */
    @Async(Executor.IO)
    @Auth(roles = USER)
    public Result toggleStudyLinkActive(Long studyId, Long batchId, String studyCode, Boolean active) {
        Study study = studyDao.findById(studyId);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        Batch batch = batchDao.findById(batchId);
        StudyLink studyLink = studyLinkDao.findByStudyCode(studyCode);
        authorizationService.canUserAccessStudy(study, signedinUser);
        authorizationService.canUserAccessBatch(batch, signedinUser);

        if (!batch.equals(studyLink.getBatch())) {
            return forbidden("Not allowed to change this study link.");
        }

        studyLink.setActive(active);
        studyLinkDao.merge(studyLink);
        return ok(defaultJson.objAsJsonNode(studyLink.isActive()));
    }

    /**
     * POST request to change a Worker's comment. Traditionally, comments are stored with the Worker and not with the
     * StudyLink.
     */
    @Async(Executor.IO)
    @Auth(roles = USER)
    public Result editWorkerComment(Http.Request request, Long workerId) {
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        Worker worker = workerDao.findById(workerId);
        try {
            authorizationService.canUserAccessWorker(signedinUser, worker);
        } catch (NotFoundException | ForbiddenException e) {
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
