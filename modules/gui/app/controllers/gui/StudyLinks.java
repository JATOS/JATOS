package controllers.gui;

import auth.gui.AuthService;
import com.fasterxml.jackson.databind.JsonNode;
import auth.gui.AuthAction.Auth;
import controllers.gui.actionannotations.GuiAccessLoggingAction.GuiAccessLogging;
import daos.common.*;
import exceptions.gui.BadRequestException;
import exceptions.gui.ForbiddenException;
import exceptions.gui.JatosGuiException;
import exceptions.gui.NotFoundException;
import general.gui.RequestScopeMessaging;
import models.common.*;
import models.common.GroupResult.GroupState;
import models.common.workers.*;
import models.gui.BatchProperties;
import models.gui.BatchSession;
import models.gui.GroupSession;
import play.data.Form;
import play.data.FormFactory;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import scala.Option;
import services.gui.*;
import utils.common.Helpers;
import utils.common.JsonUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;

/**
 * Controller for all actions regarding study links, batches, and workers within the JATOS GUI.
 *
 * @author Kristian Lange
 */
@SuppressWarnings("deprecation")
@GuiAccessLogging
@Singleton
public class StudyLinks extends Controller {

    private final JatosGuiExceptionThrower jatosGuiExceptionThrower;
    private final Checker checker;
    private final JsonUtils jsonUtils;
    private final AuthService authenticationService;
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
            JsonUtils jsonUtils, AuthService authenticationService,
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
    @Auth
    public Result studyLinks(Http.Request request, Long studyId) throws JatosGuiException {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
        } catch (ForbiddenException | NotFoundException e) {
            jatosGuiExceptionThrower.throwStudy(e, studyId);
        }

        String breadcrumbs = breadcrumbsService.generateForStudy(study, BreadcrumbsService.STUDY_LINKS);
        return ok(views.html.gui.studyLinks.studyLinks.render(loggedInUser,
                breadcrumbs, Helpers.isLocalhost(), study, request));
    }

    /**
     * GET request that returns Batch data belonging to the given study as JSON. It includes the count of its
     * StudyResults and GroupResults.
     */
    @Transactional
    @Auth
    public Result batchById(Long studyId, Long batchId) throws ForbiddenException, NotFoundException {
        Study study = studyDao.findById(studyId);
        Batch batch = batchDao.findById(batchId);
        User loggedInUser = authenticationService.getLoggedInUser();
        checker.checkStandardForStudy(study, studyId, loggedInUser);
        checker.checkStandardForBatch(batch, study, batchId);

        Integer resultCount = studyResultDao.countByBatch(batch, JatosWorker.WORKER_TYPE);
        Integer groupCount = groupResultDao.countByBatch(batch);
        return ok(jsonUtils.getBatchByStudyForUI(batch, resultCount, groupCount));
    }

    /**
     * GET request that returns the data of all Batches of the given study as JSON. It includes the count of their
     * StudyResults, count of their GroupResults, and the count of their Workers.
     */
    @Transactional
    @Auth
    public Result batchesByStudy(Long studyId) throws ForbiddenException, NotFoundException {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        checker.checkStandardForStudy(study, studyId, loggedInUser);

        List<Batch> batchList = study.getBatchList();
        List<Integer> resultCountList = new ArrayList<>();
        batchList.forEach(batch -> resultCountList.add(studyResultDao.countByBatch(batch, JatosWorker.WORKER_TYPE)));
        List<Integer> groupCountList = new ArrayList<>();
        batchList.forEach(batch -> groupCountList.add(groupResultDao.countByBatch(batch)));
        return ok(jsonUtils.allBatchesByStudyForUI(batchList, resultCountList, groupCountList));
    }

    /**
     * GET request that returns data of all groups that belong to the given batch as JSON
     */
    @Transactional
    @Auth
    public Result groupsByBatch(Long studyId, Long batchId) throws ForbiddenException, NotFoundException {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        Batch batch = batchDao.findById(batchId);
        checker.checkStandardForStudy(study, studyId, loggedInUser);
        checker.checkStandardForBatch(batch, study, batchId);

        JsonNode dataAsJson = jsonUtils.allGroupResultsForUI(groupResultDao.findAllByBatch(batch));
        return ok(dataAsJson);
    }

    /**
     * POST request to submit a newly created Batch
     */
    @Transactional
    @Auth
    public Result submitCreatedBatch(Http.Request request, Long studyId) throws ForbiddenException, NotFoundException {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        checker.checkStandardForStudy(study, studyId, loggedInUser);
        checker.checkStudyLocked(study);

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
    @Auth
    public Result batchSessionData(Long studyId, Long batchId) throws ForbiddenException, NotFoundException {
        Study study = studyDao.findById(studyId);
        Batch batch = batchDao.findById(batchId);
        User loggedInUser = authenticationService.getLoggedInUser();
        checker.checkStandardForStudy(study, studyId, loggedInUser);
        checker.checkStandardForBatch(batch, study, batchId);

        BatchSession batchSession = batchService.bindToBatchSession(batch);
        return ok(JsonUtils.asJsonNode(batchSession));
    }

    /**
     * GET request that returns the group session data as String
     */
    @Transactional
    @Auth
    public Result groupSessionData(Long studyId, Long groupResultId) throws ForbiddenException, NotFoundException {
        Study study = studyDao.findById(studyId);
        GroupResult groupResult = groupResultDao.findById(groupResultId);
        User loggedInUser = authenticationService.getLoggedInUser();
        checker.checkStandardForStudy(study, studyId, loggedInUser);
        checker.checkStandardForGroup(groupResult, study, groupResultId);

        GroupSession groupSession = groupService.bindToGroupSession(groupResult);
        return ok(JsonUtils.asJsonNode(groupSession));
    }

    /**
     * POST request to submit changed batch session data
     */
    @Transactional
    @Auth
    public Result submitEditedBatchSessionData(Http.Request request, Long studyId, Long batchId)
            throws ForbiddenException, NotFoundException {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        Batch batch = batchDao.findById(batchId);
        checker.checkStandardForStudy(study, studyId, loggedInUser);
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
        return ok(" "); // jQuery.ajax cannot handle empty responses
    }

    /**
     * POST request to submit changed group session data
     */
    @Transactional
    @Auth
    public Result submitEditedGroupSessionData(Http.Request request, Long studyId, Long groupResultId)
            throws ForbiddenException, NotFoundException {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        GroupResult groupResult = groupResultDao.findById(groupResultId);
        checker.checkStandardForStudy(study, studyId, loggedInUser);
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
        return ok(" "); // jQuery.ajax cannot handle empty responses
    }

    /**
     * GET request to toggle the group state FIXED / STARTED
     */
    @Transactional
    @Auth
    public Result toggleGroupFixed(Long studyId, Long groupResultId, boolean fixed) throws ForbiddenException, NotFoundException {
        Study study = studyDao.findById(studyId);
        GroupResult groupResult = groupResultDao.findById(groupResultId);
        User loggedInUser = authenticationService.getLoggedInUser();
        checker.checkStandardForStudy(study, studyId, loggedInUser);
        checker.checkStandardForGroup(groupResult, study, groupResultId);

        GroupState result = groupService.toggleGroupFixed(groupResult, fixed);
        return ok(JsonUtils.asJsonNode(result));
    }

    /**
     * GET request that returns Batch properties as JSON
     */
    @Transactional
    @Auth
    public Result batchProperties(Long studyId, Long batchId) throws ForbiddenException, NotFoundException {
        Study study = studyDao.findById(studyId);
        Batch batch = batchDao.findById(batchId);
        User loggedInUser = authenticationService.getLoggedInUser();
        checker.checkStandardForStudy(study, studyId, loggedInUser);
        checker.checkStandardForBatch(batch, study, batchId);

        BatchProperties batchProperties = batchService.bindToProperties(batch);
        return ok(JsonUtils.asJsonNode(batchProperties));
    }

    /**
     * POST request to submit changed Batch properties
     */
    @Transactional
    @Auth
    public Result submitEditedBatchProperties(Http.Request request, Long studyId, Long batchId)
            throws ForbiddenException, NotFoundException {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        Batch currentBatch = batchDao.findById(batchId);
        checker.checkStandardForStudy(study, studyId, loggedInUser);
        checker.checkStudyLocked(study);
        checker.checkStandardForBatch(currentBatch, study, batchId);

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
    @Auth
    public Result toggleBatchActive(Long studyId, Long batchId, Boolean active)
            throws ForbiddenException, NotFoundException {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        Batch batch = batchDao.findById(batchId);
        checker.checkStandardForStudy(study, studyId, loggedInUser);
        checker.checkStudyLocked(study);
        checker.checkStandardForBatch(batch, study, batchId);

        if (active != null) {
            batch.setActive(active);
            batchDao.update(batch);
        }
        return ok(JsonUtils.asJsonNode(batch.isActive()));
    }

    /**
     * POST request to allow or deny a worker type in a batch.
     */
    @Transactional
    @Auth
    public Result toggleAllowedWorkerType(Long studyId, Long batchId,
            String workerType, Boolean allow) throws BadRequestException, ForbiddenException, NotFoundException {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        Batch batch = batchDao.findById(batchId);
        checker.checkStandardForStudy(study, studyId, loggedInUser);
        checker.checkStudyLocked(study);
        checker.checkStandardForBatch(batch, study, batchId);

        workerType = workerService.extractWorkerType(workerType);
        if (allow == null)  return badRequest();

        if (allow) {
            batch.addAllowedWorkerType(workerType);
        } else {
            batch.removeAllowedWorkerType(workerType);
        }
        batchDao.update(batch);
        return ok(JsonUtils.asJsonNode(batch.getAllowedWorkerTypes()));
    }

    /**
     * DELETE request to remove a Batch
     */
    @Transactional
    @Auth
    public Result removeBatch(Long studyId, Long batchId) throws ForbiddenException, NotFoundException {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        Batch batch = batchDao.findById(batchId);
        checker.checkStandardForStudy(study, studyId, loggedInUser);
        checker.checkStudyLocked(study);
        checker.checkStandardForBatch(batch, study, batchId);
        checker.checkDefaultBatch(batch);

        batchService.remove(batch, loggedInUser);
        return ok(RequestScopeMessaging.getAsJson());
    }

    /**
     * GET request that returns a JSON object use in the Study Links page with a list of data aggregated from
     * StudyLink, Worker and Batch
     */
    @Transactional
    @Auth
    public Result studyLinksSetupData(Long studyId, Long batchId) throws JatosGuiException, ForbiddenException, NotFoundException {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        Batch batch = batchDao.findById(batchId);
        checker.checkStandardForStudy(study, studyId, loggedInUser);
        checker.checkStandardForBatch(batch, study, batchId);

        Map<String, Integer> studyResultCountsPerWorker = workerService.retrieveStudyResultCountsPerWorker(batch);
        Integer personalSingleLinkCount = studyLinkDao.countByBatchAndWorkerType(batch, PersonalSingleWorker.WORKER_TYPE);
        Integer personalMultipleLinkCount = studyLinkDao
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
    @Auth
    public Result studyLinksData(Long studyId, Long batchId, String workerType) throws ForbiddenException, NotFoundException {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        Batch batch = batchDao.findById(batchId);
        checker.checkStandardForStudy(study, studyId, loggedInUser);
        checker.checkStandardForBatch(batch, study, batchId);

        List<StudyLink> studyLinkList = studyLinkDao.findAllByBatchAndWorkerType(batch, workerType);
        return ok(jsonUtils.studyLinksData(studyLinkList));
    }

    /**
     * POST request to change the property 'active' of a StudyLink.
     */
    @Transactional
    @Auth
    public Result toggleStudyLinkActive(Long studyId, Long batchId, String studyCode, Boolean active)
            throws ForbiddenException, NotFoundException {
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        Batch batch = batchDao.findById(batchId);
        StudyLink studyLink = studyLinkDao.findByStudyCode(studyCode);
        checker.checkStandardForStudy(study, studyId, loggedInUser);
        checker.checkStandardForBatch(batch, study, batchId);

        if (!batch.equals(studyLink.getBatch())) {
            return forbidden("Not allowed to change this study link.");
        }

        studyLink.setActive(active);
        studyLinkDao.update(studyLink);
        return ok(JsonUtils.asJsonNode(studyLink.isActive()));
    }

}
