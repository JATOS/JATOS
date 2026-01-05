package services.gui;

import general.common.Http.Context;
import com.google.common.collect.Lists;
import daos.common.ApiTokenDao;
import daos.common.StudyDao;
import daos.common.UserDao;
import daos.common.worker.WorkerDao;
import exceptions.common.ForbiddenException;
import exceptions.common.NotFoundException;
import general.common.Common;
import general.common.MessagesStrings;
import models.common.Study;
import models.common.User;
import models.common.User.AuthMethod;
import models.common.User.Role;
import models.common.workers.JatosWorker;
import models.gui.NewUserModel;
import play.db.jpa.JPAApi;
import utils.common.HashUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.Timestamp;
import java.util.Date;

import static auth.gui.AuthAction.SIGNEDIN_USER;

/**
 * Service class mostly for Users controller. Handles everything around the User.
 *
 * @author Kristian Lange
 */
@Singleton
public class UserService {

    /**
     * Default admin username; the admin user is created during the first initialization of JATOS; don't confuse the
     * admin user with the Role ADMIN
     */
    public static final String ADMIN_USERNAME = "admin";

    private final StudyService studyService;
    private final UserDao userDao;
    private final StudyDao studyDao;
    private final WorkerDao workerDao;
    private final ApiTokenDao apiTokenDao;
    private final JPAApi jpa;

    @Inject
    UserService(StudyService studyService,
                UserDao userDao,
                StudyDao studyDao,
                WorkerDao workerDao,
                ApiTokenDao apiTokenDao,
                JPAApi jpa) {
        this.studyService = studyService;
        this.userDao = userDao;
        this.studyDao = studyDao;
        this.workerDao = workerDao;
        this.apiTokenDao = apiTokenDao;
        this.jpa = jpa;
    }

    /**
     * Retrieves the user with the given username from the DB. Throws an Exception if it doesn't exist.
     */
    public User retrieveUser(String normalizedUsername) {
        User user = userDao.findByUsername(normalizedUsername);
        if (user == null) {
            throw new NotFoundException("An user with username \"" + normalizedUsername + "\" doesn't exist.");
        }
        return user;
    }

    /**
     * Creates a user, sets password hash and persists them. Creates and persists a JatosWorker for the user.
     */
    public User bindToUserAndPersist(NewUserModel newUserModel) {
        return jpa.withTransaction((em) -> {
            User user = new User(newUserModel.getUsername(), newUserModel.getName(), newUserModel.getEmail());
            String password = newUserModel.getPassword();
            AuthMethod authMethod = newUserModel.getAuthMethod();
            createAndPersistUser(user, password, false, authMethod);
            return user;
        });
    }

    /**
     * Creates a user, sets password hash and persists them. Creates and persists a JatosWorker for the user.
     */
    public void createAndPersistUser(User user, String password, boolean adminRole, AuthMethod authMethod) {
        jpa.withTransaction(em -> {
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

            workerDao.persist(worker);
            userDao.persist(user);
        });
    }

    /**
     * Change password and persist user.
     */
    public void updatePassword(User user, String newPassword) {
        String newPasswordHash = HashUtils.getHashMD5(newPassword);
        user.setPasswordHash(newPasswordHash);
        userDao.merge(user);
    }

    public void toggleActive(String normalizedUsername, boolean active) {
        User user = retrieveUser(normalizedUsername);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        if (user.equals(signedinUser)) {
            throw new ForbiddenException("A user is not allowed to deactivate themselves.");
        }
        if (user.getUsername().equals(ADMIN_USERNAME)) {
            throw new ForbiddenException("It is not possible to deactivate user 'admin'.");
        }
        user.setActive(active);
        userDao.merge(user);
    }

    /**
     * Adds or removes the SUPERUSER role of the user with the given username and persists the change.
     */
    public boolean changeSuperuserRole(String normalizedUsername, boolean superuser) {
        if (!Common.isUserRoleAllowSuperuser()) throw new ForbiddenException("Superuser role is not allowed");
        User user = retrieveUser(normalizedUsername);
        if (superuser) user.addRole(Role.SUPERUSER);
        else user.removeRole(Role.SUPERUSER);
        userDao.merge(user);
        return user.isSuperuser();
    }

    /**
     * Adds or removes the ADMIN role of the user with the given username and persists the change. If the parameter
     * admin is true, the ADMIN role will be set, and if it's false, it will be removed. Returns true if the user has
     * the role in the end - or false if he hasn't.
     */
    public boolean changeAdminRole(String normalizedUsername, Boolean admin) {
        User user = retrieveUser(normalizedUsername);
        User signedinUser = Context.current().args().get(SIGNEDIN_USER);
        if (user.equals(signedinUser)) {
            throw new ForbiddenException(MessagesStrings.ADMIN_NOT_ALLOWED_TO_REMOVE_HIS_OWN_ADMIN_ROLE);
        }
        if (user.getUsername().equals(ADMIN_USERNAME)) {
            throw new ForbiddenException(MessagesStrings.NOT_ALLOWED_REMOVE_ADMINS_ADMIN_RIGHTS);
        }

        if (admin) user.addRole(Role.ADMIN);
        else user.removeRole(Role.ADMIN);
        return user.isAdmin();
    }

    public void setLastSignin(String normalizedUsername) {
        jpa.withTransaction(em -> {
            User user = userDao.findByUsername(normalizedUsername);
            user.setLastLogin(new Timestamp(new Date().getTime()));
            userDao.merge(user);
        });
    }

    /**
     * Removes the User belonging to the given username from the database. It also removes all studies where this user
     * is the last member (which subsequently removes all components, results and the study assets too).
     */
    public void removeUser(String normalizedUsername) {
        jpa.withTransaction(em -> {
            User user = retrieveUser(normalizedUsername);
            if (user.getUsername().equals(ADMIN_USERNAME)) {
                throw new ForbiddenException(MessagesStrings.NOT_ALLOWED_DELETE_ADMIN);
            }

            // Remove Study (including batches, components, study results, component results, group results)
            for (Study study : Lists.newArrayList(user.getStudyList())) {
                // Only remove the study if no other users are member in this study
                assert study != null;
                if (study.getUserList().size() <= 1) {
                    studyService.removeStudyInclAssets(study);
                } else {
                    study.removeUser(user);
                    studyDao.merge(study);
                }
            }

            // Delete all user's API tokens
            apiTokenDao.findByUser(user).forEach(apiTokenDao::remove);

            // Doesn't need to remove the user's JatosWorker: he is removed together with the default batch
            userDao.remove(user);
        });
    }

    /**
     * Sets the last activity time of the given user
     */
    public void setLastSeen(User user) {
        user.setLastSeen(new Timestamp(new Date().getTime()));
        userDao.merge(user);

    }

}
