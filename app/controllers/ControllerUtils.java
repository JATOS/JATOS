package controllers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import models.ComponentModel;
import models.StudyModel;
import models.UserModel;
import models.results.StudyResult;
import models.workers.Worker;
import play.api.mvc.Call;
import play.data.Form;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.SimpleResult;
import services.Breadcrumbs;
import services.ErrorMessages;
import exceptions.ResultException;

public class ControllerUtils extends Controller {

	public static final String JQDOWNLOAD_COOKIE_NAME = "Set-Cookie";
	public static final String JQDOWNLOAD_COOKIE_CONTENT = "fileDownload=true; path=/";

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
			SimpleResult result = null;
			if (isAjax()) {
				result = forbidden(errorMsg);
			} else {
				result = redirect(routes.Studies.index(study.getId(), null));
			}
			throw new ResultException(result, errorMsg);
		}
	}

	public static void checkStandardForStudy(StudyModel study, Long studyId,
			UserModel loggedInUser) throws ResultException {
		if (study == null) {
			String errorMsg = ErrorMessages.studyNotExist(studyId);
			throwHomeResultException(errorMsg, Http.Status.BAD_REQUEST);
		}
		if (!study.hasMember(loggedInUser)) {
			String errorMsg = ErrorMessages.studyNotMember(loggedInUser.getName(),
					loggedInUser.getEmail(), studyId, study.getTitle());
			SimpleResult result = null;
			if (isAjax()) {
				result = forbidden(errorMsg);
			} else {
				result = (SimpleResult) Home.home(errorMsg,
						Http.Status.FORBIDDEN);
			}
			throw new ResultException(result, errorMsg);
		}
	}

	public static void checkStandardForComponents(Long studyId,
			Long componentId, StudyModel study, UserModel loggedInUser,
			ComponentModel component) throws ResultException {
		ControllerUtils.checkStandardForStudy(study, studyId, loggedInUser);
		if (component == null) {
			String errorMsg = ErrorMessages.componentNotExist(componentId);
			throwHomeResultException(errorMsg, Http.Status.BAD_REQUEST);
		}
		if (!component.getStudy().getId().equals(study.getId())) {
			String errorMsg = ErrorMessages.componentNotBelongToStudy(studyId,
					componentId);
			throwHomeResultException(errorMsg, Http.Status.BAD_REQUEST);
		}
	}

	public static void checkWorker(Worker worker, Long workerId)
			throws ResultException {
		if (worker == null) {
			String errorMsg = ErrorMessages.workerNotExist(workerId);
			throwHomeResultException(errorMsg, Http.Status.BAD_REQUEST);
		}
	}

	public static void checkUserLoggedIn(UserModel user, UserModel loggedInUser)
			throws ResultException {
		if (!user.getEmail().equals(loggedInUser.getEmail())) {
			String errorMsg = ErrorMessages.mustBeLoggedInAsUser(user);
			throwHomeResultException(errorMsg, Http.Status.FORBIDDEN);
		}
	}

	public static void throwAjaxResultException(String errorMsg, int httpStatus)
			throws ResultException {
		SimpleResult result = status(httpStatus, errorMsg);
		throw new ResultException(result, errorMsg);
	}

	public static void throwWorkerResultException(String errorMsg,
			int httpStatus, Long workerId) throws ResultException {
		SimpleResult result = null;
		if (isAjax()) {
			result = status(httpStatus, errorMsg);
		} else {
			result = (SimpleResult) Workers.index(workerId, errorMsg,
					httpStatus);
		}
		throw new ResultException(result, errorMsg);
	}

	public static void throwHomeResultException(String errorMsg, int httpStatus)
			throws ResultException {
		SimpleResult result = null;
		if (isAjax()) {
			result = status(httpStatus, errorMsg);
		} else {
			result = (SimpleResult) Home.home(errorMsg, httpStatus);
		}
		throw new ResultException(result, errorMsg);
	}

	public static void throwStudiesResultException(String errorMsg,
			int httpStatus, Long studyId) throws ResultException {
		SimpleResult result = null;
		if (isAjax()) {
			result = status(httpStatus, errorMsg);
		} else {
			result = (SimpleResult) Studies
					.index(studyId, errorMsg, httpStatus);
		}
		throw new ResultException(result, errorMsg);
	}

	/**
	 * Error messages are within the form.
	 */
	public static void throwEditStudyResultException(
			List<StudyModel> studyList, UserModel loggedInUser,
			Form<StudyModel> form, int httpStatus, Breadcrumbs breadcrumbs,
			Call submitAction, boolean studyIsLocked) throws ResultException {
		SimpleResult result = null;
		if (isAjax()) {
			result = status(httpStatus);
		} else {
			result = status(httpStatus, views.html.jatos.study.edit.render(
					studyList, loggedInUser, breadcrumbs, null, submitAction,
					form, studyIsLocked));
		}
		throw new ResultException(result);
	}

	/**
	 * Error messages are within the form.
	 */
	public static void throwEditComponentResultException(
			List<StudyModel> studyList, UserModel loggedInUser,
			Form<ComponentModel> form, int httpStatus, Breadcrumbs breadcrumbs,
			Call submitAction, StudyModel study) throws ResultException {
		SimpleResult result = null;
		if (isAjax()) {
			result = status(httpStatus);
		} else {
			result = status(httpStatus,
					views.html.jatos.component.edit.render(studyList,
							loggedInUser, breadcrumbs, null, submitAction,
							form, study));
		}
		throw new ResultException(result);
	}

	/**
	 * Error messages are within the form.
	 */
	public static void throwCreateUserResultException(
			List<StudyModel> studyList, UserModel loggedInUser,
			Form<UserModel> form, int httpStatus) throws ResultException {
		SimpleResult result = null;
		if (isAjax()) {
			result = status(httpStatus);
		} else {
			Breadcrumbs breadcrumbs = Breadcrumbs.generateForHome("New User");
			result = status(httpStatus, views.html.jatos.user.create.render(
					studyList, loggedInUser, breadcrumbs, null, form));
		}
		throw new ResultException(result);
	}

	/**
	 * Error messages are within the form.
	 */
	public static void throwEditUserResultException(List<StudyModel> studyList,
			UserModel loggedInUser, Form<UserModel> form, UserModel user,
			int httpStatus) throws ResultException {
		SimpleResult result = null;
		if (isAjax()) {
			result = status(httpStatus);
		} else {
			Breadcrumbs breadcrumbs = Breadcrumbs.generateForUser(user,
					"Edit Profile");
			result = status(httpStatus,
					views.html.jatos.user.editProfile.render(studyList,
							loggedInUser, breadcrumbs, null, user, form));
		}
		throw new ResultException(result);
	}

	/**
	 * Error messages are within the form.
	 */
	public static void throwChangePasswordUserResultException(
			List<StudyModel> studyList, UserModel loggedInUser,
			Form<UserModel> form, int httpStatus, UserModel user)
			throws ResultException {
		SimpleResult result = null;
		if (isAjax()) {
			result = status(httpStatus);
		} else {
			Breadcrumbs breadcrumbs = Breadcrumbs.generateForUser(user,
					"Change Password");
			result = status(httpStatus,
					views.html.jatos.user.changePassword.render(studyList,
							loggedInUser, breadcrumbs, null, form));
		}
		throw new ResultException(result);
	}

	public static void throwChangeMemberOfStudiesResultException(
			String errorMsg, int httpStatus, Long studyId)
			throws ResultException {
		SimpleResult result = null;
		if (isAjax()) {
			result = status(httpStatus, errorMsg);
		} else {
			result = (SimpleResult) Studies.changeMembers(studyId, errorMsg,
					httpStatus);
		}
		throw new ResultException(result, errorMsg);
	}

	public static void throwStudyResultsResultException(String errorMsg,
			int httpStatus, Long studyId) throws ResultException {
		SimpleResult result = null;
		if (isAjax()) {
			result = status(httpStatus, errorMsg);
		} else {
			result = (SimpleResult) StudyResults.index(studyId, errorMsg,
					httpStatus);
		}
		throw new ResultException(result, errorMsg);
	}

	public static void throwComponentResultsResultException(String errorMsg,
			int httpStatus, Long studyId, Long componentId)
			throws ResultException {
		SimpleResult result = null;
		if (isAjax()) {
			result = status(httpStatus, errorMsg);
		} else {
			result = (SimpleResult) ComponentResults.index(studyId,
					componentId, errorMsg, httpStatus);
		}
		throw new ResultException(result, errorMsg);
	}

	public static UserModel retrieveUser(String email) throws ResultException {
		UserModel user = UserModel.findByEmail(email);
		if (user == null) {
			String errorMsg = ErrorMessages.userNotExist(email);
			throwHomeResultException(errorMsg, Http.Status.BAD_REQUEST);
		}
		return user;
	}

	public static UserModel retrieveLoggedInUser() throws ResultException {
		UserModel loggedInUser = UserModel
				.findByEmail(session(Users.COOKIE_EMAIL));
		if (loggedInUser == null) {
			String errorMsg = ErrorMessages.NO_USER_LOGGED_IN;
			SimpleResult result = null;
			if (isAjax()) {
				result = badRequest(errorMsg);
			} else {
				result = (SimpleResult) redirect(routes.Authentication.login());
			}
			throw new ResultException(result, errorMsg);
		}
		return loggedInUser;
	}

	public static Set<Worker> retrieveWorkers(StudyModel study) {
		List<StudyResult> studyResultList = StudyResult.findAllByStudy(study);
		Set<Worker> workerSet = new HashSet<>();
		for (StudyResult studyResult : studyResultList) {
			workerSet.add(studyResult.getWorker());
		}
		return workerSet;
	}

	public static List<Long> extractResultIds(String resultIds)
			throws ResultException {
		String[] resultIdStrArray = resultIds.split(",");
		List<Long> resultIdList = new ArrayList<>();
		for (String idStr : resultIdStrArray) {
			try {
				if (idStr.isEmpty()) {
					continue;
				}
				resultIdList.add(Long.parseLong(idStr));
			} catch (NumberFormatException e) {
				String errorMsg = ErrorMessages.resultNotExist(idStr);
				SimpleResult result = notFound(errorMsg);
				throw new ResultException(result, errorMsg);
			}
		}
		if (resultIdList.size() < 1) {
			String errorMsg = ErrorMessages.NO_RESULTS_SELECTED;
			SimpleResult result = badRequest(errorMsg);
			throw new ResultException(result, errorMsg);
		}
		return resultIdList;
	}

}
