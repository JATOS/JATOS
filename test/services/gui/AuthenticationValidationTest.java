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
import org.apache.commons.lang3.tuple.Pair;
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
import javax.naming.NamingException;
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
        checkValidateNewUserSuccess();
    }

    @Test
    public void checkValidateNewUserLdap() {
        checkValidateNewUserSuccess(
                Pair.of("authByLdap", "true"),
                Pair.of("password", null),
                Pair.of("passwordRepeat", null));
    }

    /**
     * Test AuthenticationService.validateNewUser(): successful username validation
     */
    @Test
    public void checkValidateNewUserSuccess() {
        checkValidateNewUserSuccess(Pair.of("username", "foo"));
        checkValidateNewUserSuccess(Pair.of("username", "FOO"));
        checkValidateNewUserSuccess(Pair.of("username", "föóß"));
        checkValidateNewUserSuccess(Pair.of("username", "123abc"));
        checkValidateNewUserSuccess(Pair.of("username", "foo "));
        checkValidateNewUserSuccess(Pair.of("username", " foo"));
        checkValidateNewUserSuccess(Pair.of("username", "芷若"));
        checkValidateNewUserSuccess(Pair.of("username", "かいと"));
        checkValidateNewUserSuccess(Pair.of("username", "foo.bar@gmail.com"));
        checkValidateNewUserSuccess(Pair.of("username", "foo+bar&pop=sol'big~don_tee-sam"));
    }

    /**
     * Test AuthenticationService.validateNewUser(): failed username validation
     */
    @Test
    public void checkValidateNewUserUsernameFail() {
        checkValidateNewUserFail(MessagesStrings.MISSING_USERNAME, Pair.of("username", ""));
        checkValidateNewUserFail(MessagesStrings.USERNAME_TOO_LONG, Pair.of("username",
                "123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890"));
        checkValidateNewUserFail(MessagesStrings.USERNAME_INVALID, Pair.of("username", "foo bar"));
        checkValidateNewUserFail(MessagesStrings.USERNAME_INVALID, Pair.of("username", "foo\""));
        checkValidateNewUserFail(MessagesStrings.USERNAME_INVALID, Pair.of("username", "foo/"));
        checkValidateNewUserFail(MessagesStrings.USERNAME_INVALID, Pair.of("username", "foo%"));
        checkValidateNewUserFail(MessagesStrings.USERNAME_INVALID, Pair.of("username", "foo()"));
        checkValidateNewUserFail(MessagesStrings.USERNAME_INVALID, Pair.of("username", "foo?"));
        checkValidateNewUserFail(MessagesStrings.USERNAME_INVALID, Pair.of("username", "foo*"));
        checkValidateNewUserFail(MessagesStrings.USERNAME_INVALID, Pair.of("username", "foo#"));
        checkValidateNewUserFail(MessagesStrings.USERNAME_INVALID, Pair.of("username", "foo:"));
        checkValidateNewUserFail(MessagesStrings.USERNAME_INVALID, Pair.of("username", "foo;"));
        checkValidateNewUserFail(MessagesStrings.USERNAME_INVALID, Pair.of("username", "foo,"));
        checkValidateNewUserFail(MessagesStrings.USERNAME_INVALID, Pair.of("username", "foo<"));
        checkValidateNewUserFail(MessagesStrings.USERNAME_INVALID, Pair.of("username", "foo>"));
        checkValidateNewUserFail(MessagesStrings.USERNAME_INVALID, Pair.of("username", "foo>|"));
        checkValidateNewUserFail(MessagesStrings.USERNAME_INVALID,
                Pair.of("username", "\uD83D\uDE01\uD83D\uDE09\uD83D\uDE0D"));
    }

    /**
     * Test AuthenticationService.validateNewUser(): name validation fail
     */
    @Test
    public void checkValidateNewUserNameFail() {
        checkValidateNewUserFail(MessagesStrings.MISSING_NAME, Pair.of("name", ""));
        checkValidateNewUserFail(MessagesStrings.NAME_TOO_LONG, Pair.of("name",
                "123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890"));
        checkValidateNewUserFail(MessagesStrings.NO_HTML_ALLOWED, Pair.of("name", "<html><p></p></html>"));
    }

    /**
     * Test AuthenticationService.validateNewUser(): password validation fail
     */
    @Test
    public void checkValidateNewUserPasswordFail() {
        checkValidateNewUserFail(MessagesStrings.PASSWORDS_SHOULDNT_BE_EMPTY_STRINGS, Pair.of("password", ""));
        checkValidateNewUserFail(MessagesStrings.PASSWORDS_SHOULDNT_BE_EMPTY_STRINGS, Pair.of("passwordRepeat", ""));

    }

    /**
     * Test AuthenticationService.validateNewUser(): password length min but okay
     */
    @Test
    public void checkValidateNewUserPasswordMinLength() {
        String pw = StringUtils.leftPad("aA1$", Common.getUserPasswordMinLength(), 'a');
        checkValidateNewUserSuccess(Pair.of("password", pw), Pair.of("passwordRepeat", pw));
    }

    /**
     * Test AuthenticationService.validateNewUser(): password not long enough
     */
    @Test
    public void checkValidateNewUserPasswordNotLongEnough() {
        String pw = StringUtils.leftPad("aA1$", Common.getUserPasswordMinLength() - 1, 'a');
        checkValidateNewUserFail(MessagesStrings.userPasswordMinLength(Common.getUserPasswordMinLength()),
                Pair.of("password", pw), Pair.of("passwordRepeat", pw));
    }

    /**
     * Test AuthenticationService.validateNewUser(): password strong enough (assumes strength 3)
     */
    @Test
    public void checkValidateNewUserPasswordStrongEnough() {
        checkValidateNewUserSuccess(Pair.of("password", "abcABC1$"), Pair.of("passwordRepeat", "abcABC1$"));
    }

    /**
     * Test AuthenticationService.validateNewUser(): password not strong enough (assumes strength 3)
     */
    @Test
    public void checkValidateNewUserPasswordNotStrongEnough() {
        checkValidateNewUserFail(Common.getUserPasswordStrengthRegex().getLeft(),
                Pair.of("password", "abcabc1$"),
                Pair.of("passwordRepeat", "abcabc1$"));
        checkValidateNewUserFail(Common.getUserPasswordStrengthRegex().getLeft(),
                Pair.of("password", "ABCABC1$"),
                Pair.of("passwordRepeat", "ABCABC1$"));
        checkValidateNewUserFail(Common.getUserPasswordStrengthRegex().getLeft(),
                Pair.of("password", "abcABC$$"),
                Pair.of("passwordRepeat", "abcABC$$"));
        checkValidateNewUserFail(Common.getUserPasswordStrengthRegex().getLeft(),
                Pair.of("password", "abcABC11"),
                Pair.of("passwordRepeat", "abcABC11"));
    }

    /**
     * Test AuthenticationService.validateNewUser(): users passwords are
     * different
     */
    @Test
    public void checkValidateNewUserPasswordsNotEqual() {
        checkValidateNewUserFail(MessagesStrings.PASSWORDS_DONT_MATCH,
                Pair.of("password", "abcABC1$"),
                Pair.of("passwordRepeat", "different"));
    }

    /**
     * Test AuthenticationService.validateNewUser(): user exists already
     */
    @Test
    public void checkValidateNewUserUserExistsAlready() {
        checkValidateNewUserFail(MessagesStrings.THIS_USERNAME_IS_ALREADY_REGISTERED, Pair.of("username", "admin"));
    }

    /**
     * Test AuthenticationService.validateNewUser(): wrong admin password
     */
    @Test
    public void checkValidateNewUserWrongAdminPassword() {
        checkValidateNewUserFail(MessagesStrings.WRONG_PASSWORD,
                Pair.of("adminPassword", "wrongPw"),
                Pair.of("username", "foo"));
    }

    /**
     * AuthenticationService.validateChangePassword(): change password of an
     * user via user manager and an admin user must be logged-in
     */
    @Test
    public void checkValidateChangePasswordViaAdmin() {
        checkValidateChangePasswordSuccess(
                Pair.of("adminPassword", UserService.ADMIN_PASSWORD),
                Pair.of("newPassword", "abc123A$"),
                Pair.of("newPasswordRepeat", "abc123A$"));
    }

    /**
     * AuthenticationService.validateChangePassword(): passwords not empty
     */
    @Test
    public void checkValidateChangePasswordNotEmpty() {
        checkValidateChangePasswordFail(MessagesStrings.PASSWORDS_SHOULDNT_BE_EMPTY_STRINGS,
                Pair.of("adminPassword", UserService.ADMIN_PASSWORD),
                Pair.of("newPassword", ""));
    }

    /**
     * AuthenticationService.validateChangePassword(): passwords not empty
     */
    @Test
    public void checkValidateChangePasswordRepeatNotEmpty() {
        checkValidateChangePasswordFail(MessagesStrings.PASSWORDS_SHOULDNT_BE_EMPTY_STRINGS,
                Pair.of("adminPassword", UserService.ADMIN_PASSWORD),
                Pair.of("newPassword", "abc"),
                Pair.of("newPasswordRepeat", ""));
    }

    /**
     * AuthenticationService.validateChangePassword(): different passwords
     */
    @Test
    public void checkValidateChangePasswordViaAdminNotMatch() {
        checkValidateChangePasswordFail(MessagesStrings.PASSWORDS_DONT_MATCH,
                Pair.of("adminPassword", UserService.ADMIN_PASSWORD),
                Pair.of("newPassword", "abc123A$"),
                Pair.of("newPasswordRepeat", "wer345B$"));
    }

    /**
     * AuthenticationService.validateChangePassword(): exactly min length
     */
    @Test
    public void checkValidateChangePasswordMinLength() {
        String pw = StringUtils.leftPad("aA1$", Common.getUserPasswordMinLength(), 'a');
        checkValidateChangePasswordSuccess(
                Pair.of("adminPassword", UserService.ADMIN_PASSWORD),
                Pair.of("newPassword", pw),
                Pair.of("newPasswordRepeat", pw));
    }

    /**
     * AuthenticationService.validateChangePassword(): not long enough
     */
    @Test
    public void checkValidateChangePasswordNotLongEnough() {
        String pw = StringUtils.leftPad("aA1$", Common.getUserPasswordMinLength() - 1, 'a');
        checkValidateChangePasswordFail(MessagesStrings.userPasswordMinLength(Common.getUserPasswordMinLength()),
                Pair.of("adminPassword", UserService.ADMIN_PASSWORD),
                Pair.of("newPassword", pw),
                Pair.of("newPasswordRepeat", pw));
    }

    /**
     * AuthenticationService.validateChangePassword(): not strong enough (assumes strength 3)
     */
    @Test
    public void checkValidateChangePasswordNotStrongEnough() {
        // No upper case
        checkValidateChangePasswordFail(Common.getUserPasswordStrengthRegex().getLeft(),
                Pair.of("adminPassword", UserService.ADMIN_PASSWORD),
                Pair.of("newPassword", "abcabc1$"),
                Pair.of("newPasswordRepeat", "abcabc1$"));

        // No lower case
        checkValidateChangePasswordFail(Common.getUserPasswordStrengthRegex().getLeft(),
                Pair.of("adminPassword", UserService.ADMIN_PASSWORD),
                Pair.of("newPassword", "ABCABC1$"),
                Pair.of("newPasswordRepeat", "ABCABC1$"));

        // No number
        checkValidateChangePasswordFail(Common.getUserPasswordStrengthRegex().getLeft(),
                Pair.of("adminPassword", UserService.ADMIN_PASSWORD),
                Pair.of("newPassword", "abcABC$$"),
                Pair.of("newPasswordRepeat", "abcABC$$"));

        // No special character
        checkValidateChangePasswordFail(Common.getUserPasswordStrengthRegex().getLeft(),
                Pair.of("adminPassword", UserService.ADMIN_PASSWORD),
                Pair.of("newPassword", "abcABC11"),
                Pair.of("newPasswordRepeat", "abcABC11"));
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
            Form<ChangePasswordModel> validatedForm;
            try {
                validatedForm = authenticationValidation.validateChangePassword("tester.test@test.com", form);
            } catch (NamingException e) {
                throw new RuntimeException(e);
            }
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
            Form<ChangePasswordModel> validatedForm;
            try {
                validatedForm = authenticationValidation.validateChangePassword("different.test@test.com", form);
            } catch (NamingException e) {
                throw new RuntimeException(e);
            }
            assertThat(validatedForm.errors()).isNotEmpty();
            assertThat(validatedForm.errors().get(0).message()).isEqualTo(
                    MessagesStrings.NOT_ALLOWED_TO_CHANGE_PASSWORDS);
        });
    }

    @SafeVarargs
    private final void checkValidateNewUserSuccess(Pair<String, String>... userFields) {
        testHelper.mockContext();

        Map<String, String> data = createDummyUserData();
        for (Pair<String, String> userField : userFields) {
            data.put(userField.getKey(), userField.getValue());
        }
        Form<NewUserModel> form = formFactory.form(NewUserModel.class).bind(defaultLang, TypedMap.empty(), data);

        jpaApi.withTransaction(() -> {
            Form<NewUserModel> validatedForm;
            try {
                validatedForm = authenticationValidation.validateNewUser(UserService.ADMIN_USERNAME, form);
            } catch (NamingException e) {
                throw new RuntimeException(e);
            }
            assertThat(validatedForm.errors()).isEmpty();
        });
    }

    @SafeVarargs
    private final void checkValidateNewUserFail(String expectedErrorMsg, Pair<String, String>... userFields) {
        testHelper.mockContext();

        Map<String, String> data = createDummyUserData();
        for (Pair<String, String> userField : userFields) {
            data.put(userField.getKey(), userField.getValue());
        }
        Form<NewUserModel> form = formFactory.form(NewUserModel.class).bind(defaultLang, TypedMap.empty(), data);

        jpaApi.withTransaction(() -> {
            Form<NewUserModel> validatedForm;
            try {
                validatedForm = authenticationValidation.validateNewUser(UserService.ADMIN_USERNAME, form);
            } catch (NamingException e) {
                throw new RuntimeException(e);
            }
            assertThat(validatedForm.errors()).isNotEmpty();
            assertThat(validatedForm.errors().get(0).message()).isEqualTo(expectedErrorMsg);
        });
    }

    @SafeVarargs
    private final void checkValidateChangePasswordSuccess(Pair<String, String>... userFields) {
        prepareChangePasswordWithAdminTest();

        Map<String, String> data = new HashMap<>();
        for (Pair<String, String> userField : userFields) {
            data.put(userField.getKey(), userField.getValue());
        }
        Form<ChangePasswordModel> form = formFactory.form(ChangePasswordModel.class).bind(defaultLang, TypedMap.empty(),
                data);

        jpaApi.withTransaction(() -> {
            Form<ChangePasswordModel> validatedForm;
            try {
                validatedForm = authenticationValidation.validateChangePassword("tester.test@test.com", form);
            } catch (NamingException e) {
                throw new RuntimeException(e);
            }
            assertThat(validatedForm.errors()).isEmpty();
        });
    }

    @SafeVarargs
    private final void checkValidateChangePasswordFail(String expectedErrorMsg, Pair<String, String>... userFields) {
        prepareChangePasswordWithAdminTest();

        Map<String, String> data = new HashMap<>();
        for (Pair<String, String> userField : userFields) {
            data.put(userField.getKey(), userField.getValue());
        }
        Form<ChangePasswordModel> form = formFactory.form(ChangePasswordModel.class).bind(defaultLang, TypedMap.empty(),
                data);

        jpaApi.withTransaction(() -> {
            Form<ChangePasswordModel> validatedForm;
            try {
                validatedForm = authenticationValidation.validateChangePassword("tester.test@test.com", form);
            } catch (NamingException e) {
                throw new RuntimeException(e);
            }
            assertThat(validatedForm.errors()).isNotEmpty();
            assertThat(validatedForm.errors().get(0).message()).isEqualTo(expectedErrorMsg);
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
        data.put("username", "george@bla.com");
        data.put("name", "Georg Lange");
        data.put("password", "123äbcA$");
        data.put("passwordRepeat", "123äbcA$");
        data.put("adminRole", "true");
        data.put("adminPassword", "admin");
        data.put("authByLdap", "false");
        return data;
    }

}
