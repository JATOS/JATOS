package controllers;

import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import models.MAExperiment;
import models.MAUser;
import play.data.DynamicForm;
import play.data.Form;
import play.db.jpa.Transactional;
import play.mvc.Result;
import play.mvc.Security;

public class Users extends MAController {

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result get(String email) {
		MAUser user = MAUser.findByEmail(email);
		MAUser loggedInUser = MAUser
				.findByEmail(session(MAController.COOKIE_EMAIL));
		List<MAExperiment> experimentList = MAExperiment.findAll();
		
		if (user == null) {
			return badRequestUserNotExist(email, loggedInUser, experimentList);
		}

		return ok(views.html.admin.user.index.render(experimentList, null,
				loggedInUser, user));
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result create() {
		List<MAExperiment> experimentList = MAExperiment.findAll();
		MAUser loggedInUser = MAUser
				.findByEmail(session(MAController.COOKIE_EMAIL));
		return ok(views.html.admin.user.create.render(experimentList, null,
				loggedInUser, Form.form(MAUser.class)));
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result submit() throws NoSuchAlgorithmException,
			UnsupportedEncodingException {
		Form<MAUser> form = Form.form(MAUser.class).bindFromRequest();
		List<MAExperiment> experimentList = MAExperiment.findAll();
		MAUser loggedInUser = MAUser
				.findByEmail(session(MAController.COOKIE_EMAIL));
		
		if (form.hasErrors()) {
			return badRequest(views.html.admin.user.create.render(
					experimentList, null, loggedInUser, form));
		}

		// Check if user with this email already exists.
		MAUser newUser = form.get();
		if (MAUser.findByEmail(newUser.email) != null) {
			form.reject("email", "This e-mail is already registered.");
		}

		// Check for non empty passwords
		DynamicForm requestData = Form.form().bindFromRequest();
		String password = requestData.get("password");
		String passwordRepeat = requestData.get("passwordRepeat");
		if (password.trim().isEmpty() || passwordRepeat.trim().isEmpty()) {
			form.reject("password", "Passwords shouldn't be empty strings.");
		}

		// Check that both passwords are the same
		String passwordHash = MAUser.getHashMDFive(password);
		String passwordHashRepeat = MAUser.getHashMDFive(passwordRepeat);
		if (!passwordHash.equals(passwordHashRepeat)) {
			form.reject("password", "Passwords aren't the same.");
		}

		if (form.hasErrors()) {
			return badRequest(views.html.admin.user.create.render(
					experimentList, null, loggedInUser, form));
		} else {
			newUser.passwordHash = passwordHash;
			newUser.persist();
			return redirect(routes.Users.get(newUser.email));
		}
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result update(String email) {
		MAUser user = MAUser.findByEmail(email);
		MAUser loggedInUser = MAUser
				.findByEmail(session(MAController.COOKIE_EMAIL));
		List<MAExperiment> experimentList = MAExperiment.findAll();
		
		if (user == null) {
			return badRequestUserNotExist(email, loggedInUser, experimentList);
		}

		// To change a user this user must be logged in.
		if (user.email != loggedInUser.email) {
			String errorMsg = "You must be logged in as " + user.toString()
					+ " to update this user.";
			return badRequest(views.html.admin.user.index.render(
					experimentList, errorMsg, loggedInUser, user));
		}

		Form<MAUser> form = Form.form(MAUser.class).fill(user);
		return ok(views.html.admin.user.update.render(experimentList, user,
				null, loggedInUser, form));
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result submitUpdated(String email) {
		MAUser user = MAUser.findByEmail(email);
		MAUser loggedInUser = MAUser
				.findByEmail(session(MAController.COOKIE_EMAIL));
		List<MAExperiment> experimentList = MAExperiment.findAll();
		Form<MAUser> form = Form.form(MAUser.class).bindFromRequest();
		
		if (user == null) {
			return badRequestUserNotExist(email, loggedInUser, experimentList);
		}

		// To change a user this user must be logged in.
		if (user.email != loggedInUser.email) {
			form.reject("You must be logged in as " + user.toString()
					+ " to update this user.");
		}

		if (form.hasErrors()) {
			return badRequest(views.html.admin.user.update.render(
					experimentList, user, null, loggedInUser, form));
		} else {
			// Update user in database
			// Do not update 'email' since it's the id and should stay unaltered.
			// For the password we have an extra form.
			DynamicForm requestData = Form.form().bindFromRequest();
			user.name = requestData.get("name");
			user.merge();
			return redirect(routes.Users.get(email));
		}
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result changePassword(String email) {
		MAUser user = MAUser.findByEmail(email);
		MAUser loggedInUser = MAUser
				.findByEmail(session(MAController.COOKIE_EMAIL));
		List<MAExperiment> experimentList = MAExperiment.findAll();
		
		if (user == null) {
			return badRequestUserNotExist(email, loggedInUser, experimentList);
		}

		// To change a user this user must be logged in.
		if (user.email != loggedInUser.email) {
			String errorMsg = "You must be logged in as " + user.toString()
					+ " to update this user.";
			return badRequest(views.html.admin.user.index.render(
					experimentList, errorMsg, loggedInUser, user));
		}

		Form<MAUser> form = Form.form(MAUser.class).fill(user);
		return ok(views.html.admin.user.changePassword.render(experimentList,
				user, null, loggedInUser, form));
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result submitChangedPassword(String email)
			throws NoSuchAlgorithmException, UnsupportedEncodingException {
		MAUser user = MAUser.findByEmail(email);
		Form<MAUser> form = Form.form(MAUser.class).fill(user);
		MAUser loggedInUser = MAUser
				.findByEmail(session(MAController.COOKIE_EMAIL));
		List<MAExperiment> experimentList = MAExperiment.findAll();
		DynamicForm requestData = Form.form().bindFromRequest();

		if (user == null) {
			return badRequestUserNotExist(email, loggedInUser, experimentList);
		}

		// To change a user this user must be logged in.
		if (user.email != loggedInUser.email) {
			form.reject("You must be logged in as " + user.toString()
					+ " to update this user.");
		}

		// Authenticate
		String oldPasswordHash = MAUser.getHashMDFive(requestData
				.get("oldPassword"));
		if (MAUser.authenticate(user.email, oldPasswordHash) == null) {
			form.reject("password", "Wrong old password");
		}

		// Check for non empty passwords
		String newPassword = requestData.get("newPassword");
		String newPasswordRepeat = requestData.get("newPasswordRepeat");
		if (newPassword.trim().isEmpty() || newPasswordRepeat.trim().isEmpty()) {
			form.reject("password", "Passwords shouldn't be empty strings.");
		}

		// Check that both passwords are the same
		String newPasswordHash = MAUser.getHashMDFive(newPassword);
		String newPasswordHashRepeat = MAUser.getHashMDFive(newPasswordRepeat);
		if (!newPasswordHash.equals(newPasswordHashRepeat)) {
			form.reject("password", "New passwords aren't the same.");
		}

		if (form.hasErrors()) {
			return badRequest(views.html.admin.user.changePassword.render(
					experimentList, user, null, loggedInUser, form));
		} else {
			// Update password hash in DB
			user.passwordHash = newPasswordHash;
			user.merge();
			return redirect(routes.Users.get(email));
		}
	}

}
