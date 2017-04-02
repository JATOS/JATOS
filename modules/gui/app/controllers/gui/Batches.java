package controllers.gui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;

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
import play.Logger;
import play.Logger.ALogger;
import play.data.Form;
import play.data.FormFactory;
import play.db.jpa.Transactional;
import play.libs.F.Function3;
import play.mvc.Controller;
import play.mvc.Result;
import services.gui.AuthenticationService;
import services.gui.BatchService;
import services.gui.BreadcrumbsService;
import services.gui.Checker;
import services.gui.JatosGuiExceptionThrower;
import services.gui.WorkerService;
import utils.common.HttpUtils;
import utils.common.JsonUtils;

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
	 * GET request to get the runManager page
	 */
	@Transactional
	@Authenticated
	public Result batchManager(Long studyId) throws JatosGuiException {
		LOGGER.debug(".batchManager: studyId " + studyId);
		Study study = studyDao.findById(studyId);
		User loggedInUser = authenticationService.getLoggedInUser();
		try {
			checker.checkStandardForStudy(study, studyId, loggedInUser);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwStudy(e, studyId);
		}

		String breadcrumbs = breadcrumbsService.generateForStudy(study,
				BreadcrumbsService.BATCH_MANAGER);
		return ok(views.html.gui.batch.batchManager.render(loggedInUser,
				breadcrumbs, HttpUtils.isLocalhost(), study));
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
		batchList.forEach(batch -> resultCountList
				.add(studyResultDao.countByBatch(batch)));
		return ok(jsonUtils.allBatchesByStudyForUI(study, resultCountList));
	}

	/**
	 * Ajax POST request to submit created Batch
	 * 
	 * @throws JatosGuiException
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
	 * Ajax GET request to get BatchProperties as JSON
	 * 
	 * @throws JatosGuiException
	 */
	@Transactional
	@Authenticated
	public Result properties(Long studyId, Long batchId)
			throws JatosGuiException {
		LOGGER.debug(".properties: studyId " + studyId + ", batchId " + batchId);
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
		return ok(JsonUtils.asJsonNode(batchProperties));
	}

	/**
	 * Ajax POST request to submit changed BatchProperties
	 * 
	 * @throws JatosGuiException
	 */
	@Transactional
	@Authenticated
	public Result submitEditedProperties(Long studyId, Long batchId)
			throws JatosGuiException {
		LOGGER.debug(".submitEditedProperties: studyId " + studyId + ", batchId "
				+ batchId);
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
	 * Ajax POST request: Request to change the property 'active' of the given
	 * batch.
	 * 
	 * @throws JatosGuiException
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
		return ok(JsonUtils.asJsonNode(batch.isActive()));
	}

	/**
	 * Ajax POST request: Request to allow or deny a worker type in a batch.
	 * 
	 * @throws JatosGuiException
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
		return ok(JsonUtils.asJsonNode(batch.getAllowedWorkerTypes()));
	}

	/**
	 * Ajax POST request to remove a Batch
	 * 
	 * @throws JatosGuiException
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
	 * Ajax POST request: Creates PersonalSingleWorkers and returns their worker
	 * IDs
	 */
	@Transactional
	@Authenticated
	public Result createPersonalSingleRun(Long studyId, Long batchId)
			throws JatosGuiException {
		LOGGER.debug(".createPersonalSingleRun: studyId " + studyId + ", "
				+ "batchId " + batchId);
		Function3<String, Integer, Batch, List<? extends Worker>> createAndPersistWorker = workerService::createAndPersistPersonalSingleWorker;
		return createPersonalRun(studyId, batchId, createAndPersistWorker);
	}

	/**
	 * Ajax POST request: Creates PersonalMultipleWorker and returns their
	 * worker IDs
	 */
	@Transactional
	@Authenticated
	public Result createPersonalMultipleRun(Long studyId, Long batchId)
			throws JatosGuiException {
		LOGGER.debug(".createPersonalMultipleRun: studyId " + studyId + ", "
				+ "batchId " + batchId);
		Function3<String, Integer, Batch, List<? extends Worker>> createAndPersistWorker = workerService::createAndPersistPersonalMultipleWorker;
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
		List<Long> workerIdList = null;
		try {
			List<? extends Worker> workerList = createAndPersistWorker
					.apply(comment, amount, batch);
			workerIdList = workerList.stream().map(w -> w.getId())
					.collect(Collectors.toList());
		} catch (BadRequestException e) {
			return badRequest(e.getMessage());
		} catch (Throwable e) {
			return internalServerError();
		}

		return ok(JsonUtils.asJsonNode(workerIdList));
	}

}
