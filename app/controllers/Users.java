package controllers;

import java.util.List;

import models.StudyModel;
import models.UserModel;
import play.Logger;
import play.data.DynamicForm;
import play.data.Form;
import play.data.validation.ValidationError;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.With;
import services.Breadcrumbs;
import services.JatosGuiExceptionThrower;
import services.UserService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import common.JatosGuiAction;

import daos.AbstractDao;
import daos.IStudyDao;
import daos.IUserDao;
import exceptions.JatosGuiException;

/**
 * Controller with actions concerning users
 * 
 * @author Kristian Lange
 */
@With(JatosGuiAction.class)
@Singleton
public class Users extends Controller {

	private static final String CLASS_NAME = Users.class.getSimpleName();

	public static final String SESSION_EMAIL = "email";

	private final UserService userService;
	private final JatosGuiExceptionThrower jatosGuiExceptionThrower;
	private final IUserDao userDao;
	private final IStudyDao studyDao;

	@Inject
	public Users(IUserDao userDao, UserService userService,
			JatosGuiExceptionThrower jatosGuiExceptionThrower,
			IStudyDao studyDao) {
		this.userDao = userDao;
		this.userService = userService;
		this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
		this.studyDao = studyDao;
	}

	/**
	 * Shows the profile view of a user
	 */
	@Transactional
	public Result profile(String email) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".profile: " + "email " + email + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		UserModel user = userService.retrieveUser(email);
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		List<StudyModel> studyList = studyDao.findAllByUser(loggedInUser
				.getEmail());
		userService.checkUserLoggedIn(user, loggedInUser);

		Breadcrumbs breadcrumbs = Breadcrumbs.generateForUser(user);
		return ok(views.html.jatos.user.profile.render(studyList, loggedInUser,
				breadcrumbs, null, user));
	}

	/**
	 * Shows a view with a form to create a new user.
	 */
	@Transactional
	public Result create() throws JatosGuiException {
		Logger.info(CLASS_NAME + ".create: " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		List<StudyModel> studyList = studyDao.findAllByUser(loggedInUser
				.getEmail());
		Breadcrumbs breadcrumbs = Breadcrumbs
				.generateForHome(Breadcrumbs.NEW_USER);
		return ok(views.html.jatos.user.create.render(studyList, loggedInUser,
				breadcrumbs, null, Form.form(UserModel.class)));
	}

	/**
	 * Handles post request of user create form.
	 */
	@Transactional
	public Result submit() throws Exception {
		Logger.info(CLASS_NAME + ".submit: " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		Form<UserModel> form = Form.form(UserModel.class).bindFromRequest();
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		List<StudyModel> studyList = studyDao.findAllByUser(loggedInUser
				.getEmail());

		if (form.hasErrors()) {
			jatosGuiExceptionThrower.throwCreateUser(studyList, loggedInUser,
					form, null, Http.Status.BAD_REQUEST);
		}

		UserModel newUser = form.get();
		DynamicForm requestData = Form.form().bindFromRequest();
		String password = requestData.get(UserModel.PASSWORD);
		String passwordRepeat = requestData.get(UserModel.PASSWORD_REPEAT);
		List<ValidationError> errorList = userService.validateNewUser(newUser,
				password, passwordRepeat);
		if (!errorList.isEmpty()) {
			jatosGuiExceptionThrower.throwCreateUser(studyList, loggedInUser,
					form, errorList, Http.Status.BAD_REQUEST);
		}

		String passwordHash = userService.getHashMDFive(password);
		newUser.setPasswordHash(passwordHash);
		userDao.addUser(newUser);
		return redirect(routes.Home.home());
	}

	/**
	 * Shows view with form to edit a user profile.
	 */
	@Transactional
	public Result editProfile(String email) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".editProfile: " + "email " + email + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		UserModel user = userService.retrieveUser(email);
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		List<StudyModel> studyList = studyDao.findAllByUser(loggedInUser
				.getEmail());
		userService.checkUserLoggedIn(user, loggedInUser);

		Form<UserModel> form = Form.form(UserModel.class).fill(user);
		Breadcrumbs breadcrumbs = Breadcrumbs.generateForUser(user,
				Breadcrumbs.EDIT_PROFILE);
		return ok(views.html.jatos.user.editProfile.render(studyList,
				loggedInUser, breadcrumbs, null, user, form));
	}

	/**
	 * Handles post request of user edit profile form.
	 */
	@Transactional
	public Result submitEditedProfile(String email) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".submitEditedProfile: " + "email " + email
				+ ", " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		UserModel user = userService.retrieveUser(email);
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		List<StudyModel> studyList = studyDao.findAllByUser(loggedInUser
				.getEmail());
		userService.checkUserLoggedIn(user, loggedInUser);

		Form<UserModel> form = Form.form(UserModel.class).bindFromRequest();
		if (form.hasErrors()) {
			jatosGuiExceptionThrower.throwEditUser(studyList, loggedInUser,
					form, loggedInUser, Http.Status.BAD_REQUEST);
		}
		// Update user in database
		// Do not update 'email' since it's the ID and should stay
		// unaltered. For the password we have an extra form.
		DynamicForm requestData = Form.form().bindFromRequest();
		String name = requestData.get(UserModel.NAME);
		userDao.updateUser(user, name);
		return redirect(routes.Users.profile(email));
	}

	/**
	 * Shows view to change the password of a user.
	 */
	@Transactional
	public Result changePassword(String email) throws JatosGuiException {
		Logger.info(CLASS_NAME + ".changePassword: " + "email " + email + ", "
				+ "logged-in user's email " + session(Users.SESSION_EMAIL));
		UserModel user = userService.retrieveUser(email);
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		List<StudyModel> studyList = studyDao.findAllByUser(loggedInUser
				.getEmail());
		userService.checkUserLoggedIn(user, loggedInUser);

		Form<UserModel> form = Form.form(UserModel.class).fill(user);
		Breadcrumbs breadcrumbs = Breadcrumbs.generateForUser(user,
				Breadcrumbs.CHANGE_PASSWORD);
		return ok(views.html.jatos.user.changePassword.render(studyList,
				loggedInUser, breadcrumbs, null, form));
	}

	/**
	 * Handles post request of change password form.
	 */
	@Transactional
	public Result submitChangedPassword(String email) throws Exception {
		Logger.info(CLASS_NAME + ".submitChangedPassword: " + "email " + email
				+ ", " + "logged-in user's email "
				+ session(Users.SESSION_EMAIL));
		UserModel user = userService.retrieveUser(email);
		Form<UserModel> form = Form.form(UserModel.class).fill(user);
		UserModel loggedInUser = userService.retrieveLoggedInUser();
		List<StudyModel> studyList = studyDao.findAllByUser(loggedInUser
				.getEmail());
		userService.checkUserLoggedIn(user, loggedInUser);

		DynamicForm requestData = Form.form().bindFromRequest();
		String newPassword = requestData.get(UserModel.NEW_PASSWORD);
		String newPasswordRepeat = requestData.get(UserModel.PASSWORD_REPEAT);
		String oldPasswordHash = userService.getHashMDFive(requestData
				.get(UserModel.OLD_PASSWORD));
		List<ValidationError> errorList = userService.validateChangePassword(
				user, newPassword, newPasswordRepeat, oldPasswordHash);
		if (!errorList.isEmpty()) {
			jatosGuiExceptionThrower.throwChangePasswordUser(studyList,
					loggedInUser, form, errorList, Http.Status.BAD_REQUEST,
					loggedInUser);
		}
		// Update password hash in DB
		String newPasswordHash = userService.getHashMDFive(newPassword);
		user.setPasswordHash(newPasswordHash);
		AbstractDao.merge(user);
		return redirect(routes.Users.profile(email));
	}

}
