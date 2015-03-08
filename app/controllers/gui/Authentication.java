package controllers.gui;

import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;

import persistance.IUserDao;
import play.Logger;
import play.data.Form;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.With;
import services.FlashScopeMessaging;
import services.gui.MessagesStrings;
import services.gui.UserService;

import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * Controller that deals with login/logout.
 * 
 * @author Kristian Lange
 */
@With(JatosGuiAction.class)
@Singleton
public class Authentication extends Controller {

	private static final String CLASS_NAME = Authentication.class
			.getSimpleName();

	private final UserService userService;
	private final IUserDao userDao;

	@Inject
	Authentication(IUserDao userDao, UserService userService) {
		this.userDao = userDao;
		this.userService = userService;
	}

	/**
	 * Shows the login form view.
	 */
	public Result login() {
		Logger.info(CLASS_NAME + ".login");
		return ok(views.html.gui.auth.login.render(Form
				.form(Authentication.Login.class)));
	}

	/**
	 * Deals with login form post.
	 */
	@Transactional
	public Result authenticate() throws UnsupportedEncodingException,
			NoSuchAlgorithmException {
		Form<Login> loginForm = Form.form(Login.class).bindFromRequest();
		String email = loginForm.data().get("email");
		String password = loginForm.data().get("password");
		String passwordHash = userService.getHashMDFive(password);
		if (userDao.authenticate(email, passwordHash) == null) {
			loginForm.reject("Invalid user or password");
			return badRequest(views.html.gui.auth.login.render(loginForm));
		} else {
			session(Users.SESSION_EMAIL, email);
			return redirect(controllers.gui.routes.Home.home());
		}
	}

	/**
	 * Shows login view with an logout message.
	 */
	public Result logout() {
		Logger.info(CLASS_NAME + ".logout: " + session(Users.SESSION_EMAIL));
		session().remove(Users.SESSION_EMAIL);
		FlashScopeMessaging.success(MessagesStrings.YOUVE_BEEN_LOGGED_OUT);
		return redirect(controllers.gui.routes.Authentication.login());
	}

	/**
	 * Inner class needed for login template
	 */
	public static class Login {
		public String email;
		public String password;
	}

}
