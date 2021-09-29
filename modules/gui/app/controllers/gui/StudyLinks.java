package controllers.gui;

import com.fasterxml.jackson.databind.JsonNode;
import controllers.gui.actionannotations.AuthenticationAction.Authenticated;
import controllers.gui.actionannotations.GuiAccessLoggingAction.GuiAccessLogging;
import daos.common.*;
import exceptions.gui.BadRequestException;
import exceptions.gui.ForbiddenException;
import exceptions.gui.JatosGuiException;
import general.gui.RequestScopeMessaging;
import models.common.*;
import models.common.GroupResult.GroupState;
import models.common.workers.PersonalMultipleWorker;
import models.common.workers.PersonalSingleWorker;
import models.gui.BatchProperties;
import models.gui.BatchSession;
import models.gui.GroupSession;
import play.data.Form;
import play.data.FormFactory;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.gui.*;
import utils.common.Helpers;
import utils.common.JsonUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Controller for all actions regarding study links, batches, and workers within the JATOS GUI.
 *
 * @author Kristian Lange
 */
@GuiAccessLogging
@Singleton
public class StudyLinks extends Controller {

    private final JatosGuiExceptionThrower jatosGuiExceptionThrower;
    private final Checker checker;
    private final JsonUtils jsonUtils;
    private final AuthenticationService authenticationService;
    private final WorkerService workerService;
    private final BatchService batchService;
    private final StudyLinkService studyLinkService;
    private final GroupService groupService;
    private final BreadcrumbsService breadcrumbsService;
    private final StudyDao studyDao;
    private final BatchDao batchDao;
    private final StudyResultDao studyResultDao;
    private final GroupResultDao groupResultDao;
    private final StudyLinkDao studyLinkDao;
    private final FormFactory formFactory;

    @Inject
    StudyLinks(JatosGuiExceptionThrower jatosGuiExceptionThrower, Checker checker,
            JsonUtils jsonUtils, AuthenticationService authenticationService,
            WorkerService workerService, BatchService batchService, StudyLinkService studyLinkService,
            GroupService groupService, BreadcrumbsService breadcrumbsService, StudyDao studyDao,
            BatchDao batchDao, StudyResultDao studyResultDao, GroupResultDao groupResultDao, StudyLinkDao studyLinkDao,
            FormFactory formFactory) {
        this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
        this.checker = checker;
        this.jsonUtils = jsonUtils;
        this.authenticationService = authenticationService;
        this.workerService = workerService;
        this.batchService = batchService;
        this.studyLinkService = studyLinkService;
        this.groupService = groupService;
        this.breadcrumbsService = breadcrumbsService;
        this.studyDao = studyDao;
        this.batchDao = batchDao;
        this.studyResultDao = studyResultDao;
        this.groupResultDao = groupResultDao;
        this.studyLinkDao = studyLinkDao;
        this.formFactory = formFactory;
    }

    /**
     * GET request to get the Study Links page
     */
    @Transactional
    @Authenticated
    public Result studyLinks(Http.Request request, Long studyId) throws JatosGuiException {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwStudy(e, studyId);
        }

        int allWorkersSize = study.getBatchList().stream().mapToInt(b -> b.getWorkerList().size()).sum()
                - study.getUserList().size(); // Do not count Jatos workers
        String breadcrumbs = breadcrumbsService.generateForStudy(study, BreadcrumbsService.STUDY_LINKS);
        return ok(views.html.gui.studyLinks.studyLinks.render(loggedInUser,
                breadcrumbs, Helpers.isLocalhost(), study, allWorkersSize, request));
    }

    /**
     * GET request that returns Batch data belonging to the given study as JSON. It includes the count of its
     * StudyResults and GroupResults.
     */
    @Transactional
    @Authenticated
    public Result batchById(Long studyId, Long batchId) throws JatosGuiException {
        Study study = studyDao.findById(studyId);
        Batch batch = batchDao.findById(batchId);
        User loggedInUser = authenticationService.getLoggedInUser();
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStandardForBatch(batch, study, batchId);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }

        Integer resultCount = studyResultDao.countByBatch(batch);
        Integer groupCount = groupResultDao.countByBatch(batch);
        return ok(jsonUtils.getBatchByStudyForUI(batch, resultCount, groupCount));
    }

    /**
     * GET request that returns the data of all Batches of the given study as JSON. It includes the count of their
     * StudyResults, count of their GroupResults, and the count of their Workers.
     */
    @Transactional
    @Authenticated
    public Result batchesByStudy(Long studyId) throws JatosGuiException {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }

        List<Batch> batchList = study.getBatchList();
        List<Integer> resultCountList = new ArrayList<>();
        batchList.forEach(batch -> resultCountList.add(studyResultDao.countByBatch(batch)));
        List<Integer> groupCountList = new ArrayList<>();
        batchList.forEach(batch -> groupCountList.add(groupResultDao.countByBatch(batch)));
        return ok(jsonUtils.allBatchesByStudyForUI(batchList, resultCountList, groupCountList));
    }

    /**
     * GET request that returns data of all groups that belong to the given batch as JSON
     */
    @Transactional
    @Authenticated
    public Result groupsByBatch(Long studyId, Long batchId) throws JatosGuiException {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        Batch batch = batchDao.findById(batchId);
        JsonNode dataAsJson = null;
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStandardForBatch(batch, study, batchId);
            dataAsJson = jsonUtils.allGroupResultsForUI(groupResultDao.findAllByBatch(batch));
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }

        return ok(dataAsJson);
    }

    /**
     * POST request to submit a newly created Batch
     */
    @Transactional
    @Authenticated
    public Result submitCreatedBatch(Http.Request request, Long studyId) throws JatosGuiException {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStudyLocked(study);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }

        Form<BatchProperties> form = formFactory.form(BatchProperties.class).bindFromRequest(request);
        if (form.hasErrors()) return badRequest(form.errorsAsJson());

        BatchProperties batchProperties = form.get();
        Batch batch = batchService.bindToBatch(batchProperties);

        batchService.createAndPersistBatch(batch, study, loggedInUser);
        return ok(batch.getId().toString());
    }

    /**
     * GET request that returns the batch session data as String
     */
    @Transactional
    @Authenticated
    public Result batchSessionData(Long studyId, Long batchId) throws JatosGuiException {
        Study study = studyDao.findById(studyId);
        Batch batch = batchDao.findById(batchId);
        User loggedInUser = authenticationService.getLoggedInUser();
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStandardForBatch(batch, study, batchId);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }

        BatchSession batchSession = batchService.bindToBatchSession(batch);
        return ok(jsonUtils.asJsonNode(batchSession));
    }

    /**
     * GET request that returns the group session data as String
     */
    @Transactional
    @Authenticated
    public Result groupSessionData(Long studyId, Long groupResultId) throws JatosGuiException {
        Study study = studyDao.findById(studyId);
        GroupResult groupResult = groupResultDao.findById(groupResultId);
        User loggedInUser = authenticationService.getLoggedInUser();
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStandardForGroup(groupResult, study, groupResultId);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }

        GroupSession groupSession = groupService.bindToGroupSession(groupResult);
        return ok(jsonUtils.asJsonNode(groupSession));
    }

    /**
     * POST request to submit changed batch session data
     */
    @Transactional
    @Authenticated
    public Result submitEditedBatchSessionData(Http.Request request, Long studyId, Long batchId)
            throws JatosGuiException {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        Batch batch = batchDao.findById(batchId);
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStudyLocked(study);
            checker.checkStandardForBatch(batch, study, batchId);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }

        Form<BatchSession> form = formFactory.form(BatchSession.class).bindFromRequest(request);
        if (form.hasErrors()) return badRequest(form.errorsAsJson());

        BatchSession batchSession = form.get();
        boolean success = batchService.updateBatchSession(batch.getId(), batchSession);
        if (!success) {
            return forbidden("The Batch Session has been updated since you " +
                    "loaded this page. Reload before trying to save again.");
        }
        return ok(" "); // jQuery.ajax cannot handle empty responses
    }

    /**
     * POST request to submit changed group session data
     */
    @Transactional
    @Authenticated
    public Result submitEditedGroupSessionData(Http.Request request, Long studyId, Long groupResultId)
            throws JatosGuiException {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        GroupResult groupResult = groupResultDao.findById(groupResultId);
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStudyLocked(study);
            checker.checkStandardForGroup(groupResult, study, groupResultId);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }

        Form<GroupSession> form = formFactory.form(GroupSession.class).bindFromRequest(request);
        if (form.hasErrors()) return badRequest(form.errorsAsJson());

        GroupSession groupSession = form.get();
        boolean success = groupService.updateGroupSession(groupResult.getId(), groupSession);
        if (!success) {
            return forbidden("The Group Session has been updated since you " +
                    "loaded this page. Reload before trying to save again.");
        }
        return ok(" "); // jQuery.ajax cannot handle empty responses
    }

    /**
     * GET request to toggle the group state FIXED / STARTED
     */
    @Transactional
    @Authenticated
    public Result toggleGroupFixed(Long studyId, Long groupResultId, boolean fixed) throws JatosGuiException {
        Study study = studyDao.findById(studyId);
        GroupResult groupResult = groupResultDao.findById(groupResultId);
        User loggedInUser = authenticationService.getLoggedInUser();
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStandardForGroup(groupResult, study, groupResultId);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }

        GroupState result = groupService.toggleGroupFixed(groupResult, fixed);
        return ok(jsonUtils.asJsonNode(result));
    }

    /**
     * GET request that returns Batch properties as JSON
     */
    @Transactional
    @Authenticated
    public Result batchProperties(Long studyId, Long batchId) throws JatosGuiException {
        Study study = studyDao.findById(studyId);
        Batch batch = batchDao.findById(batchId);
        User loggedInUser = authenticationService.getLoggedInUser();
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStandardForBatch(batch, study, batchId);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }

        BatchProperties batchProperties = batchService.bindToProperties(batch);
        return ok(jsonUtils.asJsonNode(batchProperties));
    }

    /**
     * POST request to submit changed Batch properties
     */
    @Transactional
    @Authenticated
    public Result submitEditedBatchProperties(Http.Request request, Long studyId, Long batchId) throws JatosGuiException {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        Batch currentBatch = batchDao.findById(batchId);
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStudyLocked(study);
            checker.checkStandardForBatch(currentBatch, study, batchId);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }

        Form<BatchProperties> form = formFactory.form(BatchProperties.class).bindFromRequest();
        if (form.hasErrors()) return badRequest(form.errorsAsJson());

        BatchProperties batchProperties = form.get();
        // Have to bind ALLOWED_WORKER_TYPES from checkboxes by hand
        String[] allowedWorkerArray = request.body().asFormUrlEncoded().get(BatchProperties.ALLOWED_WORKER_TYPES);
        if (allowedWorkerArray != null) {
            Arrays.stream(allowedWorkerArray).forEach(batchProperties::addAllowedWorkerType);
        }

        batchService.updateBatch(currentBatch, batchProperties);
        return ok(" "); // jQuery.ajax cannot handle empty responses
    }

    /**
     * POST request to toggle the property 'active' of the given batch.
     */
    @Transactional
    @Authenticated
    public Result toggleBatchActive(Long studyId, Long batchId, Boolean active)
            throws JatosGuiException {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        Batch batch = batchDao.findById(batchId);
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStudyLocked(study);
            checker.checkStandardForBatch(batch, study, batchId);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }

        if (active != null) {
            batch.setActive(active);
            batchDao.update(batch);
        }
        return ok(jsonUtils.asJsonNode(batch.isActive()));
    }

    /**
     * POST request to allow or deny a worker type in a batch.
     */
    @Transactional
    @Authenticated
    public Result toggleAllowedWorkerType(Long studyId, Long batchId,
            String workerType, Boolean allow) throws JatosGuiException {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        Batch batch = batchDao.findById(batchId);
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStudyLocked(study);
            checker.checkStandardForBatch(batch, study, batchId);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }

        if (allow != null && workerType != null) {
            if (allow) {
                batch.addAllowedWorkerType(workerType);
            } else {
                batch.removeAllowedWorkerType(workerType);
            }
            batchDao.update(batch);
        } else {
            return badRequest();
        }
        return ok(jsonUtils.asJsonNode(batch.getAllowedWorkerTypes()));
    }

    /**
     * DELETE request to remove a Batch
     */
    @Transactional
    @Authenticated
    public Result removeBatch(Long studyId, Long batchId) throws Exception {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        Batch batch = batchDao.findById(batchId);
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStudyLocked(study);
            checker.checkStandardForBatch(batch, study, batchId);
            checker.checkDefaultBatch(batch);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }

        batchService.remove(batch, loggedInUser);
        return ok(RequestScopeMessaging.getAsJson());
    }

    /**
     * POST request that creates either Personal Single or Personal Multiple study links and their corresponding
     * workers.
     */
    @Transactional
    @Authenticated
    public Result createPersonalRun(Http.Request request, Long studyId, Long batchId, String workerType)
            throws JatosGuiException {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        Batch batch = batchDao.findById(batchId);
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStandardForBatch(batch, study, batchId);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }

        JsonNode json = request.body().asJson();
        String comment = json.findPath("comment").asText().trim();
        int amount = json.findPath("amount").asInt();
        List<String> studyLinkIdList;
        try {
            studyLinkIdList = studyLinkService.createAndPersistStudyLinks(comment, amount, batch, workerType);
        } catch (BadRequestException e) {
            return badRequest(e.getMessage());
        } catch (Throwable e) {
            return internalServerError();
        }

        return ok(jsonUtils.asJsonNode(studyLinkIdList));
    }

    /**
     * GET request that returns a study link ID for the given worker type (possible types are GeneralMultipleWorker,
     * GeneralSingleWorker, MTWorker). This ID is used on the client side to generate the study link. Since for the
     * 'General' workers only one study link is necessary per batch and worker type it only creates one if it is not
     * already stored in the StudyLink table.
     */
    @Transactional
    @Authenticated
    public Result createGeneralRun(Long studyId, Long batchId, String workerType) throws JatosGuiException {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        Batch batch = batchDao.findById(batchId);
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStudyLocked(study);
            checker.checkStandardForBatch(batch, study, batchId);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }

        StudyLink studyLink = studyLinkDao.findFirstByBatchAndWorkerType(batch, workerType)
                .orElseGet(() -> studyLinkDao.create(new StudyLink(batch, workerType)));
        return ok(studyLink.getId());
    }

    /**
     * GET request that returns a JSON object use in the Study Links page with a list of data aggregated from
     * StudyLink, Worker and Batch
     */
    @Transactional
    @Authenticated
    public Result studyLinksSetupData(Long studyId, Long batchId) throws JatosGuiException {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        Batch batch = batchDao.findById(batchId);
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStandardForBatch(batch, study, batchId);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }

        Map<String, Integer> studyResultCountsPerWorker =
                workerService.retrieveStudyResultCountsPerWorker(batch);
        Long personalSingleLinkCount = studyLinkDao.countByBatchAndWorkerType(batch, PersonalSingleWorker.WORKER_TYPE);
        Long personalMultipleLinkCount = studyLinkDao
                .countByBatchAndWorkerType(batch, PersonalMultipleWorker.WORKER_TYPE);
        JsonNode studyLinksSetupData = jsonUtils.studyLinksSetupData(batch, studyResultCountsPerWorker,
                personalSingleLinkCount, personalMultipleLinkCount);
        return ok(studyLinksSetupData);
    }

    /**
     * GET request that returns a JSON object used in the Study Links page to fill the study links table for the
     * Personal type workers
     */
    @Transactional
    @Authenticated
    public Result studyLinksData(Long studyId, Long batchId, String workerType) throws JatosGuiException {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        Batch batch = batchDao.findById(batchId);
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStandardForBatch(batch, study, batchId);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }

        List<StudyLink> studyLinkList = studyLinkDao.findAllByBatchAndWorkerType(batch, workerType);
        return ok(jsonUtils.studyLinksData(studyLinkList));
    }

    /**
     * POST request to change the property 'active' of a StudyLink.
     */
    @Transactional
    @Authenticated
    public Result toggleStudyLinkActive(Long studyId, Long batchId, String studyLinkId, Boolean active) throws JatosGuiException {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        Batch batch = batchDao.findById(batchId);
        StudyLink studyLink = studyLinkDao.findById(studyLinkId);
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStandardForBatch(batch, study, batchId);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }
        if (!batch.equals(studyLink.getBatch())) {
            jatosGuiExceptionThrower.throwAjax("Not allowed to change this study link.", FORBIDDEN);
        }

        studyLink.setActive(active);
        studyLinkDao.update(studyLink);
        return ok(jsonUtils.asJsonNode(studyLink.isActive()));
    }

}
