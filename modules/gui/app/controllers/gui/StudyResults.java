package controllers.gui;

import java.io.IOException;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.fasterxml.jackson.databind.JsonNode;

import controllers.gui.actionannotations.AuthenticationAction.Authenticated;
import controllers.gui.actionannotations.JatosGuiAction.JatosGui;
import daos.common.BatchDao;
import daos.common.StudyDao;
import daos.common.StudyResultDao;
import daos.common.worker.WorkerDao;
import exceptions.gui.BadRequestException;
import exceptions.gui.ForbiddenException;
import exceptions.gui.JatosGuiException;
import exceptions.gui.NotFoundException;
import general.common.MessagesStrings;
import models.common.Batch;
import models.common.Study;
import models.common.StudyResult;
import models.common.User;
import models.common.workers.Worker;
import play.Logger;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.gui.BreadcrumbsService;
import services.gui.Checker;
import services.gui.JatosGuiExceptionThrower;
import services.gui.ResultRemover;
import services.gui.ResultService;
import services.gui.UserService;
import utils.common.JsonUtils;

/**
 * Controller for actions around StudyResults in the JATOS GUI.
 * 
 * @author Kristian Lange
 */
@JatosGui
@Authenticated
@Singleton
public class StudyResults extends Controller {

	private static final String CLASS_NAME = StudyResults.class.getSimpleName();

	private final JatosGuiExceptionThrower jatosGuiExceptionThrower;
	private final Checker checker;
	private final JsonUtils jsonUtils;
	private final UserService userService;
	private final BreadcrumbsService breadcrumbsService;
	private final ResultRemover resultRemover;
	private final ResultService resultService;
	private final StudyDao studyDao;
	private final BatchDao batchDao;
	private final WorkerDao workerDao;
	private final StudyResultDao studyResultDao;

	@Inject
	StudyResults(JatosGuiExceptionThrower jatosGuiExceptionThrower,
			Checker checker, UserService userService,
			BreadcrumbsService breadcrumbsService, ResultRemover resultRemover,
			ResultService resultService, StudyDao studyDao, BatchDao batchDao,
			JsonUtils jsonUtils, WorkerDao workerDao,
			StudyResultDao studyResultDao) {
		this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
		this.checker = checker;
		this.userService = userService;
		this.breadcrumbsService = breadcrumbsService;
		this.resultRemover = resultRemover;
		this.resultService = resultService;
		this.studyDao = studyDao;
		this.batchDao = batchDao;
		this.jsonUtils = jsonUtils;
		this.workerDao = workerDao;
		this.studyResultDao = studyResultDao;
	}

	/**
	 * Shows view with all StudyResults of a study.
	 */
	@Transactional
	public Result studysStudyResults(Long studyId) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".studysStudyResults: studyId " + studyId
				+ ", " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		try {
			checker.checkStandardForStudy(study, studyId, loggedInUser);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwStudyIndex(e, study.getId());
		}

		String breadcrumbs = breadcrumbsService.generateForStudy(study,
				BreadcrumbsService.RESULTS);
		String dataUrl = controllers.gui.routes.StudyResults
				.tableDataByStudy(study.getId()).url();
		return ok(views.html.gui.result.studyResults.render(loggedInUser,
				breadcrumbs, study, dataUrl));
	}

	/**
	 * Shows view with all StudyResults of a batch.
	 */
	@Transactional
	public Result batchesStudyResults(Long studyId, Long batchId)
			throws JatosGuiException {
		Logger.info(CLASS_NAME + ".batchesStudyResults: studyId " + studyId
				+ ", " + "batchId " + batchId + ", " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		Batch batch = batchDao.findById(batchId);
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		try {
			checker.checkStandardForStudy(study, studyId, loggedInUser);
			checker.checkStandardForBatch(batch, study, batchId);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwStudyIndex(e, study.getId());
		}

		String breadcrumbs = breadcrumbsService.generateForBatch(study, batch,
				BreadcrumbsService.RESULTS);
		String dataUrl = controllers.gui.routes.StudyResults
				.tableDataByBatch(study.getId(), batch.getId()).url();
		return ok(views.html.gui.result.studyResults.render(loggedInUser,
				breadcrumbs, study, dataUrl));
	}

	/**
	 * Shows view with all StudyResults of a worker.
	 */
	@Transactional
	public Result workersStudyResults(Long workerId) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".workersStudyResults: " + "workerId "
				+ workerId + ", " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		User loggedInUser = userService.retrieveLoggedInUser();
		Worker worker = workerDao.findById(workerId);
		try {
			checker.checkWorker(worker, workerId);
		} catch (BadRequestException e) {
			jatosGuiExceptionThrower.throwRedirect(e,
					controllers.gui.routes.Home.home());
		}

		String breadcrumbs = breadcrumbsService.generateForWorker(worker,
				BreadcrumbsService.RESULTS);
		return ok(views.html.gui.result.workersStudyResults.render(loggedInUser,
				breadcrumbs, worker));
	}

	/**
	 * Ajax request
	 * 
	 * Removes all StudyResults specified in the parameter. The parameter is a
	 * comma separated list of of StudyResults IDs as a String. Removing a
	 * StudyResult always removes it's ComponentResults.
	 */
	@Transactional
	public Result remove(String studyResultIds) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".remove: studyResultIds " + studyResultIds
				+ ", " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		User loggedInUser = userService.retrieveLoggedInUser();
		try {
			resultRemover.removeStudyResults(studyResultIds, loggedInUser);
		} catch (ForbiddenException | BadRequestException
				| NotFoundException e) {
			jatosGuiExceptionThrower.throwAjax(e);
		}
		return ok();
	}

	/**
	 * Ajax request
	 * 
	 * Removes all StudyResults of the given study.
	 */
	@Transactional
	public Result removeAllOfStudy(Long studyId) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".removeAllOfStudy: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		try {
			checker.checkStandardForStudy(study, studyId, loggedInUser);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwStudyIndex(e, study.getId());
		}

		try {
			resultRemover.removeAllStudyResults(study, loggedInUser);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwAjax(e);
		}
		return ok();
	}

	/**
	 * Ajax request
	 * 
	 * Removes all StudyResults that belong to the given worker and the
	 * logged-in user is allowed to delete (only if he's a user of the study).
	 */
	@Transactional
	public Result removeAllOfWorker(Long workerId) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".removeAllOfWorker: workerId " + workerId
				+ ", " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		Worker worker = workerDao.findById(workerId);
		User loggedInUser = userService.retrieveLoggedInUser();
		try {
			checker.checkWorker(worker, workerId);
		} catch (BadRequestException e) {
			jatosGuiExceptionThrower.throwRedirect(e,
					controllers.gui.routes.Home.home());
		}

		try {
			resultRemover.removeAllStudyResults(worker, loggedInUser);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwAjax(e);
		}
		return ok();
	}

	/**
	 * Ajax request: Returns all StudyResults of a study in JSON format.
	 */
	@Transactional
	public Result tableDataByStudy(Long studyId) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".tableDataByStudy: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		JsonNode dataAsJson = null;
		try {
			checker.checkStandardForStudy(study, studyId, loggedInUser);
			dataAsJson = jsonUtils
					.allStudyResultsForUI(studyResultDao.findAllByStudy(study));
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
	 * Ajax request: Returns all StudyResults of a batch in JSON format.
	 */
	@Transactional
	public Result tableDataByBatch(Long studyId, Long batchId)
			throws JatosGuiException {
		Logger.info(CLASS_NAME + ".tableDataByStudy: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		Study study = studyDao.findById(studyId);
		Batch batch = batchDao.findById(batchId);
		User loggedInUser = userService.retrieveLoggedInUser();
		JsonNode dataAsJson = null;
		try {
			checker.checkStandardForStudy(study, studyId, loggedInUser);
			checker.checkStandardForBatch(batch, study, batchId);
			dataAsJson = jsonUtils
					.allStudyResultsForUI(studyResultDao.findAllByBatch(batch));
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
	 * Ajax request: Returns all StudyResults belonging to a worker as JSON.
	 */
	@Transactional
	public Result tableDataByWorker(Long workerId) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".tableDataByWorker: workerId " + workerId
				+ ", " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		User loggedInUser = userService.retrieveLoggedInUser();
		Worker worker = workerDao.findById(workerId);
		try {
			checker.checkWorker(worker, workerId);
		} catch (BadRequestException e) {
			jatosGuiExceptionThrower.throwAjax(e);
		}

		List<StudyResult> allowedStudyResultList = resultService
				.getAllowedStudyResultList(loggedInUser, worker);
		JsonNode dataAsJson = null;
		try {
			dataAsJson = jsonUtils.allStudyResultsForUI(allowedStudyResultList);
		} catch (IOException e) {
			String errorMsg = MessagesStrings.PROBLEM_GENERATING_JSON_DATA;
			jatosGuiExceptionThrower.throwAjax(errorMsg,
					Http.Status.INTERNAL_SERVER_ERROR);
		}
		return ok(dataAsJson);
	}

}
