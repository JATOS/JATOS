package controllers;

import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;

import play.Logger;
import play.data.Form;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.With;
import services.UserService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import common.JatosGuiAction;

import daos.IUserDao;

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
	public Authentication(IUserDao userDao, UserService userService) {
		this.userDao = userDao;
		this.userService = userService;
	}

	/**
	 * Shows the login form view.
	 */
	public Result login() {
		Logger.info(CLASS_NAME + ".login");
		return ok(views.html.jatos.auth.login.render(Form
				.form(Authentication.Login.class)));
	}

	/**
	 * Deals with login form post.
	 */
	@Transactional
	public Result authenticate() throws UnsupportedEncodingException,
			NoSuchAlgorithmException {
		Form<Login> loginForm = Form.form(Login.class).bindFromRequest();
		String email = loginForm.get().email;
		String password = loginForm.get().password;
		String passwordHash = userService.getHashMDFive(password);
		if (userDao.authenticate(email, passwordHash) == null) {
			loginForm.reject("Invalid user or password");
			return badRequest(views.html.jatos.auth.login.render(loginForm));
		} else {
			session(Users.SESSION_EMAIL, loginForm.get().email);
			return redirect(routes.Home.home());
		}
	}

	/**
	 * Shows login view with an logout message.
	 */
	public Result logout() {
		Logger.info(CLASS_NAME + ".logout: " + session(Users.SESSION_EMAIL));
		session().remove(Users.SESSION_EMAIL);
		flash("success", "You've been logged out");
		return redirect(routes.Authentication.login());
	}

	/**
	 * Inner class needed for login template
	 */
	public static class Login {
		public String email;
		public String password;
	}

}
