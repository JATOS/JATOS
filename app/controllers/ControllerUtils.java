package controllers;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import models.ComponentModel;
import models.StudyModel;
import models.UserDao;
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

import com.google.inject.Inject;

import exceptions.ResultException;

/**
 * Utility class for all JATOS Controllers (not Publix).
 * 
 * @author Kristian Lange
 */
public class ControllerUtils extends Controller {

	public static final String JQDOWNLOAD_COOKIE_NAME = "Set-Cookie";
	public static final String JQDOWNLOAD_COOKIE_CONTENT = "fileDownload=true; path=/";

	private final UserDao userDao;
	private final Home home;
	private final Workers workers;
	private final Studies studies;
	private final StudyResults studyResults;
	private final ComponentResults componentResults;

	@Inject
	public ControllerUtils(UserDao userDao, Workers workers, Studies studies,
			StudyResults studyResults, Home home, ComponentResults componentResults) {
		this.userDao = userDao;
		this.workers = workers;
		this.studies = studies;
		this.studyResults = studyResults;
		this.home = home;
		this.componentResults = componentResults;
	}

	/**
	 * Check if the request was made via Ajax or not.
	 */
	public Boolean isAjax() {
		String requestWithHeader = "X-Requested-With";
		String requestWithHeaderValueForAjax = "XMLHttpRequest";
		String[] value = request().headers().get(requestWithHeader);
		return value != null && value.length > 0
				&& value[0].equals(requestWithHeaderValueForAjax);
	}

	/**
	 * Same as {@link #getRefererUrl()} but returns the URL's String if the
	 * 'Referer' exists or "" otherwise.
	 */
	public String getReferer() throws ResultException {
		URL refererUrl = getRefererUrl();
		return (refererUrl != null) ? refererUrl.toString() : "";
	}

	/**
	 * Returns the request's referer without the path (only protocol, host,
	 * port). Sometimes (e.g. if JATOS is behind a proxy) this is the only way
	 * to get JATOS' absolute URL. If the 'Referer' isn't set in the header it
	 * returns null.
	 */
	public URL getRefererUrl() throws ResultException {
		URL jatosURL = null;
		try {
			String[] referer = request().headers().get("Referer");
			if (referer != null && referer.length > 0) {
				URL refererURL = new URL(referer[0]);
				jatosURL = new URL(refererURL.getProtocol(),
						refererURL.getHost(), refererURL.getPort(), "");
			}
		} catch (MalformedURLException e) {
			String errorMsg = ErrorMessages.COULDNT_GENERATE_JATOS_URL + " ("
					+ e.getMessage() + ")";
			throwHomeResultException(errorMsg, Http.Status.BAD_REQUEST);
		}
		return jatosURL;
	}

	/**
	 * Throws a ResultException if a study is locked. Distinguishes between
	 * normal and Ajax request.
	 */
	public void checkStudyLocked(StudyModel study) throws ResultException {
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

	/**
	 * Checks the study and throws a ResultException in case of a problem.
	 * Distinguishes between normal and Ajax request.
	 */
	public void checkStandardForStudy(StudyModel study, Long studyId,
			UserModel user) throws ResultException {
		if (study == null) {
			String errorMsg = ErrorMessages.studyNotExist(studyId);
			throwHomeResultException(errorMsg, Http.Status.BAD_REQUEST);
		}
		// Check that the user is a member of the study
		if (!study.hasMember(user)) {
			String errorMsg = ErrorMessages.studyNotMember(user.getName(),
					user.getEmail(), studyId, study.getTitle());
			SimpleResult result = null;
			if (isAjax()) {
				result = forbidden(errorMsg);
			} else {
				result = (SimpleResult) home.home(errorMsg,
						Http.Status.FORBIDDEN);
			}
			throw new ResultException(result, errorMsg);
		}
	}

	/**
	 * Checks the component of this study and throws a ResultException in case
	 * of a problem. Distinguishes between normal and Ajax request.
	 */
	public void checkStandardForComponents(Long studyId, Long componentId,
			StudyModel study, UserModel loggedInUser, ComponentModel component)
			throws ResultException {
		checkStandardForStudy(study, studyId, loggedInUser);
		if (component == null) {
			String errorMsg = ErrorMessages.componentNotExist(componentId);
			throwHomeResultException(errorMsg, Http.Status.BAD_REQUEST);
		}
		// Check component belongs to the study
		if (!component.getStudy().getId().equals(study.getId())) {
			String errorMsg = ErrorMessages.componentNotBelongToStudy(studyId,
					componentId);
			throwHomeResultException(errorMsg, Http.Status.BAD_REQUEST);
		}
	}

	/**
	 * Throws a ResultException in case the worker doesn't exist. Distinguishes
	 * between normal and Ajax request.
	 */
	public void checkWorker(Worker worker, Long workerId)
			throws ResultException {
		if (worker == null) {
			String errorMsg = ErrorMessages.workerNotExist(workerId);
			throwHomeResultException(errorMsg, Http.Status.BAD_REQUEST);
		}
	}

	/**
	 * Throws a ResultException in case the user's email isn't equal to the
	 * loggedInUser' email. Distinguishes between normal and Ajax request.
	 */
	public void checkUserLoggedIn(UserModel user, UserModel loggedInUser)
			throws ResultException {
		if (!user.getEmail().equals(loggedInUser.getEmail())) {
			String errorMsg = ErrorMessages.mustBeLoggedInAsUser(user);
			throwHomeResultException(errorMsg, Http.Status.FORBIDDEN);
		}
	}

	/**
	 * Throws a ResultException for an Ajax request (doesn't return a view) with
	 * the given error msg and HTTP status.
	 */
	public void throwAjaxResultException(String errorMsg, int httpStatus)
			throws ResultException {
		SimpleResult result = status(httpStatus, errorMsg);
		throw new ResultException(result, errorMsg);
	}

	/**
	 * Throws a ResultException with the given error msg and HTTP status. If non
	 * Ajax it shows worker's index view. Distinguishes between normal and Ajax
	 * request.
	 */
	public void throwWorkerResultException(String errorMsg, int httpStatus,
			Long workerId) throws ResultException {
		SimpleResult result = null;
		if (isAjax()) {
			result = status(httpStatus, errorMsg);
		} else {
			result = (SimpleResult) workers.index(workerId, errorMsg,
					httpStatus);
		}
		throw new ResultException(result, errorMsg);
	}

	/**
	 * Throws a ResultException with the given error msg and HTTP status. If non
	 * Ajax it shows home view. Distinguishes between normal and Ajax request.
	 */
	public void throwHomeResultException(String errorMsg, int httpStatus)
			throws ResultException {
		SimpleResult result = null;
		if (isAjax()) {
			result = status(httpStatus, errorMsg);
		} else {
			result = (SimpleResult) home.home(errorMsg, httpStatus);
		}
		throw new ResultException(result, errorMsg);
	}

	/**
	 * Throws a ResultException with the given error msg and HTTP status. If non
	 * Ajax it shows study's index view. Distinguishes between normal and Ajax
	 * request.
	 * 
	 * Difference between throwStudyResultsResultException and
	 * throwStudyResultException: First one throws a ResultException for a
	 * study's result page - second one for a study page.
	 */
	public void throwStudiesResultException(String errorMsg, int httpStatus,
			Long studyId) throws ResultException {
		SimpleResult result = null;
		if (isAjax()) {
			result = status(httpStatus, errorMsg);
		} else {
			result = (SimpleResult) studies
					.index(studyId, errorMsg, httpStatus);
		}
		throw new ResultException(result, errorMsg);
	}

	/**
	 * Throws a ResultException with the given error messages (within the form)
	 * and HTTP status. If non Ajax it shows study's edit view. Distinguishes
	 * between normal and Ajax request.
	 */
	public void throwEditStudyResultException(List<StudyModel> studyList,
			UserModel loggedInUser, Form<StudyModel> form, int httpStatus,
			Breadcrumbs breadcrumbs, Call submitAction, boolean studyIsLocked)
			throws ResultException {
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
	 * Throws a ResultException with the given error messages (within the form)
	 * and HTTP status. If non Ajax it shows component's edit view.
	 * Distinguishes between normal and Ajax request.
	 */
	public void throwEditComponentResultException(List<StudyModel> studyList,
			UserModel loggedInUser, Form<ComponentModel> form, int httpStatus,
			Breadcrumbs breadcrumbs, Call submitAction, StudyModel study)
			throws ResultException {
		SimpleResult result = null;
		if (isAjax()) {
			result = status(httpStatus);
		} else {
			result = status(httpStatus, views.html.jatos.component.edit.render(
					studyList, loggedInUser, breadcrumbs, null, submitAction,
					form, study));
		}
		throw new ResultException(result);
	}

	/**
	 * Throws a ResultException with the given error messages (within the form)
	 * and HTTP status. If non Ajax it shows create user view. Distinguishes
	 * between normal and Ajax request.
	 */
	public void throwCreateUserResultException(List<StudyModel> studyList,
			UserModel loggedInUser, Form<UserModel> form, int httpStatus)
			throws ResultException {
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
	 * Throws a ResultException with the given error messages (within the form)
	 * and HTTP status. If non Ajax it shows edit user view. Distinguishes
	 * between normal and Ajax request.
	 */
	public void throwEditUserResultException(List<StudyModel> studyList,
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
	 * Throws a ResultException with the given error messages (within the form)
	 * and HTTP status. If non Ajax it shows change password view. Distinguishes
	 * between normal and Ajax request.
	 */
	public void throwChangePasswordUserResultException(
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

	/**
	 * Throws a ResultException with the given error msg and HTTP status. If non
	 * Ajax it study's change members view. Distinguishes between normal and
	 * Ajax request.
	 */
	public void throwChangeMemberOfStudiesResultException(String errorMsg,
			int httpStatus, Long studyId) throws ResultException {
		SimpleResult result = null;
		if (isAjax()) {
			result = status(httpStatus, errorMsg);
		} else {
			result = (SimpleResult) studies.changeMembers(studyId, errorMsg,
					httpStatus);
		}
		throw new ResultException(result, errorMsg);
	}

	/**
	 * Throws a ResultException with the given error msg and HTTP status. If non
	 * Ajax it shows StudyResult's index view. Distinguishes between normal and
	 * Ajax request.
	 * 
	 * Difference between throwStudyResultsResultException and
	 * throwStudyResultException: First one throws a ResultException for a
	 * study's result page - second one for a study page.
	 */
	public void throwStudyResultsResultException(String errorMsg,
			int httpStatus, Long studyId) throws ResultException {
		SimpleResult result = null;
		if (isAjax()) {
			result = status(httpStatus, errorMsg);
		} else {
			result = (SimpleResult) studyResults.index(studyId, errorMsg,
					httpStatus);
		}
		throw new ResultException(result, errorMsg);
	}

	/**
	 * Throws a ResultException with the given error msg and HTTP status. If non
	 * Ajax it shows the ComponentResults.index() view. Distinguishes between
	 * normal and Ajax request.
	 */
	public void throwComponentResultsResultException(String errorMsg,
			int httpStatus, Long studyId, Long componentId)
			throws ResultException {
		SimpleResult result = null;
		if (isAjax()) {
			result = status(httpStatus, errorMsg);
		} else {
			result = (SimpleResult) componentResults.index(studyId,
					componentId, errorMsg, httpStatus);
		}
		throw new ResultException(result, errorMsg);
	}

	/**
	 * Retrieves the user with the given email form the DB. Throws a
	 * ResultException if it doesn't exist.
	 */
	public UserModel retrieveUser(String email) throws ResultException {
		UserModel user = userDao.findByEmail(email);
		if (user == null) {
			String errorMsg = ErrorMessages.userNotExist(email);
			throwHomeResultException(errorMsg, Http.Status.BAD_REQUEST);
		}
		return user;
	}

	/**
	 * Retrieves the user with the given email form the DB. Throws a
	 * ResultException if it doesn't exist. The ResultException will redirect to
	 * the login screen.
	 */
	public UserModel retrieveLoggedInUser() throws ResultException {
		String email = session(Users.SESSION_EMAIL);
		UserModel loggedInUser = null;
		if (email != null) {
			loggedInUser = userDao.findByEmail(email);
		}
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

	/**
	 * Retrieve all workers that did this study.
	 */
	public Set<Worker> retrieveWorkers(StudyModel study) {
		List<StudyResult> studyResultList = StudyResult.findAllByStudy(study);
		Set<Worker> workerSet = new HashSet<>();
		for (StudyResult studyResult : studyResultList) {
			workerSet.add(studyResult.getWorker());
		}
		return workerSet;
	}

	/**
	 * Parses a String with result IDs and returns them in a List<Long>. Throws
	 * a ResultException if an ID is not a number or if the original String
	 * dosn't contain any ID.
	 */
	public List<Long> extractResultIds(String resultIds) throws ResultException {
		String[] resultIdStrArray = resultIds.split(",");
		List<Long> resultIdList = new ArrayList<>();
		for (String idStr : resultIdStrArray) {
			try {
				if (idStr.isEmpty()) {
					continue;
				}
				resultIdList.add(Long.parseLong(idStr.trim()));
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
