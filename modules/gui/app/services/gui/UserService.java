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
import models.common.User.AuthMethod;
import models.common.User.Role;
import models.common.workers.JatosWorker;
import models.gui.NewUserModel;
import play.Logger;
import play.db.jpa.JPAApi;
import utils.common.HashUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Date;

/**
 * Service class mostly for Users controller. Handles everything around User.
 *
 * @author Kristian Lange
 */
@SuppressWarnings("deprecation")
@Singleton
public class UserService {

    private static final Logger.ALogger LOGGER = Logger.of(UserService.class);

    /**
     * Default admin username; the admin user is created during first initialization of JATOS; don't confuse admin user
     * with the Role ADMIN
     */
    public static final String ADMIN_USERNAME = "admin";
    /**
     * Default admin password; the admin user is created during first initialization of JATOS; don't confuse admin user
     * with the Role ADMIN
     */
    public static final String ADMIN_PASSWORD = "admin";
    /**
     * Default admin name; the admin user is created during first initialization of JATOS; don't confuse admin user with
     * the Role ADMIN
     */
    public static final String ADMIN_NAME = "Admin";

    private final StudyService studyService;
    private final AuthenticationService authenticationService;
    private final UserDao userDao;
    private final StudyDao studyDao;
    private final WorkerDao workerDao;
    private final JPAApi jpa;

    @Inject
    UserService(StudyService studyService, AuthenticationService authenticationService, UserDao userDao,
            StudyDao studyDao, WorkerDao workerDao, JPAApi jpa) {
        this.studyService = studyService;
        this.authenticationService = authenticationService;
        this.userDao = userDao;
        this.studyDao = studyDao;
        this.workerDao = workerDao;
        this.jpa = jpa;
    }

    /**
     * Retrieves the user with the given username from the DB. Throws an Exception if it doesn't exist.
     */
    public User retrieveUser(String normalizedUsername) throws NotFoundException {
        User user = userDao.findByUsername(normalizedUsername);
        if (user == null) {
            throw new NotFoundException(MessagesStrings.userNotExist(normalizedUsername));
        }
        return user;
    }

    /**
     * Check for user admin: In case the application is started the first time we need an initial user: admin. If admin
     * can't be found, create one.
     */
    public void createAdminIfNotExists() {
        jpa.withTransaction(() -> {
            User admin = userDao.findByUsername(UserService.ADMIN_USERNAME);
            if (admin == null) {
                admin = new User(ADMIN_USERNAME, ADMIN_NAME);
                createAndPersistUser(admin, ADMIN_PASSWORD, true, AuthMethod.DB);
                LOGGER.info("Created Admin user");
            }

            // Some older JATOS versions miss the ADMIN role
            if (!admin.isAdmin()) {
                admin.addRole(Role.ADMIN);
                userDao.update(admin);
            }
        });
    }

    /**
     * Creates a user, sets password hash and persists him. Creates and persists an JatosWorker for the user.
     */
    public void bindToUserAndPersist(NewUserModel newUserModel) {
        User user = new User(newUserModel.getUsername(), newUserModel.getName());
        String password = newUserModel.getPassword();
        boolean adminRole = newUserModel.getAdminRole();
        AuthMethod authMethod = newUserModel.getAuthByLdap() ? AuthMethod.LDAP :
                newUserModel.getAuthByOAuthGoogle() ? AuthMethod.OAUTH_GOOGLE : AuthMethod.DB;
        createAndPersistUser(user, password, adminRole, authMethod);
    }

    /**
     * Creates a user, sets password hash and persists them. Creates and persists an JatosWorker for the user.
     */
    public void createAndPersistUser(User user, String password, boolean adminRole, AuthMethod authMethod) {
        user.setAuthMethod(authMethod);

        // Set password only if DB authentication
        if (authMethod == AuthMethod.DB) {
            String passwordHash = HashUtils.getHashMD5(password);
            user.setPasswordHash(passwordHash);
        }

        // Every user has a JatosWorker
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
    public void updatePassword(User user, String newPassword) {
        String newPasswordHash = HashUtils.getHashMD5(newPassword);
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

    public void toggleActive(String normalizedUsername, boolean active) throws NotFoundException, ForbiddenException {
        User user = retrieveUser(normalizedUsername);
        User loggedInUser = authenticationService.getLoggedInUser();
        if (user.equals(loggedInUser)) {
            throw new ForbiddenException("A user is not allowed to deactivate themselves.");
        }
        if (user.getUsername().equals(ADMIN_USERNAME)) {
            throw new ForbiddenException("It is not possible to deactivate user 'admin'.");
        }
        user.setActive(active);
        userDao.update(user);
    }

    /**
     * Adds or removes ADMIN role of the user with the given username and persists the change. It the parameter admin
     * is true the ADMIN role will be set and if it's false it will be removed. Returns true if the user has the role in
     * the end - or false if he hasn't.
     */
    public boolean changeAdminRole(String normalizedUsername, boolean adminRole)
            throws NotFoundException, ForbiddenException {
        User user = retrieveUser(normalizedUsername);
        User loggedInUser = authenticationService.getLoggedInUser();
        if (user.equals(loggedInUser)) {
            throw new ForbiddenException(MessagesStrings.ADMIN_NOT_ALLOWED_TO_REMOVE_HIS_OWN_ADMIN_ROLE);
        }
        if (user.getUsername().equals(ADMIN_USERNAME)) {
            throw new ForbiddenException(MessagesStrings.NOT_ALLOWED_REMOVE_ADMINS_ADMIN_RIGHTS);
        }

        if (adminRole) {
            user.addRole(Role.ADMIN);
        } else {
            user.removeRole(Role.ADMIN);
        }
        userDao.update(user);
        return user.isAdmin();
    }

    public void setLastLogin(String normalizedUsername) {
        User user = userDao.findByUsername(normalizedUsername);
        user.setLastLogin(new Timestamp(new Date().getTime()));
        userDao.update(user);
    }

    /**
     * Removes the User belonging to the given username from the database. It also removes all studies where this user
     * is
     * the last member (which subsequently removes all components, results and the study assets too).
     */
    public void removeUser(String normalizedUsername) throws NotFoundException, ForbiddenException, IOException {
        User user = retrieveUser(normalizedUsername);
        if (user.getUsername().equals(ADMIN_USERNAME)) {
            throw new ForbiddenException(MessagesStrings.NOT_ALLOWED_DELETE_ADMIN);
        }

        // Remove Study (including batches, components, study results, component
        // results, group results)
        for (Study study : Lists.newArrayList(user.getStudyList())) {
            // Only remove the study if no other users are member in this study
            if (study.getUserList().size() == 1) {
                studyService.removeStudyInclAssets(study, user);
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
