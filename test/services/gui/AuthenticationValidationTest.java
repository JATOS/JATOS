package services.gui;

import com.google.inject.Guice;
import com.google.inject.Injector;
import general.TestHelper;
import general.common.Common;
import general.common.MessagesStrings;
import general.common.RequestScope;
import models.common.User;
import models.gui.ChangePasswordModel;
import models.gui.NewUserModel;
import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import play.ApplicationLoader;
import play.Environment;
import play.data.Form;
import play.data.FormFactory;
import play.db.jpa.JPAApi;
import play.i18n.Lang;
import play.inject.guice.GuiceApplicationBuilder;
import play.inject.guice.GuiceApplicationLoader;
import play.libs.typedmap.TypedMap;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

import static org.fest.assertions.Assertions.assertThat;

/**
 * Tests UserService
 *
 * @author Kristian Lange
 */
public class AuthenticationValidationTest {

    private static final Lang defaultLang = new Lang(java.util.Locale.getDefault());

    @Inject
    private TestHelper testHelper;

    @Inject
    private JPAApi jpaApi;

    @Inject
    private AuthenticationValidation authenticationValidation;

    @Inject
    private FormFactory formFactory;

    @Before
    public void startApp() throws Exception {
        GuiceApplicationBuilder builder = new GuiceApplicationLoader()
                .builder(new ApplicationLoader.Context(Environment.simple()));
        Injector injector = Guice.createInjector(builder.applicationModule());
        injector.injectMembers(this);
    }

    @After
    public void stopApp() throws Exception {
        // Clean up
        testHelper.removeAllStudies();
        testHelper.removeStudyAssetsRootDir();
        testHelper.removeAllStudyLogs();

        testHelper.removeUser("tester.test@test.com");
    }

    @Test
    public void simpleCheck() {
        int a = 1 + 1;
        assertThat(a).isEqualTo(2);
    }

    /**
     * Test AuthenticationService.validateNewUser(): successful validation
     */
    @Test
    public void checkValidateNewUser() {
        testHelper.mockContext();

        Form<NewUserModel> form = formFactory.form(NewUserModel.class).bind(defaultLang, TypedMap.empty(),
                createDummyUserData());

        jpaApi.withTransaction(() -> {
            Form<NewUserModel> validatedForm = authenticationValidation.validateNewUser(UserService.ADMIN_EMAIL, form);
            assertThat(validatedForm.errors()).isEmpty();
        });
    }

    /**
     * Test AuthenticationService.validateNewUser(): email is empty
     */
    @Test
    public void checkValidateNewUserEmailEmpty() {
        testHelper.mockContext();

        Map<String, String> data = createDummyUserData();
        data.put("email", "");
        Form<NewUserModel> form = formFactory.form(NewUserModel.class).bind(defaultLang, TypedMap.empty(), data);

        jpaApi.withTransaction(() -> {
            Form<NewUserModel> validatedForm = authenticationValidation.validateNewUser(UserService.ADMIN_EMAIL, form);
            assertThat(validatedForm.errors()).isNotEmpty();
            assertThat(validatedForm.errors().get(0).message()).isEqualTo(MessagesStrings.MISSING_EMAIL);
        });
    }

    /**
     * Test AuthenticationService.validateNewUser(): email too long
     */
    @Test
    public void checkValidateNewUserEmailTooLong() {
        testHelper.mockContext();

        Map<String, String> data = createDummyUserData();
        data.put("email",
                "123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890");
        Form<NewUserModel> form = formFactory.form(NewUserModel.class).bind(defaultLang, TypedMap.empty(), data);

        jpaApi.withTransaction(() -> {
            Form<NewUserModel> validatedForm = authenticationValidation.validateNewUser(UserService.ADMIN_EMAIL, form);
            assertThat(validatedForm.errors()).isNotEmpty();
            assertThat(validatedForm.errors().get(0).message()).isEqualTo(MessagesStrings.EMAIL_TOO_LONG);
        });
    }

    /**
     * Test AuthenticationService.validateNewUser(): HTML is not allowed
     */
    @Test
    public void checkValidateNewUserEmailNoHtml() {
        testHelper.mockContext();

        Map<String, String> data = createDummyUserData();
        data.put("email", "<html><p></p></html>");
        Form<NewUserModel> form = formFactory.form(NewUserModel.class).bind(defaultLang, TypedMap.empty(), data);

        jpaApi.withTransaction(() -> {
            Form<NewUserModel> validatedForm = authenticationValidation.validateNewUser(UserService.ADMIN_EMAIL, form);
            assertThat(validatedForm.errors()).isNotEmpty();
            assertThat(validatedForm.errors().get(0).message()).isEqualTo(MessagesStrings.NO_HTML_ALLOWED);
        });
    }

    /**
     * Test AuthenticationService.validateNewUser(): name is empty
     */
    @Test
    public void checkValidateNewUserNameEmpty() {
        testHelper.mockContext();

        Map<String, String> data = createDummyUserData();
        data.put("name", "");
        Form<NewUserModel> form = formFactory.form(NewUserModel.class).bind(defaultLang, TypedMap.empty(), data);

        jpaApi.withTransaction(() -> {
            Form<NewUserModel> validatedForm = authenticationValidation.validateNewUser(UserService.ADMIN_EMAIL, form);
            assertThat(validatedForm.errors()).isNotEmpty();
            assertThat(validatedForm.errors().get(0).message()).isEqualTo(MessagesStrings.MISSING_NAME);
        });
    }

    /**
     * Test AuthenticationService.validateNewUser(): name is too long
     */
    @Test
    public void checkValidateNewUserNameTooLong() {
        testHelper.mockContext();

        Map<String, String> data = createDummyUserData();
        data.put("name",
                "123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890");
        Form<NewUserModel> form = formFactory.form(NewUserModel.class).bind(defaultLang, TypedMap.empty(), data);

        jpaApi.withTransaction(() -> {
            Form<NewUserModel> validatedForm = authenticationValidation.validateNewUser(UserService.ADMIN_EMAIL, form);
            assertThat(validatedForm.errors()).isNotEmpty();
            assertThat(validatedForm.errors().get(0).message()).isEqualTo(MessagesStrings.NAME_TOO_LONG);
        });
    }

    /**
     * Test AuthenticationService.validateNewUser(): HTML is not allowed
     */
    @Test
    public void checkValidateNewUserNameNoHtml() {
        testHelper.mockContext();

        Map<String, String> data = createDummyUserData();
        data.put("name", "<html><p></p></html>");
        Form<NewUserModel> form = formFactory.form(NewUserModel.class).bind(defaultLang, TypedMap.empty(), data);

        jpaApi.withTransaction(() -> {
            Form<NewUserModel> validatedForm = authenticationValidation.validateNewUser(UserService.ADMIN_EMAIL, form);
            assertThat(validatedForm.errors()).isNotEmpty();
            assertThat(validatedForm.errors().get(0).message()).isEqualTo(MessagesStrings.NO_HTML_ALLOWED);
        });
    }

    /**
     * Test AuthenticationService.validateNewUser(): password is empty
     */
    @Test
    public void checkValidateNewUserPasswordEmpty() {
        testHelper.mockContext();

        Map<String, String> data = createDummyUserData();
        data.put("password", "");
        Form<NewUserModel> form = formFactory.form(NewUserModel.class).bind(defaultLang, TypedMap.empty(), data);

        jpaApi.withTransaction(() -> {
            Form<NewUserModel> validatedForm = authenticationValidation.validateNewUser(UserService.ADMIN_EMAIL, form);
            assertThat(validatedForm.errors()).isNotEmpty();
            assertThat(validatedForm.errors().get(0).message()).isEqualTo(
                    MessagesStrings.PASSWORDS_SHOULDNT_BE_EMPTY_STRINGS);
        });
    }

    /**
     * Test AuthenticationService.validateNewUser(): password is empty
     */
    @Test
    public void checkValidateNewUserRepeatedPasswordEmpty() {
        testHelper.mockContext();

        Map<String, String> data = createDummyUserData();
        data.put("passwordRepeat", "");
        Form<NewUserModel> form = formFactory.form(NewUserModel.class).bind(defaultLang, TypedMap.empty(), data);

        jpaApi.withTransaction(() -> {
            Form<NewUserModel> validatedForm = authenticationValidation.validateNewUser(UserService.ADMIN_EMAIL, form);
            assertThat(validatedForm.errors()).isNotEmpty();
            assertThat(validatedForm.errors().get(0).message()).isEqualTo(
                    MessagesStrings.PASSWORDS_SHOULDNT_BE_EMPTY_STRINGS);
        });
    }

    /**
     * Test AuthenticationService.validateNewUser(): password length min but okay
     */
    @Test
    public void checkValidateNewUserPasswordMinLength() {
        testHelper.mockContext();

        Map<String, String> data = createDummyUserData();
        String pw = StringUtils.leftPad("aA1$", Common.getUserPasswordMinLength(), 'a');
        data.put("password", pw);
        data.put("passwordRepeat", pw);
        Form<NewUserModel> form = formFactory.form(NewUserModel.class).bind(defaultLang, TypedMap.empty(), data);

        jpaApi.withTransaction(() -> {
            Form<NewUserModel> validatedForm = authenticationValidation.validateNewUser(UserService.ADMIN_EMAIL, form);
            assertThat(validatedForm.errors()).isEmpty();
        });
    }

    /**
     * Test AuthenticationService.validateNewUser(): password not long enough
     */
    @Test
    public void checkValidateNewUserPasswordNotLongEnough() {
        testHelper.mockContext();

        Map<String, String> data = createDummyUserData();
        String pw = StringUtils.leftPad("aA1$", Common.getUserPasswordMinLength() - 1, 'a');
        data.put("password", pw);
        data.put("passwordRepeat", pw);
        Form<NewUserModel> form = formFactory.form(NewUserModel.class).bind(defaultLang, TypedMap.empty(), data);

        jpaApi.withTransaction(() -> {
            Form<NewUserModel> validatedForm = authenticationValidation.validateNewUser(UserService.ADMIN_EMAIL, form);
            assertThat(validatedForm.errors()).isNotEmpty();
            assertThat(validatedForm.errors().get(0).message()).isEqualTo(MessagesStrings
                    .userPasswordMinLength(Common.getUserPasswordMinLength()));
        });
    }

    /**
     * Test AuthenticationService.validateNewUser(): password strong enough (assumes strength 3)
     */
    @Test
    public void checkValidateNewUserPasswordStrongEnough() {
        testHelper.mockContext();

        Map<String, String> data = createDummyUserData();
        data.put("password", "abcABC1$");
        data.put("passwordRepeat", "abcABC1$");
        Form<NewUserModel> form = formFactory.form(NewUserModel.class).bind(defaultLang, TypedMap.empty(), data);

        jpaApi.withTransaction(() -> {
            Form<NewUserModel> validatedForm = authenticationValidation.validateNewUser(UserService.ADMIN_EMAIL, form);
            assertThat(validatedForm.errors()).isEmpty();
        });
    }

    /**
     * Test AuthenticationService.validateNewUser(): password not strong enough (assumes strength 3)
     */
    @Test
    public void checkValidateNewUserPasswordNotStrongEnough() {
        testHelper.mockContext();

        // No upper case
        jpaApi.withTransaction(() -> {
            Map<String, String> data = createDummyUserData();
            data.put("password", "abcabc1$");
            data.put("passwordRepeat", "abcabc1$");
            Form<NewUserModel> form = formFactory.form(NewUserModel.class).bind(defaultLang, TypedMap.empty(), data);

            Form<NewUserModel> validatedForm = authenticationValidation.validateNewUser(UserService.ADMIN_EMAIL, form);
            assertThat(validatedForm.errors()).isNotEmpty();
            assertThat(validatedForm.errors().get(0).message()).isEqualTo(
                    Common.getUserPasswordStrengthRegex().getLeft());
        });

        // No lower case
        jpaApi.withTransaction(() -> {
            Map<String, String> data = createDummyUserData();
            data.put("password", "ABCABC1$");
            data.put("passwordRepeat", "ABCABC1$");
            Form<NewUserModel> form = formFactory.form(NewUserModel.class).bind(defaultLang, TypedMap.empty(), data);

            Form<NewUserModel> validatedForm = authenticationValidation.validateNewUser(UserService.ADMIN_EMAIL, form);
            assertThat(validatedForm.errors()).isNotEmpty();
            assertThat(validatedForm.errors().get(0).message()).isEqualTo(
                    Common.getUserPasswordStrengthRegex().getLeft());
        });

        // No number
        jpaApi.withTransaction(() -> {
            Map<String, String> data = createDummyUserData();
            data.put("password", "abcABC$$");
            data.put("passwordRepeat", "abcABC$$");
            Form<NewUserModel> form = formFactory.form(NewUserModel.class).bind(defaultLang, TypedMap.empty(), data);

            Form<NewUserModel> validatedForm = authenticationValidation.validateNewUser(UserService.ADMIN_EMAIL, form);
            assertThat(validatedForm.errors()).isNotEmpty();
            assertThat(validatedForm.errors().get(0).message()).isEqualTo(
                    Common.getUserPasswordStrengthRegex().getLeft());
        });

        // No special character
        jpaApi.withTransaction(() -> {
            Map<String, String> data = createDummyUserData();
            data.put("password", "abcABC11");
            data.put("passwordRepeat", "abcABC11");
            Form<NewUserModel> form = formFactory.form(NewUserModel.class).bind(defaultLang, TypedMap.empty(), data);

            Form<NewUserModel> validatedForm = authenticationValidation.validateNewUser(UserService.ADMIN_EMAIL, form);
            assertThat(validatedForm.errors()).isNotEmpty();
            assertThat(validatedForm.errors().get(0).message()).isEqualTo(
                    Common.getUserPasswordStrengthRegex().getLeft());
        });
    }

    /**
     * Test AuthenticationService.validateNewUser(): users passwords are
     * different
     */
    @Test
    public void checkValidateNewUserPasswordsNotEqual() {
        testHelper.mockContext();

        Map<String, String> data = createDummyUserData();
        data.put("passwordRepeat", "different");
        Form<NewUserModel> form = formFactory.form(NewUserModel.class).bind(defaultLang, TypedMap.empty(), data);

        jpaApi.withTransaction(() -> {
            Form<NewUserModel> validatedForm = authenticationValidation.validateNewUser(UserService.ADMIN_EMAIL, form);
            assertThat(validatedForm.errors()).isNotEmpty();
            assertThat(validatedForm.errors().get(0).message()).isEqualTo(MessagesStrings.PASSWORDS_DONT_MATCH);
        });
    }

    /**
     * Test AuthenticationService.validateNewUser(): user exists already
     */
    @Test
    public void checkValidateNewUserUserExistsAlready() {
        testHelper.mockContext();

        Map<String, String> data = createDummyUserData();
        data.put("email", "admin");
        Form<NewUserModel> form = formFactory.form(NewUserModel.class).bind(defaultLang, TypedMap.empty(), data);

        jpaApi.withTransaction(() -> {
            Form<NewUserModel> validatedForm = authenticationValidation.validateNewUser(UserService.ADMIN_EMAIL, form);
            assertThat(validatedForm.errors()).isNotEmpty();
            assertThat(validatedForm.errors().get(0).message()).isEqualTo(
                    MessagesStrings.THIS_EMAIL_IS_ALREADY_REGISTERED);
        });
    }

    /**
     * Test AuthenticationService.validateNewUser(): wrong admin password
     */
    @Test
    public void checkValidateNewUserWrongAdminPassword() {
        testHelper.mockContext();

        Map<String, String> data = createDummyUserData();
        data.put("adminPassword", "wrongPw");
        Form<NewUserModel> form = formFactory.form(NewUserModel.class).bind(defaultLang, TypedMap.empty(), data);

        jpaApi.withTransaction(() -> {
            Form<NewUserModel> validatedForm = authenticationValidation.validateNewUser(UserService.ADMIN_EMAIL, form);
            assertThat(validatedForm.errors()).isNotEmpty();
            assertThat(validatedForm.errors().get(0).message()).isEqualTo(MessagesStrings.WRONG_PASSWORD);
        });
    }

    /**
     * AuthenticationService.validateChangePassword(): change password of an
     * user via user manager and an admin user must be logged-in
     */
    @Test
    public void checkValidateChangePasswordViaAdmin() {
        prepareChangePasswordWithAdminTest();

        Map<String, String> data = new HashMap<>();
        data.put("adminPassword", UserService.ADMIN_PASSWORD);
        data.put("newPassword", "abc123A$");
        data.put("newPasswordRepeat", "abc123A$");
        Form<ChangePasswordModel> form = formFactory.form(ChangePasswordModel.class).bind(defaultLang, TypedMap.empty(),
                data);

        jpaApi.withTransaction(() -> {
            Form<ChangePasswordModel> validatedForm = authenticationValidation.validateChangePassword(
                    "tester.test@test.com", form);
            assertThat(validatedForm.errors()).isEmpty();
        });
    }

    /**
     * AuthenticationService.validateChangePassword(): passwords not empty
     */
    @Test
    public void checkValidateChangePasswordNotEmpty() {
        prepareChangePasswordWithAdminTest();

        Map<String, String> data = new HashMap<>();
        data.put("adminPassword", UserService.ADMIN_PASSWORD);
        data.put("newPassword", "");
        Form<ChangePasswordModel> form = formFactory.form(ChangePasswordModel.class).bind(defaultLang, TypedMap.empty(),
                data);

        jpaApi.withTransaction(() -> {
            Form<ChangePasswordModel> validatedForm = authenticationValidation.validateChangePassword(
                    "tester.test@test.com", form);
            assertThat(validatedForm.errors()).isNotEmpty();
            assertThat(validatedForm.errors().get(0).message()).isEqualTo(
                    MessagesStrings.PASSWORDS_SHOULDNT_BE_EMPTY_STRINGS);
        });
    }

    /**
     * AuthenticationService.validateChangePassword(): passwords not empty
     */
    @Test
    public void checkValidateChangePasswordRepeatNotEmpty() {
        prepareChangePasswordWithAdminTest();

        Map<String, String> data = new HashMap<>();
        data.put("adminPassword", UserService.ADMIN_PASSWORD);
        data.put("newPassword", "abc");
        data.put("newPasswordRepeat", "");
        Form<ChangePasswordModel> form = formFactory.form(ChangePasswordModel.class).bind(defaultLang, TypedMap.empty(),
                data);

        jpaApi.withTransaction(() -> {
            Form<ChangePasswordModel> validatedForm = authenticationValidation.validateChangePassword(
                    "tester.test@test.com", form);
            assertThat(validatedForm.errors()).isNotEmpty();
            assertThat(validatedForm.errors().get(0).message()).isEqualTo(
                    MessagesStrings.PASSWORDS_SHOULDNT_BE_EMPTY_STRINGS);
        });
    }

    /**
     * AuthenticationService.validateChangePassword(): different passwords
     */
    @Test
    public void checkValidateChangePasswordViaAdminNotMatch() {
        prepareChangePasswordWithAdminTest();

        Map<String, String> data = new HashMap<>();
        data.put("adminPassword", UserService.ADMIN_PASSWORD);
        data.put("newPassword", "abc123A$");
        data.put("newPasswordRepeat", "wer345B$");
        Form<ChangePasswordModel> form = formFactory.form(ChangePasswordModel.class).bind(defaultLang, TypedMap.empty(),
                data);

        jpaApi.withTransaction(() -> {
            Form<ChangePasswordModel> validatedForm = authenticationValidation.validateChangePassword(
                    "tester.test@test.com", form);
            assertThat(validatedForm.errors()).isNotEmpty();
            assertThat(validatedForm.errors().get(0).message()).isEqualTo(MessagesStrings.PASSWORDS_DONT_MATCH);
        });
    }

    /**
     * AuthenticationService.validateChangePassword(): exactly min length
     */
    @Test
    public void checkValidateChangePasswordMinLength() {
        prepareChangePasswordWithAdminTest();

        Map<String, String> data = new HashMap<>();
        data.put("adminPassword", UserService.ADMIN_PASSWORD);
        String pw = StringUtils.leftPad("aA1$", Common.getUserPasswordMinLength(), 'a');
        data.put("newPassword", pw);
        data.put("newPasswordRepeat", pw);
        Form<ChangePasswordModel> form = formFactory.form(ChangePasswordModel.class).bind(defaultLang, TypedMap.empty(),
                data);

        jpaApi.withTransaction(() -> {
            Form<ChangePasswordModel> validatedForm = authenticationValidation.validateChangePassword(
                    "tester.test@test.com", form);
            assertThat(validatedForm.errors()).isEmpty();
        });
    }

    /**
     * AuthenticationService.validateChangePassword(): not long enough
     */
    @Test
    public void checkValidateChangePasswordNotLongEnough() {
        prepareChangePasswordWithAdminTest();

        Map<String, String> data = new HashMap<>();
        data.put("adminPassword", UserService.ADMIN_PASSWORD);
        String pw = StringUtils.leftPad("aA1$", Common.getUserPasswordMinLength() - 1, 'a');
        data.put("newPassword", pw);
        data.put("newPasswordRepeat", pw);
        Form<ChangePasswordModel> form = formFactory.form(ChangePasswordModel.class).bind(defaultLang, TypedMap.empty(),
                data);

        jpaApi.withTransaction(() -> {
            Form<ChangePasswordModel> validatedForm = authenticationValidation.validateChangePassword(
                    "tester.test@test.com", form);
            assertThat(validatedForm.errors()).isNotEmpty();
            assertThat(validatedForm.errors().get(0).message()).isEqualTo(MessagesStrings
                    .userPasswordMinLength(Common.getUserPasswordMinLength()));
        });
    }

    /**
     * AuthenticationService.validateChangePassword(): not strong enough (assumes strength 3)
     */
    @Test
    public void checkValidateChangePasswordNotStrongEnough() {
        prepareChangePasswordWithAdminTest();

        // No upper case
        jpaApi.withTransaction(() -> {
            Map<String, String> data = new HashMap<>();
            data.put("adminPassword", UserService.ADMIN_PASSWORD);
            data.put("newPassword", "abcabc1$");
            data.put("newPasswordRepeat", "abcabc1$");
            Form<ChangePasswordModel> form = formFactory.form(ChangePasswordModel.class).bind(defaultLang,
                    TypedMap.empty(), data);
            Form<ChangePasswordModel> validatedForm = authenticationValidation.validateChangePassword(
                    "tester.test@test.com", form);
            assertThat(validatedForm.errors()).isNotEmpty();
            assertThat(validatedForm.errors().get(0).message()).isEqualTo(
                    Common.getUserPasswordStrengthRegex().getLeft());
        });

        // No lower case
        jpaApi.withTransaction(() -> {
            Map<String, String> data = new HashMap<>();
            data.put("adminPassword", UserService.ADMIN_PASSWORD);
            data.put("newPassword", "ABCABC1$");
            data.put("newPasswordRepeat", "ABCABC1$");
            Form<ChangePasswordModel> form = formFactory.form(ChangePasswordModel.class).bind(defaultLang,
                    TypedMap.empty(), data);
            Form<ChangePasswordModel> validatedForm = authenticationValidation.validateChangePassword(
                    "tester.test@test.com", form);
            assertThat(validatedForm.errors()).isNotEmpty();
            assertThat(validatedForm.errors().get(0).message()).isEqualTo(
                    Common.getUserPasswordStrengthRegex().getLeft());
        });

        // No number
        jpaApi.withTransaction(() -> {
            Map<String, String> data = new HashMap<>();
            data.put("adminPassword", UserService.ADMIN_PASSWORD);
            data.put("newPassword", "abcABC$$");
            data.put("newPasswordRepeat", "abcABC$$");
            Form<ChangePasswordModel> form = formFactory.form(ChangePasswordModel.class).bind(defaultLang,
                    TypedMap.empty(), data);
            Form<ChangePasswordModel> validatedForm = authenticationValidation.validateChangePassword(
                    "tester.test@test.com", form);
            assertThat(validatedForm.errors()).isNotEmpty();
            assertThat(validatedForm.errors().get(0).message()).isEqualTo(
                    Common.getUserPasswordStrengthRegex().getLeft());
        });

        // No special character
        jpaApi.withTransaction(() -> {
            Map<String, String> data = new HashMap<>();
            data.put("adminPassword", UserService.ADMIN_PASSWORD);
            data.put("newPassword", "abcABC11");
            data.put("newPasswordRepeat", "abcABC11");
            Form<ChangePasswordModel> form = formFactory.form(ChangePasswordModel.class).bind(defaultLang,
                    TypedMap.empty(), data);
            Form<ChangePasswordModel> validatedForm = authenticationValidation.validateChangePassword(
                    "tester.test@test.com", form);
            assertThat(validatedForm.errors()).isNotEmpty();
            assertThat(validatedForm.errors().get(0).message()).isEqualTo(
                    Common.getUserPasswordStrengthRegex().getLeft());
        });
    }

    /**
     * AuthenticationService.validateChangePassword(): user changes their own password
     */
    @Test
    public void checkValidateChangePasswordViaLoggedInUser() {
        testHelper.mockContext();

        User loggedInUser = testHelper.createAndPersistUser("tester.test@test.com", "Test Tester", "password");
        RequestScope.put(AuthenticationService.LOGGED_IN_USER, loggedInUser);

        Map<String, String> data = new HashMap<>();
        data.put("oldPassword", "password");
        data.put("newPassword", "abc123A$");
        data.put("newPasswordRepeat", "abc123A$");
        Form<ChangePasswordModel> form = formFactory.form(ChangePasswordModel.class).bind(defaultLang, TypedMap.empty(),
                data);

        jpaApi.withTransaction(() -> {
            Form<ChangePasswordModel> validatedForm = authenticationValidation.validateChangePassword(
                    "tester.test@test.com", form);
            assertThat(validatedForm.errors()).isEmpty();
        });
    }

    /**
     * AuthenticationService.validateChangePassword(): unauthorized user tries to change pw
     */
    @Test
    public void checkValidateChangePasswordViaUnauthorizedUser() {
        testHelper.mockContext();

        User loggedInUser = testHelper.createAndPersistUser("tester.test@test.com", "Test Tester", "password");
        RequestScope.put(AuthenticationService.LOGGED_IN_USER, loggedInUser);

        Map<String, String> data = new HashMap<>();
        data.put("oldPassword", "password");
        data.put("newPassword", "abc123A$");
        data.put("newPasswordRepeat", "abc123A$");
        Form<ChangePasswordModel> form = formFactory.form(ChangePasswordModel.class).bind(defaultLang, TypedMap.empty(),
                data);

        jpaApi.withTransaction(() -> {
            Form<ChangePasswordModel> validatedForm = authenticationValidation.validateChangePassword(
                    "different.test@test.com", form);
            assertThat(validatedForm.errors()).isNotEmpty();
            assertThat(validatedForm.errors().get(0).message()).isEqualTo(
                    MessagesStrings.NOT_ALLOWED_TO_CHANGE_PASSWORDS);
        });
    }

    private void prepareChangePasswordWithAdminTest() {
        testHelper.mockContext();

        testHelper.createAndPersistUser("tester.test@test.com", "Test Tester", "password");
        User admin = testHelper.getAdmin();
        RequestScope.put(AuthenticationService.LOGGED_IN_USER, admin);
    }

    private Map<String, String> createDummyUserData() {
        Map<String, String> data = new HashMap<>();
        data.put("email", "george@bla.com");
        data.put("name", "Georg Lange");
        data.put("password", "123äbcA$");
        data.put("passwordRepeat", "123äbcA$");
        data.put("adminRole", "true");
        data.put("adminPassword", "admin");
        return data;
    }

}
