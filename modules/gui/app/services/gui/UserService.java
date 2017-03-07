package services.gui;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import controllers.gui.Authentication;
import daos.common.UserDao;
import daos.common.worker.WorkerDao;
import exceptions.gui.NotFoundException;
import general.common.MessagesStrings;
import general.common.RequestScope;
import models.common.User;
import models.common.User.Role;
import models.common.workers.JatosWorker;
import models.gui.NewUserModel;
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
	 * Retrieves all users from the database.
	 */
	public List<User> retrieveAllUsers() {
		return userDao.findAll();
	}

	/**
	 * Retrieves the user with the given email from the DB. Throws an Exception
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

	public User createAndPersistAdmin() {
		User adminUser = new User(ADMIN_EMAIL, ADMIN_NAME);
		createAndPersistUser(adminUser, ADMIN_PASSWORD, true);
		return adminUser;
	}

	/**
	 * Creates a user, sets password hash and persists him. Creates and persists
	 * an JatosWorker for the user.
	 */
	public void createAndPersistNewUser(NewUserModel newUserModel) {
		User user = new User(newUserModel.getEmail(), newUserModel.getName());
		String password = newUserModel.getPassword();
		boolean adminRole = newUserModel.getAdminRole();
		createAndPersistUser(user, password, adminRole);
	}

	/**
	 * Creates a user, sets password hash and persists him. Creates and persists
	 * an JatosWorker for the user.
	 */
	public void createAndPersistUser(User user, String password,
			boolean adminRole) {
		String passwordHash = HashUtils.getHashMDFive(password);
		user.setPasswordHash(passwordHash);
		JatosWorker worker = new JatosWorker(user);
		user.setWorker(worker);
		// Every user has the Role USER
		user.addRole(Role.USER);
		if (adminRole) {
			user.addRole(Role.ADMIN);
		}
		workerDao.create(worker);
		userDao.create(user);
	}

	/**
	 * Change password and persist user.
	 */
	public void updatePasswordHash(String emailOfUserToChange,
			String newPassword) throws NotFoundException {
		User user = retrieveUser(emailOfUserToChange);
		String newPasswordHash = HashUtils.getHashMDFive(newPassword);
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
