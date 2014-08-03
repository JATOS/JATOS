package controllers;

import models.UserModel;
import models.workers.MAWorker;
import play.data.Form;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Security;

public class Authentication extends Controller {

	@Transactional
	public static Result login() throws Exception {
		// Check for user admin: In case the app is started the first time we
		// need an initial user: admin. If admin can't be found, create one.
		UserModel admin = UserModel.findByEmail("admin");
		if (admin == null) {
			MAWorker worker = new MAWorker();
			worker.persist();
			String passwordHash = UserModel.getHashMDFive("admin");
			admin = new UserModel("admin", "Admin", passwordHash);
			admin.setWorker(worker);
			admin.persist();
			worker.setUser(admin);
			worker.merge();
		}
		return ok(views.html.mecharg.auth.login.render(Form.form(Login.class)));
	}
	
	@Transactional
	public static Result authenticate() {
		Form<Login> loginForm = Form.form(Login.class).bindFromRequest();
		if (loginForm.hasErrors()) {
			return badRequest(views.html.mecharg.auth.login.render(loginForm));
		} else {
			session(Users.COOKIE_EMAIL, loginForm.get().email);
			return redirect(routes.Dashboard.dashboard());
		}
	}

	@Security.Authenticated(Secured.class)
	public static Result logout() {
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
