package controllers;

import java.util.List;

import models.MAStudy;
import models.MAUser;
import play.data.DynamicForm;
import play.data.Form;
import play.db.jpa.Transactional;
import play.mvc.Result;
import play.mvc.Security;

public class Users extends MAController {

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

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result profile(String email) {
		MAUser user = MAUser.findByEmail(email);
		MAUser loggedInUser = MAUser
				.findByEmail(session(MAController.COOKIE_EMAIL));
		if (loggedInUser == null) {
			return redirect(routes.Admin.login());
		}
		List<MAStudy> studyList = MAStudy.findAll();

		if (user == null) {
			return badRequestUserNotExist(email, loggedInUser, studyList);
		}

		String breadcrumbs = MAController.generateBreadcrumbs(
				MAController.getDashboardBreadcrumb(), getUserBreadcrumb(user));
		return ok(views.html.admin.user.profile.render(studyList, loggedInUser,
				breadcrumbs, null, user));
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result create() {
		List<MAStudy> studyList = MAStudy.findAll();
		MAUser loggedInUser = MAUser
				.findByEmail(session(MAController.COOKIE_EMAIL));
		if (loggedInUser == null) {
			return redirect(routes.Admin.login());
		}
		String breadcrumbs = MAController.generateBreadcrumbs(
				MAController.getDashboardBreadcrumb(), "New User");
		return ok(views.html.admin.user.create.render(studyList, loggedInUser,
				breadcrumbs, Form.form(MAUser.class)));
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result submit() throws Exception {
		Form<MAUser> form = Form.form(MAUser.class).bindFromRequest();
		List<MAStudy> studyList = MAStudy.findAll();
		MAUser loggedInUser = MAUser
				.findByEmail(session(MAController.COOKIE_EMAIL));
		if (loggedInUser == null) {
			return redirect(routes.Admin.login());
		}

		if (form.hasErrors()) {
			String breadcrumbs = MAController.generateBreadcrumbs(
					MAController.getDashboardBreadcrumb(), "New User");
			return badRequest(views.html.admin.user.create.render(studyList,
					loggedInUser, breadcrumbs, form));
		}

		// Check if user with this email already exists.
		MAUser newUser = form.get();
		if (MAUser.findByEmail(newUser.getEmail()) != null) {
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
		String passwordHash = MAUser.getHashMDFive(password);
		String passwordHashRepeat = MAUser.getHashMDFive(passwordRepeat);
		if (!passwordHash.equals(passwordHashRepeat)) {
			form.reject(PASSWORD, PASSWORDS_ARENT_THE_SAME);
		}

		if (form.hasErrors()) {
			String breadcrumbs = MAController.generateBreadcrumbs(
					MAController.getDashboardBreadcrumb(), "New User");
			return badRequest(views.html.admin.user.create.render(studyList,
					loggedInUser, breadcrumbs, form));
		} else {
			newUser.setPasswordHash(passwordHash);
			newUser.persist();
			return redirect(routes.Users.profile(newUser.getEmail()));
		}
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result editProfile(String email) {
		MAUser user = MAUser.findByEmail(email);
		MAUser loggedInUser = MAUser
				.findByEmail(session(MAController.COOKIE_EMAIL));
		List<MAStudy> studyList = MAStudy.findAll();
		if (loggedInUser == null) {
			return redirect(routes.Admin.login());
		}

		if (user == null) {
			return badRequestUserNotExist(email, loggedInUser, studyList);
		}

		// To change a user this user must be logged in.
		if (!user.getEmail().equals(loggedInUser.getEmail())) {
			String errorMsg = errorMsgYouMustBeLoggedIn(user);
			String breadcrumbs = MAController.generateBreadcrumbs(
					MAController.getDashboardBreadcrumb());
			List<MAUser> userList = MAUser.findAll();
			return badRequest(views.html.admin.dashboard.render(studyList,
					loggedInUser, breadcrumbs, userList, errorMsg));
		}

		Form<MAUser> form = Form.form(MAUser.class).fill(user);
		String breadcrumbs = MAController.generateBreadcrumbs(
				MAController.getDashboardBreadcrumb(), getUserBreadcrumb(user),
				"Edit Profile");
		return ok(views.html.admin.user.editProfile.render(studyList, loggedInUser,
				breadcrumbs, user, form));
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result submitEditedProfile(String email) {
		MAUser user = MAUser.findByEmail(email);
		MAUser loggedInUser = MAUser
				.findByEmail(session(MAController.COOKIE_EMAIL));
		List<MAStudy> studyList = MAStudy.findAll();
		if (loggedInUser == null) {
			return redirect(routes.Admin.login());
		}

		Form<MAUser> form = Form.form(MAUser.class).bindFromRequest();

		if (user == null) {
			return badRequestUserNotExist(email, loggedInUser, studyList);
		}

		// To change a user this user must be logged in.
		if (!user.getEmail().equals(loggedInUser.getEmail())) {
			form.reject(errorMsgYouMustBeLoggedIn(user));
		}

		if (form.hasErrors()) {
			String breadcrumbs = MAController.generateBreadcrumbs(
					MAController.getDashboardBreadcrumb(), getUserBreadcrumb(user),
					"Edit Profile");
			return badRequest(views.html.admin.user.editProfile.render(studyList,
					loggedInUser, breadcrumbs, user, form));
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
	public static Result changePassword(String email) {
		MAUser user = MAUser.findByEmail(email);
		MAUser loggedInUser = MAUser
				.findByEmail(session(MAController.COOKIE_EMAIL));
		List<MAStudy> studyList = MAStudy.findAll();
		if (loggedInUser == null) {
			return redirect(routes.Admin.login());
		}

		if (user == null) {
			return badRequestUserNotExist(email, loggedInUser, studyList);
		}

		// To change a user's password this user must be logged in.
		if (!user.getEmail().equals(loggedInUser.getEmail())) {
			String errorMsg = errorMsgYouMustBeLoggedIn(user);
			String breadcrumbs = MAController.generateBreadcrumbs(
					MAController.getDashboardBreadcrumb());
			List<MAUser> userList = MAUser.findAll();
			return badRequest(views.html.admin.dashboard.render(studyList,
					loggedInUser, breadcrumbs, userList, errorMsg));
		}

		Form<MAUser> form = Form.form(MAUser.class).fill(user);
		String breadcrumbs = MAController.generateBreadcrumbs(
				MAController.getDashboardBreadcrumb(), getUserBreadcrumb(user),
				"Change Password");
		return ok(views.html.admin.user.changePassword.render(studyList,
				loggedInUser, breadcrumbs, form));
	}

	@Transactional
	@Security.Authenticated(Secured.class)
	public static Result submitChangedPassword(String email) throws Exception {
		MAUser user = MAUser.findByEmail(email);
		Form<MAUser> form = Form.form(MAUser.class).fill(user);
		MAUser loggedInUser = MAUser
				.findByEmail(session(MAController.COOKIE_EMAIL));
		List<MAStudy> studyList = MAStudy.findAll();
		DynamicForm requestData = Form.form().bindFromRequest();
		if (loggedInUser == null) {
			return redirect(routes.Admin.login());
		}

		if (user == null) {
			return badRequestUserNotExist(email, loggedInUser, studyList);
		}

		// To change a user this user must be logged in.
		if (!user.getEmail().equals(loggedInUser.getEmail())) {
			form.reject(errorMsgYouMustBeLoggedIn(user));
		}

		// Authenticate
		String oldPasswordHash = MAUser.getHashMDFive(requestData
				.get(OLD_PASSWORD));
		if (MAUser.authenticate(user.getEmail(), oldPasswordHash) == null) {
			form.reject(OLD_PASSWORD, WRONG_OLD_PASSWORD);
		}

		// Check for non empty passwords
		String newPassword = requestData.get(NEW_PASSWORD);
		String newPasswordRepeat = requestData.get(NEW_PASSWORD_REPEAT);
		if (newPassword.trim().isEmpty() || newPasswordRepeat.trim().isEmpty()) {
			form.reject(NEW_PASSWORD, PASSWORDS_SHOULDNT_BE_EMPTY_STRINGS);
		}

		// Check that both passwords are the same
		String newPasswordHash = MAUser.getHashMDFive(newPassword);
		String newPasswordHashRepeat = MAUser.getHashMDFive(newPasswordRepeat);
		if (!newPasswordHash.equals(newPasswordHashRepeat)) {
			form.reject(NEW_PASSWORD, PASSWORDS_ARENT_THE_SAME);
		}

		if (form.hasErrors()) {
			String breadcrumbs = MAController.generateBreadcrumbs(
					MAController.getDashboardBreadcrumb(), getUserBreadcrumb(user),
					"Change Password");
			return badRequest(views.html.admin.user.changePassword.render(
					studyList, loggedInUser, breadcrumbs, form));
		} else {
			// Update password hash in DB
			user.setPasswordHash(newPasswordHash);
			user.merge();
			return redirect(routes.Users.profile(email));
		}
	}

	private static String errorMsgYouMustBeLoggedIn(MAUser user) {
		return "You must be logged in as " + user.toString()
				+ " to update this user.";
	}

	public static String getUserBreadcrumb(MAUser user) {
		StringBuffer sb = new StringBuffer();
		sb.append("<a href=\"");
		sb.append(routes.Users.profile(user.getEmail()));
		sb.append("\">");
		sb.append(user.getName());
		sb.append(" (");
		sb.append(user.getEmail());
		sb.append(")");
		sb.append("</a>");
		return sb.toString();
	}

}
