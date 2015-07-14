package controllers;

import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import models.UserModel;
import play.Logger;
import play.data.DynamicForm;
import play.data.Form;
import play.data.validation.ValidationError;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.BreadcrumbsService;
import services.JatosGuiExceptionThrower;
import services.UserService;
import utils.ControllerUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import controllers.actionannotations.AuthenticationAction.Authenticated;
import controllers.actionannotations.JatosGuiAction.JatosGui;
import exceptions.ForbiddenException;
import exceptions.JatosGuiException;
import exceptions.NotFoundException;

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
	private final BreadcrumbsService breadcrumbsService;

	@Inject
	Users(JatosGuiExceptionThrower jatosGuiExceptionThrower,
			UserService userService, BreadcrumbsService breadcrumbsService) {
		this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
		this.userService = userService;
		this.breadcrumbsService = breadcrumbsService;
	}

	/**
	 * Shows the profile view of a user
	 */
	@Transactional
	public Result profile(String email) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".profile: " + "email " + email + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		UserModel user = checkStandard(email, loggedInUser);
		String breadcrumbs = breadcrumbsService.generateForUser(user);
		return ok(views.html.gui.user.profile.render(loggedInUser, breadcrumbs,
				user));
	}

	/**
	 * Shows a view with a form to create a new user.
	 */
	@Transactional
	public Result create() {
		Logger.info(CLASS_NAME + ".create: " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		String breadcrumbs = breadcrumbsService
				.generateForHome(BreadcrumbsService.NEW_USER);
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

		if (form.hasErrors()) {
			return showCreateUserAfterError(loggedInUser, form, null,
					Http.Status.BAD_REQUEST);
		}

		UserModel newUser = form.get();
		DynamicForm requestData = Form.form().bindFromRequest();
		String password = requestData.get(UserModel.PASSWORD);
		String passwordRepeat = requestData.get(UserModel.PASSWORD_REPEAT);
		List<ValidationError> errorList = userService.validateNewUser(newUser,
				password, passwordRepeat);
		if (!errorList.isEmpty()) {
			return showCreateUserAfterError(loggedInUser, form, errorList,
					Http.Status.BAD_REQUEST);
		}

		userService.createUser(newUser, password);
		return redirect(controllers.routes.Home.home());
	}

	private Result showCreateUserAfterError(UserModel loggedInUser,
			Form<UserModel> form, List<ValidationError> errorList,
			int httpStatus) {
		if (ControllerUtils.isAjax()) {
			return status(httpStatus);
		} else {
			if (errorList != null) {
				errorList.forEach(form::reject);
			}
			String breadcrumbs = breadcrumbsService.generateForHome("New User");
			return status(httpStatus, views.html.gui.user.create.render(
					loggedInUser, breadcrumbs, form));
		}
	}

	private Result showEditUserAfterError(UserModel loggedInUser,
			Form<UserModel> form, UserModel user, int httpStatus) {
		if (ControllerUtils.isAjax()) {
			return status(httpStatus);
		} else {
			String breadcrumbs = breadcrumbsService.generateForUser(user,
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
				errorList.forEach(form::reject);
			}
			String breadcrumbs = breadcrumbsService.generateForUser(user,
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
		UserModel user = checkStandard(email, loggedInUser);
		Form<UserModel> form = Form.form(UserModel.class).fill(user);
		String breadcrumbs = breadcrumbsService.generateForUser(user,
				BreadcrumbsService.EDIT_PROFILE);
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
		UserModel user = checkStandard(email, loggedInUser);

		Form<UserModel> form = Form.form(UserModel.class).bindFromRequest();
		if (form.hasErrors()) {
			return showEditUserAfterError(loggedInUser, form, loggedInUser,
					Http.Status.BAD_REQUEST);
		}
		// Update user in database
		// Do not update 'email' since it's the ID and should stay
		// unaltered. For the password we have an extra form.
		DynamicForm requestData = Form.form().bindFromRequest();
		String name = requestData.get(UserModel.NAME);
		userService.updateName(user, name);
		return redirect(controllers.routes.Users.profile(email));
	}

	/**
	 * Shows view to change the password of a user.
	 */
	@Transactional
	public Result changePassword(String email) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".changePassword: " + "email " + email + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		UserModel user = checkStandard(email, loggedInUser);

		Form<UserModel> form = Form.form(UserModel.class).fill(user);
		String breadcrumbs = breadcrumbsService.generateForUser(user,
				BreadcrumbsService.CHANGE_PASSWORD);
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
		UserModel user = checkStandard(email, loggedInUser);
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

		return redirect(controllers.routes.Users.profile(email));
	}

	private UserModel checkStandard(String email, UserModel loggedInUser)
			throws JatosGuiException {
		UserModel user = null;
		try {
			user = userService.retrieveUser(email);
			userService.checkUserLoggedIn(user, loggedInUser);
		} catch (ForbiddenException | NotFoundException e) {
			jatosGuiExceptionThrower.throwRedirect(e,
					controllers.routes.Home.home());
		}
		return user;
	}

}
