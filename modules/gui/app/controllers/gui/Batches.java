package controllers.gui;

import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import controllers.gui.actionannotations.AuthenticationAction.Authenticated;
import controllers.gui.actionannotations.JatosGuiAction.JatosGui;
import daos.common.BatchDao;
import daos.common.StudyDao;
import exceptions.gui.BadRequestException;
import exceptions.gui.ForbiddenException;
import exceptions.gui.JatosGuiException;
import general.common.MessagesStrings;
import general.gui.RequestScopeMessaging;
import models.common.Batch;
import models.common.Study;
import models.common.User;
import models.gui.BatchProperties;
import play.Logger;
import play.data.Form;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Result;
import services.gui.BatchService;
import services.gui.BreadcrumbsService;
import services.gui.JatosGuiExceptionThrower;
import services.gui.StudyService;
import services.gui.UserService;
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
	private final StudyService studyService;
	private final UserService userService;
	private final BatchService batchService;
	private final BreadcrumbsService breadcrumbsService;
	private final StudyDao studyDao;
	private final BatchDao batchDao;

	@Inject
	Batches(JatosGuiExceptionThrower jatosGuiExceptionThrower,
			StudyService studyService, UserService userService,
			BatchService batchService, BreadcrumbsService breadcrumbsService,
			StudyDao studyDao, BatchDao batchDao) {
		this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
		this.studyService = studyService;
		this.userService = userService;
		this.batchService = batchService;
		this.breadcrumbsService = breadcrumbsService;
		this.studyDao = studyDao;
		this.batchDao = batchDao;
	}

	/**
	 * GET request to get the runManager page
	 */
	@Transactional
	public Result runManager(Long studyId) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".runManager: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		try {
			studyService.checkStandardForStudy(study, studyId, loggedInUser);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwHome(e);
		}

		String breadcrumbs = breadcrumbsService.generateForRunManager(study);
		return ok(views.html.gui.study.runManager.render(loggedInUser,
				breadcrumbs, study));
	}

	/**
	 * Ajax GET request: Returns all Batches of the given study as JSON.
	 */
	@Transactional
	public Result batchesByStudy(Long studyId) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".batchesByStudy: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		try {
			studyService.checkStandardForStudy(study, studyId, loggedInUser);
		} catch (ForbiddenException | BadRequestException e) {
			return badRequest(e.getMessage());
		}

		List<Batch> batchList = study.getBatchList();
		return ok(JsonUtils.asJsonNode(batchList));
	}

	/**
	 * Ajax POST request to submit created Batch
	 */
	@Transactional
	public Result submitCreated(Long studyId) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".submitCreated: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		try {
			studyService.checkStandardForStudy(study, studyId, loggedInUser);
		} catch (ForbiddenException | BadRequestException e) {
			return badRequest(e.getMessage());
		}

		Form<BatchProperties> form = Form.form(BatchProperties.class)
				.bindFromRequest();
		if (form.hasErrors()) {
			return badRequest(form.errorsAsJson());
		}
		BatchProperties batchProperties = form.get();
		Batch batch = batchService.bindToBatch(batchProperties);

		batchService.createBatch(batch, study);
		return ok(batch.getId().toString());
	}

	/**
	 * GET request to get the batch page
	 */
	@Transactional
	public Result batch(Long studyId, Long batchId) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".runManager: studyId " + studyId + ", "
				+ "batchId " + batchId + ", " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		Batch batch = batchDao.findById(batchId);
		try {
			studyService.checkStandardForStudy(study, studyId, loggedInUser);
			batchService.checkStandardForBatch(batch, study, batchId);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwHome(e);
		}

		String breadcrumbs = breadcrumbsService.generateForRunManager(study,
				batch);
		return ok(views.html.gui.study.batch.render(loggedInUser, breadcrumbs,
				batch.getId(), studyId, study.isLocked()));
	}

	/**
	 * Ajax GET request to get BatchProperties as JSON
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
			studyService.checkStandardForStudy(study, studyId, loggedInUser);
			batchService.checkStandardForBatch(batch, study, batchId);
		} catch (ForbiddenException | BadRequestException e) {
			return badRequest(e.getMessage());
		}

		BatchProperties batchProperties = batchService
				.bindToBatchProperties(batch);
		return ok(JsonUtils.asJsonNode(batchProperties));
	}

	/**
	 * Ajax POST request to submit changed BatchProperties
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
			studyService.checkStandardForStudy(study, studyId, loggedInUser);
			batchService.checkStandardForBatch(currentBatch, study, batchId);
		} catch (ForbiddenException | BadRequestException e) {
			return badRequest(e.getMessage());
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

		Batch updatedBatch = batchService.bindToBatch(batchProperties);
		batchService.updateBatch(currentBatch, updatedBatch);
		return ok();
	}

	/**
	 * Ajax POST request to remove a Batch
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
			studyService.checkStandardForStudy(study, studyId, loggedInUser);
			batchService.checkStandardForBatch(batch, study, batchId);
		} catch (ForbiddenException | BadRequestException e) {
			return badRequest(e.getMessage());
		}
		
		batchService.removeBatch(batch, study);
		RequestScopeMessaging.success(MessagesStrings.BATCH_DELETED);
		return ok(RequestScopeMessaging.getAsJson());
	}
}
