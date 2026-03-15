package services.gui;

import auth.gui.AuthService;
import com.google.common.collect.Lists;
import daos.common.ApiTokenDao;
import daos.common.StudyDao;
import daos.common.UserDao;
import daos.common.worker.WorkerDao;
import exceptions.gui.ForbiddenException;
import exceptions.gui.NotFoundException;
import general.common.Common;
import general.common.MessagesStrings;
import models.common.Study;
import models.common.User;
import models.common.User.AuthMethod;
import models.common.User.Role;
import models.common.workers.JatosWorker;
import models.gui.NewUserProperties;
import models.gui.UserProperties;
import org.hibernate.Hibernate;
import play.db.jpa.JPAApi;
import utils.common.HashUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.Set;

/**
 * Service class mostly for Users controller. Handles everything around User.
 *
 * @author Kristian Lange
 */
@Singleton
public class UserService {

    /**
     * Default admin username; the admin user is created during the first initialization of JATOS; don't confuse admin
     * user with the Role ADMIN
     */
    public static final String ADMIN_USERNAME = "admin";
    /**
     * Default admin password; the admin user is created during the first initialization of JATOS; don't confuse admin
     * user with the Role ADMIN
     */
    public static final String ADMIN_PASSWORD = "admin";
    /**
     * Default admin name; the admin user is created during the first initialization of JATOS; don't confuse admin user
     * with the Role ADMIN
     */
    public static final String ADMIN_NAME = "Admin";

    private final StudyService studyService;
    private final AuthService authService;
    private final UserDao userDao;
    private final StudyDao studyDao;
    private final WorkerDao workerDao;
    private final ApiTokenDao apiTokenDao;
    private final JPAApi jpa;

    @Inject
    UserService(StudyService studyService, AuthService authService, UserDao userDao, StudyDao studyDao,
                WorkerDao workerDao, ApiTokenDao apiTokenDao, JPAApi jpa) {
        this.studyService = studyService;
        this.authService = authService;
        this.userDao = userDao;
        this.studyDao = studyDao;
        this.workerDao = workerDao;
        this.apiTokenDao = apiTokenDao;
        this.jpa = jpa;
    }

    /**
     * Retrieves the user with the given username from the DB. Throws an Exception if it doesn't exist.
     */
    public User retrieveUser(String normalizedUsername) throws NotFoundException {
        User user = userDao.findByUsername(normalizedUsername);
        if (user == null) {
            throw new NotFoundException("An user with username \"" + normalizedUsername + "\" doesn't exist.");
        }
        return user;
    }

    public User registerUser(NewUserProperties newUserProperties) throws ForbiddenException {
        User user = userDao.findByUsername(newUserProperties.getUsername());
        if (user != null) {
            throw new ForbiddenException("User exists already");
        }
        return bindToUserAndPersist(newUserProperties);
    }

    /**
     * Creates a user, sets password hash and persists them. Creates and persists a JatosWorker for the user.
     */
    public User bindToUserAndPersist(NewUserProperties newUserProperties) {
        //noinspection deprecation
        return jpa.withTransaction(() -> {
            User user = new User(newUserProperties.getUsername(), newUserProperties.getName(), newUserProperties.getEmail());
            String password = newUserProperties.getPassword();
            AuthMethod authMethod = newUserProperties.getAuthMethod();
            createAndPersistUser(user, password, false, authMethod);
            return user;
        });
    }

    public UserProperties bindToProperties(User user) {
        UserProperties model = new UserProperties();
        model.setId(user.getId());
        model.setUsername(user.getUsername());
        model.setName(user.getName());
        model.setEmail(user.getEmail());
        model.setActive(user.isActive());
        return model;
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

        // We have to flush and refresh the user to get the ID
        userDao.flush();
        userDao.refresh(user);
        Hibernate.initialize(user.getStudyList());
    }

    public void updateUser(User user, UserProperties props) {
        user.setName(props.getName());
        user.setEmail(props.getEmail());
        user.setActive(props.isActive());
        if (props.getPassword() != null) {
            String newPasswordHash = HashUtils.getHashMD5(props.getPassword());
            user.setPasswordHash(newPasswordHash);
        }
        userDao.update(user);
    }

    /**
     * Change password and persist user.
     */
    public void updatePassword(User user, String newPassword) {
        String newPasswordHash = HashUtils.getHashMD5(newPassword);
        user.setPasswordHash(newPasswordHash);
        userDao.update(user);
    }

    public void toggleActive(String normalizedUsername, boolean active) throws NotFoundException, ForbiddenException {
        User user = retrieveUser(normalizedUsername);
        toggleActive(user, active);
    }

    public void toggleActive(User user, boolean active) throws ForbiddenException {
        User signedinUser = authService.getSignedinUser();
        if (user.equals(signedinUser)) {
            throw new ForbiddenException("A user is not allowed to deactivate themselves.");
        }
        if (user.getUsername().equals(ADMIN_USERNAME)) {
            throw new ForbiddenException("It is not possible to deactivate user 'admin'.");
        }
        user.setActive(active);
        userDao.update(user);
    }

    /**
     * Adds or removes SUPERUSER role of the user with the given username and persists the change.
     */
    public Set<Role> changeSuperuserRole(String normalizedUsername, boolean superuser)
            throws NotFoundException, ForbiddenException {
        if (!Common.isUserRoleAllowSuperuser()) throw new ForbiddenException("Superuser role is not allowed");
        User user = retrieveUser(normalizedUsername);
        if (superuser) {
            user.addRole(Role.SUPERUSER);
        } else {
            user.removeRole(Role.SUPERUSER);
        }
        userDao.update(user);
        return user.getRoleList();
    }

    /**
     * Adds or removes an ADMIN role of the user with the given username and persists the change. If the parameter admin
     * is true, the ADMIN role will be set, and if it's false, it will be removed. Returns true if the user has the role in
     * the end - or false if he hasn't.
     */
    public Set<Role> changeAdminRole(String normalizedUsername, Boolean admin) throws NotFoundException, ForbiddenException {
        User user = retrieveUser(normalizedUsername);
        User signedinUser = authService.getSignedinUser();
        if (user.equals(signedinUser)) {
            throw new ForbiddenException(MessagesStrings.ADMIN_NOT_ALLOWED_TO_REMOVE_HIS_OWN_ADMIN_ROLE);
        }
        if (user.getUsername().equals(ADMIN_USERNAME)) {
            throw new ForbiddenException(MessagesStrings.NOT_ALLOWED_REMOVE_ADMINS_ADMIN_RIGHTS);
        }

        if (admin) {
            user.addRole(Role.ADMIN);
        } else {
            user.removeRole(Role.ADMIN);
        }
        return user.getRoleList();
    }

    public void setLastSignin(String normalizedUsername) {
        //noinspection deprecation
        jpa.withTransaction(() -> {
            User user = userDao.findByUsername(normalizedUsername);
            user.setLastLogin(new Timestamp(new Date().getTime()));
            userDao.update(user);
        });
    }

    public void removeUser(String normalizedUsername) throws NotFoundException, ForbiddenException, IOException {
        User user = retrieveUser(normalizedUsername);
        if (user.getUsername().equals(ADMIN_USERNAME)) {
            throw new ForbiddenException(MessagesStrings.NOT_ALLOWED_DELETE_ADMIN);
        }
        removeUser(user);
    }

    /**
     * Removes the User belonging to the given username from the database. It also removes all studies where this user
     * is the last member (which subsequently removes all components, results and the study assets too).
     */
    public void removeUser(User user) throws IOException {
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

        // Delete all user's API tokens
        apiTokenDao.findByUser(user).forEach(apiTokenDao::remove);

        // Don't necessary to remove the user's JatosWorker: he is removed
        // together with the default batch
        userDao.remove(user);
    }

    /**
     * Sets the time of the last activity of the given user
     */
    public void setLastSeen(User user) {
        user.setLastSeen(new Timestamp(new Date().getTime()));
        userDao.update(user);

    }

}
