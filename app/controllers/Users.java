package controllers;

import java.util.List;

import controllers.routes;
import exceptions.ResultException;
import models.StudyModel;
import models.UserModel;
import models.workers.MAWorker;
import play.data.DynamicForm;
import play.data.Form;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Security;
import play.mvc.SimpleResult;

public class Users extends Controller {

	public static final String NAME = "name";
	public static final String EMAIL = "email";
	public static final String PASSWORD_REPEAT = "passwordRepeat";
	public static final String NEW_PASSWORD_REPEAT = "newPasswordRepeat";
	public static final String PASSWORD = "password";
	public static final String OLD_PASSWORD = "oldPassword";
	public static final String NEW_PASSWORD = "newPassword";
	public static final String WRONG_OLD_PASSWORD = "Wrong old password";
	public static final String PASSWORDS_ARENT_THE_SAME = "Passwords aren't the same.";
	public static final String PASSWORDS_SHOULDNT_BE_EMPTY_STRINGS = "Passwords shouldn't be empty strings.";
	public static final String THIS_EMAIL_IS_ALREADY_REGISTERED = "This email is already registered.";
	public static final String COOKIE_EMAIL = "email";

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result profile(String email) throws ResultException {
		UserModel user = UserModel.findByEmail(email);
		UserModel loggedInUser = UserModel
				.findByEmail(session(Users.COOKIE_EMAIL));
		if (loggedInUser == null) {
			return redirect(routes.Authentication.login());
		}
		List<StudyModel> studyList = StudyModel.findAll();

		if (user == null) {
			throw BadRequests.badRequestUserNotExist(email, loggedInUser,
					studyList);
		}

		String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
				Breadcrumbs.getDashboardBreadcrumb(),
				Breadcrumbs.getUserBreadcrumb(user));
		return ok(views.html.mecharg.user.profile.render(studyList,
				loggedInUser, breadcrumbs, null, user));
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result create() {
		List<StudyModel> studyList = StudyModel.findAll();
		UserModel loggedInUser = UserModel
				.findByEmail(session(Users.COOKIE_EMAIL));
		if (loggedInUser == null) {
			return redirect(routes.Authentication.login());
		}
		String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
				Breadcrumbs.getDashboardBreadcrumb(), "New User");
		return ok(views.html.mecharg.user.create.render(studyList,
				loggedInUser, breadcrumbs, Form.form(UserModel.class)));
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result submit() throws Exception {
		Form<UserModel> form = Form.form(UserModel.class).bindFromRequest();
		List<StudyModel> studyList = StudyModel.findAll();
		UserModel loggedInUser = UserModel
				.findByEmail(session(Users.COOKIE_EMAIL));
		if (loggedInUser == null) {
			return redirect(routes.Authentication.login());
		}

		if (form.hasErrors()) {
			String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
					Breadcrumbs.getDashboardBreadcrumb(), "New User");
			SimpleResult result = badRequest(views.html.mecharg.user.create
					.render(studyList, loggedInUser, breadcrumbs, form));
			throw new ResultException(result);
		}

		// Check if user with this email already exists.
		UserModel newUser = form.get();
		if (UserModel.findByEmail(newUser.getEmail()) != null) {
			form.reject(EMAIL, THIS_EMAIL_IS_ALREADY_REGISTERED);
		}

		// Check for non empty passwords
		DynamicForm requestData = Form.form().bindFromRequest();
		String password = requestData.get(PASSWORD);
		String passwordRepeat = requestData.get(PASSWORD_REPEAT);
		if (password.trim().isEmpty() || passwordRepeat.trim().isEmpty()) {
			form.reject(PASSWORD, PASSWORDS_SHOULDNT_BE_EMPTY_STRINGS);
		}

		// Check that both passwords are the same
		String passwordHash = UserModel.getHashMDFive(password);
		String passwordHashRepeat = UserModel.getHashMDFive(passwordRepeat);
		if (!passwordHash.equals(passwordHashRepeat)) {
			form.reject(PASSWORD, PASSWORDS_ARENT_THE_SAME);
		}

		if (form.hasErrors()) {
			String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
					Breadcrumbs.getDashboardBreadcrumb(), "New User");
			SimpleResult result = badRequest(views.html.mecharg.user.create
					.render(studyList, loggedInUser, breadcrumbs, form));
			throw new ResultException(result);
		} else {
			MAWorker worker = new MAWorker(newUser);
			worker.persist();
			newUser.setPasswordHash(passwordHash);
			newUser.setWorker(worker);
			newUser.persist();
			worker.merge();
			return redirect(routes.Users.profile(newUser.getEmail()));
		}
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result editProfile(String email) throws ResultException {
		UserModel user = UserModel.findByEmail(email);
		UserModel loggedInUser = UserModel
				.findByEmail(session(Users.COOKIE_EMAIL));
		List<StudyModel> studyList = StudyModel.findAll();
		if (loggedInUser == null) {
			return redirect(routes.Authentication.login());
		}

		if (user == null) {
			throw BadRequests.badRequestUserNotExist(email, loggedInUser,
					studyList);
		}

		// To change a user this user must be logged in.
		if (!user.getEmail().equals(loggedInUser.getEmail())) {
			throw BadRequests.badRequestMustBeLoggedInAsUser(user,
					loggedInUser, studyList);
		}

		Form<UserModel> form = Form.form(UserModel.class).fill(user);
		String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
				Breadcrumbs.getDashboardBreadcrumb(),
				Breadcrumbs.getUserBreadcrumb(user), "Edit Profile");
		return ok(views.html.mecharg.user.editProfile.render(studyList,
				loggedInUser, breadcrumbs, user, form));
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result submitEditedProfile(String email)
			throws ResultException {
		UserModel user = UserModel.findByEmail(email);
		UserModel loggedInUser = UserModel
				.findByEmail(session(Users.COOKIE_EMAIL));
		List<StudyModel> studyList = StudyModel.findAll();
		if (loggedInUser == null) {
			return redirect(routes.Authentication.login());
		}

		Form<UserModel> form = Form.form(UserModel.class).bindFromRequest();

		if (user == null) {
			throw BadRequests.badRequestUserNotExist(email, loggedInUser,
					studyList);
		}

		// To change a user this user must be logged in.
		if (!user.getEmail().equals(loggedInUser.getEmail())) {
			form.reject(BadRequests.mustBeLoggedInAsUser(user));
		}

		if (form.hasErrors()) {
			String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
					Breadcrumbs.getDashboardBreadcrumb(),
					Breadcrumbs.getUserBreadcrumb(user), "Edit Profile");
			SimpleResult result = badRequest(views.html.mecharg.user.editProfile
					.render(studyList, loggedInUser, breadcrumbs, user, form));
			throw new ResultException(result);
		} else {
			// Update user in database
			// Do not update 'email' since it's the id and should stay
			// unaltered. For the password we have an extra form.
			DynamicForm requestData = Form.form().bindFromRequest();
			String name = requestData.get(NAME);
			user.update(name);
			user.merge();
			return redirect(routes.Users.profile(email));
		}
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result changePassword(String email) throws ResultException {
		UserModel user = UserModel.findByEmail(email);
		UserModel loggedInUser = UserModel
				.findByEmail(session(Users.COOKIE_EMAIL));
		List<StudyModel> studyList = StudyModel.findAll();
		if (loggedInUser == null) {
			return redirect(routes.Authentication.login());
		}

		if (user == null) {
			throw BadRequests.badRequestUserNotExist(email, loggedInUser,
					studyList);
		}

		// To change a user's password this user must be logged in.
		if (!user.getEmail().equals(loggedInUser.getEmail())) {
			throw BadRequests.badRequestMustBeLoggedInAsUser(user,
					loggedInUser, studyList);
		}

		Form<UserModel> form = Form.form(UserModel.class).fill(user);
		String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
				Breadcrumbs.getDashboardBreadcrumb(),
				Breadcrumbs.getUserBreadcrumb(user), "Change Password");
		return ok(views.html.mecharg.user.changePassword.render(studyList,
				loggedInUser, breadcrumbs, form));
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result submitChangedPassword(String email) throws Exception {
		UserModel user = UserModel.findByEmail(email);
		Form<UserModel> form = Form.form(UserModel.class).fill(user);
		UserModel loggedInUser = UserModel
				.findByEmail(session(Users.COOKIE_EMAIL));
		List<StudyModel> studyList = StudyModel.findAll();
		DynamicForm requestData = Form.form().bindFromRequest();
		if (loggedInUser == null) {
			return redirect(routes.Authentication.login());
		}

		if (user == null) {
			throw BadRequests.badRequestUserNotExist(email, loggedInUser,
					studyList);
		}

		// To change a user this user must be logged in.
		if (!user.getEmail().equals(loggedInUser.getEmail())) {
			form.reject(BadRequests.mustBeLoggedInAsUser(user));
		}

		// Authenticate
		String oldPasswordHash = UserModel.getHashMDFive(requestData
				.get(OLD_PASSWORD));
		if (UserModel.authenticate(user.getEmail(), oldPasswordHash) == null) {
			form.reject(OLD_PASSWORD, WRONG_OLD_PASSWORD);
		}

		// Check for non empty passwords
		String newPassword = requestData.get(NEW_PASSWORD);
		String newPasswordRepeat = requestData.get(NEW_PASSWORD_REPEAT);
		if (newPassword.trim().isEmpty() || newPasswordRepeat.trim().isEmpty()) {
			form.reject(NEW_PASSWORD, PASSWORDS_SHOULDNT_BE_EMPTY_STRINGS);
		}

		// Check that both passwords are the same
		String newPasswordHash = UserModel.getHashMDFive(newPassword);
		String newPasswordHashRepeat = UserModel
				.getHashMDFive(newPasswordRepeat);
		if (!newPasswordHash.equals(newPasswordHashRepeat)) {
			form.reject(NEW_PASSWORD, PASSWORDS_ARENT_THE_SAME);
		}

		if (form.hasErrors()) {
			String breadcrumbs = Breadcrumbs.generateBreadcrumbs(
					Breadcrumbs.getDashboardBreadcrumb(),
					Breadcrumbs.getUserBreadcrumb(user), "Change Password");
			SimpleResult result = badRequest(views.html.mecharg.user.changePassword
					.render(studyList, loggedInUser, breadcrumbs, form));
			throw new ResultException(result);
		} else {
			// Update password hash in DB
			user.setPasswordHash(newPasswordHash);
			user.merge();
			return redirect(routes.Users.profile(email));
		}
	}

}
