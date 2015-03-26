package controllers.gui;

import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import models.StudyModel;
import models.UserModel;
import persistance.StudyDao;
import play.Logger;
import play.data.DynamicForm;
import play.data.Form;
import play.data.validation.ValidationError;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.gui.Breadcrumbs;
import services.gui.JatosGuiExceptionThrower;
import services.gui.UserService;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import controllers.gui.actionannotations.Authenticated;
import controllers.gui.actionannotations.JatosGui;
import exceptions.BadRequestException;
import exceptions.ForbiddenException;
import exceptions.gui.JatosGuiException;

/**
 * Controller with actions concerning users
 * 
 * @author Kristian Lange
 */
@JatosGui
@Authenticated
@Singleton
public class Users extends Controller {

	private static final String CLASS_NAME = Users.class.getSimpleName();

	public static final String SESSION_EMAIL = "email";

	private final JatosGuiExceptionThrower jatosGuiExceptionThrower;
	private final UserService userService;
	private final StudyDao studyDao;

	@Inject
	Users(JatosGuiExceptionThrower jatosGuiExceptionThrower,
			UserService userService, StudyDao studyDao) {
		this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
		this.userService = userService;
		this.studyDao = studyDao;
	}

	/**
	 * Shows the profile view of a user
	 */
	@Transactional
	public Result profile(String email) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".profile: " + "email " + email + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		UserModel user = null;
		try {
			user = userService.retrieveUser(email);
			userService.checkUserLoggedIn(user, loggedInUser);
		} catch (BadRequestException | ForbiddenException e) {
			jatosGuiExceptionThrower.throwRedirect(e,
					controllers.gui.routes.Home.home());
		}
		String breadcrumbs = Breadcrumbs.generateForUser(user);
		return ok(views.html.gui.user.profile.render(loggedInUser, breadcrumbs,
				user));
	}

	/**
	 * Shows a view with a form to create a new user.
	 */
	@Transactional
	public Result create() throws JatosGuiException {
		Logger.info(CLASS_NAME + ".create: " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		String breadcrumbs = Breadcrumbs.generateForHome(Breadcrumbs.NEW_USER);
		return ok(views.html.gui.user.create.render(loggedInUser, breadcrumbs,
				Form.form(UserModel.class)));
	}

	/**
	 * Handles post request of user create form.
	 */
	@Transactional
	public Result submit() throws UnsupportedEncodingException,
			NoSuchAlgorithmException {
		Logger.info(CLASS_NAME + ".submit: " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		Form<UserModel> form = Form.form(UserModel.class).bindFromRequest();
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		List<StudyModel> studyList = studyDao.findAllByUser(loggedInUser
				.getEmail());

		if (form.hasErrors()) {
			return showCreateUserAfterError(studyList, loggedInUser, form,
					null, Http.Status.BAD_REQUEST);
		}

		UserModel newUser = form.get();
		DynamicForm requestData = Form.form().bindFromRequest();
		String password = requestData.get(UserModel.PASSWORD);
		String passwordRepeat = requestData.get(UserModel.PASSWORD_REPEAT);
		List<ValidationError> errorList = userService.validateNewUser(newUser,
				password, passwordRepeat);
		if (!errorList.isEmpty()) {
			return showCreateUserAfterError(studyList, loggedInUser, form,
					errorList, Http.Status.BAD_REQUEST);
		}

		userService.createUser(newUser, password);
		return redirect(controllers.gui.routes.Home.home());
	}

	private Result showCreateUserAfterError(List<StudyModel> studyList,
			UserModel loggedInUser, Form<UserModel> form,
			List<ValidationError> errorList, int httpStatus) {
		if (ControllerUtils.isAjax()) {
			return status(httpStatus);
		} else {
			if (errorList != null) {
				for (ValidationError error : errorList) {
					form.reject(error);
				}
			}
			String breadcrumbs = Breadcrumbs.generateForHome("New User");
			return status(httpStatus, views.html.gui.user.create.render(
					loggedInUser, breadcrumbs, form));
		}
	}

	private Result showEditUserAfterError(List<StudyModel> studyList,
			UserModel loggedInUser, Form<UserModel> form, UserModel user,
			int httpStatus) {
		if (ControllerUtils.isAjax()) {
			return status(httpStatus);
		} else {
			String breadcrumbs = Breadcrumbs.generateForUser(user,
					"Edit Profile");
			return status(httpStatus, views.html.gui.user.editProfile.render(
					loggedInUser, breadcrumbs, user, form));
		}
	}

	private Result showChangePasswordAfterError(UserModel loggedInUser,
			Form<UserModel> form, List<ValidationError> errorList,
			int httpStatus, UserModel user) {
		if (ControllerUtils.isAjax()) {
			return status(httpStatus);
		} else {
			if (errorList != null) {
				for (ValidationError error : errorList) {
					form.reject(error);
				}
			}
			String breadcrumbs = Breadcrumbs.generateForUser(user,
					"Change Password");
			return status(httpStatus,
					views.html.gui.user.changePassword.render(loggedInUser,
							breadcrumbs, form));
		}
	}

	/**
	 * Shows view with form to edit a user profile.
	 */
	@Transactional
	public Result editProfile(String email) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".editProfile: " + "email " + email + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		UserModel user = null;
		try {
			user = userService.retrieveUser(email);
			userService.checkUserLoggedIn(user, loggedInUser);
		} catch (BadRequestException | ForbiddenException e) {
			jatosGuiExceptionThrower.throwHome(e);
		}
		Form<UserModel> form = Form.form(UserModel.class).fill(user);
		String breadcrumbs = Breadcrumbs.generateForUser(user,
				Breadcrumbs.EDIT_PROFILE);
		return ok(views.html.gui.user.editProfile.render(loggedInUser,
				breadcrumbs, user, form));
	}

	/**
	 * Handles post request of user edit profile form.
	 */
	@Transactional
	public Result submitEditedProfile(String email) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".submitEditedProfile: " + "email " + email
				+ ", " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		UserModel user = null;
		try {
			user = userService.retrieveUser(email);
			userService.checkUserLoggedIn(user, loggedInUser);
		} catch (BadRequestException | ForbiddenException e) {
			jatosGuiExceptionThrower.throwRedirect(e,
					controllers.gui.routes.Home.home());
		}
		List<StudyModel> studyList = studyDao.findAllByUser(loggedInUser
				.getEmail());

		Form<UserModel> form = Form.form(UserModel.class).bindFromRequest();
		if (form.hasErrors()) {
			return showEditUserAfterError(studyList, loggedInUser, form,
					loggedInUser, Http.Status.BAD_REQUEST);
		}
		// Update user in database
		// Do not update 'email' since it's the ID and should stay
		// unaltered. For the password we have an extra form.
		DynamicForm requestData = Form.form().bindFromRequest();
		String name = requestData.get(UserModel.NAME);
		userService.updateName(user, name);
		return redirect(controllers.gui.routes.Users.profile(email));
	}

	/**
	 * Shows view to change the password of a user.
	 */
	@Transactional
	public Result changePassword(String email) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".changePassword: " + "email " + email + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		UserModel user = null;
		try {
			user = userService.retrieveUser(email);
			userService.checkUserLoggedIn(user, loggedInUser);
		} catch (BadRequestException | ForbiddenException e) {
			jatosGuiExceptionThrower.throwHome(e);
		}

		Form<UserModel> form = Form.form(UserModel.class).fill(user);
		String breadcrumbs = Breadcrumbs.generateForUser(user,
				Breadcrumbs.CHANGE_PASSWORD);
		return ok(views.html.gui.user.changePassword.render(loggedInUser,
				breadcrumbs, form));
	}

	/**
	 * Handles post request of change password form.
	 */
	@Transactional
	public Result submitChangedPassword(String email) throws JatosGuiException,
			UnsupportedEncodingException, NoSuchAlgorithmException {
		Logger.info(CLASS_NAME + ".submitChangedPassword: " + "email " + email
				+ ", " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		UserModel user = null;
		try {
			user = userService.retrieveUser(email);
			userService.checkUserLoggedIn(user, loggedInUser);
		} catch (BadRequestException | ForbiddenException e) {
			jatosGuiExceptionThrower.throwRedirect(e,
					controllers.gui.routes.Home.home());
		}
		Form<UserModel> form = Form.form(UserModel.class).fill(user);

		DynamicForm requestData = Form.form().bindFromRequest();
		String newPassword = requestData.get(UserModel.PASSWORD);
		String newPasswordRepeat = requestData.get(UserModel.PASSWORD_REPEAT);
		String oldPasswordHash = userService.getHashMDFive(requestData
				.get(UserModel.OLD_PASSWORD));
		List<ValidationError> errorList = userService.validateChangePassword(
				user, newPassword, newPasswordRepeat, oldPasswordHash);
		if (!errorList.isEmpty()) {
			return showChangePasswordAfterError(loggedInUser, form, errorList,
					Http.Status.BAD_REQUEST, loggedInUser);
		}
		String newPasswordHash = userService.getHashMDFive(newPassword);
		userService.changePasswordHash(user, newPasswordHash);

		return redirect(controllers.gui.routes.Users.profile(email));
	}

}
