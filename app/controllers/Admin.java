package controllers;

import java.util.List;

import models.MAStudy;
import models.MAUser;
import play.data.Form;
import play.db.jpa.Transactional;
import play.mvc.Result;
import play.mvc.Security;

public class Admin extends MAController {

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result index() {
		List<MAStudy> studyList = MAStudy.findAll();
		MAUser loggedInUser = MAUser.findByEmail(session(COOKIE_EMAIL));
		if (loggedInUser == null) {
			return redirect(routes.Admin.login());
		}
		List<MAUser> userList = MAUser.findAll();
		return ok(views.html.admin.index.render(studyList, userList, null,
				loggedInUser));
	}

	@Transactional
	public static Result login() throws Exception {
		// Check for user admin: In case the app is started the first time we
		// need an initial user: admin. If admin can't be found, create one.
		MAUser admin = MAUser.findByEmail("admin");
		if (admin == null) {
			String passwordHash = MAUser.getHashMDFive("admin");
			admin = new MAUser("admin", "Admin", passwordHash);
			admin.persist();
		}
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
			try {
				String passwordHash = MAUser.getHashMDFive(password);
				if (MAUser.authenticate(email, passwordHash) == null) {
					return "Invalid user or password";
				}
			} catch (Exception e) {
				return null;
			}
			return null;
		}
	}

}
