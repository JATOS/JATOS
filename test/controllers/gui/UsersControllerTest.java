package controllers.gui;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Guice;
import daos.common.UserDao;
import general.TestHelper;
import models.common.User;
import models.gui.ChangePasswordModel;
import models.gui.ChangeUserProfileModel;
import models.gui.NewUserModel;
import org.fest.assertions.Fail;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import play.Application;
import play.ApplicationLoader;
import play.Environment;
import play.db.jpa.JPAApi;
import play.inject.guice.GuiceApplicationBuilder;
import play.inject.guice.GuiceApplicationLoader;
import play.libs.Json;
import play.mvc.Call;
import play.mvc.Http;
import play.mvc.Http.RequestBuilder;
import play.mvc.Result;
import play.test.Helpers;
import services.gui.BreadcrumbsService;
import services.gui.UserService;

import javax.inject.Inject;
import javax.naming.NamingException;
import java.util.HashMap;
import java.util.Map;

import static org.fest.assertions.Assertions.assertThat;
import static play.mvc.Http.Status.*;
import static play.test.Helpers.contentAsString;
import static play.test.Helpers.route;

/**
 * Testing actions of controller.gui.Users
 *
 * @author Kristian Lange
 */
public class UsersControllerTest {

    @Inject
    private Application fakeApplication;

    @Inject
    private TestHelper testHelper;

    @Inject
    private JPAApi jpaApi;

    @Inject
    private UserDao userDao;

    @Before
    public void startApp() throws Exception {
        fakeApplication = Helpers.fakeApplication();

        GuiceApplicationBuilder builder = new GuiceApplicationLoader()
                .builder(new ApplicationLoader.Context(Environment.simple()));
        Guice.createInjector(builder.applicationModule()).injectMembers(this);

        Helpers.start(fakeApplication);
    }

    @After
    public void stopApp() throws Exception {
        // Clean up
        testHelper.removeAllStudies();

        Helpers.stop(fakeApplication);
        testHelper.removeStudyAssetsRootDir();
        testHelper.removeAllStudyLogs();
    }

    @Test
    public void callUserManager() {
        Map<String, String> formMap = new HashMap<>();
        Result result = call("GET", testHelper.getAdmin(), formMap, routes.Users.userManager());

        assertThat(result.status()).isEqualTo(OK);
        assertThat(result.charset().get()).isEqualToIgnoringCase("utf-8");
        assertThat(result.contentType().get()).isEqualTo("text/html");
        assertThat(contentAsString(result)).contains(BreadcrumbsService.USER_MANAGER);
    }

    @Test
    public void callAllUserData() {
        Map<String, String> formMap = new HashMap<>();
        Result result = call("GET", testHelper.getAdmin(), formMap, routes.Users.allUserData());

        assertThat(result.status()).isEqualTo(OK);
        JsonNode json = Json.parse(contentAsString(result)).get(0);
        assertThat(json.get("active").toString()).isNotEmpty();
        assertThat(json.get("name").toString()).isNotEmpty();
        assertThat(json.get("username").toString()).isNotEmpty();
        assertThat(json.get("roleList").toString()).isNotEmpty();
        assertThat(json.get("authMethod").toString()).isNotEmpty();
        assertThat(json.get("studyCount").toString()).isNotEmpty();
        assertThat(json.get("lastSeen").toString()).isNotEmpty();
        assertThat(json.get("lastLogin").toString()).isNotEmpty();
    }

    @Test
    public void callToggleActive() {
        testHelper.createAndPersistUser("bla", "Bla", "bla");

        Map<String, String> formMap = new HashMap<>();
        Result result = call("POST", testHelper.getAdmin(), formMap, routes.Users.toggleActive("bla", false));

        assertThat(result.status()).isEqualTo(OK);

        // Clean-up
        testHelper.removeUser("bla");
    }

    @Test
    public void callToggleAdmin() {
        testHelper.createAndPersistUser("bla", "Bla", "bla");

        Map<String, String> formMap = new HashMap<>();
        Result result = call("POST", testHelper.getAdmin(), formMap, routes.Users.toggleAdmin("bla", true));

        assertThat(result.status()).isEqualTo(OK);
        assertThat(result.contentType().get()).isEqualTo("application/json");

        // Clean-up
        testHelper.removeUser("bla");
    }

    @Test
    public void callProfile() {
        Map<String, String> formMap = new HashMap<>();
        Result result = call("GET", testHelper.getAdmin(), formMap, routes.Users.profile(UserService.ADMIN_USERNAME));

        assertThat(result.status()).isEqualTo(OK);
        assertThat(result.charset().get()).isEqualToIgnoringCase("UTF-8");
        assertThat(result.contentType().get()).isEqualTo("text/html");
        assertThat(contentAsString(result)).contains(UserService.ADMIN_USERNAME);
    }

    @Test
    public void callSingleUserData() {
        Map<String, String> formMap = new HashMap<>();
        Result result = call("GET", testHelper.getAdmin(), formMap,
                routes.Users.singleUserData(UserService.ADMIN_USERNAME));

        assertThat(result.status()).isEqualTo(OK);
        assertThat(result.contentType().get()).isEqualTo("application/json");
    }

    @Test
    public void callCreate() {
        Map<String, String> formMap = new HashMap<>();
        formMap.put(NewUserModel.ADMIN_PASSWORD, UserService.ADMIN_PASSWORD);
        formMap.put(NewUserModel.ADMIN_ROLE, "true");
        formMap.put(NewUserModel.USERNAME, "foo@foo.org");
        formMap.put(NewUserModel.NAME, "Foo Fool");
        formMap.put(NewUserModel.PASSWORD, "abcABC1!");
        formMap.put(NewUserModel.PASSWORD_REPEAT, "abcABC1!");

        Result result = call("POST", testHelper.getAdmin(), formMap, routes.Users.create());

        assertThat(result.status()).isEqualTo(OK);

        // Clean-up
        testHelper.removeUser("foo@foo.org");
    }

    @Test
    public void callCreateLdap() {
        Map<String, String> formMap = new HashMap<>();
        formMap.put(NewUserModel.ADMIN_PASSWORD, UserService.ADMIN_PASSWORD);
        formMap.put(NewUserModel.ADMIN_ROLE, "true");
        formMap.put(NewUserModel.USERNAME, "einstein");
        formMap.put(NewUserModel.NAME, "Albert Einstein");
        formMap.put(NewUserModel.AUTH_BY_LDAP, "true");

        Result result = call("POST", testHelper.getAdmin(), formMap, routes.Users.create());

        assertThat(result.status()).isEqualTo(OK);

        // Clean-up
        testHelper.removeUser("einstein");
    }

    /**
     * Google user can create without typing an password
     */
    @Test
    public void callCreateWithGoogleAdmin() {
        User userBla = testHelper.createAndPersistUserOAuthGoogle(TestHelper.BLA_EMAIL, "Bla Bla", "bla", true);

        Map<String, String> formMap = new HashMap<>();
        formMap.put(NewUserModel.USERNAME, "foo@foo.org");
        formMap.put(NewUserModel.NAME, "Foo Fool");
        formMap.put(NewUserModel.PASSWORD, "abcABC1!");
        formMap.put(NewUserModel.PASSWORD_REPEAT, "abcABC1!");

        Result result = call("POST", userBla, formMap, routes.Users.create());

        assertThat(result.status()).isEqualTo(OK);

        // Clean-up
        testHelper.removeUser(TestHelper.BLA_EMAIL);
        testHelper.removeUser("foo@foo.org");
    }

    @Test
    public void callCreateWithLdapAdmin() {
        testHelper.setupLdap("ldap://ldap.forumsys.com:389", "dc=example,dc=com");
        User user = testHelper.createAndPersistUserLdap("einstein", "Albert Einstein", "password", true);

        Map<String, String> formMap = new HashMap<>();
        formMap.put(NewUserModel.ADMIN_PASSWORD, "password");
        formMap.put(NewUserModel.USERNAME, TestHelper.BLA_EMAIL);
        formMap.put(NewUserModel.NAME, "Foo Fool");
        formMap.put(NewUserModel.PASSWORD, "abcABC1!");
        formMap.put(NewUserModel.PASSWORD_REPEAT, "abcABC1!");

        Result result = call("POST", user, formMap, routes.Users.create());

        assertThat(result.status()).isEqualTo(OK);

        // Clean-up
        testHelper.removeUser(TestHelper.BLA_EMAIL);
        testHelper.removeUser("einstein");
        testHelper.removeUser("foo@foo.org");
    }

    @Test
    public void callCreateWithLdapAdminWrongLdapUrl() {
        testHelper.setupLdap("ldap://ldap.wrong.com:389", "dc=example,dc=com");
        User user = testHelper.createAndPersistUserLdap("einstein", "Albert Einstein", "password", true);

        Map<String, String> formMap = new HashMap<>();
        formMap.put(NewUserModel.ADMIN_PASSWORD, "password");
        formMap.put(NewUserModel.USERNAME, TestHelper.BLA_EMAIL);
        formMap.put(NewUserModel.NAME, "Foo Fool");
        formMap.put(NewUserModel.PASSWORD, "abcABC1!");
        formMap.put(NewUserModel.PASSWORD_REPEAT, "abcABC1!");

        try {
            call("POST", user, formMap, routes.Users.create());
            Fail.fail();
        } catch (RuntimeException e) {
            assertThat(e.getCause()).isInstanceOf(NamingException.class);
        }

        // Clean-up
        testHelper.removeUser(TestHelper.BLA_EMAIL);
        testHelper.removeUser("einstein");
        testHelper.removeUser("foo@foo.org");
    }

    @Test
    public void callCreateWrongPassword() {
        Map<String, String> formMap = new HashMap<>();
        formMap.put(NewUserModel.ADMIN_PASSWORD, "wrongpassword");
        formMap.put(NewUserModel.ADMIN_ROLE, "true");
        formMap.put(NewUserModel.USERNAME, "foo@foo.org");
        formMap.put(NewUserModel.NAME, "Foo Fool");
        formMap.put(NewUserModel.PASSWORD, "abcABC1!");
        formMap.put(NewUserModel.PASSWORD_REPEAT, "abcABC1!");

        Result result = call("POST", testHelper.getAdmin(), formMap, routes.Users.create());

        assertThat(result.status()).isEqualTo(FORBIDDEN);

        // Clean-up
        testHelper.removeUser("foo@foo.org");
    }

    @Test
    public void callCreateWithLdapAdminButWrongPassword() {
        testHelper.setupLdap("ldap://ldap.forumsys.com:389", "dc=example,dc=com");
        User user = testHelper.createAndPersistUserLdap("einstein", "Albert Einstein", "password", true);

        Map<String, String> formMap = new HashMap<>();
        formMap.put(NewUserModel.ADMIN_PASSWORD, "wrongpassword");
        formMap.put(NewUserModel.USERNAME, TestHelper.BLA_EMAIL);
        formMap.put(NewUserModel.NAME, "Foo Fool");
        formMap.put(NewUserModel.PASSWORD, "abcABC1!");
        formMap.put(NewUserModel.PASSWORD_REPEAT, "abcABC1!");

        Result result = call("POST", user, formMap, routes.Users.create());

        assertThat(result.status()).isEqualTo(FORBIDDEN);

        // Clean-up
        testHelper.removeUser(TestHelper.BLA_EMAIL);
        testHelper.removeUser("einstein");
        testHelper.removeUser("foo@foo.org");
    }

    @Test
    public void callEditProfile() {
        User userBla = testHelper.createAndPersistUser(TestHelper.BLA_EMAIL, "Bla Bla", "bla");

        Map<String, String> formMap = new HashMap<>();
        formMap.put(ChangeUserProfileModel.NAME, "Different Name");

        Result result = call("POST", userBla, formMap, routes.Users.edit(TestHelper.BLA_EMAIL));

        assertThat(result.status()).isEqualTo(OK);

        // Clean-up
        testHelper.removeUser(TestHelper.BLA_EMAIL);
    }

    @Test
    public void callChangePasswordByUser() {
        User userBla = testHelper.createAndPersistUser(TestHelper.BLA_EMAIL, "Bla Bla", "bla");

        Map<String, String> formMap = new HashMap<>();
        formMap.put(ChangePasswordModel.USERNAME, TestHelper.BLA_EMAIL);
        formMap.put(ChangePasswordModel.NEW_PASSWORD, "DifferentPw1!");
        formMap.put(ChangePasswordModel.NEW_PASSWORD_REPEAT, "DifferentPw1!");
        formMap.put(ChangePasswordModel.OLD_PASSWORD, "bla");

        Result result = call("POST", userBla, formMap, routes.Users.changePasswordByUser());

        assertThat(result.status()).isEqualTo(OK);

        // Clean-up
        testHelper.removeUser(TestHelper.BLA_EMAIL);
    }

    @Test
    public void callChangePasswordByUserWrongUser() {
        User userBla = testHelper.createAndPersistUser(TestHelper.BLA_EMAIL, "Bla Bla", "bla");
        testHelper.createAndPersistUser("wronguser", "Wrong User", "wronguser");

        Map<String, String> formMap = new HashMap<>();
        formMap.put(ChangePasswordModel.USERNAME, "wronguser");
        formMap.put(ChangePasswordModel.NEW_PASSWORD, "DifferentPw1!");
        formMap.put(ChangePasswordModel.NEW_PASSWORD_REPEAT, "DifferentPw1!");
        formMap.put(ChangePasswordModel.OLD_PASSWORD, "bla");

        Result result = call("POST", userBla, formMap, routes.Users.changePasswordByUser());

        assertThat(result.status()).isEqualTo(FORBIDDEN);

        // Clean-up
        testHelper.removeUser(TestHelper.BLA_EMAIL);
        testHelper.removeUser("wronguser");
    }

    @Test
    public void callChangePasswordByUserWrongPassword() {
        User userBla = testHelper.createAndPersistUser(TestHelper.BLA_EMAIL, "Bla Bla", "bla");

        Map<String, String> formMap = new HashMap<>();
        formMap.put(ChangePasswordModel.USERNAME, TestHelper.BLA_EMAIL);
        formMap.put(ChangePasswordModel.NEW_PASSWORD, "Different Password");
        formMap.put(ChangePasswordModel.NEW_PASSWORD_REPEAT, "Different Password");
        formMap.put(ChangePasswordModel.OLD_PASSWORD, "wrong password");

        Result result = call("POST", userBla, formMap, routes.Users.changePasswordByUser());

        assertThat(result.status()).isEqualTo(FORBIDDEN);

        // Clean-up
        testHelper.removeUser(TestHelper.BLA_EMAIL);
    }

    /**
     * Action Users.changedPasswordByUser(): forbidden to change password of LDAP user
     */
    @Test
    public void callChangePasswordByUserLdap() {
        testHelper.setupLdap("ldap://ldap.forumsys.com:389", "dc=example,dc=com");
        User userBla = testHelper.createAndPersistUserLdap(TestHelper.BLA_EMAIL, "Bla Bla", "bla", false);

        Map<String, String> formMap = new HashMap<>();
        formMap.put(ChangePasswordModel.USERNAME, TestHelper.BLA_EMAIL);
        formMap.put(ChangePasswordModel.NEW_PASSWORD, "DifferentPw1!");
        formMap.put(ChangePasswordModel.NEW_PASSWORD_REPEAT, "DifferentPw1!");
        formMap.put(ChangePasswordModel.OLD_PASSWORD, "bla");

        Result result = call("POST", userBla, formMap, routes.Users.changePasswordByUser());

        assertThat(result.status()).isEqualTo(FORBIDDEN);

        // Clean-up
        testHelper.removeUser(TestHelper.BLA_EMAIL);
    }

    /**
     * Action Users.changePasswordByUser(): forbidden to change password of Oauth Google users
     */
    @Test
    public void callchangePasswordByUserGoogle() {
        User userBla = testHelper.createAndPersistUserOAuthGoogle(TestHelper.BLA_EMAIL, "Bla Bla", "bla", false);

        Map<String, String> formMap = new HashMap<>();
        formMap.put(ChangePasswordModel.USERNAME, TestHelper.BLA_EMAIL);
        formMap.put(ChangePasswordModel.NEW_PASSWORD, "DifferentPw1!");
        formMap.put(ChangePasswordModel.NEW_PASSWORD_REPEAT, "DifferentPw1!");
        formMap.put(ChangePasswordModel.OLD_PASSWORD, "bla");

        Result result = call("POST", userBla, formMap, routes.Users.changePasswordByUser());

        assertThat(result.status()).isEqualTo(FORBIDDEN);

        // Clean-up
        testHelper.removeUser(TestHelper.BLA_EMAIL);
    }

    @Test
    public void callChangePasswordByAdmin() {
        testHelper.createAndPersistUser(TestHelper.BLA_EMAIL, "Bla Bla", "bla");

        Map<String, String> formMap = new HashMap<>();
        formMap.put(ChangePasswordModel.USERNAME, TestHelper.BLA_EMAIL);
        formMap.put(ChangePasswordModel.ADMIN_PASSWORD, UserService.ADMIN_PASSWORD);
        formMap.put(ChangePasswordModel.NEW_PASSWORD, "DifferentPw1!");
        formMap.put(ChangePasswordModel.NEW_PASSWORD_REPEAT, "DifferentPw1!");

        Result result = call("POST", testHelper.getAdmin(), formMap, routes.Users.changePasswordByAdmin());

        assertThat(result.status()).isEqualTo(OK);

        // Clean-up
        testHelper.removeUser(TestHelper.BLA_EMAIL);
    }

    @Test
    public void callChangePasswordByAdminNotAdmin() {
        testHelper.createAndPersistUser(TestHelper.BLA_EMAIL, "Bla Bla", "bla");
        User nonAdmin = testHelper.createAndPersistUser("foobar", "Foo Bar", "foobar");

        Map<String, String> formMap = new HashMap<>();
        formMap.put(ChangePasswordModel.USERNAME, TestHelper.BLA_EMAIL);
        formMap.put(ChangePasswordModel.ADMIN_PASSWORD, "foobar");
        formMap.put(ChangePasswordModel.NEW_PASSWORD, "DifferentPw1!");
        formMap.put(ChangePasswordModel.NEW_PASSWORD_REPEAT, "DifferentPw1!");

        Result result = call("POST", nonAdmin, formMap, routes.Users.changePasswordByAdmin());

        assertThat(result.status()).isEqualTo(FORBIDDEN);

        // Clean-up
        testHelper.removeUser(TestHelper.BLA_EMAIL);
        testHelper.removeUser("foobar");
    }

    @Test
    public void callChangePasswordByAdminWrongPassword() {
        User userBla = testHelper.createAndPersistUser(TestHelper.BLA_EMAIL, "Bla Bla", "bla");

        Map<String, String> formMap = new HashMap<>();
        formMap.put(ChangePasswordModel.USERNAME, TestHelper.BLA_EMAIL);
        formMap.put(ChangePasswordModel.ADMIN_PASSWORD, "wrong password");
        formMap.put(ChangePasswordModel.NEW_PASSWORD, "Different Password");
        formMap.put(ChangePasswordModel.NEW_PASSWORD_REPEAT, "Different Password");

        Result result = call("POST", userBla, formMap, routes.Users.changePasswordByAdmin());

        assertThat(result.status()).isEqualTo(FORBIDDEN);

        // Clean-up
        testHelper.removeUser(TestHelper.BLA_EMAIL);
    }

    @Test
    public void callChangePasswordByAdminUserNotExists() {
        Map<String, String> formMap = new HashMap<>();
        formMap.put(ChangePasswordModel.USERNAME, TestHelper.BLA_EMAIL);
        formMap.put(ChangePasswordModel.ADMIN_PASSWORD, "wrong password");
        formMap.put(ChangePasswordModel.NEW_PASSWORD, "Different Password");
        formMap.put(ChangePasswordModel.NEW_PASSWORD_REPEAT, "Different Password");

        Result result = call("POST", testHelper.getAdmin(), formMap, routes.Users.changePasswordByAdmin());

        assertThat(result.status()).isEqualTo(BAD_REQUEST);

        // Clean-up
        testHelper.removeUser(TestHelper.BLA_EMAIL);
    }

    /**
     * Action Users.changePasswordByAdmin(): forbidden to change password of LDAP user
     */
    @Test
    public void callChangePasswordByAdminLdap() {
        testHelper.setupLdap("ldap://ldap.forumsys.com:389", "dc=example,dc=com");
        User userBla = testHelper.createAndPersistUserLdap(TestHelper.BLA_EMAIL, "Bla Bla", "bla", false);

        Map<String, String> formMap = new HashMap<>();
        formMap.put(ChangePasswordModel.USERNAME, TestHelper.BLA_EMAIL);
        formMap.put(ChangePasswordModel.ADMIN_PASSWORD, "wrong password");
        formMap.put(ChangePasswordModel.NEW_PASSWORD, "Different Password");
        formMap.put(ChangePasswordModel.NEW_PASSWORD_REPEAT, "Different Password");

        Result result = call("POST", userBla, formMap, routes.Users.changePasswordByAdmin());

        assertThat(result.status()).isEqualTo(FORBIDDEN);

        // Clean-up
        testHelper.removeUser(TestHelper.BLA_EMAIL);
    }

    /**
     * Action Users.changePasswordByAdmin(): forbidden to change password of Oauth Google users
     */
    @Test
    public void callChangePasswordByAdminGoogle() {
        User userBla = testHelper.createAndPersistUserOAuthGoogle(TestHelper.BLA_EMAIL, "Bla Bla", "bla", false);

        Map<String, String> formMap = new HashMap<>();
        formMap.put(ChangePasswordModel.USERNAME, TestHelper.BLA_EMAIL);
        formMap.put(ChangePasswordModel.NEW_PASSWORD, "DifferentPw1!");
        formMap.put(ChangePasswordModel.NEW_PASSWORD_REPEAT, "DifferentPw1!");
        formMap.put(ChangePasswordModel.OLD_PASSWORD, "bla");

        Result result = call("POST", userBla, formMap, routes.Users.changePasswordByAdmin());

        assertThat(result.status()).isEqualTo(FORBIDDEN);

        // Clean-up
        testHelper.removeUser(TestHelper.BLA_EMAIL);
    }

    /**
     * Action Users.remove(): this action can be used in two ways: 1) by an
     * admin (with ADMIN role) to delete another user, or 2) by the user himself
     * to delete its own user account. This tests the first way. Here the
     * password in the form has to be admin's password.
     */
    @Test
    public void callRemoveByAdmin() {
        testHelper.createAndPersistUser(TestHelper.BLA_EMAIL, "Bla Bla", "bla");

        Map<String, String> formMap = new HashMap<>();
        formMap.put("password", UserService.ADMIN_PASSWORD);

        Result result = call("POST", testHelper.getAdmin(), formMap, routes.Users.remove(TestHelper.BLA_EMAIL));

        assertThat(result.status()).isEqualTo(OK);
        jpaApi.withTransaction(() -> assertThat(userDao.findByUsername(TestHelper.BLA_EMAIL)).isNull());

        // Clean-up
        testHelper.removeUser(TestHelper.BLA_EMAIL);
    }

    /**
     * Action Users.remove(): this action can be used in two ways: 1) by an
     * admin (with ADMIN role) to delete another user, or 2) by the user himself
     * to delete its own user account. This tests the second way. Here the
     * password in the form has to be the user's own password.
     */
    @Test
    public void callRemoveByUserSelf() {
        User userBla = testHelper.createAndPersistUser(TestHelper.BLA_EMAIL, "Bla Bla", "bla");

        Map<String, String> formMap = new HashMap<>();
        formMap.put("password", "bla");

        Result result = call("POST", userBla, formMap, routes.Users.remove(TestHelper.BLA_EMAIL));

        assertThat(result.status()).isEqualTo(OK);

        jpaApi.withTransaction(() -> assertThat(userDao.findByUsername(TestHelper.BLA_EMAIL)).isNull());

        // Clean-up
        testHelper.removeUser(TestHelper.BLA_EMAIL);
    }

    /**
     * Action Users.remove(): this action can be used in two ways: 1) by an
     * admin (with ADMIN role) to delete another user, or 2) by the user himself
     * to delete its own user account. This tests the second way.
     * <p>
     * In case the user has no ADMIN role but wants to delete another user a 403
     * is returned.
     */
    @Test
    public void callRemoveButNoAdminRole() {
        User userBla = testHelper.createAndPersistUser(TestHelper.BLA_EMAIL, "Bla Bla", "bla");

        Map<String, String> formMap = new HashMap<>();
        formMap.put("password", "bla");

        Result result = call("POST", userBla, formMap, routes.Users.remove("foo@foo.org"));

        assertThat(result.status()).isEqualTo(FORBIDDEN);

        // Clean-up
        testHelper.removeUser(TestHelper.BLA_EMAIL);
    }

    /**
     * Action Users.remove(): in case the wrong password is given an 403 is
     * returned
     */
    @Test
    public void callRemoveWrongPassword() {
        User userBla = testHelper.createAndPersistUser(TestHelper.BLA_EMAIL, "Bla Bla", "bla");

        Map<String, String> formMap = new HashMap<>();
        formMap.put("password", "wrong password");

        Result result = call("POST", userBla, formMap, routes.Users.remove(TestHelper.BLA_EMAIL));

        assertThat(result.status()).isEqualTo(FORBIDDEN);

        // Clean-up
        testHelper.removeUser(TestHelper.BLA_EMAIL);
    }

    private Result call(String method, User user, Map<String, String> formMap, Call call) {
        Http.Session session = testHelper.mockSessionCookieandCache(user);
        RequestBuilder request = new RequestBuilder()
                .method(method)
                .session(session)
                .remoteAddress(TestHelper.WWW_EXAMPLE_COM)
                .bodyForm(formMap)
                .uri(call.url());
        return route(fakeApplication, request);
    }

}
