package controllers;

import java.io.IOException;
import java.util.List;

import models.StudyModel;
import models.StudyResult;
import models.UserModel;
import models.workers.Worker;
import persistance.StudyDao;
import persistance.workers.WorkerDao;
import play.Logger;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.Breadcrumbs;
import services.JatosGuiExceptionThrower;
import services.MessagesStrings;
import services.ResultRemover;
import services.ResultService;
import services.StudyService;
import services.UserService;
import services.WorkerService;
import utils.JsonUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import common.RequestScopeMessaging;
import controllers.actionannotations.AuthenticationAction.Authenticated;
import controllers.actionannotations.JatosGuiAction.JatosGui;
import exceptions.BadRequestException;
import exceptions.ForbiddenException;
import exceptions.JatosGuiException;
import exceptions.NotFoundException;

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
	private final ResultRemover resultRemover;
	private final ResultService resultService;
	private final StudyDao studyDao;
	private final WorkerDao workerDao;

	@Inject
	StudyResults(JatosGuiExceptionThrower jatosGuiExceptionThrower,
			StudyService studyService, UserService userService,
			WorkerService workerService, ResultRemover resultRemover,
			ResultService resultService, StudyDao studyDao,
			JsonUtils jsonUtils, WorkerDao workerDao) {
		this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
		this.studyService = studyService;
		this.userService = userService;
		this.workerService = workerService;
		this.resultRemover = resultRemover;
		this.resultService = resultService;
		this.studyDao = studyDao;
		this.jsonUtils = jsonUtils;
		this.workerDao = workerDao;
	}

	/**
	 * Shows view with all StudyResults of a study.
	 */
	@Transactional
	public Result index(Long studyId, String errorMsg, int httpStatus)
			throws JatosGuiException {
		Logger.info(CLASS_NAME + ".index: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		StudyModel study = studyDao.findById(studyId);
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		try {
			studyService.checkStandardForStudy(study, studyId, loggedInUser);
		} catch (ForbiddenException | BadRequestException e) {
			jatosGuiExceptionThrower.throwStudyIndex(e, study.getId());
		}

		RequestScopeMessaging.error(errorMsg);
		String breadcrumbs = Breadcrumbs.generateForStudy(study,
				Breadcrumbs.RESULTS);
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
		UserModel loggedInUser = userService.retrieveLoggedInUser();
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
		StudyModel study = studyDao.findById(studyId);
		UserModel loggedInUser = userService.retrieveLoggedInUser();
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
	 * logged-in user is allowed to delete (only if he's a member of the study).
	 */
	@Transactional
	public Result removeAllOfWorker(Long workerId) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".removeAllOfWorker: workerId " + workerId
				+ ", " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		Worker worker = workerDao.findById(workerId);
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		try {
			workerService.checkWorker(worker, workerId);
		} catch (BadRequestException e) {
			jatosGuiExceptionThrower.throwRedirect(e,
					controllers.routes.Home.home());
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
		StudyModel study = studyDao.findById(studyId);
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		String dataAsJson = null;
		try {
			studyService.checkStandardForStudy(study, studyId, loggedInUser);
			dataAsJson = jsonUtils.allStudyResultsForUI(study);
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
		UserModel loggedInUser = userService.retrieveLoggedInUser();
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
