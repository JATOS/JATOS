package controllers.gui;

import java.io.IOException;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import controllers.gui.actionannotations.AuthenticationAction.Authenticated;
import controllers.gui.actionannotations.GuiAccessLoggingAction.GuiAccessLogging;
import exceptions.gui.ForbiddenException;
import exceptions.gui.JatosGuiException;
import exceptions.gui.NotFoundException;
import general.common.MessagesStrings;
import models.common.User;
import models.common.User.Role;
import models.gui.ChangePasswordModel;
import models.gui.ChangeUserProfileModel;
import models.gui.NewUserModel;
import play.Logger;
import play.Logger.ALogger;
import play.data.DynamicForm;
import play.data.Form;
import play.data.FormFactory;
import play.data.validation.ValidationError;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Result;
import services.gui.AuthenticationService;
import services.gui.AuthenticationValidation;
import services.gui.BreadcrumbsService;
import services.gui.JatosGuiExceptionThrower;
import services.gui.UserService;
import utils.common.HttpUtils;
import utils.common.JsonUtils;

/**
 * Controller with actions concerning users
 *
 * @author Kristian Lange
 */
@GuiAccessLogging
@Singleton
public class Users extends Controller {

	private static final ALogger LOGGER = Logger.of(Users.class);

	private final JatosGuiExceptionThrower jatosGuiExceptionThrower;
	private final UserService userService;
	private final AuthenticationService authenticationService;
	private final AuthenticationValidation authenticationValidation;
	private final BreadcrumbsService breadcrumbsService;
	private final FormFactory formFactory;
	private final JsonUtils jsonUtils;

	@Inject
	Users(JatosGuiExceptionThrower jatosGuiExceptionThrower,
			UserService userService,
			AuthenticationService authenticationService,
			AuthenticationValidation authenticationValidation,
			BreadcrumbsService breadcrumbsService, FormFactory formFactory,
			JsonUtils jsonUtils) {
		this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
		this.userService = userService;
		this.authenticationService = authenticationService;
		this.authenticationValidation = authenticationValidation;
		this.breadcrumbsService = breadcrumbsService;
		this.formFactory = formFactory;
		this.jsonUtils = jsonUtils;
	}

	@Transactional
	@Authenticated(Role.ADMIN)
	public Result userManager() throws JatosGuiException {
		LOGGER.info(".userManager");
		User loggedInUser = authenticationService.getLoggedInUser();
		String breadcrumbs = breadcrumbsService
				.generateForHome(BreadcrumbsService.USER_MANAGER);
		return ok(views.html.gui.user.userManager.render(loggedInUser,
				breadcrumbs, HttpUtils.isLocalhost()));
	}

	/**
	 * Ajax GET request: Returns a list of all users as JSON
	 */
	@Transactional
	@Authenticated(Role.ADMIN)
	public Result allUserData() {
		LOGGER.info(".allUserData");
		List<User> userList = userService.retrieveAllUsers();
		return ok(jsonUtils.userData(userList));
	}

	/**
	 * Ajax POST
	 * 
	 * Request to add or remove the ADMIN role from a user.
	 */
	@Transactional
	@Authenticated(Role.ADMIN)
	public Result toggleAdmin(String emailOfUserToChange, Boolean adminRole)
			throws JatosGuiException {
		LOGGER.info(".toggleAdmin: emailOfUserToChange " + emailOfUserToChange
				+ ", " + "adminRole " + adminRole);
		boolean hasAdminRole;
		try {
			hasAdminRole = userService.changeAdminRole(emailOfUserToChange,
					adminRole);
		} catch (NotFoundException e) {
			return badRequest(e.getMessage());
		} catch (ForbiddenException e) {
			return forbidden(e.getMessage());
		}
		return ok(JsonUtils.asJsonNode(hasAdminRole));
	}

	/**
	 * Shows the profile view of a user
	 */
	@Transactional
	@Authenticated
	public Result profile(String email) throws JatosGuiException {
		LOGGER.info(".profile: " + "email " + email);
		User loggedInUser = authenticationService.getLoggedInUser();
		checkEmailIsOfLoggedInUser(email, loggedInUser);

		String breadcrumbs = breadcrumbsService.generateForUser(loggedInUser);
		return ok(views.html.gui.user.profile.render(loggedInUser, breadcrumbs,
				HttpUtils.isLocalhost()));
	}

	/**
	 * Ajax GET request: Returns data of the user that belongs to the given
	 * email
	 */
	@Transactional
	@Authenticated
	public Result singleUserData(String email) throws JatosGuiException {
		LOGGER.info(".singleUserData: " + "email " + email);
		User loggedInUser = authenticationService.getLoggedInUser();
		checkEmailIsOfLoggedInUser(email, loggedInUser);
		return ok(jsonUtils.userData(loggedInUser));
	}

	/**
	 * Handles POST request of user create form. Only users with Role ADMIN are
	 * allowed to create new users.
	 */
	@Transactional
	@Authenticated(Role.ADMIN)
	public Result submitCreated() {
		LOGGER.info(".submitCreated");
		User loggedInUser = authenticationService.getLoggedInUser();

		// Validate via model's validate method
		Form<NewUserModel> form = formFactory.form(NewUserModel.class)
				.bindFromRequest();
		if (form.hasErrors()) {
			return badRequest(form.errorsAsJson());
		}

		// Validate via AuthenticationService
		NewUserModel newUser = form.get();
		List<ValidationError> errorList = authenticationValidation
				.validateNewUser(newUser, loggedInUser.getEmail());
		if (!errorList.isEmpty()) {
			errorList.forEach(form::reject);
			return badRequest(form.errorsAsJson());
		}

		userService.bindToUserAndPersist(newUser);
		return ok();
	}

	/**
	 * Handles POST request of user edit profile form (so far it's only the
	 * user's name - password is handled in another method).
	 */
	@Transactional
	@Authenticated
	public Result submitEditedProfile(String email) throws JatosGuiException {
		LOGGER.info(".submitEditedProfile: " + "email " + email);
		User loggedInUser = authenticationService.getLoggedInUser();
		checkEmailIsOfLoggedInUser(email, loggedInUser);

		Form<ChangeUserProfileModel> form = formFactory
				.form(ChangeUserProfileModel.class).bindFromRequest();
		if (form.hasErrors()) {
			return badRequest(form.errorsAsJson());
		}

		// Update user in database: so far it's only the user's name
		String name = form.get().getName();
		userService.updateName(loggedInUser, name);
		return ok();
	}

	/**
	 * Handles POST request of change password form. Can be either origin in the
	 * user manager or in the user profile.
	 */
	@Transactional
	@Authenticated
	public Result submitChangedPassword(String emailOfUserToChange) {
		LOGGER.info(
				".submitChangedPassword: " + "email " + emailOfUserToChange);

		// Validate via model's validate method
		Form<ChangePasswordModel> form = formFactory
				.form(ChangePasswordModel.class).bindFromRequest();
		if (form.hasErrors()) {
			return badRequest(form.errorsAsJson());
		}

		// Validate via AuthenticationService
		ChangePasswordModel changePasswordModel = form.get();
		List<ValidationError> errorList = authenticationValidation
				.validateChangePassword(emailOfUserToChange,
						changePasswordModel);
		if (!errorList.isEmpty()) {
			errorList.forEach(form::reject);
			return forbidden(form.errorsAsJson());
		}

		// Change password
		try {
			String newPassword = changePasswordModel.getNewPassword();
			userService.updatePassword(emailOfUserToChange, newPassword);
		} catch (NotFoundException e) {
			return badRequest(e.getMessage());
		}
		return ok();
	}

	/**
	 * POST request to delete a user. Is called from user manager and user
	 * profile.
	 * 
	 * It can't be a HTTP DELETE because it contains form data and Play doesn't
	 * handle body data in a DELETE request.
	 */
	@Transactional
	@Authenticated
	public Result remove(String emailOfUserToRemove) throws JatosGuiException {
		LOGGER.info(".remove: " + "emailOfUserToRemove " + emailOfUserToRemove);

		User loggedInUser = authenticationService.getLoggedInUser();
		String loggedInUserEmail = loggedInUser.getEmail();
		if (!loggedInUser.hasRole(Role.ADMIN)
				&& !emailOfUserToRemove.equals(loggedInUserEmail)) {
			return forbidden(MessagesStrings.NOT_ALLOWED_TO_DELETE_USER);
		}

		DynamicForm requestData = formFactory.form().bindFromRequest();
		String password = requestData.get("password");
		if (!authenticationService.authenticate(loggedInUserEmail, password)) {
			return forbidden(MessagesStrings.WRONG_PASSWORD);
		}

		try {
			userService.removeUser(emailOfUserToRemove);
		} catch (NotFoundException e) {
			return badRequest(e.getMessage());
		} catch (ForbiddenException e) {
			return forbidden(e.getMessage());
		} catch (IOException e) {
			return internalServerError(e.getMessage());
		}
		// If the user removes himself: logout
		if (emailOfUserToRemove.equals(loggedInUserEmail)) {
			authenticationService.logout(session());
		}
		return ok();
	}

	private void checkEmailIsOfLoggedInUser(String email, User loggedInUser)
			throws JatosGuiException {
		if (!email.equals(loggedInUser.getEmail())) {
			ForbiddenException e = new ForbiddenException(
					MessagesStrings.userNotAllowedToGetData(email));
			jatosGuiExceptionThrower.throwRedirect(e,
					controllers.gui.routes.Home.home());
		}
	}

}
