package controllers;

import java.util.List;

import models.StudyModel;
import models.UserModel;
import models.workers.JatosWorker;
import play.Logger;
import play.data.DynamicForm;
import play.data.Form;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.Security;
import services.Breadcrumbs;
import services.ErrorMessages;
import exceptions.ResultException;

@Security.Authenticated(Secured.class)
public class Users extends Controller {

	private static final String CLASS_NAME = Users.class.getSimpleName();

	public static final String COOKIE_EMAIL = "email";

	@Transactional
	public static Result profile(String email) throws ResultException {
		Logger.info(CLASS_NAME + ".profile: " + "email " + email + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		UserModel user = ControllerUtils.retrieveUser(email);
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		List<StudyModel> studyList = StudyModel.findAllByUser(loggedInUser
				.getEmail());
		ControllerUtils.checkUserLoggedIn(user, loggedInUser);

		Breadcrumbs breadcrumbs = Breadcrumbs.generateForUser(user);
		return ok(views.html.jatos.user.profile.render(studyList, loggedInUser,
				breadcrumbs, null, user));
	}

	@Transactional
	public static Result create() throws ResultException {
		Logger.info(CLASS_NAME + ".create: " + "logged-in user's email "
				+ session(Users.COOKIE_EMAIL));
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		List<StudyModel> studyList = StudyModel.findAllByUser(loggedInUser
				.getEmail());
		Breadcrumbs breadcrumbs = Breadcrumbs
				.generateForHome(Breadcrumbs.NEW_USER);
		return ok(views.html.jatos.user.create.render(studyList, loggedInUser,
				breadcrumbs, null, Form.form(UserModel.class)));
	}

	@Transactional
	public static Result submit() throws Exception {
		Logger.info(CLASS_NAME + ".submit: " + "logged-in user's email "
				+ session(Users.COOKIE_EMAIL));
		Form<UserModel> form = Form.form(UserModel.class).bindFromRequest();
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		List<StudyModel> studyList = StudyModel.findAllByUser(loggedInUser
				.getEmail());

		if (form.hasErrors()) {
			ControllerUtils.throwCreateUserResultException(studyList,
					loggedInUser, form, Http.Status.BAD_REQUEST);
		}

		// Check if user with this email already exists.
		UserModel newUser = form.get();
		if (UserModel.findByEmail(newUser.getEmail()) != null) {
			form.reject(UserModel.EMAIL,
					ErrorMessages.THIS_EMAIL_IS_ALREADY_REGISTERED);
		}

		// Check for non empty passwords
		DynamicForm requestData = Form.form().bindFromRequest();
		String password = requestData.get(UserModel.PASSWORD);
		String passwordRepeat = requestData.get(UserModel.PASSWORD_REPEAT);
		if (password.trim().isEmpty() || passwordRepeat.trim().isEmpty()) {
			form.reject(UserModel.PASSWORD,
					ErrorMessages.PASSWORDS_SHOULDNT_BE_EMPTY_STRINGS);
		}

		// Check that both passwords are the same
		String passwordHash = UserModel.getHashMDFive(password);
		String passwordHashRepeat = UserModel.getHashMDFive(passwordRepeat);
		if (!passwordHash.equals(passwordHashRepeat)) {
			form.reject(UserModel.PASSWORD, ErrorMessages.PASSWORDS_DONT_MATCH);
		}

		if (form.hasErrors()) {
			ControllerUtils.throwCreateUserResultException(studyList,
					loggedInUser, form, Http.Status.BAD_REQUEST);
		}
		JatosWorker worker = new JatosWorker(newUser);
		worker.persist();
		newUser.setPasswordHash(passwordHash);
		newUser.setWorker(worker);
		newUser.persist();
		worker.merge();
		return redirect(routes.Home.home());
	}

	@Transactional
	public static Result editProfile(String email) throws ResultException {
		Logger.info(CLASS_NAME + ".editProfile: " + "email " + email + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		UserModel user = ControllerUtils.retrieveUser(email);
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		List<StudyModel> studyList = StudyModel.findAllByUser(loggedInUser
				.getEmail());
		ControllerUtils.checkUserLoggedIn(user, loggedInUser);

		Form<UserModel> form = Form.form(UserModel.class).fill(user);
		Breadcrumbs breadcrumbs = Breadcrumbs.generateForUser(user,
				Breadcrumbs.EDIT_PROFILE);
		return ok(views.html.jatos.user.editProfile.render(studyList,
				loggedInUser, breadcrumbs, null, user, form));
	}

	@Transactional
	public static Result submitEditedProfile(String email)
			throws ResultException {
		Logger.info(CLASS_NAME + ".submitEditedProfile: " + "email " + email
				+ ", " + "logged-in user's email "
				+ session(Users.COOKIE_EMAIL));
		UserModel user = ControllerUtils.retrieveUser(email);
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		List<StudyModel> studyList = StudyModel.findAllByUser(loggedInUser
				.getEmail());
		ControllerUtils.checkUserLoggedIn(user, loggedInUser);

		Form<UserModel> form = Form.form(UserModel.class).bindFromRequest();
		if (form.hasErrors()) {
			ControllerUtils.throwEditUserResultException(studyList,
					loggedInUser, form, loggedInUser, Http.Status.BAD_REQUEST);
		}
		// Update user in database
		// Do not update 'email' since it's the ID and should stay
		// unaltered. For the password we have an extra form.
		DynamicForm requestData = Form.form().bindFromRequest();
		String name = requestData.get(UserModel.NAME);
		user.update(name);
		user.merge();
		return redirect(routes.Users.profile(email));
	}

	@Transactional
	public static Result changePassword(String email) throws ResultException {
		Logger.info(CLASS_NAME + ".changePassword: " + "email " + email + ", "
				+ "logged-in user's email " + session(Users.COOKIE_EMAIL));
		UserModel user = ControllerUtils.retrieveUser(email);
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		List<StudyModel> studyList = StudyModel.findAllByUser(loggedInUser
				.getEmail());
		ControllerUtils.checkUserLoggedIn(user, loggedInUser);

		Form<UserModel> form = Form.form(UserModel.class).fill(user);
		Breadcrumbs breadcrumbs = Breadcrumbs.generateForUser(user,
				Breadcrumbs.CHANGE_PASSWORD);
		return ok(views.html.jatos.user.changePassword.render(studyList,
				loggedInUser, breadcrumbs, null, form));
	}

	@Transactional
	public static Result submitChangedPassword(String email) throws Exception {
		Logger.info(CLASS_NAME + ".submitChangedPassword: " + "email " + email
				+ ", " + "logged-in user's email "
				+ session(Users.COOKIE_EMAIL));
		UserModel user = ControllerUtils.retrieveUser(email);
		Form<UserModel> form = Form.form(UserModel.class).fill(user);
		UserModel loggedInUser = ControllerUtils.retrieveLoggedInUser();
		List<StudyModel> studyList = StudyModel.findAllByUser(loggedInUser
				.getEmail());
		ControllerUtils.checkUserLoggedIn(user, loggedInUser);

		DynamicForm requestData = Form.form().bindFromRequest();

		// Authenticate
		String oldPasswordHash = UserModel.getHashMDFive(requestData
				.get(UserModel.OLD_PASSWORD));
		if (UserModel.authenticate(user.getEmail(), oldPasswordHash) == null) {
			form.reject(UserModel.OLD_PASSWORD,
					ErrorMessages.WRONG_OLD_PASSWORD);
		}

		// Check for non empty passwords
		String newPassword = requestData.get(UserModel.NEW_PASSWORD);
		String newPasswordRepeat = requestData.get(UserModel.PASSWORD_REPEAT);
		if (newPassword.trim().isEmpty() || newPasswordRepeat.trim().isEmpty()) {
			form.reject(UserModel.NEW_PASSWORD,
					ErrorMessages.PASSWORDS_SHOULDNT_BE_EMPTY_STRINGS);
		}

		// Check that both passwords are the same
		String newPasswordHash = UserModel.getHashMDFive(newPassword);
		String newPasswordHashRepeat = UserModel
				.getHashMDFive(newPasswordRepeat);
		if (!newPasswordHash.equals(newPasswordHashRepeat)) {
			form.reject(UserModel.NEW_PASSWORD,
					ErrorMessages.PASSWORDS_DONT_MATCH);
		}

		if (form.hasErrors()) {
			ControllerUtils.throwChangePasswordUserResultException(studyList,
					loggedInUser, form, Http.Status.BAD_REQUEST, loggedInUser);
		}
		// Update password hash in DB
		user.setPasswordHash(newPasswordHash);
		user.merge();
		return redirect(routes.Users.profile(email));
	}

}
