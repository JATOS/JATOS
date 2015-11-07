package controllers.gui;

import java.io.IOException;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

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
import services.gui.JatosGuiExceptionThrower;
import services.gui.ResultRemover;
import services.gui.ResultService;
import services.gui.StudyService;
import services.gui.UserService;
import services.gui.WorkerService;
import utils.common.JsonUtils;
import controllers.gui.actionannotations.AuthenticationAction.Authenticated;
import controllers.gui.actionannotations.JatosGuiAction.JatosGui;
import daos.StudyDao;
import daos.StudyResultDao;
import daos.workers.WorkerDao;
import exceptions.gui.BadRequestException;
import exceptions.gui.ForbiddenException;
import exceptions.gui.JatosGuiException;
import exceptions.gui.NotFoundException;
import general.common.MessagesStrings;
import general.gui.RequestScopeMessaging;

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
	private final JsonUtils jsonUtils;
	private final StudyService studyService;
	private final UserService userService;
	private final WorkerService workerService;
	private final BreadcrumbsService breadcrumbsService;
	private final ResultRemover resultRemover;
	private final ResultService resultService;
	private final StudyDao studyDao;
	private final WorkerDao workerDao;
	private final StudyResultDao studyResultDao;

	@Inject
	StudyResults(JatosGuiExceptionThrower jatosGuiExceptionThrower,
			StudyService studyService, UserService userService,
			WorkerService workerService, BreadcrumbsService breadcrumbsService,
			ResultRemover resultRemover, ResultService resultService,
			StudyDao studyDao, JsonUtils jsonUtils, WorkerDao workerDao,
			StudyResultDao studyResultDao) {
		this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
		this.studyService = studyService;
		this.userService = userService;
		this.workerService = workerService;
		this.breadcrumbsService = breadcrumbsService;
		this.resultRemover = resultRemover;
		this.resultService = resultService;
		this.studyDao = studyDao;
		this.jsonUtils = jsonUtils;
		this.workerDao = workerDao;
		this.studyResultDao = studyResultDao;
	}

	/**
	 * Shows view with all StudyResults of a study.
	 */
	@Transactional
	public Result index(Long studyId, String errorMsg, int httpStatus)
			throws JatosGuiException {
		Logger.info(CLASS_NAME + ".index: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		try {
			studyService.checkStandardForStudy(study, studyId, loggedInUser);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwStudyIndex(e, study.getId());
		}

		RequestScopeMessaging.error(errorMsg);
		String breadcrumbs = breadcrumbsService.generateForStudy(study,
				BreadcrumbsService.RESULTS);
		return status(httpStatus,
				views.html.gui.result.studysStudyResults.render(loggedInUser,
						breadcrumbs, study));
	}

	@Transactional
	public Result index(Long studyId, String errorMsg) throws JatosGuiException {
		return index(studyId, errorMsg, Http.Status.OK);
	}

	@Transactional
	public Result index(Long studyId) throws JatosGuiException {
		return index(studyId, null, Http.Status.OK);
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
		} catch (ForbiddenException | BadRequestException | NotFoundException e) {
			jatosGuiExceptionThrower.throwAjax(e);
		}
		return ok().as("text/html");
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
			studyService.checkStandardForStudy(study, studyId, loggedInUser);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwStudyIndex(e, study.getId());
		}

		try {
			resultRemover.removeAllStudyResults(study, loggedInUser);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwAjax(e);
		}
		return ok().as("text/html");
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
			workerService.checkWorker(worker, workerId);
		} catch (BadRequestException e) {
			jatosGuiExceptionThrower.throwRedirect(e,
					controllers.gui.routes.Home.home());
		}

		try {
			resultRemover.removeAllStudyResults(worker, loggedInUser);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwAjax(e);
		}
		return ok().as("text/html");
	}

	/**
	 * Ajax request
	 * 
	 * Returns all StudyResults of a study in JSON format.
	 */
	@Transactional
	public Result tableDataByStudy(Long studyId) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".tableDataByStudy: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		Study study = studyDao.findById(studyId);
		User loggedInUser = userService.retrieveLoggedInUser();
		String dataAsJson = null;
		try {
			studyService.checkStandardForStudy(study, studyId, loggedInUser);
			dataAsJson = jsonUtils.allStudyResultsForUI(studyResultDao
					.findAllByStudy(study));
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
	 * Ajax request
	 * 
	 * Returns all StudyResults belonging to a worker as JSON.
	 */
	@Transactional
	public Result tableDataByWorker(Long workerId) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".tableDataByWorker: workerId " + workerId
				+ ", " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		User loggedInUser = userService.retrieveLoggedInUser();
		Worker worker = workerDao.findById(workerId);
		try {
			workerService.checkWorker(worker, workerId);
		} catch (BadRequestException e) {
			jatosGuiExceptionThrower.throwAjax(e);
		}

		List<StudyResult> allowedStudyResultList = resultService
				.getAllowedStudyResultList(loggedInUser, worker);
		String dataAsJson = null;
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
