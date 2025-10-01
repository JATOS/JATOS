package services.gui;

import auth.gui.AuthService;
import daos.common.ApiTokenDao;
import daos.common.StudyDao;
import daos.common.UserDao;
import exceptions.gui.ForbiddenException;
import exceptions.gui.NotFoundException;
import general.common.RequestScope;
import models.common.Study;
import models.common.User;
import models.gui.NewUserModel;
import org.fest.assertions.Fail;
import org.junit.Test;
import testutils.ContextMocker;
import testutils.JatosTest;

import javax.inject.Inject;
import java.io.IOException;

import static com.pivovarit.function.ThrowingConsumer.unchecked;
import static org.fest.assertions.Assertions.assertThat;

/**
 * @author Kristian Lange
 */
public class UserServiceTest extends JatosTest {

    @Inject
    private UserService userService;

    @Inject
    private AuthService authService;

    @Inject
    StudyService studyService;

    @Inject
    private UserDao userDao;

    @Inject
    private StudyDao studyDao;

    @Inject
    private ApiTokenDao apiTokenDao;

    @Test
    public void checkRetrieveUser() {
        jpaApi.withTransaction(unchecked((em) -> {
            User user = userService.retrieveUser("admin");
            assertThat(user).isEqualTo(admin);
        }));
    }

    @Test
    public void checkRetrieveUnknownUser() {
        // Unknown user should throw NotFoundException
        jpaApi.withTransaction(em -> {
            try {
                userService.retrieveUser("user-not-exist");
                Fail.fail();
            } catch (NotFoundException e) {
                // A NotFoundException must be thrown
            }
        });
    }

    @Test
    public void checkBindToUserAndPersist() {
        NewUserModel userModel = new NewUserModel();
        userModel.setUsername("foo@foo.org");
        userModel.setName("Foo Bar");
        userModel.setPassword("blaPw");
        userModel.setPasswordRepeat("blaPw");

        jpaApi.withTransaction(em -> {
            userService.bindToUserAndPersist(userModel);
        });

        // Check that the user is stored in the DB properly
        jpaApi.withTransaction(em -> {
            User u = userDao.findByUsername("foo@foo.org");
            assertThat(u.getUsername()).isEqualTo("foo@foo.org");
            assertThat(u.getName()).isEqualTo(userModel.getName());
            assertThat(u.getPasswordHash()).isNotEmpty();
            assertThat(u.getRoleList()).containsOnly(User.Role.USER);
            assertThat(u.getStudyList()).isEmpty();
            assertThat(u.getWorker()).isNotNull();
        });
    }

    @Test
    public void checkCreateAndPersistUser() {
        createUser("foo@foo.org");

        // Check that the user is stored in the DB properly
        User u = getUser("foo@foo.org");
        assertThat(u.getUsername()).isEqualTo("foo@foo.org");
        assertThat(u.getName()).isEqualTo("Foo Bar");
        assertThat(u.getPasswordHash()).isNotEmpty();
        assertThat(u.getRoleList()).containsOnly(User.Role.USER);
        assertThat(u.getWorker()).isNotNull();
    }

    /**
     * Test UserService.createAndPersistUser(): must be case-insensitive for emails
     */
    @Test
    public void checkCreateAndPersistUsernameCaseInsensitive() {
        createUser("FoO@FoO.OrG");

        // Retrieve user with lower-case email
        User u = getUser("foo@foo.org");
        assertThat(u.getUsername()).isEqualTo("foo@foo.org");
        assertThat(u.getName()).isEqualTo("Foo Bar");
        assertThat(u.getPasswordHash()).isNotEmpty();
        assertThat(u.getRoleList()).containsOnly(User.Role.USER);
        assertThat(u.getWorker()).isNotNull();
    }

    @Test
    public void checkUpdatePassword() {
        User user = createUser("foo@foo.org");

        jpaApi.withTransaction(em -> {
            userService.updatePassword(user, "newPassword");
        });

        User userWithUpdatedPassword = getUser("foo@foo.org");

        jpaApi.withTransaction(unchecked((em) -> authService.authenticate(userWithUpdatedPassword, "newPassword")));
    }

    @Test
    public void checkToggleActive() {
        createUser("foo@foo.org");
        // We need a Play context to be able to use RequestScope
        ContextMocker.mock();
        RequestScope.put(AuthService.SIGNEDIN_USER, admin);

        jpaApi.withTransaction(unchecked((em) -> userService.toggleActive("foo@foo.org", false)));
        User u = getUser("foo@foo.org");
        assertThat(u.isActive()).isFalse();
    }

    /**
     * Test UserService.changeAdminRole(): add or remove the ADMIN role to a user
     */
    @Test
    public void checkChangeAdminRole() {
        User user = createUser("foo@foo.org");
        ContextMocker.mock();
        RequestScope.put(AuthService.SIGNEDIN_USER, admin);

        // Add the ADMIN role to the user
        jpaApi.withTransaction(unchecked((em) -> userService.changeAdminRole("foo@foo.org", true)));
        {
            User u = getUser("foo@foo.org");
            // User has the role ADMIN now
            assertThat(u.getRoleList()).containsOnly(User.Role.USER, User.Role.ADMIN);
        }

        // Remove ADMIN role from user
        jpaApi.withTransaction(unchecked((em) -> userService.changeAdminRole("foo@foo.org", false)));
        {
            User u = getUser(user.getUsername());
            // User does not have the role ADMIN now
            assertThat(u.getRoleList()).containsOnly(User.Role.USER);
        }
    }

    /**
     * Test UserService.changeAdminRole(): user must exist
     */
    @Test
    public void checkChangeAdminRoleUserNotFound() {
        ContextMocker.mock();
        RequestScope.put(AuthService.SIGNEDIN_USER, admin);

        jpaApi.withTransaction(em -> {
            try {
                userService.changeAdminRole("non-existing@user.org", false);
                Fail.fail();
            } catch (NotFoundException e) {
                // A NotFoundException must be thrown
            } catch (ForbiddenException e) {
                Fail.fail();
            }
        });
    }

    /**
     * Test UserService.changeAdminRole(): the user 'admin' can't lose its ADMIN role
     */
    @Test
    public void checkChangeAdminRoleAdminAlwaysAdmin() {
        // Put a different user than 'admin' in RequestScope as signed in
        User user = createUser("foo@foo.org");
        ContextMocker.mock();
        RequestScope.put(AuthService.SIGNEDIN_USER, user);

        jpaApi.withTransaction(em -> {
            try {
                userService.changeAdminRole(UserService.ADMIN_USERNAME, false);
                Fail.fail();
            } catch (NotFoundException e) {
                Fail.fail();
            } catch (ForbiddenException e) {
                // A ForbiddenException must be thrown
            }
        });
    }

    /**
     * Test UserService.changeAdminRole():the logged-in user can't toggle its own ADMIN rights
     */
    @Test
    public void checkChangeAdminRoleLoggedInCantLoose() {
        User user = createUser("foo@foo.org");
        ContextMocker.mock();
        RequestScope.put(AuthService.SIGNEDIN_USER, admin);

        jpaApi.withTransaction(unchecked((em) -> userService.changeAdminRole(user.getUsername(), true)));

        // Now make a different user the logged-in user
        RequestScope.put(AuthService.SIGNEDIN_USER, user);

        // Try to remove the ADMIN role from the user
        jpaApi.withTransaction(em -> {
            try {
                userService.changeAdminRole(user.getUsername(), false);
                Fail.fail();
            } catch (NotFoundException e) {
                Fail.fail();
            } catch (ForbiddenException e) {
                // A ForbiddenException must be thrown
            }
            return null;
        });
    }

    /**
     * Test UserService.removeUser()
     */
    @Test
    public void checkRemoveUser() {
        User user = createUser("foo@foo.org");
        Long studyId = importExampleStudy();

        // Add the user as a member to the study
        jpaApi.withTransaction(unchecked((em) -> {
            Study study = studyDao.findById(studyId);
            studyService.changeUserMember(study, user, true);
        }));

        // Remove user
        jpaApi.withTransaction(unchecked((em) -> userService.removeUser("foo@foo.org")));

        jpaApi.withTransaction(em -> {
            // User is removed from the database
            assertThat(userDao.findByUsername("foo@foo.org")).isNull();
            // User's studies are removed (the user object is still the old before removal)
            user.getStudyList().forEach(s -> assertThat(studyDao.findById(s.getId())).isNull());
            // User's API tokens are removed
            assertThat(apiTokenDao.findByUser(user)).isEmpty();
        });
    }

    /**
     * Test UserService.removeUser(): it's not allowed to remove the user 'admin'
     */
    @Test
    public void checkRemoveUserNotAdmin() {
        jpaApi.withTransaction((em) -> {
            try {
                userService.removeUser(UserService.ADMIN_USERNAME);
                Fail.fail();
            } catch (NotFoundException | IOException e) {
                Fail.fail();
            } catch (ForbiddenException e) {
                // Must throw a ForbiddenException
            }
        });
    }

    private User getUser(String username) {
        return jpaApi.withTransaction((em) -> {
            return userDao.findByUsername(username);
        });
    }

}
