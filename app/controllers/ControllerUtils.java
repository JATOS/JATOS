package controllers;

import java.util.List;

import models.ComponentModel;
import models.StudyModel;
import models.UserModel;
import models.workers.Worker;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.SimpleResult;
import services.ErrorMessages;
import exceptions.ResultException;

public class ControllerUtils extends Controller {

	/**
	 * Check if the request was made via Ajax or not.
	 */
	public static Boolean isAjax() {
		String requestWithHeader = "X-Requested-With";
		String requestWithHeaderValueForAjax = "XMLHttpRequest";
		String[] value = request().headers().get(requestWithHeader);
		return value != null && value.length > 0
				&& value[0].equals(requestWithHeaderValueForAjax);
	}

	public static void checkStudyLocked(StudyModel study)
			throws ResultException {
		if (study.isLocked()) {
			String errorMsg = ErrorMessages.studyLocked(study.getId());
			SimpleResult result = (SimpleResult) Studies.index(study.getId(),
					errorMsg, Http.Status.FORBIDDEN);
			throw new ResultException(result, errorMsg);
		}
	}

	public static void checkStudyLockedAjax(StudyModel study)
			throws ResultException {
		if (study.isLocked()) {
			String errorMsg = ErrorMessages.studyLocked(study.getId());
			SimpleResult result = forbidden(errorMsg);
			throw new ResultException(result, errorMsg);
		}
	}

	public static void checkStandardForStudy(StudyModel study, Long studyId,
			UserModel loggedInUser, List<StudyModel> studyList)
			throws ResultException {
		if (study == null) {
			String errorMsg = ErrorMessages.studyNotExist(studyId);
			SimpleResult result = (SimpleResult) Home.home(errorMsg,
					Http.Status.BAD_REQUEST);
			throw new ResultException(result, errorMsg);
		}
		if (!study.hasMember(loggedInUser)) {
			String errorMsg = ErrorMessages.notMember(loggedInUser.getName(),
					loggedInUser.getEmail(), studyId, study.getTitle());
			SimpleResult result = (SimpleResult) Home.home(errorMsg,
					Http.Status.FORBIDDEN);
			throw new ResultException(result, errorMsg);
		}
	}

	public static void checkStandardForStudyAjax(StudyModel study,
			Long studyId, UserModel loggedInUser) throws ResultException {
		if (study == null) {
			String errorMsg = ErrorMessages.studyNotExist(studyId);
			SimpleResult result = badRequest(errorMsg);
			throw new ResultException(result, errorMsg);
		}
		if (!study.hasMember(loggedInUser)) {
			String errorMsg = ErrorMessages.notMember(loggedInUser.getName(),
					loggedInUser.getEmail(), study.getId(), study.getTitle());
			SimpleResult result = forbidden(errorMsg);
			throw new ResultException(result, errorMsg);
		}
	}

	public static void checkStandardForComponents(Long studyId,
			Long componentId, StudyModel study, List<StudyModel> studyList,
			UserModel loggedInUser, ComponentModel component)
			throws ResultException {
		ControllerUtils.checkStandardForStudy(study, studyId, loggedInUser,
				studyList);
		if (component == null) {
			String errorMsg = ErrorMessages.componentNotExist(componentId);
			SimpleResult result = (SimpleResult) Home.home(errorMsg,
					Http.Status.BAD_REQUEST);
			throw new ResultException(result, errorMsg);
		}
		if (!component.getStudy().getId().equals(study.getId())) {
			String errorMsg = ErrorMessages.componentNotBelongToStudy(studyId,
					componentId);
			SimpleResult result = (SimpleResult) Home.home(errorMsg,
					Http.Status.BAD_REQUEST);
			throw new ResultException(result, errorMsg);
		}
	}

	public static void checkStandardForComponentsAjax(Long studyId,
			Long componentId, StudyModel study, UserModel loggedInUser,
			ComponentModel component) throws ResultException {
		ControllerUtils.checkStandardForStudyAjax(study, studyId, loggedInUser);
		if (component == null) {
			String errorMsg = ErrorMessages.componentNotExist(componentId);
			SimpleResult result = badRequest(errorMsg);
			throw new ResultException(result, errorMsg);
		}
		if (!component.getStudy().getId().equals(study.getId())) {
			String errorMsg = ErrorMessages.componentNotBelongToStudy(studyId,
					componentId);
			SimpleResult result = badRequest(errorMsg);
			throw new ResultException(result, errorMsg);
		}
	}

	public static void checkWorker(Worker worker, Long workerId)
			throws ResultException {
		if (worker == null) {
			String errorMsg = ErrorMessages.workerNotExist(workerId);
			SimpleResult result = (SimpleResult) Home.home(errorMsg,
					Http.Status.BAD_REQUEST);
			throw new ResultException(result, errorMsg);
		}
	}

	public static UserModel getUser(String email) throws ResultException {
		UserModel user = UserModel.findByEmail(email);
		if (user == null) {
			String errorMsg = ErrorMessages.userNotExist(email);
			SimpleResult result = (SimpleResult) Home.home(errorMsg,
					Http.Status.BAD_REQUEST);
			throw new ResultException(result, errorMsg);
		}
		return user;
	}

	public static UserModel getLoggedInUser() throws ResultException {
		UserModel loggedInUser = UserModel
				.findByEmail(session(Users.COOKIE_EMAIL));
		if (loggedInUser == null) {
			String errorMsg = ErrorMessages.NO_USER_LOGGED_IN;
			throw new ResultException(redirect(routes.Authentication.login()),
					errorMsg);
		}
		return loggedInUser;
	}

	public static UserModel getLoggedInUserAjax() throws ResultException {
		UserModel loggedInUser = UserModel
				.findByEmail(session(Users.COOKIE_EMAIL));
		if (loggedInUser == null) {
			String errorMsg = ErrorMessages.NO_USER_LOGGED_IN;
			SimpleResult result = badRequest(errorMsg);
			throw new ResultException(result, errorMsg);
		}
		return loggedInUser;
	}

}
