package controllers.gui;

import java.util.Arrays;

import javax.inject.Inject;
import javax.inject.Singleton;

import controllers.gui.actionannotations.AuthenticationAction.Authenticated;
import controllers.gui.actionannotations.JatosGuiAction.JatosGui;
import daos.common.StudyDao;
import exceptions.gui.BadRequestException;
import exceptions.gui.ForbiddenException;
import exceptions.gui.JatosGuiException;
import models.common.Batch;
import models.common.Study;
import models.common.User;
import models.gui.BatchProperties;
import play.Logger;
import play.data.Form;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Result;
import services.gui.BreadcrumbsService;
import services.gui.BatchService;
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

	@Inject
	Batches(JatosGuiExceptionThrower jatosGuiExceptionThrower,
			StudyService studyService, UserService userService,
			BatchService batchService, BreadcrumbsService breadcrumbsService,
			StudyDao studyDao) {
		this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
		this.studyService = studyService;
		this.userService = userService;
		this.batchService = batchService;
		this.breadcrumbsService = breadcrumbsService;
		this.studyDao = studyDao;
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

		Batch batch = study.getBatchList().get(0);
		String breadcrumbs = breadcrumbsService.generateForStudy(study,
				BreadcrumbsService.RUN_MANAGER);
		return ok(views.html.gui.study.runManager.render(loggedInUser,
				breadcrumbs, batch.getId(), studyId, study.isLocked()));
	}
	
	/**
	 * Ajax GET request to get BatchProperties as JSON
	 */
	@Transactional
	public Result properties(Long studyId, Long batchId) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".properties: studyId " + studyId
				+ ", batchId " + batchId + ", " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		try {
			studyService.checkStandardForStudy(study, studyId, loggedInUser);
			studyService.checkStudyLocked(study);
		} catch (ForbiddenException | BadRequestException e) {
			return badRequest(e.getMessage());
		}
		
		Batch batch = study.getBatchList().get(0);
		BatchProperties batchProperties = batchService
				.bindToBatchProperties(batch);
		return ok(JsonUtils.asJsonNode(batchProperties));
	}

	/**
	 * Ajax POST request to submit changed BatchProperties
	 */
	@Transactional
	public Result submitProperties(Long studyId, Long batchId)
			throws JatosGuiException {
		Logger.info(CLASS_NAME + ".submitProperties: studyId " + studyId
				+ ", batchId " + batchId + ", " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		Batch currentBatch = study.getBatchList().get(0);
		try {
			studyService.checkStandardForStudy(study, studyId, loggedInUser);
			studyService.checkStudyLocked(study);
		} catch (ForbiddenException | BadRequestException e) {
			return badRequest(e.getMessage());
		}

		Form<BatchProperties> form = Form.form(BatchProperties.class)
				.bindFromRequest();
		if (form.hasErrors()) {
			return badRequest(form.errorsAsJson());
		}
		BatchProperties batchProperties = form.get();
		// Have to bind ALLOWED_WORKER_TYPES by hand from checkboxes
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

}
