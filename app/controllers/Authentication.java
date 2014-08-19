package controllers;

import controllers.publix.MAPublix;
import models.UserModel;
import play.Logger;
import play.data.Form;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Security;
import services.Persistance;

public class Authentication extends Controller {
	
	private static final String CLASS_NAME = Authentication.class.getSimpleName();

	@Transactional
	public static Result login() throws Exception {
		Logger.info(CLASS_NAME + ".login");
		// Check for user admin: In case the app is started the first time we
		// need an initial user: admin. If admin can't be found, create one.
		UserModel admin = UserModel.findByEmail("admin");
		if (admin == null) {
			Persistance.createAdmin();
		}
		return ok(views.html.mecharg.auth.login.render(Form.form(Login.class)));
	}
	
	@Transactional
	public static Result authenticate() {
		Form<Login> loginForm = Form.form(Login.class).bindFromRequest();
		Logger.info(CLASS_NAME + ".authenticate: " + loginForm.get().email);
		
		if (loginForm.hasErrors()) {
			return badRequest(views.html.mecharg.auth.login.render(loginForm));
		} else {
			session(Users.COOKIE_EMAIL, loginForm.get().email);
			return redirect(routes.Dashboard.dashboard());
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
