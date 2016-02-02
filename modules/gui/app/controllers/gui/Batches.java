package controllers.gui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.fasterxml.jackson.databind.JsonNode;

import controllers.gui.actionannotations.AuthenticationAction.Authenticated;
import controllers.gui.actionannotations.JatosGuiAction.JatosGui;
import daos.common.BatchDao;
import daos.common.StudyDao;
import daos.common.StudyResultDao;
import exceptions.gui.BadRequestException;
import exceptions.gui.ForbiddenException;
import exceptions.gui.JatosGuiException;
import general.common.MessagesStrings;
import general.gui.RequestScopeMessaging;
import models.common.Batch;
import models.common.Study;
import models.common.User;
import models.common.workers.PersonalMultipleWorker;
import models.common.workers.PersonalSingleWorker;
import models.common.workers.Worker;
import models.gui.BatchProperties;
import play.Logger;
import play.data.Form;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Result;
import services.gui.BatchService;
import services.gui.BreadcrumbsService;
import services.gui.Checker;
import services.gui.JatosGuiExceptionThrower;
import services.gui.UserService;
import services.gui.WorkerService;
import utils.common.ControllerUtils;
import utils.common.JsonUtils;

/**
 * Controller for all actions regarding batches and runs within the JATOS GUI.
 * 
 * @author Kristian Lange
 */
@JatosGui
@Authenticated
@Singleton
public class Batches extends Controller {

	private static final String CLASS_NAME = Batches.class.getSimpleName();

	private final JatosGuiExceptionThrower jatosGuiExceptionThrower;
	private final Checker checker;
	private final JsonUtils jsonUtils;
	private final UserService userService;
	private final WorkerService workerService;
	private final BatchService batchService;
	private final BreadcrumbsService breadcrumbsService;
	private final StudyDao studyDao;
	private final BatchDao batchDao;
	private final StudyResultDao studyResultDao;

	@Inject
	Batches(JatosGuiExceptionThrower jatosGuiExceptionThrower, Checker checker,
			JsonUtils jsonUtils, UserService userService,
			WorkerService workerService, BatchService batchService,
			BreadcrumbsService breadcrumbsService, StudyDao studyDao,
			BatchDao batchDao, StudyResultDao studyResultDao) {
		this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
		this.checker = checker;
		this.jsonUtils = jsonUtils;
		this.userService = userService;
		this.workerService = workerService;
		this.batchService = batchService;
		this.breadcrumbsService = breadcrumbsService;
		this.studyDao = studyDao;
		this.batchDao = batchDao;
		this.studyResultDao = studyResultDao;
	}

	/**
	 * GET request to get the runManager page
	 */
	@Transactional
	public Result batchManager(Long studyId) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".batchManager: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		try {
			checker.checkStandardForStudy(study, studyId, loggedInUser);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwStudyIndex(e, studyId);
		}

		String breadcrumbs = breadcrumbsService.generateForStudy(study,
				BreadcrumbsService.BATCH_MANAGER);
		return ok(views.html.gui.batch.batchManager.render(loggedInUser,
				breadcrumbs, study));
	}

	/**
	 * Ajax GET request: Returns all Batches of the given study as JSON. It
	 * includes the count of their StudyResults.
	 */
	@Transactional
	public Result batchesByStudy(Long studyId) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".batchesByStudy: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
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
	 * Ajax GET request: Returns a list of workers as JSON
	 */
	@Transactional
	public Result workerData(Long studyId, Long batchId)
			throws JatosGuiException {
		Logger.info(CLASS_NAME + ".workersData: studyId " + studyId + ", "
				+ "batchId " + batchId + ", " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		Batch batch = batchDao.findById(batchId);
		try {
			checker.checkStandardForStudy(study, studyId, loggedInUser);
			checker.checkStandardForBatch(batch, study, batchId);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwAjax(e);
		}

		Set<Worker> workerList = batchService.retrieveAllWorkers(study, batch);
		return ok(JsonUtils.asJsonNode(workerList));
	}

	/**
	 * Ajax POST request to submit created Batch
	 * 
	 * @throws JatosGuiException
	 */
	@Transactional
	public Result submitCreated(Long studyId) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".submitCreated: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		try {
			checker.checkStandardForStudy(study, studyId, loggedInUser);
			checker.checkStudyLocked(study);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwAjax(e);
		}

		Form<BatchProperties> form = Form.form(BatchProperties.class)
				.bindFromRequest();
		if (form.hasErrors()) {
			return badRequest(form.errorsAsJson());
		}
		BatchProperties batchProperties = form.get();
		Batch batch = batchService.bindToBatch(batchProperties);

		batchService.createAndPersistBatch(batch, study, loggedInUser);
		return ok(batch.getId().toString());
	}

	/**
	 * GET request to get the batch page
	 */
	@Transactional
	public Result workers(Long studyId, Long batchId) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".workers: studyId " + studyId + ", "
				+ "batchId " + batchId + ", " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		Batch batch = batchDao.findById(batchId);
		try {
			checker.checkStandardForStudy(study, studyId, loggedInUser);
			checker.checkStandardForBatch(batch, study, batchId);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwStudyIndex(e, studyId);
		}

		String baseUrl = ControllerUtils.getReferer();
		Map<String, Integer> studyResultCountsPerWorker = batchService
				.retrieveStudyResultCountsPerWorker(batch);
		String breadcrumbs = breadcrumbsService.generateForBatch(study, batch,
				BreadcrumbsService.WORKERS);
		return ok(views.html.gui.batch.workers.render(loggedInUser, breadcrumbs,
				batch.getId(), study, baseUrl, studyResultCountsPerWorker));
	}

	/**
	 * Ajax GET request to get BatchProperties as JSON
	 * 
	 * @throws JatosGuiException
	 */
	@Transactional
	public Result properties(Long studyId, Long batchId)
			throws JatosGuiException {
		Logger.info(CLASS_NAME + ".properties: studyId " + studyId
				+ ", batchId " + batchId + ", " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		Study study = studyDao.findById(studyId);
		Batch batch = batchDao.findById(batchId);
		User loggedInUser = userService.retrieveLoggedInUser();
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
	public Result submitEditedProperties(Long studyId, Long batchId)
			throws JatosGuiException {
		Logger.info(CLASS_NAME + ".submitEditedProperties: studyId " + studyId
				+ ", batchId " + batchId + ", " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		Batch currentBatch = batchDao.findById(batchId);
		try {
			checker.checkStandardForStudy(study, studyId, loggedInUser);
			checker.checkStudyLocked(study);
			checker.checkStandardForBatch(currentBatch, study, batchId);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwAjax(e);
		}

		Form<BatchProperties> form = Form.form(BatchProperties.class)
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
	 * batch. If needed this method can be extended for other properties.
	 * 
	 * @throws JatosGuiException
	 */
	@Transactional
	public Result changeProperty(Long studyId, Long batchId, Boolean active)
			throws JatosGuiException {
		Logger.info(CLASS_NAME + ".changeProperty: studyId " + studyId + ", "
				+ "batchId " + batchId + ", " + "active " + active + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
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
		return ok(String.valueOf(batch.isActive()));
	}

	/**
	 * Ajax POST request to remove a Batch
	 * 
	 * @throws JatosGuiException
	 */
	@Transactional
	public Result remove(Long studyId, Long batchId) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".remove: studyId " + studyId + ", batchId "
				+ batchId + ", " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
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
		RequestScopeMessaging.success(MessagesStrings.BATCH_DELETED);
		return ok(RequestScopeMessaging.getAsJson());
	}

	/**
	 * Ajax POST request: Creates a PersonalSingleWorker and the URL that can be
	 * used for this kind of run.
	 */
	@Transactional
	public Result createPersonalSingleRun(Long studyId, Long batchId)
			throws JatosGuiException {
		Logger.info(CLASS_NAME + ".createPersonalSingleRun: studyId " + studyId
				+ ", " + "batchId " + batchId + ", " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		Batch batch = batchDao.findById(batchId);
		try {
			checker.checkStandardForStudy(study, studyId, loggedInUser);
			checker.checkStudyLocked(study);
			checker.checkStandardForBatch(batch, study, batchId);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwAjax(e);
		}

		JsonNode json = request().body().asJson();
		if (json == null) {
			String errorMsg = MessagesStrings
					.studyCreationOfPersonalSingleRunFailed(studyId);
			return badRequest(errorMsg);
		}
		String comment = json.findPath(PersonalSingleWorker.COMMENT).asText()
				.trim();
		PersonalSingleWorker worker;
		try {
			worker = workerService.createAndPersistPersonalSingleWorker(comment,
					batch);
		} catch (BadRequestException e) {
			return badRequest(e.getMessage());
		}

		String url = ControllerUtils.getReferer() + "/publix/" + study.getId()
				+ "/start?" + "batchId=" + batchId + "&"
				+ "personalSingleWorkerId" + "=" + worker.getId();
		return ok(url);
	}

	/**
	 * Ajax POST request: Creates a PersonalMultipleWorker and returns the URL
	 * that can be used for a personal multiple run.
	 */
	@Transactional
	public Result createPersonalMultipleRun(Long studyId, Long batchId)
			throws JatosGuiException {
		Logger.info(CLASS_NAME + ".createPersonalMultipleRun: studyId "
				+ studyId + ", " + "batchId " + batchId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		Batch batch = batchDao.findById(batchId);
		try {
			checker.checkStandardForStudy(study, studyId, loggedInUser);
			checker.checkStudyLocked(study);
			checker.checkStandardForBatch(batch, study, batchId);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwAjax(e);
		}

		JsonNode json = request().body().asJson();
		if (json == null) {
			String errorMsg = MessagesStrings
					.studyCreationOfPersonalMultipleRunFailed(studyId);
			return badRequest(errorMsg);
		}
		String comment = json.findPath(PersonalMultipleWorker.COMMENT).asText()
				.trim();
		PersonalMultipleWorker worker;
		try {
			worker = workerService
					.createAndPersistPersonalMultipleWorker(comment, batch);
		} catch (BadRequestException e) {
			return badRequest(e.getMessage());
		}

		String url = ControllerUtils.getReferer() + "/publix/" + study.getId()
				+ "/start?" + "batchId=" + batchId + "&" + "personalMultipleId"
				+ "=" + worker.getId();
		return ok(url);
	}

}
