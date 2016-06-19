package controllers.gui;

import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.fasterxml.jackson.databind.JsonNode;

import controllers.gui.actionannotations.AuthenticationAction.Authenticated;
import controllers.gui.actionannotations.GuiExceptionAction.GuiExceptionCatching;
import controllers.gui.actionannotations.GuiAccessLoggingAction.GuiAccessLogging;
import daos.common.BatchDao;
import daos.common.StudyDao;
import daos.common.worker.WorkerDao;
import exceptions.gui.BadRequestException;
import exceptions.gui.ForbiddenException;
import exceptions.gui.JatosGuiException;
import general.common.MessagesStrings;
import models.common.Batch;
import models.common.Study;
import models.common.User;
import models.common.workers.Worker;
import play.Logger;
import play.Logger.ALogger;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.gui.BreadcrumbsService;
import services.gui.Checker;
import services.gui.JatosGuiExceptionThrower;
import services.gui.UserService;
import services.gui.WorkerService;
import utils.common.ControllerUtils;
import utils.common.JsonUtils;

/**
 * Controller that handles all worker actions in JATOS' GUI
 * 
 * @author Kristian Lange
 */
@GuiExceptionCatching
@GuiAccessLogging
@Authenticated
@Singleton
public class Workers extends Controller {

	private static final ALogger LOGGER = Logger.of(Workers.class);

	private final JatosGuiExceptionThrower jatosGuiExceptionThrower;
	private final Checker checker;
	private final UserService userService;
	private final WorkerService workerService;
	private final BreadcrumbsService breadcrumbsService;
	private final JsonUtils jsonUtils;
	private final StudyDao studyDao;
	private final BatchDao batchDao;
	private final WorkerDao workerDao;

	@Inject
	Workers(JatosGuiExceptionThrower jatosGuiExceptionThrower, Checker checker,
			UserService userService, WorkerService workerService,
			BreadcrumbsService breadcrumbsService, JsonUtils jsonUtils,
			StudyDao studyDao, BatchDao batchDao, WorkerDao workerDao) {
		this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
		this.checker = checker;
		this.userService = userService;
		this.workerService = workerService;
		this.breadcrumbsService = breadcrumbsService;
		this.jsonUtils = jsonUtils;
		this.studyDao = studyDao;
		this.batchDao = batchDao;
		this.workerDao = workerDao;
	}

	/**
	 * Ajax request
	 * 
	 * Remove a worker including its results.
	 */
	@Transactional
	public Result remove(Long workerId) throws JatosGuiException {
		LOGGER.info(".remove: workerId " + workerId);
		Worker worker = workerDao.findById(workerId);
		User loggedInUser = userService.retrieveLoggedInUser();
		try {
			checker.checkWorker(worker, workerId);
		} catch (BadRequestException e) {
			jatosGuiExceptionThrower.throwRedirect(e,
					controllers.gui.routes.Home.home());
		}

		try {
			checker.checkRemovalAllowed(worker, loggedInUser);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwAjax(e);
		}
		workerDao.remove(worker);
		return ok();
	}

	/**
	 * Ajax request
	 * 
	 * Returns a list of workers (as JSON) that did the specified study.
	 */
	@Transactional
	public Result tableDataByStudy(Long studyId) throws JatosGuiException {
		LOGGER.info(".tableDataByStudy: studyId " + studyId);
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();

		JsonNode dataAsJson = null;
		try {
			checker.checkStandardForStudy(study, studyId, loggedInUser);

			Set<Worker> workerSet = workerService.retrieveWorkers(study);
			dataAsJson = jsonUtils.allWorkersForUI(workerSet);
		} catch (IOException e) {
			String errorMsg = MessagesStrings.PROBLEM_GENERATING_JSON_DATA;
			jatosGuiExceptionThrower.throwAjax(errorMsg,
					Http.Status.INTERNAL_SERVER_ERROR);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwAjax(e);
		}
		return ok(dataAsJson);
	}

	/**
	 * GET request to get the workers page of the given study and batch
	 */
	@Transactional
	public Result workerSetup(Long studyId, Long batchId)
			throws JatosGuiException {
		LOGGER.info(
				".workers: studyId " + studyId + ", " + "batchId " + batchId);
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		Batch batch = batchDao.findById(batchId);
		try {
			checker.checkStandardForStudy(study, studyId, loggedInUser);
			checker.checkStandardForBatch(batch, study, batchId);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwStudy(e, studyId);
		}

		URL jatosURL = ControllerUtils.getRequestUrl();
		Map<String, Integer> studyResultCountsPerWorker = workerService
				.retrieveStudyResultCountsPerWorker(batch);
		String breadcrumbs = breadcrumbsService.generateForBatch(study, batch,
				BreadcrumbsService.WORKER_SETUP);
		String allowedWorkerTypes = JsonUtils
				.asJson(batch.getAllowedWorkerTypes());
		return ok(views.html.gui.batch.workerSetup.render(loggedInUser,
				breadcrumbs, batch.getId(), allowedWorkerTypes, study, jatosURL,
				studyResultCountsPerWorker));
	}

	/**
	 * Ajax GET request: Returns a list of workers as JSON
	 */
	@Transactional
	public Result workerData(Long studyId, Long batchId)
			throws JatosGuiException {
		LOGGER.info(".workersData: studyId " + studyId + ", " + "batchId "
				+ batchId);
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		Batch batch = batchDao.findById(batchId);
		try {
			checker.checkStandardForStudy(study, studyId, loggedInUser);
			checker.checkStandardForBatch(batch, study, batchId);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwAjax(e);
		}

		Set<Worker> workerList = workerService.retrieveAllWorkers(study, batch);
		return ok(JsonUtils.asJsonNode(workerList));
	}

}
