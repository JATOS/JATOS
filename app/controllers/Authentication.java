package controllers;

import models.UserModel;
import play.Logger;
import play.data.Form;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.With;
import services.UserService;

import com.google.inject.Singleton;
import common.JatosGuiAction;

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

	/**
	 * Shows the login form view.
	 */
	public Result login() {
		Logger.info(CLASS_NAME + ".login");
		return ok(views.html.jatos.auth.login.render(Form.form(Login.class)));
	}

	/**
	 * Deals with login form post.
	 */
	@Transactional
	public Result authenticate() {
		Form<Login> loginForm = Form.form(Login.class).bindFromRequest();
		if (loginForm.hasErrors()) {
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
	 * Inner class needed for authentication
	 */
	public static class Login {
		public String email;
		public String password;

		public String validate() {
			try {
				String passwordHash = UserService.getHashMDFive(password);
				if (UserModel.authenticate(email, passwordHash) == null) {
					return "Invalid user or password";
				}
			} catch (Exception e) {
				return null;
			}
			return null;
		}
	}

}
