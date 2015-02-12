package services;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import models.UserModel;
import play.data.validation.ValidationError;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Results;
import play.mvc.SimpleResult;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import controllers.ControllerUtils;
import controllers.Users;
import controllers.routes;
import daos.IUserDao;
import exceptions.JatosGuiException;

/**
 * Service class mostly for Users controller. Handles everything around
 * UserModel.
 * 
 * @author Kristian Lange
 */
@Singleton
public class UserService {

	public static final String ADMIN_EMAIL = "admin";
	public static final String ADMIN_PASSWORD = "admin";
	public static final String ADMIN_NAME = "Admin";

	private final IUserDao userDao;
	private final JatosGuiExceptionThrower jatosGuiExceptionThrower;

	@Inject
	public UserService(IUserDao userDao,
			JatosGuiExceptionThrower jatosGuiExceptionThrower) {
		this.userDao = userDao;
		this.jatosGuiExceptionThrower = jatosGuiExceptionThrower;
	}

	/**
	 * Retrieves the user with the given email form the DB. Throws a
	 * JatosGuiException if it doesn't exist.
	 */
	public UserModel retrieveUser(String email) throws JatosGuiException {
		UserModel user = userDao.findByEmail(email);
		if (user == null) {
			String errorMsg = ErrorMessages.userNotExist(email);
			jatosGuiExceptionThrower.throwHome(errorMsg,
					Http.Status.BAD_REQUEST);
		}
		return user;
	}

	/**
	 * Retrieves the user with the given email form the DB. Throws a
	 * JatosGuiException if it doesn't exist. The JatosGuiException will
	 * redirect to the login screen.
	 */
	public UserModel retrieveLoggedInUser() throws JatosGuiException {
		String email = Controller.session(Users.SESSION_EMAIL);
		UserModel loggedInUser = null;
		if (email != null) {
			loggedInUser = userDao.findByEmail(email);
		}
		if (loggedInUser == null) {
			String errorMsg = ErrorMessages.NO_USER_LOGGED_IN;
			SimpleResult result = null;
			if (ControllerUtils.isAjax()) {
				result = Results.badRequest(errorMsg);
			} else {
				result = (SimpleResult) Results.redirect(routes.Authentication
						.login());
			}
			throw new JatosGuiException(result, errorMsg);
		}
		return loggedInUser;
	}

	/**
	 * Throws a JatosGuiException in case the user's email isn't equal to the
	 * loggedInUser' email. Distinguishes between normal and Ajax request.
	 */
	public void checkUserLoggedIn(UserModel user, UserModel loggedInUser)
			throws JatosGuiException {
		if (!user.getEmail().equals(loggedInUser.getEmail())) {
			String errorMsg = ErrorMessages.mustBeLoggedInAsUser(user);
			jatosGuiExceptionThrower.throwHome(errorMsg, Http.Status.FORBIDDEN);
		}
	}

	public UserModel createAdmin() throws UnsupportedEncodingException,
			NoSuchAlgorithmException {
		String passwordHash = getHashMDFive(ADMIN_PASSWORD);
		UserModel adminUser = new UserModel(ADMIN_EMAIL, ADMIN_NAME,
				passwordHash);
		userDao.create(adminUser);
		return adminUser;
	}

	public String getHashMDFive(String str)
			throws UnsupportedEncodingException, NoSuchAlgorithmException {
		byte[] strBytes = str.getBytes("UTF-8");
		MessageDigest md = MessageDigest.getInstance("MD5");
		byte[] hashByte = md.digest(strBytes);

		// Convert the byte to hex format
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < hashByte.length; i++) {
			sb.append(Integer.toString((hashByte[i] & 0xff) + 0x100, 16)
					.substring(1));
		}
		return sb.toString();
	}

	public List<ValidationError> validateNewUser(UserModel newUser,
			String password, String passwordRepeat)
			throws UnsupportedEncodingException, NoSuchAlgorithmException {
		List<ValidationError> errorList = new ArrayList<ValidationError>();

		// Check if user with this email already exists.
		if (userDao.findByEmail(newUser.getEmail()) != null) {
			errorList.add(new ValidationError(UserModel.EMAIL,
					ErrorMessages.THIS_EMAIL_IS_ALREADY_REGISTERED));
		}

		checkPasswords(password, passwordRepeat, errorList);
		return errorList;
	}

	public List<ValidationError> validateChangePassword(UserModel user,
			String password, String passwordRepeat, String oldPasswordHash)
			throws UnsupportedEncodingException, NoSuchAlgorithmException {
		List<ValidationError> errorList = new ArrayList<ValidationError>();

		// Authenticate
		if (userDao.authenticate(user.getEmail(), oldPasswordHash) == null) {
			errorList.add(new ValidationError(UserModel.OLD_PASSWORD,
					ErrorMessages.WRONG_OLD_PASSWORD));
		}

		checkPasswords(password, passwordRepeat, errorList);
		return errorList;
	}

	public void checkPasswords(String password, String passwordRepeat,
			List<ValidationError> errorList)
			throws UnsupportedEncodingException, NoSuchAlgorithmException {

		// Check for non empty passwords
		if (password.trim().isEmpty() || passwordRepeat.trim().isEmpty()) {
			errorList.add(new ValidationError(UserModel.PASSWORD,
					ErrorMessages.PASSWORDS_SHOULDNT_BE_EMPTY_STRINGS));
		}

		// Check that both passwords are the same
		String passwordHash = getHashMDFive(password);
		String passwordHashRepeat = getHashMDFive(passwordRepeat);
		if (!passwordHash.equals(passwordHashRepeat)) {
			errorList.add(new ValidationError(UserModel.PASSWORD,
					ErrorMessages.PASSWORDS_DONT_MATCH));
		}
	}

}
