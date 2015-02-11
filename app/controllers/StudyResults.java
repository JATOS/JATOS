package controllers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import models.ComponentResult;
import models.StudyModel;
import models.StudyResult;
import models.UserModel;
import models.workers.Worker;
import play.Logger;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.With;
import services.Breadcrumbs;
import services.ErrorMessages;
import services.JatosGuiExceptionThrower;
import services.Messages;
import services.ResultService;
import services.StudyService;
import services.UserService;
import services.WorkerService;
import utils.DateUtils;
import utils.IOUtils;
import utils.JsonUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import common.JatosGuiAction;

import daos.IStudyDao;
import daos.IStudyResultDao;
import daos.workers.IWorkerDao;
import exceptions.JatosGuiException;

/**
 * Controller for actions around StudyResults in the JATOS GUI.
 * 
 * @author Kristian Lange
 */
@With(JatosGuiAction.class)
@Singleton
public class StudyResults extends Controller {

	private static final String CLASS_NAME = StudyResults.class.getSimpleName();

	private final JatosGuiExceptionThrower jatosGuiExceptionThrower;
	private final JsonUtils jsonUtils;
	private final StudyService studyService;
	private final UserService userService;
	private final WorkerService workerService;
	private final ResultService resultService;
	private final IStudyDao studyDao;
	private final IStudyResultDao studyResultDao;
	private final IWorkerDao workerDao;

	@Inject
	public StudyResults(JatosGuiExceptionThrower jatosGuiExceptionThrower,
			StudyService studyService, UserService userService,
			WorkerService workerService, ResultService resultService,
			IStudyDao studyDao, JsonUtils jsonUtils,
			IStudyResultDao studyResultDao, IWorkerDao workerDao) {
		this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
		this.studyService = studyService;
		this.userService = userService;
		this.workerService = workerService;
		this.resultService = resultService;
		this.studyDao = studyDao;
		this.jsonUtils = jsonUtils;
		this.studyResultDao = studyResultDao;
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
		List<StudyModel> studyList = studyDao.findAllByUser(loggedInUser
				.getEmail());
		studyService.checkStandardForStudy(study, studyId, loggedInUser);

		Messages messages = new Messages().error(errorMsg);
		Breadcrumbs breadcrumbs = Breadcrumbs.generateForStudy(study,
				Breadcrumbs.RESULTS);
		return status(httpStatus,
				views.html.jatos.result.studysStudyResults.render(studyList,
						loggedInUser, breadcrumbs, messages, study));
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
	 * Takes a string with a list of StudyResults and removes them all.
	 */
	@Transactional
	public Result remove(String studyResultIds) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".remove: studyResultIds " + studyResultIds
				+ ", " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		UserModel loggedInUser = userService.retrieveLoggedInUser();

		List<Long> studyResultIdList = resultService
				.extractResultIds(studyResultIds);
		List<StudyResult> studyResultList = getAllStudyResults(studyResultIdList);
		checkAllStudyResults(studyResultList, loggedInUser, true);

		for (StudyResult studyResult : studyResultList) {
			studyResultDao.removeStudyResult(studyResult);
		}
		return ok();
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
		studyService.checkStandardForStudy(study, studyId, loggedInUser);
		String dataAsJson = null;
		try {
			dataAsJson = jsonUtils.allStudyResultsForUI(study);
		} catch (IOException e) {
			String errorMsg = ErrorMessages.PROBLEM_GENERATING_JSON_DATA;
			jatosGuiExceptionThrower.throwAjax(errorMsg,
					Http.Status.INTERNAL_SERVER_ERROR);
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
		workerService.checkWorker(worker, workerId);

		List<StudyResult> allowedStudyResultList = getAllowedStudyResultList(
				loggedInUser, worker);
		String dataAsJson = null;
		try {
			dataAsJson = jsonUtils.allStudyResultsForUI(allowedStudyResultList);
		} catch (IOException e) {
			String errorMsg = ErrorMessages.PROBLEM_GENERATING_JSON_DATA;
			jatosGuiExceptionThrower.throwAjax(errorMsg,
					Http.Status.INTERNAL_SERVER_ERROR);
		}
		return ok(dataAsJson);
	}

	/**
	 * Generate the list of StudyResults that the logged-in user is allowed to
	 * see.
	 */
	private List<StudyResult> getAllowedStudyResultList(UserModel loggedInUser,
			Worker worker) {
		List<StudyResult> allowedStudyResultList = new ArrayList<StudyResult>();
		for (StudyResult studyResult : worker.getStudyResultList()) {
			if (studyResult.getStudy().hasMember(loggedInUser)) {
				allowedStudyResultList.add(studyResult);
			}
		}
		return allowedStudyResultList;
	}

	/**
	 * Ajax request
	 * 
	 * Returns all result data of ComponentResults belonging to StudyResults
	 * specified in the given string as text.
	 */
	@Transactional
	public Result exportData(String studyResultIds) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".exportData: studyResultIds "
				+ studyResultIds + ", " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		// Remove cookie of jQuery.fileDownload plugin
		response().discardCookie(ImportExport.JQDOWNLOAD_COOKIE_NAME);
		UserModel loggedInUser = userService.retrieveLoggedInUser();

		List<Long> studyResultIdList = resultService
				.extractResultIds(studyResultIds);
		List<StudyResult> studyResultList = getAllStudyResults(studyResultIdList);
		checkAllStudyResults(studyResultList, loggedInUser, false);
		String studyResultDataAsStr = getStudyResultData(studyResultList);

		response().setContentType("application/x-download");
		String filename = "results_" + DateUtils.getDateForFile(new Date())
				+ "." + IOUtils.TXT_FILE_SUFFIX;
		response().setHeader("Content-disposition",
				"attachment; filename=" + filename);
		// Set cookie for jQuery.fileDownload plugin
		response().setCookie(ImportExport.JQDOWNLOAD_COOKIE_NAME,
				ImportExport.JQDOWNLOAD_COOKIE_CONTENT);
		return ok(studyResultDataAsStr);
	}

	/**
	 * Put all ComponentResult's data into a String each in a separate line.
	 */
	private String getStudyResultData(List<StudyResult> studyResultList)
			throws JatosGuiException {
		StringBuilder sb = new StringBuilder();
		for (StudyResult studyResult : studyResultList) {
			Iterator<ComponentResult> iterator = studyResult
					.getComponentResultList().iterator();
			while (iterator.hasNext()) {
				ComponentResult componentResult = iterator.next();
				String data = componentResult.getData();
				if (data != null) {
					sb.append(data);
					if (iterator.hasNext()) {
						sb.append("\n");
					}
				}
			}
		}
		return sb.toString();
	}

	/**
	 * Get all StudyResults or throw a JatosGuiException if one doesn't exist.
	 */
	private List<StudyResult> getAllStudyResults(List<Long> studyResultIdList)
			throws JatosGuiException {
		List<StudyResult> studyResultList = new ArrayList<>();
		for (Long studyResultId : studyResultIdList) {
			StudyResult studyResult = studyResultDao.findById(studyResultId);
			if (studyResult == null) {
				String errorMsg = ErrorMessages
						.studyResultNotExist(studyResultId);
				jatosGuiExceptionThrower.throwAjax(errorMsg,
						Http.Status.NOT_FOUND);
			}
			studyResultList.add(studyResult);
		}
		return studyResultList;
	}

	private void checkAllStudyResults(List<StudyResult> studyResultList,
			UserModel loggedInUser, boolean studyMustNotBeLocked)
			throws JatosGuiException {
		for (StudyResult studyResult : studyResultList) {
			StudyModel study = studyResult.getStudy();
			studyService.checkStandardForStudy(study, study.getId(),
					loggedInUser);
			if (studyMustNotBeLocked) {
				studyService.checkStudyLocked(study);
			}
		}
	}

}
