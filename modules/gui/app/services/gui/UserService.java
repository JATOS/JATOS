package services.gui;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import controllers.gui.Authentication;
import daos.common.UserDao;
import daos.common.worker.WorkerDao;
import exceptions.gui.NotFoundException;
import general.common.MessagesStrings;
import general.gui.RequestScope;
import models.common.User;
import models.common.workers.JatosWorker;
import play.data.validation.ValidationError;
import utils.common.HashUtils;

/**
 * Service class mostly for Users controller. Handles everything around User.
 * 
 * @author Kristian Lange
 */
@Singleton
public class UserService {

	public static final String ADMIN_EMAIL = "admin";
	public static final String ADMIN_PASSWORD = "admin";
	public static final String ADMIN_NAME = "Admin";

	private final UserDao userDao;
	private final WorkerDao workerDao;

	@Inject
	UserService(UserDao userDao, WorkerDao workerDao) {
		this.userDao = userDao;
		this.workerDao = workerDao;
	}

	/**
	 * Retrieves the user with the given email form the DB. Throws an Exception
	 * if it doesn't exist.
	 */
	public User retrieveUser(String email) throws NotFoundException {
		User user = userDao.findByEmail(email);
		if (user == null) {
			throw new NotFoundException(MessagesStrings.userNotExist(email));
		}
		return user;
	}

	/**
	 * Retrieves the user with the given email form the RequestScope. It was put
	 * into the RequestScope by the AuthenticationAction.
	 */
	public User retrieveLoggedInUser() {
		return (User) RequestScope.get(Authentication.LOGGED_IN_USER);
	}

	public List<ValidationError> validateNewUser(User newUser, String password,
			String passwordRepeat) {
		List<ValidationError> errorList = new ArrayList<>();

		// Check if user with this email already exists.
		if (userDao.findByEmail(newUser.getEmail()) != null) {
			errorList.add(new ValidationError(User.EMAIL,
					MessagesStrings.THIS_EMAIL_IS_ALREADY_REGISTERED));
		}

		checkPasswords(password, passwordRepeat, errorList);
		return errorList;
	}

	public List<ValidationError> validateChangePassword(User user,
			String password, String passwordRepeat, String oldPasswordHash) {
		List<ValidationError> errorList = new ArrayList<>();

		if (!userDao.authenticate(user.getEmail(), oldPasswordHash)) {
			errorList.add(new ValidationError(User.OLD_PASSWORD,
					MessagesStrings.WRONG_OLD_PASSWORD));
		}

		checkPasswords(password, passwordRepeat, errorList);
		return errorList;
	}

	private void checkPasswords(String password, String passwordRepeat,
			List<ValidationError> errorList) {

		// Check for non empty passwords
		if (password.trim().isEmpty() || passwordRepeat.trim().isEmpty()) {
			errorList.add(new ValidationError(User.PASSWORD,
					MessagesStrings.PASSWORDS_SHOULDNT_BE_EMPTY_STRINGS));
		}

		// Check that both passwords are the same
		String passwordHash = HashUtils.getHashMDFive(password);
		String passwordHashRepeat = HashUtils.getHashMDFive(passwordRepeat);
		if (!passwordHash.equals(passwordHashRepeat)) {
			errorList.add(new ValidationError(User.PASSWORD,
					MessagesStrings.PASSWORDS_DONT_MATCH));
		}
	}

	public User createAndPersistAdmin() {
		User adminUser = new User(ADMIN_EMAIL, ADMIN_NAME);
		createAndPersistUser(adminUser, ADMIN_PASSWORD);
		return adminUser;
	}

	/**
	 * Creates a user, sets password hash and persists him. Creates and persists
	 * an JatosWorker for the user.
	 */
	public void createAndPersistUser(User user, String password) {
		String passwordHash = HashUtils.getHashMDFive(password);
		user.setPasswordHash(passwordHash);
		JatosWorker worker = new JatosWorker(user);
		workerDao.create(worker);
		user.setWorker(worker);
		userDao.create(user);
		workerDao.update(worker);
	}

	/**
	 * Change password hash and persist user.
	 */
	public void updatePasswordHash(User user, String newPasswordHash) {
		user.setPasswordHash(newPasswordHash);
		userDao.update(user);
	}

	/**
	 * Changes name and persists user.
	 */
	public void updateName(User user, String name) {
		user.setName(name);
		userDao.update(user);
	}

}
