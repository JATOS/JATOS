package services.gui;

import java.util.List;

import models.ComponentModel;
import models.StudyModel;
import models.UserModel;
import play.api.mvc.Call;
import play.data.Form;
import play.data.validation.ValidationError;
import play.mvc.Results;
import play.mvc.SimpleResult;
import services.RequestScopeMessaging;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import controllers.gui.ControllerUtils;
import controllers.gui.Home;
import controllers.gui.Studies;
import controllers.gui.Workers;
import exceptions.gui.JatosGuiException;

/**
 * Class with convenience methods to throw a {@link JatosGuiException}
 * 
 * @author Kristian Lange
 */
@Singleton
public class JatosGuiExceptionThrower {

	private final Provider<Home> homeProvider;
	private final Provider<Studies> studiesProvider;
	private final Provider<Workers> workersProvider;

	@Inject
	JatosGuiExceptionThrower(Provider<Workers> workersProvider,
			Provider<Studies> studiesProvider, Provider<Home> homeProvider) {
		this.homeProvider = homeProvider;
		this.studiesProvider = studiesProvider;
		this.workersProvider = workersProvider;
	}

	/**
	 * Throws a JatosGuiException for an Ajax request (doesn't return a view)
	 * with the given error msg and HTTP status.
	 */
	public void throwAjax(String errorMsg, int httpStatus)
			throws JatosGuiException {
		SimpleResult result = Results.status(httpStatus, errorMsg);
		throw new JatosGuiException(result, errorMsg);
	}

	/**
	 * Throws a JatosGuiException that either redirects to the given call if
	 * it's a non-Ajax request - or returns a HTTP code FORBIDDEN with an error
	 * msg if it's a Ajax request.
	 */
	public void throwRedirectOrForbidden(Call call, String errorMsg)
			throws JatosGuiException {
		SimpleResult result;
		if (ControllerUtils.isAjax()) {
			result = Results.forbidden(errorMsg);
		} else {
			result = Results.redirect(call);
		}
		throw new JatosGuiException(result, errorMsg);
	}

	/**
	 * Throws a JatosGuiException with the given error msg and HTTP status. If
	 * non Ajax it shows home view. Distinguishes between normal and Ajax
	 * request.
	 */
	public void throwHome(String errorMsg, int httpStatus)
			throws JatosGuiException {
		SimpleResult result = null;
		if (ControllerUtils.isAjax()) {
			result = Results.status(httpStatus, errorMsg);
		} else {
			RequestScopeMessaging.error(errorMsg);
			result = (SimpleResult) homeProvider.get().home(httpStatus);
		}
		throw new JatosGuiException(result, errorMsg);
	}

	/**
	 * Throws a JatosGuiException with the given error msg and HTTP status. If
	 * non Ajax it shows study's index view. Distinguishes between normal and
	 * Ajax request.
	 */
	public void throwStudies(String errorMsg, int httpStatus, Long studyId)
			throws JatosGuiException {
		SimpleResult result = null;
		if (ControllerUtils.isAjax()) {
			result = Results.status(httpStatus, errorMsg);
		} else {
			RequestScopeMessaging.error(errorMsg);
			result = (SimpleResult) studiesProvider.get().index(studyId,
					httpStatus);
		}
		throw new JatosGuiException(result, errorMsg);
	}

	/**
	 * Throws a JatosGuiException with the given error msg and HTTP status. If
	 * non Ajax it study's change members view. Distinguishes between normal and
	 * Ajax request.
	 */
	public void throwChangeMemberOfStudies(String errorMsg, int httpStatus,
			Long studyId) throws JatosGuiException {
		SimpleResult result = null;
		if (ControllerUtils.isAjax()) {
			result = Results.status(httpStatus, errorMsg);
		} else {
			RequestScopeMessaging.error(errorMsg);
			result = (SimpleResult) studiesProvider.get().changeMembers(
					studyId, httpStatus);
		}
		throw new JatosGuiException(result, errorMsg);
	}

	/**
	 * Throws a JatosGuiException with the given error msg and HTTP status. If
	 * non Ajax it shows worker's index view. Distinguishes between normal and
	 * Ajax request.
	 */
	public void throwWorker(String errorMsg, int httpStatus, Long workerId)
			throws JatosGuiException {
		SimpleResult result = null;
		if (ControllerUtils.isAjax()) {
			result = Results.status(httpStatus, errorMsg);
		} else {
			RequestScopeMessaging.error(errorMsg);
			result = (SimpleResult) workersProvider.get().index(workerId,
					httpStatus);
		}
		throw new JatosGuiException(result, errorMsg);
	}

	/**
	 * Throws a JatosGuiException with the given error messages (within the
	 * form) and HTTP status. If non Ajax it shows study's edit view.
	 * Distinguishes between normal and Ajax request.
	 */
	public void throwEditStudy(List<StudyModel> studyList,
			UserModel loggedInUser, Form<StudyModel> form,
			List<ValidationError> errorList, int httpStatus,
			String breadcrumbs, Call submitAction, boolean studyIsLocked)
			throws JatosGuiException {
		SimpleResult result = null;
		if (ControllerUtils.isAjax()) {
			result = Results.status(httpStatus);
		} else {
			if (errorList != null) {
				for (ValidationError error : errorList) {
					form.reject(error);
				}
			}
			result = Results.status(httpStatus, views.html.gui.study.edit
					.render(studyList, loggedInUser, breadcrumbs,
							submitAction, form, studyIsLocked));
		}
		throw new JatosGuiException(result);
	}

	/**
	 * Throws a JatosGuiException with the given error messages (within the
	 * form) and HTTP status. If non Ajax it shows component's edit view.
	 * Distinguishes between normal and Ajax request.
	 */
	public void throwEditComponent(List<StudyModel> studyList,
			UserModel loggedInUser, Form<ComponentModel> form, int httpStatus,
			String breadcrumbs, Call submitAction, StudyModel study)
			throws JatosGuiException {
		SimpleResult result = null;
		if (ControllerUtils.isAjax()) {
			result = Results.status(httpStatus);
		} else {
			result = Results.status(httpStatus, views.html.gui.component.edit
					.render(studyList, loggedInUser, breadcrumbs,
							submitAction, form, study));
		}
		throw new JatosGuiException(result);
	}

	/**
	 * Throws a JatosGuiException with the given error messages (within the
	 * form) and HTTP status. If non Ajax it shows create user view.
	 * Distinguishes between normal and Ajax request.
	 */
	public void throwCreateUser(List<StudyModel> studyList,
			UserModel loggedInUser, Form<UserModel> form,
			List<ValidationError> errorList, int httpStatus)
			throws JatosGuiException {
		SimpleResult result = null;
		if (ControllerUtils.isAjax()) {
			result = Results.status(httpStatus);
		} else {
			if (errorList != null) {
				for (ValidationError error : errorList) {
					form.reject(error);
				}
			}
			String breadcrumbs = Breadcrumbs.generateForHome("New User");
			result = Results.status(httpStatus, views.html.gui.user.create
					.render(studyList, loggedInUser, breadcrumbs, form));
		}
		throw new JatosGuiException(result);
	}

	/**
	 * Throws a JatosGuiException with the given error messages (within the
	 * form) and HTTP status. If non Ajax it shows edit user view. Distinguishes
	 * between normal and Ajax request.
	 */
	public void throwEditUser(List<StudyModel> studyList,
			UserModel loggedInUser, Form<UserModel> form, UserModel user,
			int httpStatus) throws JatosGuiException {
		SimpleResult result = null;
		if (ControllerUtils.isAjax()) {
			result = Results.status(httpStatus);
		} else {
			String breadcrumbs = Breadcrumbs.generateForUser(user,
					"Edit Profile");
			result = Results.status(httpStatus, views.html.gui.user.editProfile
					.render(studyList, loggedInUser, breadcrumbs, user,
							form));
		}
		throw new JatosGuiException(result);
	}

	/**
	 * Throws a JatosGuiException with the given error messages (within the
	 * form) and HTTP status. If non Ajax it shows change password view.
	 * Distinguishes between normal and Ajax request.
	 */
	public void throwChangePasswordUser(List<StudyModel> studyList,
			UserModel loggedInUser, Form<UserModel> form,
			List<ValidationError> errorList, int httpStatus, UserModel user)
			throws JatosGuiException {
		SimpleResult result = null;
		if (ControllerUtils.isAjax()) {
			result = Results.status(httpStatus);
		} else {
			if (errorList != null) {
				for (ValidationError error : errorList) {
					form.reject(error);
				}
			}
			String breadcrumbs = Breadcrumbs.generateForUser(user,
					"Change Password");
			result = Results.status(httpStatus,
					views.html.gui.user.changePassword.render(studyList,
							loggedInUser, breadcrumbs, form));
		}
		throw new JatosGuiException(result);
	}

}
