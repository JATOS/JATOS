package services.gui;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import daos.common.UserDao;
import general.common.MessagesStrings;
import models.common.User;
import models.common.User.Role;
import models.gui.ChangePasswordModel;
import models.gui.NewUserModel;
import play.data.validation.ValidationError;
import utils.common.HashUtils;

/**
 * Service class that handles user authentication and validation of new users
 * and validation of password changes
 * 
 * @author Kristian Lange (2017)
 */
@Singleton
public class AuthenticationService {

	private final UserService userService;
	private final UserDao userDao;

	@Inject
	AuthenticationService(UserService userService, UserDao userDao) {
		this.userService = userService;
		this.userDao = userDao;
	}

	public boolean authenticate(String email, String passwordHash) {
		return userDao.authenticate(email, passwordHash);
	}

	/**
	 * Validates a NewUserModel and returns a list with errors. Usually this is
	 * done in the model class, but since here the user DAO is needed I put it
	 * in this class. Still in the model class is some simple validation.
	 */
	public List<ValidationError> validateNewUser(NewUserModel newUserModel,
			String loggedInAdminEmail) {
		List<ValidationError> errorList = new ArrayList<>();

		// Check if user with this email already exists.
		String email = newUserModel.getEmail();
		User existingUser = userDao.findByEmail(email);
		if (existingUser != null) {
			errorList.add(new ValidationError(NewUserModel.EMAIL,
					MessagesStrings.THIS_EMAIL_IS_ALREADY_REGISTERED));
		}

		// Check both passwords equal
		String password = newUserModel.getPassword();
		String passwordRepeat = newUserModel.getPasswordRepeat();
		String passwordHash = HashUtils.getHashMDFive(password);
		String passwordHashRepeat = HashUtils.getHashMDFive(passwordRepeat);
		if (!passwordHash.equals(passwordHashRepeat)) {
			errorList.add(new ValidationError(NewUserModel.PASSWORD,
					MessagesStrings.PASSWORDS_DONT_MATCH));
		}

		// Authenticate: check admin password
		String adminPassword = newUserModel.getAdminPassword();
		String adminPasswordHash = HashUtils.getHashMDFive(adminPassword);
		if (!userDao.authenticate(loggedInAdminEmail, adminPasswordHash)) {
			errorList.add(new ValidationError(NewUserModel.ADMIN_PASSWORD,
					MessagesStrings.WRONG_PASSWORD));
		}

		return errorList;
	}

	/**
	 * Validates a ChangePasswordModel and returns a list with errors. It can
	 * either originate in the GUI in the user manager or in the user profile.
	 * Both cases have to be validated differently. Usually this is done in the
	 * model class, but since here the user DAO is needed I put it in this
	 * class.
	 */
	public List<ValidationError> validateChangePassword(
			String emailOfUserToChange,
			ChangePasswordModel changePasswordModel) {

		User loggedInUser = userService.retrieveLoggedInUser();

		// All form related errors go into errorList
		List<ValidationError> errorList = new ArrayList<>();

		// Check both passwords equal
		String newPassword = changePasswordModel.getNewPassword();
		String newPasswordRepeat = changePasswordModel.getNewPasswordRepeat();
		String newPasswordHash = HashUtils.getHashMDFive(newPassword);
		String newPasswordHashRepeat = HashUtils
				.getHashMDFive(newPasswordRepeat);
		if (!newPasswordHash.equals(newPasswordHashRepeat)) {
			errorList.add(new ValidationError(ChangePasswordModel.NEW_PASSWORD,
					MessagesStrings.PASSWORDS_DONT_MATCH));
		}

		// Authenticate: Either admin changes a password for some other user
		// or an user changes his own password
		if (loggedInUser.hasRole(Role.ADMIN)
				&& changePasswordModel.getAdminPassword() != null) {
			String adminEmail = loggedInUser.getEmail();
			String adminPassword = changePasswordModel.getAdminPassword();
			String passwordHash = HashUtils.getHashMDFive(adminPassword);
			if (!userDao.authenticate(adminEmail, passwordHash)) {
				errorList.add(
						new ValidationError(ChangePasswordModel.ADMIN_PASSWORD,
								MessagesStrings.WRONG_PASSWORD));
			}

		} else if (loggedInUser.getEmail().equals(emailOfUserToChange)
				&& changePasswordModel.getOldPassword() != null) {
			String oldPassword = changePasswordModel.getOldPassword();
			String oldPasswordHash = HashUtils.getHashMDFive(oldPassword);
			if (!userDao.authenticate(emailOfUserToChange, oldPasswordHash)) {
				errorList.add(
						new ValidationError(ChangePasswordModel.OLD_PASSWORD,
								MessagesStrings.WRONG_OLD_PASSWORD));
			}

		} else {
			// Should never happen since we checked role ADMIN already
			errorList
					.add(new ValidationError(ChangePasswordModel.ADMIN_PASSWORD,
							MessagesStrings.NOT_ALLOWED_TO_CHANGE_PASSWORDS));
		}

		return errorList;
	}

}
