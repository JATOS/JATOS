package services.gui;

import auth.gui.AuthService;
import daos.common.ApiTokenDao;
import daos.common.StudyDao;
import daos.common.UserDao;
import exceptions.common.ForbiddenException;
import exceptions.common.NotFoundException;
import http.common.Http;
import models.common.Study;
import models.common.User;
import models.gui.NewUserProperties;
import org.assertj.core.api.Fail;
import org.junit.Test;
import play.test.Helpers;
import testutils.JatosTest;

import javax.inject.Inject;
import javax.persistence.EntityManager;

import static auth.gui.AuthAction.SIGNEDIN_USER;
import static org.assertj.core.api.Assertions.assertThat;

public class UserServiceIntegrationTest extends JatosTest {

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
        User user = userService.retrieveUser("admin");
        assertThat(user).isEqualTo(admin);
    }

    @Test
    public void checkRetrieveUnknownUser() {
        // Unknown user should throw NotFoundException
        try {
            userService.retrieveUser("user-not-exist");
            Fail.fail();
        } catch (NotFoundException e) {
            // A NotFoundException must be thrown
        }
    }

    @Test
    public void checkBindToUserAndPersist() {
        NewUserProperties userModel = new NewUserProperties();
        userModel.setUsername("foo@foo.org");
        userModel.setName("Foo Bar");
        userModel.setPassword("blaPw");

        userService.bindToUserAndPersist(userModel);

        // Check that the user is stored in the DB properly
        User u = userDao.findByUsernameWithStudies("foo@foo.org");
        assertThat(u.getUsername()).isEqualTo("foo@foo.org");
        assertThat(u.getName()).isEqualTo(userModel.getName());
        assertThat(u.getPasswordHash()).isNotEmpty();
        assertThat(u.getRoleList()).containsOnly(User.Role.USER);
        assertThat(u.getStudyList()).isEmpty();
        assertThat(u.getWorker()).isNotNull();
    }

    @Test
    public void checkCreateAndPersistUser() {
        createUser("foo@foo.org");

        // Check that the user is stored in the DB properly
        User u = userDao.findByUsername("foo@foo.org");
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
        User u = userDao.findByUsername("foo@foo.org");
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

        User userWithUpdatedPassword = userDao.findByUsername("foo@foo.org");

        jpaApi.withTransaction((EntityManager em) -> authService.authenticate(userWithUpdatedPassword, "newPassword"));
    }

    @Test
    public void checkToggleActive() {
        createUser("foo@foo.org");

        Http.Context.setCurrent(new Http.Context(Helpers.fakeRequest().build()));
        Http.Context.current().args().put(SIGNEDIN_USER, admin);

        userService.toggleActive("foo@foo.org", false);
        User u = userDao.findByUsername("foo@foo.org");
        assertThat(u.isActive()).isFalse();
    }

    /**
     * Test UserService.changeAdminRole(): add or remove the ADMIN role to a user
     */
    @Test
    public void checkChangeAdminRole() {
        User user = createUser("foo@foo.org");

        Http.Context.setCurrent(new Http.Context(Helpers.fakeRequest().build()));
        Http.Context.current().args().put(SIGNEDIN_USER, admin);

        // Add the ADMIN role to the user
        userService.changeAdminRole("foo@foo.org", true);
        {
            User u = userDao.findByUsername("foo@foo.org");
            // User has the role ADMIN now
            assertThat(u.getRoleList()).containsOnly(User.Role.USER, User.Role.ADMIN);
        }

        // Remove ADMIN role from user
        userService.changeAdminRole("foo@foo.org", false);
        {
            User u = userDao.findByUsername(user.getUsername());
            // User does not have the role ADMIN now
            assertThat(u.getRoleList()).containsOnly(User.Role.USER);
        }
    }

    /**
     * Test UserService.changeAdminRole(): user must exist
     */
    @Test
    public void checkChangeAdminRoleUserNotFound() {
        Http.Context.setCurrent(new Http.Context(Helpers.fakeRequest().build()));
        Http.Context.current().args().put(SIGNEDIN_USER, admin);

        try {
            userService.changeAdminRole("non-existing@user.org", false);
            Fail.fail();
        } catch (NotFoundException e) {
            // A NotFoundException must be thrown
        } catch (ForbiddenException e) {
            Fail.fail();
        }
    }

    /**
     * Test UserService.changeAdminRole(): the user 'admin' can't lose its ADMIN role
     */
    @Test
    public void checkChangeAdminRoleAdminAlwaysAdmin() {
        // Put a different user than 'admin' in Context as signed in
        User user = createUser("foo@foo.org");

        Http.Context.setCurrent(new Http.Context(Helpers.fakeRequest().build()));
        Http.Context.current().args().put(SIGNEDIN_USER, user);

        try {
            userService.changeAdminRole(UserService.ADMIN_USERNAME, false);
            Fail.fail();
        } catch (NotFoundException e) {
            Fail.fail();
        } catch (ForbiddenException e) {
            // A ForbiddenException must be thrown
        }
    }

    /**
     * Test UserService.changeAdminRole():the logged-in user can't toggle its own ADMIN rights
     */
    @Test
    public void checkChangeAdminRoleLoggedInCantLoose() {
        User user = createUser("foo@foo.org");

        Http.Context.setCurrent(new Http.Context(Helpers.fakeRequest().build()));
        Http.Context.current().args().put(SIGNEDIN_USER, admin);

        userService.changeAdminRole(user.getUsername(), true);

        // Now make a different user the logged-in user
        Http.Context.current().args().put(SIGNEDIN_USER, user);

        // Try to remove the ADMIN role from the user
        try {
            userService.changeAdminRole(user.getUsername(), false);
            Fail.fail();
        } catch (NotFoundException e) {
            Fail.fail();
        } catch (ForbiddenException e) {
            // A ForbiddenException must be thrown
        }
    }

    /**
     * Test UserService.removeUser()
     */
    @Test
    public void checkRemoveUser() {
        User user = createUser("foo@foo.org");
        Long studyId = importExampleStudy();

        // Make the new user the only member of the study
        jpaApi.withTransaction(em -> {
            Study study = studyDao.findById(studyId);
            studyService.changeUserMember(study, user, true);
            studyService.changeUserMember(study, admin, false);
        });

        Http.Context.setCurrent(new Http.Context(Helpers.fakeRequest().build()));
        Http.Context.current().args().put(SIGNEDIN_USER, admin);

        // Remove user
        jpaApi.withTransaction((EntityManager em) -> userService.removeUser("foo@foo.org"));

        // User is removed from the database
        assertThat(userDao.findByUsername("foo@foo.org")).isNull();
        // User's studies are removed (the user object is still the old before removal)
        user.getStudyList().forEach(s -> assertThat(studyDao.findById(s.getId())).isNull());
        // User's API tokens are removed
        assertThat(apiTokenDao.findByUser(user)).isEmpty();
    }

    /**
     * Test UserService.removeUser(): it's not allowed to remove the user 'admin'
     */
    @Test
    public void checkRemoveUserNotAdmin() {
        try {
            userService.removeUser(UserService.ADMIN_USERNAME);
            Fail.fail();
        } catch (NotFoundException e) {
            Fail.fail();
        } catch (ForbiddenException e) {
            // Must throw a ForbiddenException
        }
    }

}
