package controllers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import models.StudyModel;
import models.UserModel;
import models.results.ComponentResult;
import models.results.StudyResult;
import models.workers.Worker;
import play.Logger;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.Breadcrumbs;
import services.DateUtils;
import services.ErrorMessages;
import services.IOUtils;
import services.JsonUtils;
import services.Messages;
import services.PersistanceUtils;
import exceptions.ResultException;

/**
 * Controller for actions around StudyResults in the JATOS GUI.
 * 
 * @author Kristian Lange
 */
public class StudyResults extends Controller {

	private static final String CLASS_NAME = StudyResults.class.getSimpleName();

	/**
	 * Shows view with all StudyResults of a study.
	 */
	@Transactional
	public static Result index(Long studyId, String errorMsg, int httpStatus)
			throws ResultException {
		Logger.info(CLASS_NAME + ".index: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		List<StudyModel> studyList = StudyModel.findAllByUser(loggedInUser
				.getEmail());
		ControllerUtils.checkStandardForStudy(study, studyId, loggedInUser);

		Messages messages = new Messages().error(errorMsg);
		Breadcrumbs breadcrumbs = Breadcrumbs.generateForStudy(study,
				Breadcrumbs.RESULTS);
		return status(httpStatus,
				views.html.jatos.result.studysStudyResults.render(studyList,
						loggedInUser, breadcrumbs, messages, study));
	}

	@Transactional
	public static Result index(Long studyId, String errorMsg)
			throws ResultException {
		return index(studyId, errorMsg, Http.Status.OK);
	}

	@Transactional
	public static Result index(Long studyId) throws ResultException {
		return index(studyId, null, Http.Status.OK);
	}

	/**
	 * Ajax request
	 * 
	 * Takes a string with a list of StudyResults and removes them all.
	 */
	@Transactional
	public static Result remove(String studyResultIds) throws ResultException {
		Logger.info(CLASS_NAME + ".remove: studyResultIds " + studyResultIds
				+ ", " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();

		List<Long> studyResultIdList = ControllerUtils
				.extractResultIds(studyResultIds);
		List<StudyResult> studyResultList = getAllStudyResults(studyResultIdList);
		checkAllStudyResults(studyResultList, loggedInUser, true);

		for (StudyResult studyResult : studyResultList) {
			PersistanceUtils.removeStudyResult(studyResult);
		}
		return ok();
	}

	/**
	 * Ajax request
	 * 
	 * Returns all StudyResults of a study in JSON format.
	 */
	@Transactional
	public static Result tableDataByStudy(Long studyId) throws ResultException {
		Logger.info(CLASS_NAME + ".tableDataByStudy: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		ControllerUtils.checkStandardForStudy(study, studyId, loggedInUser);
		String dataAsJson = null;
		try {
			dataAsJson = JsonUtils.allStudyResultsForUI(study);
		} catch (IOException e) {
			String errorMsg = ErrorMessages.PROBLEM_GENERATING_JSON_DATA;
			ControllerUtils.throwAjaxResultException(errorMsg,
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
	public static Result tableDataByWorker(Long workerId)
			throws ResultException {
		Logger.info(CLASS_NAME + ".tableDataByWorker: workerId " + workerId
				+ ", " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		Worker worker = Worker.findById(workerId);
		ControllerUtils.checkWorker(worker, workerId);

		List<StudyResult> allowedStudyResultList = getAllowedStudyResultList(
				loggedInUser, worker);
		String dataAsJson = null;
		try {
			dataAsJson = JsonUtils.allStudyResultsForUI(allowedStudyResultList);
		} catch (IOException e) {
			String errorMsg = ErrorMessages.PROBLEM_GENERATING_JSON_DATA;
			ControllerUtils.throwAjaxResultException(errorMsg,
					Http.Status.INTERNAL_SERVER_ERROR);
		}
		return ok(dataAsJson);
	}

	/**
	 * Generate the list of StudyResults that the logged-in user is allowed to
	 * see.
	 */
	private static List<StudyResult> getAllowedStudyResultList(
			UserModel loggedInUser, Worker worker) {
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
	public static Result exportData(String studyResultIds)
			throws ResultException {
		Logger.info(CLASS_NAME + ".exportData: studyResultIds "
				+ studyResultIds + ", " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		// Remove cookie of jQuery.fileDownload plugin
		response().discardCookie(ControllerUtils.JQDOWNLOAD_COOKIE_NAME);
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();

		List<Long> studyResultIdList = ControllerUtils
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
		response().setCookie(ControllerUtils.JQDOWNLOAD_COOKIE_NAME,
				ControllerUtils.JQDOWNLOAD_COOKIE_CONTENT);
		return ok(studyResultDataAsStr);
	}

	/**
	 * Put all ComponentResult's data into a String each in a separate line.
	 */
	private static String getStudyResultData(List<StudyResult> studyResultList)
			throws ResultException {
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
	 * Get all StudyResults or throw a ResultException if one doesn't exist.
	 */
	private static List<StudyResult> getAllStudyResults(
			List<Long> studyResultIdList) throws ResultException {
		List<StudyResult> studyResultList = new ArrayList<>();
		for (Long studyResultId : studyResultIdList) {
			StudyResult studyResult = StudyResult.findById(studyResultId);
			if (studyResult == null) {
				String errorMsg = ErrorMessages
						.studyResultNotExist(studyResultId);
				ControllerUtils.throwAjaxResultException(errorMsg,
						Http.Status.NOT_FOUND);
			}
			studyResultList.add(studyResult);
		}
		return studyResultList;
	}

	private static void checkAllStudyResults(List<StudyResult> studyResultList,
			UserModel loggedInUser, boolean studyMustNotBeLocked)
			throws ResultException {
		for (StudyResult studyResult : studyResultList) {
			StudyModel study = studyResult.getStudy();
			ControllerUtils.checkStandardForStudy(study, study.getId(),
					loggedInUser);
			if (studyMustNotBeLocked) {
				ControllerUtils.checkStudyLocked(study);
			}
		}
	}

}
