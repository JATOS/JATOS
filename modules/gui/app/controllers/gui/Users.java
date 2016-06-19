package controllers.gui;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import controllers.gui.actionannotations.AuthenticationAction.Authenticated;
import controllers.gui.actionannotations.GuiExceptionAction.GuiExceptionCatching;
import controllers.gui.actionannotations.GuiAccessLoggingAction.GuiAccessLogging;
import exceptions.gui.ForbiddenException;
import exceptions.gui.JatosGuiException;
import exceptions.gui.NotFoundException;
import models.common.User;
import play.Logger;
import play.Logger.ALogger;
import play.data.DynamicForm;
import play.data.Form;
import play.data.validation.ValidationError;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Result;
import services.gui.BreadcrumbsService;
import services.gui.Checker;
import services.gui.JatosGuiExceptionThrower;
import services.gui.UserService;
import utils.common.HashUtils;

/**
 * Controller with actions concerning users
 *
 * @author Kristian Lange
 */
@GuiExceptionCatching
@GuiAccessLogging
@Authenticated
@Singleton
public class Users extends Controller {

	private static final ALogger LOGGER = Logger.of(Users.class);

	public static final String SESSION_EMAIL = "email";

	private final JatosGuiExceptionThrower jatosGuiExceptionThrower;
	private final Checker checker;
	private final UserService userService;
	private final BreadcrumbsService breadcrumbsService;

	@Inject
	Users(JatosGuiExceptionThrower jatosGuiExceptionThrower, Checker checker,
			UserService userService, BreadcrumbsService breadcrumbsService) {
		this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
		this.checker = checker;
		this.userService = userService;
		this.breadcrumbsService = breadcrumbsService;
	}

	/**
	 * Shows the profile view of a user
	 */
	@Transactional
	public Result profile(String email) throws JatosGuiException {
		LOGGER.info(".profile: " + "email " + email);
		User loggedInUser = userService.retrieveLoggedInUser();
		User user = checkStandard(email, loggedInUser);
		String breadcrumbs = breadcrumbsService.generateForUser(user);
		return ok(views.html.gui.user.profile.render(loggedInUser, breadcrumbs,
				user));
	}

	/**
	 * Handles post request of user create form.
	 */
	@Transactional
	public Result submit() {
		LOGGER.info(".submit");
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

		userService.createAndPersistUser(newUser, password);
		return ok();
	}

	/**
	 * Handles post request of user edit profile form.
	 */
	@Transactional
	public Result submitEditedProfile(String email) throws JatosGuiException {
		LOGGER.info(".submitEditedProfile: " + "email " + email);
		User loggedInUser = userService.retrieveLoggedInUser();
		User user = checkStandard(email, loggedInUser);

		Form<User> form = Form.form(User.class).bindFromRequest();
		if (form.hasErrors()) {
			return badRequest(form.errorsAsJson());
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
	 * Handles post request of change password form.
	 */
	@Transactional
	public Result submitChangedPassword(String email) throws JatosGuiException {
		LOGGER.info(".submitChangedPassword: " + "email " + email);
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
			errorList.forEach(form::reject);
			return badRequest(form.errorsAsJson());
		}
		String newPasswordHash = HashUtils.getHashMDFive(newPassword);
		userService.updatePasswordHash(user, newPasswordHash);

		return redirect(controllers.gui.routes.Users.profile(email));
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
