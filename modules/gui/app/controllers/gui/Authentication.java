package controllers.gui;

import javax.inject.Inject;
import javax.inject.Singleton;

import controllers.gui.actionannotations.AuthenticationAction.Authenticated;
import controllers.gui.actionannotations.GuiAccessLoggingAction.GuiAccessLogging;
import general.common.MessagesStrings;
import general.gui.FlashScopeMessaging;
import play.Logger;
import play.Logger.ALogger;
import play.data.Form;
import play.data.FormFactory;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Result;
import services.gui.AuthenticationService;
import utils.common.HttpUtils;

/**
 * Controller that deals with login/logout.
 * 
 * @author Kristian Lange
 */
@GuiAccessLogging
@Singleton
public class Authentication extends Controller {

	private static final ALogger LOGGER = Logger.of(Authentication.class);

	private final AuthenticationService authenticationService;
	private final FormFactory formFactory;

	@Inject
	Authentication(AuthenticationService authenticationService,
			FormFactory formFactory) {
		this.authenticationService = authenticationService;
		this.formFactory = formFactory;
	}

	/**
	 * Shows the login form view.
	 */
	public Result login() {
		LOGGER.info(".login");
		return ok(views.html.gui.auth.login
				.render(formFactory.form(Authentication.Login.class)));
	}

	/**
	 * HTTP POST Endpoint for the login form. It handles both Ajax and normal
	 * requests.
	 */
	@Transactional
	public Result authenticate() {
		Form<Login> loginForm = formFactory.form(Login.class).bindFromRequest();
		String email = loginForm.data().get("email");
		String password = loginForm.data().get("password");

		if (authenticationService.authenticate(email, password)) {
			authenticationService.login(session(), email);
			if (HttpUtils.isAjax()) {
				return ok();
			} else {
				return redirect(controllers.gui.routes.Home.home());
			}
		} else {
			if (HttpUtils.isAjax()) {
				return badRequest(MessagesStrings.INVALID_USER_OR_PASSWORD);
			} else {
				loginForm.reject(MessagesStrings.INVALID_USER_OR_PASSWORD);
				return badRequest(views.html.gui.auth.login.render(loginForm));
			}
		}
	}

	/**
	 * Removes user from session and shows login view with an logout message.
	 */
	@Transactional
	@Authenticated
	public Result logout() {
		LOGGER.info(".logout: "
				+ session(AuthenticationService.SESSION_USER_EMAIL));
		authenticationService.logout(session());
		FlashScopeMessaging.success("You've been logged out.");
		return redirect(controllers.gui.routes.Authentication.login());
	}

	/**
	 * Simple model class needed for login template
	 */
	public static class Login {

		public static final String EMAIL = "email";
		public static final String PASSWORD = "password";

		public String email;
		public String password;
	}

}
