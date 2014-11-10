package controllers;

import models.UserModel;
import play.Logger;
import play.data.Form;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Security;

public class Authentication extends Controller {
	
	private static final String CLASS_NAME = Authentication.class.getSimpleName();

	public static Result login() {
		Logger.info(CLASS_NAME + ".login");
		return ok(views.html.mecharg.auth.login2.render(Form.form(Login.class)));
	}
	
	@Transactional
	public static Result authenticate() {
		Form<Login> loginForm = Form.form(Login.class).bindFromRequest();
		if (loginForm.hasErrors()) {
			return badRequest(views.html.mecharg.auth.login2.render(loginForm));
		} else {
			session(Users.COOKIE_EMAIL, loginForm.get().email);
			return redirect(routes.Home.home());
		}
	}

	@Security.Authenticated(Secured.class)
	public static Result logout() {
		Logger.info(CLASS_NAME + ".logout: " + session(Users.COOKIE_EMAIL));
		session().remove(Users.COOKIE_EMAIL);
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
				String passwordHash = UserModel.getHashMDFive(password);
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
