package controllers.gui;

import com.fasterxml.jackson.databind.JsonNode;
import controllers.gui.actionannotations.AuthenticationAction.Authenticated;
import controllers.gui.actionannotations.GuiAccessLoggingAction.GuiAccessLogging;
import daos.common.BatchDao;
import daos.common.StudyDao;
import daos.common.StudyResultDao;
import exceptions.gui.BadRequestException;
import exceptions.gui.ForbiddenException;
import exceptions.gui.JatosGuiException;
import general.gui.RequestScopeMessaging;
import models.common.Batch;
import models.common.Study;
import models.common.User;
import models.common.workers.Worker;
import models.gui.BatchProperties;
import models.gui.BatchSession;
import play.Logger;
import play.Logger.ALogger;
import play.data.Form;
import play.data.FormFactory;
import play.db.jpa.Transactional;
import play.libs.F.Function3;
import play.mvc.Controller;
import play.mvc.Result;
import services.gui.*;
import utils.common.HttpUtils;
import utils.common.JsonUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller for all actions regarding batches and runs within the JATOS GUI.
 *
 * @author Kristian Lange
 */
@GuiAccessLogging
@Singleton
public class Batches extends Controller {

    private static final ALogger LOGGER = Logger.of(Batches.class);

    private final JatosGuiExceptionThrower jatosGuiExceptionThrower;
    private final Checker checker;
    private final JsonUtils jsonUtils;
    private final AuthenticationService authenticationService;
    private final WorkerService workerService;
    private final BatchService batchService;
    private final BreadcrumbsService breadcrumbsService;
    private final StudyDao studyDao;
    private final BatchDao batchDao;
    private final StudyResultDao studyResultDao;
    private final FormFactory formFactory;

    @Inject
    Batches(JatosGuiExceptionThrower jatosGuiExceptionThrower, Checker checker,
            JsonUtils jsonUtils, AuthenticationService authenticationService,
            WorkerService workerService, BatchService batchService,
            BreadcrumbsService breadcrumbsService, StudyDao studyDao,
            BatchDao batchDao, StudyResultDao studyResultDao,
            FormFactory formFactory) {
        this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
        this.checker = checker;
        this.jsonUtils = jsonUtils;
        this.authenticationService = authenticationService;
        this.workerService = workerService;
        this.batchService = batchService;
        this.breadcrumbsService = breadcrumbsService;
        this.studyDao = studyDao;
        this.batchDao = batchDao;
        this.studyResultDao = studyResultDao;
        this.formFactory = formFactory;
    }

    /**
     * GET request to get the Worker & Batch Manager page
     */
    @Transactional
    @Authenticated
    public Result workerAndBatchManager(Long studyId) throws JatosGuiException {
        LOGGER.debug(".workerAndBatchManager: studyId " + studyId);
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwStudy(e, studyId);
        }

        int allWorkersSize =
                study.getBatchList().stream().mapToInt(b -> b.getWorkerList().size()).sum();
        String breadcrumbs = breadcrumbsService.generateForStudy(study,
                BreadcrumbsService.WORKER_AND_BATCH_MANAGER);
        URL jatosURL = HttpUtils.getHostUrl();
        return ok(views.html.gui.batch.workerAndBatchManager.render(loggedInUser, breadcrumbs,
                HttpUtils.isLocalhost(), study, jatosURL, allWorkersSize));
    }

    /**
     * Ajax GET request: Returns the Batch belonging to the given study as JSON. It
     * includes the count of its StudyResults.
     */
    @Transactional
    @Authenticated
    public Result batchById(Long studyId, Long batchId) throws JatosGuiException {
        LOGGER.debug(".batchById: studyId " + studyId + ", batchId " + batchId);
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
        return ok(jsonUtils.getBatchByStudyForUI(batch, resultCount));
    }

    /**
     * Ajax GET request: Returns all Batches of the given study as JSON. It
     * includes the count of their StudyResults.
     */
    @Transactional
    @Authenticated
    public Result batchesByStudy(Long studyId) throws JatosGuiException {
        LOGGER.debug(".batchesByStudy: studyId " + studyId);
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
        return ok(jsonUtils.allBatchesByStudyForUI(batchList, resultCountList));
    }

    /**
     * Ajax POST request to submit created Batch
     */
    @Transactional
    @Authenticated
    public Result submitCreated(Long studyId) throws JatosGuiException {
        LOGGER.debug(".submitCreated: studyId " + studyId);
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);
            checker.checkStudyLocked(study);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }

        Form<BatchProperties> form = formFactory.form(BatchProperties.class)
                .bindFromRequest();
        if (form.hasErrors()) {
            return badRequest(form.errorsAsJson());
        }
        BatchProperties batchProperties = form.get();
        Batch batch = batchService.bindToBatch(batchProperties);

        batchService.createAndPersistBatch(batch, study);
        return ok(batch.getId().toString());
    }

    /**
     * Ajax GET request to get the batch session data as String
     */
    @Transactional
    @Authenticated
    public Result batchSessionData(Long studyId, Long batchId) throws JatosGuiException {
        LOGGER.debug(".batchSessionData: studyId " + studyId + ", batchId "
                + batchId);
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
     * Ajax POST request to submit changed batch session data
     */
    @Transactional
    @Authenticated
    public Result submitEditedBatchSessionData(Long studyId, Long batchId)
            throws JatosGuiException {
        LOGGER.debug(".submitEditedBatchSessionData: studyId " + studyId
                + ", batchId " + batchId);
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

        Form<BatchSession> form = formFactory.form(BatchSession.class)
                .bindFromRequest();
        if (form.hasErrors()) {
            return badRequest(form.errorsAsJson());
        }

        BatchSession batchSession = form.get();
        boolean success = batchService.updateBatchSession(batch.getId(),
                batchSession);
        if (!success) {
            return forbidden("The Batch Session has been updated since you " +
                    "loaded this page. Reload before trying to save again.");
        }
        return ok();
    }

    /**
     * Ajax GET request to get BatchProperties as JSON
     */
    @Transactional
    @Authenticated
    public Result properties(Long studyId, Long batchId) throws JatosGuiException {
        LOGGER.debug(
                ".properties: studyId " + studyId + ", batchId " + batchId);
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
     * Ajax POST request to submit changed BatchProperties
     */
    @Transactional
    @Authenticated
    public Result submitEditedProperties(Long studyId, Long batchId) throws JatosGuiException {
        LOGGER.debug(".submitEditedProperties: studyId " + studyId
                + ", batchId " + batchId);
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

        Form<BatchProperties> form = formFactory.form(BatchProperties.class)
                .bindFromRequest();
        if (form.hasErrors()) {
            return badRequest(form.errorsAsJson());
        }
        BatchProperties batchProperties = form.get();
        // Have to bind ALLOWED_WORKER_TYPES from checkboxes by hand
        String[] allowedWorkerArray = Controller.request().body()
                .asFormUrlEncoded().get(BatchProperties.ALLOWED_WORKER_TYPES);
        if (allowedWorkerArray != null) {
            Arrays.stream(allowedWorkerArray)
                    .forEach(batchProperties::addAllowedWorkerType);
        }

        batchService.updateBatch(currentBatch, batchProperties);
        return ok();
    }

    /**
     * Ajax POST request: Request to change the property 'active' of the given batch.
     */
    @Transactional
    @Authenticated
    public Result toggleActive(Long studyId, Long batchId, Boolean active)
            throws JatosGuiException {
        LOGGER.debug(".toggleActive: studyId " + studyId + ", " + "batchId "
                + batchId + ", " + "active " + active + ", ");
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
     * Ajax POST request: Request to allow or deny a worker type in a batch.
     */
    @Transactional
    @Authenticated
    public Result toggleAllowedWorkerType(Long studyId, Long batchId,
            String workerType, Boolean allow) throws JatosGuiException {
        LOGGER.debug(".toggleAllowedWorkerType: studyId " + studyId + ", "
                + "batchId " + batchId + ", " + "workerType " + workerType
                + ", " + "allow " + allow);
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
     * Ajax POST request to remove a Batch
     */
    @Transactional
    @Authenticated
    public Result remove(Long studyId, Long batchId) throws JatosGuiException {
        LOGGER.debug(".remove: studyId " + studyId + ", batchId " + batchId);
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

        batchService.remove(batch);
        return ok(RequestScopeMessaging.getAsJson());
    }

    /**
     * Ajax POST request: Creates PersonalSingleWorkers and returns their worker IDs
     */
    @Transactional
    @Authenticated
    public Result createPersonalSingleRun(Long studyId, Long batchId) throws JatosGuiException {
        LOGGER.debug(".createPersonalSingleRun: studyId " + studyId + ", "
                + "batchId " + batchId);
        Function3<String, Integer, Batch, List<? extends Worker>> createAndPersistWorker =
                workerService::createAndPersistPersonalSingleWorker;
        return createPersonalRun(studyId, batchId, createAndPersistWorker);
    }

    /**
     * Ajax POST request: Creates PersonalMultipleWorker and returns their
     * worker IDs
     */
    @Transactional
    @Authenticated
    public Result createPersonalMultipleRun(Long studyId, Long batchId) throws JatosGuiException {
        LOGGER.debug(".createPersonalMultipleRun: studyId " + studyId + ", "
                + "batchId " + batchId);
        Function3<String, Integer, Batch, List<? extends Worker>> createAndPersistWorker =
                workerService::createAndPersistPersonalMultipleWorker;
        return createPersonalRun(studyId, batchId, createAndPersistWorker);
    }

    /**
     * This method creates either PersonalSingleWorker or
     * PersonalMultipleWorker. Both workers are very similar and can be created
     * the same way (with the exception of the actual creation for which a
     * function reference is passed).
     */
    private Result createPersonalRun(Long studyId, Long batchId,
            Function3<String, Integer, Batch, List<? extends Worker>> createAndPersistWorker)
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

        JsonNode json = request().body().asJson();
        String comment = json.findPath("comment").asText().trim();
        int amount = json.findPath("amount").asInt();
        List<Long> workerIdList;
        try {
            List<? extends Worker> workerList = createAndPersistWorker
                    .apply(comment, amount, batch);
            workerIdList = workerList.stream().map(Worker::getId).collect(Collectors.toList());
        } catch (BadRequestException e) {
            return badRequest(e.getMessage());
        } catch (Throwable e) {
            return internalServerError();
        }

        return ok(jsonUtils.asJsonNode(workerIdList));
    }

    /**
     * Ajax GET request
     * <p>
     * Returns a list of workers (as JSON) that did the specified study.
     */
    @Transactional
    @Authenticated
    public Result workersTableDataByStudy(Long studyId) throws JatosGuiException {
        LOGGER.debug(".workersTableDataByStudy: studyId " + studyId);
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();

        JsonNode dataAsJson = null;
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);

            Set<Worker> workerSet = workerService.retrieveWorkersWithStudyResult(study);
            dataAsJson = jsonUtils.allWorkersForTableDataByStudy(workerSet, study);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }
        return ok(dataAsJson);
    }

    /**
     * Ajax GET request
     * <p>
     * Returns a list of all workers as JSON that belong to this study including the ones that did
     * not start yet.
     */
    @Transactional
    @Authenticated
    public Result allWorkersTableDataByStudy(Long studyId) throws JatosGuiException {
        LOGGER.debug(".allWorkersTableData: studyId " + studyId);
        Study study = studyDao.findById(studyId);
        User loggedInUser = authenticationService.getLoggedInUser();

        JsonNode dataAsJson = null;
        try {
            checker.checkStandardForStudy(study, studyId, loggedInUser);

            Set<Worker> workerSet = workerService.retrieveAllWorkers(study);
            dataAsJson = jsonUtils.allWorkersForTableDataByStudy(workerSet, study);
        } catch (ForbiddenException | BadRequestException e) {
            jatosGuiExceptionThrower.throwAjax(e);
        }
        return ok(dataAsJson);
    }

    /**
     * Ajax GET request: Returns a list of workers for a study and a batch as JSON
     */
    @Transactional
    @Authenticated
    public Result workerSetupData(Long studyId, Long batchId) throws JatosGuiException {
        LOGGER.debug(".workerSetupData: studyId " + studyId + ", " + "batchId "
                + batchId);
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
        JsonNode workerSetupData = jsonUtils.workerSetupData(batch, studyResultCountsPerWorker);
        return ok(workerSetupData);
    }

}
