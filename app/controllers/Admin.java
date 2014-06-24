package controllers;

import java.util.List;

import models.MAExperiment;
import models.MAUser;
import play.data.Form;
import play.db.jpa.Transactional;
import play.mvc.Result;
import play.mvc.Security;

public class Admin extends MAController {

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result index() {
		List<MAExperiment> experimentList = MAExperiment.findAll();
		MAUser user = MAUser.findByEmail(session(COOKIE_EMAIL));
		return ok(views.html.admin.index.render(experimentList, null, user));
	}

	public static Result login() {
		return ok(views.html.admin.login.render(Form.form(Login.class)));
	}

	@Transactional
	public static Result authenticate() {
		Form<Login> loginForm = Form.form(Login.class).bindFromRequest();
		if (loginForm.hasErrors()) {
			return badRequest(views.html.admin.login.render(loginForm));
		} else {
			session(COOKIE_EMAIL, loginForm.get().email);
			return redirect(routes.Admin.index());
		}
	}

	@Security.Authenticated(Secured.class)
	public static Result logout() {
		session().remove(COOKIE_EMAIL);
		flash("success", "You've been logged out");
		return redirect(routes.Admin.login());
	}

	/**
	 * Inner class needed for authentication
	 */
	public static class Login {
		public String email;
		public String password;

		public String validate() {
			if (MAUser.authenticate(email, password) == null) {
				return "Invalid user or password";
			}
			return null;
		}
	}

}
