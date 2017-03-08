package controllers.gui;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import controllers.gui.actionannotations.AuthenticationAction.Authenticated;
import controllers.gui.actionannotations.GuiAccessLoggingAction.GuiAccessLogging;
import exceptions.gui.ForbiddenException;
import exceptions.gui.JatosGuiException;
import exceptions.gui.NotFoundException;
import models.common.User;
import models.common.User.Role;
import models.gui.ChangePasswordModel;
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
import services.gui.BreadcrumbsService;
import services.gui.Checker;
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
	private final Checker checker;
	private final UserService userService;
	private final AuthenticationService authenticationService;
	private final BreadcrumbsService breadcrumbsService;
	private final FormFactory formFactory;

	@Inject
	Users(JatosGuiExceptionThrower jatosGuiExceptionThrower, Checker checker,
			UserService userService,
			AuthenticationService authenticationService,
			BreadcrumbsService breadcrumbsService, FormFactory formFactory) {
		this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
		this.checker = checker;
		this.userService = userService;
		this.authenticationService = authenticationService;
		this.breadcrumbsService = breadcrumbsService;
		this.formFactory = formFactory;
	}

	@Transactional
	@Authenticated(Role.ADMIN)
	public Result userManager() throws JatosGuiException {
		LOGGER.info(".userManager");
		User loggedInUser = userService.retrieveLoggedInUser();
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
	public Result userData() throws JatosGuiException {
		LOGGER.info(".userData");
		List<User> userList = userService.retrieveAllUsers();
		return ok(JsonUtils.asJsonNodeWithinData(userList));
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
		User loggedInUser = userService.retrieveLoggedInUser();
		User user = checkStandard(email, loggedInUser);
		String breadcrumbs = breadcrumbsService.generateForUser(user);
		return ok(views.html.gui.user.profile.render(loggedInUser, breadcrumbs,
				HttpUtils.isLocalhost(), user));
	}

	/**
	 * Handles post request of user create form. Only users with Role ADMIN are
	 * allowed to create new users.
	 */
	@Transactional
	@Authenticated(Role.ADMIN)
	public Result submitCreated() {
		LOGGER.info(".submitCreated");
		User loggedInUser = userService.retrieveLoggedInUser();

		// Validate via model's validate method
		Form<NewUserModel> form = formFactory.form(NewUserModel.class)
				.bindFromRequest();
		if (form.hasErrors()) {
			return badRequest(form.errorsAsJson());
		}

		// Validate via AuthenticationService
		NewUserModel newUser = form.get();
		List<ValidationError> errorList = authenticationService
				.validateNewUser(newUser, loggedInUser.getEmail());
		if (!errorList.isEmpty()) {
			errorList.forEach(form::reject);
			return badRequest(form.errorsAsJson());
		}

		userService.createAndPersistNewUser(newUser);
		return ok();
	}

	/**
	 * Handles post request of user edit profile form.
	 */
	@Transactional
	@Authenticated
	public Result submitEditedProfile(String email) throws JatosGuiException {
		LOGGER.info(".submitEditedProfile: " + "email " + email);
		User loggedInUser = userService.retrieveLoggedInUser();
		User user = checkStandard(email, loggedInUser);

		Form<User> form = formFactory.form(User.class).bindFromRequest();
		if (form.hasErrors()) {
			return badRequest(form.errorsAsJson());
		}
		// Update user in database
		// Do not update 'email' since it's the ID and should stay
		// unaltered. For the password we have an extra form.
		DynamicForm requestData = formFactory.form().bindFromRequest();
		String name = requestData.get("name");
		userService.updateName(user, name);
		return redirect(controllers.gui.routes.Users.profile(email));
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
		List<ValidationError> errorList = authenticationService
				.validateChangePassword(emailOfUserToChange,
						changePasswordModel);
		if (!errorList.isEmpty()) {
			errorList.forEach(form::reject);
			return badRequest(form.errorsAsJson());
		}

		// Change password
		try {
			String newPassword = changePasswordModel.getNewPassword();
			userService.updatePasswordHash(emailOfUserToChange, newPassword);
		} catch (NotFoundException e) {
			badRequest(e.getMessage());
		}
		return ok();
	}

	private User checkStandard(String email, User loggedInUser)
			throws JatosGuiException {
		User user = null;
		try {
			user = userService.retrieveUser(email);
			checker.checkUserLoggedIn(user, loggedInUser);
		} catch (ForbiddenException | NotFoundException e) {
			jatosGuiExceptionThrower.throwRedirect(e,
					controllers.gui.routes.Home.home());
		}
		return user;
	}

}
