package controllers;

import java.io.IOException;
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
import play.mvc.SimpleResult;
import services.ErrorMessages;
import services.JsonUtils;
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

		String tableDataUrl = routes.StudyResults.tableDataByStudy(studyId)
				.url();
		String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
				Breadcrumbs.getHomeBreadcrumb(),
				Breadcrumbs.getStudyBreadcrumb(study), "Results");
		return status(httpStatus,
				views.html.mecharg.result.studysStudyResults.render(studyList,
						loggedInUser, breadcrumbs, study, errorMsg));
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
				ControllerUtils.throwWorkerException(errorMsg,
						Http.Status.NOT_FOUND, workerId);
			}
			if (studyResult.getWorkerId() != workerId) {
				String errorMsg = ErrorMessages.studyResultNotFromWorker(
						studyResultId, workerId);
				ControllerUtils.throwWorkerException(errorMsg,
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
				SimpleResult result = notFound(errorMsg);
				throw new ResultException(result, errorMsg);
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
		Logger.info(CLASS_NAME + ".tableData: studyId " + studyId + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		StudyModel study = StudyModel.findById(studyId);
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		ControllerUtils.checkStandardForStudy(study, studyId, loggedInUser);
		String dataAsJson = null;
		try {
			dataAsJson = JsonUtils.allStudyResultsForUI(study);
		} catch (IOException e) {
			return internalServerError(ErrorMessages.PROBLEM_GENERATING_JSON_DATA);
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
			return internalServerError(ErrorMessages.PROBLEM_GENERATING_JSON_DATA);
		}
		return ok(dataAsJson);
	}

	@Transactional
	public static Result exportData(String studyResultIds)
			throws ResultException {
		Logger.info(CLASS_NAME + ".exportData: studyResultIds "
				+ studyResultIds + ", " + "logged-in user's email "
				+ session(Users.COOKIE_EMAIL));
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();

		String studyResultDataAsStr = getStudyResultData(studyResultIds,
				loggedInUser);
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
				SimpleResult result = notFound(errorMsg);
				throw new ResultException(result, errorMsg);
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
