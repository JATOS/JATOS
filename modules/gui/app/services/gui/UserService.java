package services.gui;

import com.google.common.collect.Lists;
import daos.common.StudyDao;
import daos.common.UserDao;
import daos.common.worker.WorkerDao;
import exceptions.gui.ForbiddenException;
import exceptions.gui.NotFoundException;
import general.common.MessagesStrings;
import models.common.Study;
import models.common.User;
import models.common.User.Role;
import models.common.workers.JatosWorker;
import models.gui.NewUserModel;
import utils.common.HashUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.List;

/**
 * Service class mostly for Users controller. Handles everything around User. For retrieval of users
 * from the database emails are turned into their lower case version.
 *
 * @author Kristian Lange
 */
@Singleton
public class UserService {

    /**
     * Default admin email; the admin user is created during first
     * initialization of JATOS; don't confuse admin user with the Role ADMIN
     */
    public static final String ADMIN_EMAIL = "admin";
    /**
     * Default admin password; the admin user is created during first
     * initialization of JATOS; don't confuse admin user with the Role ADMIN
     */
    public static final String ADMIN_PASSWORD = "admin";
    /**
     * Default admin name; the admin user is created during first initialization
     * of JATOS; don't confuse admin user with the Role ADMIN
     */
    public static final String ADMIN_NAME = "Admin";

    private final StudyService studyService;
    private final AuthenticationService authenticationService;
    private final UserDao userDao;
    private final StudyDao studyDao;
    private final WorkerDao workerDao;

    @Inject
    UserService(StudyService studyService,
            AuthenticationService authenticationService, UserDao userDao,
            StudyDao studyDao, WorkerDao workerDao) {
        this.studyService = studyService;
        this.authenticationService = authenticationService;
        this.userDao = userDao;
        this.studyDao = studyDao;
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
        email = email.toLowerCase();
        User user = userDao.findByEmail(email);
        if (user == null) {
            throw new NotFoundException(MessagesStrings.userNotExist(email));
        }
        return user;
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
    public void bindToUserAndPersist(NewUserModel newUserModel) {
        User user = new User(newUserModel.getEmail(), newUserModel.getName());
        String password = newUserModel.getPassword();
        boolean adminRole = newUserModel.getAdminRole();
        createAndPersistUser(user, password, adminRole);
    }

    /**
     * Creates a user, sets password hash and persists him. Creates and persists
     * an JatosWorker for the user.
     */
    public void createAndPersistUser(User user, String password, boolean adminRole) {
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
    public void updatePassword(String emailOfUserToChange, String newPassword)
            throws NotFoundException {
        emailOfUserToChange = emailOfUserToChange.toLowerCase();
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

    /**
     * Adds or removes the ADMIN role of the user with the given email and
     * persists the change. It the parameter admin is true the ADMIN role will
     * be set and if it's false it will be removed. Returns true if the user has
     * the role in the end - or false if he hasn't.
     */
    public boolean changeAdminRole(String email, boolean adminRole)
            throws NotFoundException, ForbiddenException {
        email = email.toLowerCase();
        User user = retrieveUser(email);
        User loggedInUser = authenticationService.getLoggedInUser();
        if (user.equals(loggedInUser)) {
            throw new ForbiddenException(
                    MessagesStrings.ADMIN_NOT_ALLOWED_TO_REMOVE_HIS_OWN_ADMIN_ROLE);
        }
        if (user.getEmail().equals(ADMIN_EMAIL)) {
            throw new ForbiddenException(MessagesStrings.NOT_ALLOWED_REMOVE_ADMINS_ADMIN_RIGHTS);
        }

        if (adminRole) {
            user.addRole(Role.ADMIN);
        } else {
            user.removeRole(Role.ADMIN);
        }
        userDao.update(user);
        return user.hasRole(Role.ADMIN);
    }

    /**
     * Removes the User belonging to the given email from the database. It also
     * removes all studies where this user is the last member (which
     * subsequently removes all components, results and the study assets too).
     */
    public void removeUser(String email)
            throws NotFoundException, ForbiddenException, IOException {
        email = email.toLowerCase();
        User user = retrieveUser(email);
        if (user.getEmail().equals(ADMIN_EMAIL)) {
            throw new ForbiddenException(MessagesStrings.NOT_ALLOWED_DELETE_ADMIN);
        }

        // Remove Study (including batches, components, study results, component
        // results, group results)
        for (Study study : Lists.newArrayList(user.getStudyList())) {
            // Only remove the study if no other users are member in this study
            if (study.getUserList().size() == 1) {
                studyService.removeStudyInclAssets(study);
            } else {
                study.removeUser(user);
                studyDao.update(study);
            }
        }
        // Don't necessary to remove the user's JatosWorker: he is removed
        // together with the default batch
        userDao.remove(user);
    }

}
