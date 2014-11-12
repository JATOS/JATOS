package controllers;

import java.io.IOException;
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
import play.mvc.Security;
import services.DateUtils;
import services.ErrorMessages;
import services.IOUtils;
import services.JsonUtils;
import services.Messages;
import services.PersistanceUtils;
import exceptions.ResultException;

@Security.Authenticated(Secured.class)
public class StudyResults extends Controller {

	private static final String CLASS_NAME = StudyResults.class.getSimpleName();

	@Transactional
	public static Result index(Long studyId, String errorMsg, int httpStatus)
			throws ResultException {
		Logger.info(CLASS_NAME + ".index: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		List<StudyModel> studyList = StudyModel.findAllByUser(loggedInUser
				.getEmail());
		ControllerUtils.checkStandardForStudy(study, studyId, loggedInUser);

		Messages messages = new Messages().error(errorMsg);
		services.Breadcrumbs breadcrumbs = services.Breadcrumbs
				.generateForStudyResult(study, "Index");
		return status(httpStatus,
				views.html.mecharg.result.studysStudyResults.render(studyList,
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
	 * HTTP Ajax request
	 */
	@Transactional
	public static Result removeByWorker(Long workerId, String studyResultIds)
			throws ResultException {
		Logger.info(CLASS_NAME + ".removeByWorker: workerId " + workerId + ", "
				+ "studyResultIds " + studyResultIds + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		Worker worker = Worker.findById(workerId);
		ControllerUtils.checkWorker(worker, workerId);

		List<Long> studyResultIdList = ControllerUtils
				.extractResultIds(studyResultIds);
		for (Long studyResultId : studyResultIdList) {
			StudyResult studyResult = StudyResult.findById(studyResultId);
			if (studyResult == null) {
				String errorMsg = ErrorMessages
						.studyResultNotExist(studyResultId);
				ControllerUtils.throwWorkerResultException(errorMsg,
						Http.Status.NOT_FOUND, workerId);
			}
			if (studyResult.getWorkerId() != workerId) {
				String errorMsg = ErrorMessages.studyResultNotFromWorker(
						studyResultId, workerId);
				ControllerUtils.throwWorkerResultException(errorMsg,
						Http.Status.FORBIDDEN, workerId);
			}
			StudyModel resultsStudy = studyResult.getStudy();
			ControllerUtils.checkStandardForStudy(resultsStudy,
					resultsStudy.getId(), loggedInUser);
			ControllerUtils.checkStudyLocked(resultsStudy);
			PersistanceUtils.removeStudyResult(studyResult);
		}

		return ok();
	}

	/**
	 * HTTP Ajax request
	 */
	@Transactional
	public static Result remove(String studyResultIds) throws ResultException {
		Logger.info(CLASS_NAME + ".remove: studyResultIds " + studyResultIds
				+ ", " + "logged-in user's email "
				+ session(Users.COOKIE_EMAIL));
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();

		List<Long> studyResultIdList = ControllerUtils
				.extractResultIds(studyResultIds);
		for (Long studyResultId : studyResultIdList) {
			StudyResult studyResult = StudyResult.findById(studyResultId);
			if (studyResult == null) {
				String errorMsg = ErrorMessages
						.studyResultNotExist(studyResultId);
				ControllerUtils.throwAjaxResultException(errorMsg,
						Http.Status.NOT_FOUND);
			}
			StudyModel resultsStudy = studyResult.getStudy();
			ControllerUtils.checkStandardForStudy(resultsStudy,
					resultsStudy.getId(), loggedInUser);
			ControllerUtils.checkStudyLocked(resultsStudy);
			PersistanceUtils.removeStudyResult(studyResult);
		}

		return ok();
	}

	/**
	 * HTTP Ajax request
	 */
	@Transactional
	public static Result tableDataByStudy(Long studyId) throws ResultException {
		Logger.info(CLASS_NAME + ".tableDataByStudy: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
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
	 * HTTP Ajax request
	 */
	@Transactional
	public static Result tableDataByWorker(Long workerId)
			throws ResultException {
		Logger.info(CLASS_NAME + ".tableDataByWorker: workerId " + workerId
				+ ", " + "logged-in user's email "
				+ session(Users.COOKIE_EMAIL));
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		Worker worker = Worker.findById(workerId);
		ControllerUtils.checkWorker(worker, workerId);

		String dataAsJson = null;
		try {
			dataAsJson = JsonUtils.allStudyResultsByWorkerForUI(worker,
					loggedInUser);
		} catch (IOException e) {
			String errorMsg = ErrorMessages.PROBLEM_GENERATING_JSON_DATA;
			ControllerUtils.throwAjaxResultException(errorMsg,
					Http.Status.INTERNAL_SERVER_ERROR);
		}
		return ok(dataAsJson);
	}

	/**
	 * HTTP Ajax request
	 */
	@Transactional
	public static Result exportData(String studyResultIds)
			throws ResultException {
		Logger.info(CLASS_NAME + ".exportData: studyResultIds "
				+ studyResultIds + ", " + "logged-in user's email "
				+ session(Users.COOKIE_EMAIL));
		// Remove cookie of jQuery.fileDownload plugin
		response().discardCookie(ControllerUtils.JQDOWNLOAD_COOKIE_NAME);
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();

		String studyResultDataAsStr = getStudyResultData(studyResultIds,
				loggedInUser);

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

	private static String getStudyResultData(String studyResultIds,
			UserModel loggedInUser) throws ResultException {
		List<Long> studyResultIdList = ControllerUtils
				.extractResultIds(studyResultIds);

		// Put all ComponentResult's data into a String each in a separate line
		StringBuilder sb = new StringBuilder();
		for (Long studyResultId : studyResultIdList) {
			StudyResult studyResult = StudyResult.findById(studyResultId);
			if (studyResult == null) {
				String errorMsg = ErrorMessages
						.studyResultNotExist(studyResultId);
				ControllerUtils.throwAjaxResultException(errorMsg,
						Http.Status.NOT_FOUND);
			}
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

}
