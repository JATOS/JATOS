package controllers.gui;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import models.common.User;
import play.Logger;
import play.data.DynamicForm;
import play.data.Form;
import play.data.validation.ValidationError;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import services.gui.BreadcrumbsService;
import services.gui.JatosGuiExceptionThrower;
import services.gui.UserService;
import utils.common.ControllerUtils;
import utils.common.HashUtils;
import controllers.gui.actionannotations.AuthenticationAction.Authenticated;
import controllers.gui.actionannotations.JatosGuiAction.JatosGui;
import exceptions.gui.ForbiddenException;
import exceptions.gui.JatosGuiException;
import exceptions.gui.NotFoundException;

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
		User loggedInUser = userService.retrieveLoggedInUser();
		User user = checkStandard(email, loggedInUser);
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
		User loggedInUser = userService.retrieveLoggedInUser();
		String breadcrumbs = breadcrumbsService
				.generateForHome(BreadcrumbsService.NEW_USER);
		return ok(views.html.gui.user.create.render(loggedInUser, breadcrumbs,
				Form.form(User.class)));
	}

	/**
	 * Handles post request of user create form.
	 */
	@Transactional
	public Result submit() {
		Logger.info(CLASS_NAME + ".submit: " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		Form<User> form = Form.form(User.class).bindFromRequest();

		if (form.hasErrors()) {
			return badRequest(form.errorsAsJson());
		}

		User newUser = form.get();
		DynamicForm requestData = Form.form().bindFromRequest();
		String password = requestData.get(User.PASSWORD);
		String passwordRepeat = requestData.get(User.PASSWORD_REPEAT);
		List<ValidationError> errorList = userService.validateNewUser(newUser,
				password, passwordRepeat);
		if (!errorList.isEmpty()) {
			errorList.forEach(form::reject);
			return badRequest(form.errorsAsJson());
		}

		userService.createUser(newUser, password);
		return ok();
	}

	private Result showEditUserAfterError(User loggedInUser, Form<User> form,
			User user, int httpStatus) {
		if (ControllerUtils.isAjax()) {
			return status(httpStatus);
		} else {
			String breadcrumbs = breadcrumbsService.generateForUser(user,
					"Edit Profile");
			return status(httpStatus, views.html.gui.user.editProfile
					.render(loggedInUser, breadcrumbs, user, form));
		}
	}

	private Result showChangePasswordAfterError(User loggedInUser,
			Form<User> form, List<ValidationError> errorList, int httpStatus,
			User user) {
		if (ControllerUtils.isAjax()) {
			return status(httpStatus);
		} else {
			if (errorList != null) {
				errorList.forEach(form::reject);
			}
			String breadcrumbs = breadcrumbsService.generateForUser(user,
					"Change Password");
			return status(httpStatus, views.html.gui.user.changePassword
					.render(loggedInUser, breadcrumbs, form));
		}
	}

	/**
	 * Shows view with form to edit a user profile.
	 */
	@Transactional
	public Result editProfile(String email) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".editProfile: " + "email " + email + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		User loggedInUser = userService.retrieveLoggedInUser();
		User user = checkStandard(email, loggedInUser);
		Form<User> form = Form.form(User.class).fill(user);
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
		User loggedInUser = userService.retrieveLoggedInUser();
		User user = checkStandard(email, loggedInUser);

		Form<User> form = Form.form(User.class).bindFromRequest();
		if (form.hasErrors()) {
			return showEditUserAfterError(loggedInUser, form, loggedInUser,
					Http.Status.BAD_REQUEST);
		}
		// Update user in database
		// Do not update 'email' since it's the ID and should stay
		// unaltered. For the password we have an extra form.
		DynamicForm requestData = Form.form().bindFromRequest();
		String name = requestData.get(User.NAME);
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
		User loggedInUser = userService.retrieveLoggedInUser();
		User user = checkStandard(email, loggedInUser);

		Form<User> form = Form.form(User.class).fill(user);
		String breadcrumbs = breadcrumbsService.generateForUser(user,
				BreadcrumbsService.CHANGE_PASSWORD);
		return ok(views.html.gui.user.changePassword.render(loggedInUser,
				breadcrumbs, form));
	}

	/**
	 * Handles post request of change password form.
	 */
	@Transactional
	public Result submitChangedPassword(String email) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".submitChangedPassword: " + "email " + email
				+ ", " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		User loggedInUser = userService.retrieveLoggedInUser();
		User user = checkStandard(email, loggedInUser);
		Form<User> form = Form.form(User.class).fill(user);

		DynamicForm requestData = Form.form().bindFromRequest();
		String newPassword = requestData.get(User.PASSWORD);
		String newPasswordRepeat = requestData.get(User.PASSWORD_REPEAT);
		String oldPasswordHash = HashUtils
				.getHashMDFive(requestData.get(User.OLD_PASSWORD));
		List<ValidationError> errorList = userService.validateChangePassword(
				user, newPassword, newPasswordRepeat, oldPasswordHash);
		if (!errorList.isEmpty()) {
			return showChangePasswordAfterError(loggedInUser, form, errorList,
					Http.Status.BAD_REQUEST, loggedInUser);
		}
		String newPasswordHash = HashUtils.getHashMDFive(newPassword);
		userService.changePasswordHash(user, newPasswordHash);

		return redirect(controllers.gui.routes.Users.profile(email));
	}

	private User checkStandard(String email, User loggedInUser)
			throws JatosGuiException {
		User user = null;
		try {
			user = userService.retrieveUser(email);
			userService.checkUserLoggedIn(user, loggedInUser);
		} catch (ForbiddenException | NotFoundException e) {
			jatosGuiExceptionThrower.throwRedirect(e,
					controllers.gui.routes.Home.home());
		}
		return user;
	}

}
