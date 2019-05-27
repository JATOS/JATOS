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
import play.data.validation.ValidationError;
import play.db.jpa.JPAApi;
import play.inject.guice.GuiceApplicationBuilder;
import play.inject.guice.GuiceApplicationLoader;

import javax.inject.Inject;
import java.util.List;

import static org.fest.assertions.Assertions.assertThat;

/**
 * Tests UserService
 *
 * @author Kristian Lange
 */
public class AuthenticationValidationTest {

    @Inject
    private TestHelper testHelper;

    @Inject
    private JPAApi jpaApi;

    @Inject
    private AuthenticationValidation authenticationValidation;

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

        NewUserModel newUserModel = createDummyNewUserModel();

        jpaApi.withTransaction(() -> {
            List<ValidationError> errorList = authenticationValidation
                    .validateNewUser(newUserModel, UserService.ADMIN_EMAIL);
            assertThat(errorList).isEmpty();
        });
    }

    /**
     * Test AuthenticationService.validateNewUser(): email is empty
     */
    @Test
    public void checkValidateNewUserEmailEmpty() {
        testHelper.mockContext();

        NewUserModel newUserModel = createDummyNewUserModel();
        newUserModel.setEmail("");

        jpaApi.withTransaction(() -> {
            List<ValidationError> errorList = authenticationValidation
                    .validateNewUser(newUserModel, UserService.ADMIN_EMAIL);
            assertThat(errorList).isNotEmpty();
            assertThat(errorList.get(0).message()).isEqualTo(MessagesStrings.MISSING_EMAIL);
        });
    }

    /**
     * Test AuthenticationService.validateNewUser(): email too long
     */
    @Test
    public void checkValidateNewUserEmailTooLong() {
        testHelper.mockContext();

        NewUserModel newUserModel = createDummyNewUserModel();
        newUserModel.setEmail(
                "123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890");

        jpaApi.withTransaction(() -> {
            List<ValidationError> errorList = authenticationValidation
                    .validateNewUser(newUserModel, UserService.ADMIN_EMAIL);
            assertThat(errorList).isNotEmpty();
            assertThat(errorList.get(0).message()).isEqualTo(MessagesStrings.EMAIL_TOO_LONG);
        });
    }

    /**
     * Test AuthenticationService.validateNewUser(): HTML is not allowed
     */
    @Test
    public void checkValidateNewUserEmailNoHtml() {
        testHelper.mockContext();

        NewUserModel newUserModel = createDummyNewUserModel();
        newUserModel.setEmail("<html><p></p></html>");

        jpaApi.withTransaction(() -> {
            List<ValidationError> errorList = authenticationValidation
                    .validateNewUser(newUserModel, UserService.ADMIN_EMAIL);
            assertThat(errorList).isNotEmpty();
            assertThat(errorList.get(0).message()).isEqualTo(MessagesStrings.NO_HTML_ALLOWED);
        });
    }

    /**
     * Test AuthenticationService.validateNewUser(): name is empty
     */
    @Test
    public void checkValidateNewUserNameEmpty() {
        testHelper.mockContext();

        NewUserModel newUserModel = createDummyNewUserModel();
        newUserModel.setName("");

        jpaApi.withTransaction(() -> {
            List<ValidationError> errorList = authenticationValidation
                    .validateNewUser(newUserModel, UserService.ADMIN_EMAIL);
            assertThat(errorList).isNotEmpty();
            assertThat(errorList.get(0).message()).isEqualTo(MessagesStrings.MISSING_NAME);
        });
    }

    /**
     * Test AuthenticationService.validateNewUser(): name is too long
     */
    @Test
    public void checkValidateNewUserNameTooLong() {
        testHelper.mockContext();

        NewUserModel newUserModel = createDummyNewUserModel();
        newUserModel.setName(
                "123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890");

        jpaApi.withTransaction(() -> {
            List<ValidationError> errorList = authenticationValidation
                    .validateNewUser(newUserModel, UserService.ADMIN_EMAIL);
            assertThat(errorList).isNotEmpty();
            assertThat(errorList.get(0).message()).isEqualTo(MessagesStrings.NAME_TOO_LONG);
        });
    }

    /**
     * Test AuthenticationService.validateNewUser(): HTML is not allowed
     */
    @Test
    public void checkValidateNewUserNameNoHtml() {
        testHelper.mockContext();

        NewUserModel newUserModel = createDummyNewUserModel();
        newUserModel.setName("<html><p></p></html>");

        jpaApi.withTransaction(() -> {
            List<ValidationError> errorList = authenticationValidation
                    .validateNewUser(newUserModel, UserService.ADMIN_EMAIL);
            assertThat(errorList).isNotEmpty();
            assertThat(errorList.get(0).message()).isEqualTo(MessagesStrings.NO_HTML_ALLOWED);
        });
    }

    /**
     * Test AuthenticationService.validateNewUser(): password is empty
     */
    @Test
    public void checkValidateNewUserPasswordEmpty() {
        testHelper.mockContext();

        NewUserModel newUserModel = createDummyNewUserModel();
        newUserModel.setPassword("");

        jpaApi.withTransaction(() -> {
            List<ValidationError> errorList = authenticationValidation
                    .validateNewUser(newUserModel, UserService.ADMIN_EMAIL);
            assertThat(errorList).isNotEmpty();
            assertThat(errorList.get(0).message()).isEqualTo(MessagesStrings.PASSWORDS_SHOULDNT_BE_EMPTY_STRINGS);
        });
    }

    /**
     * Test AuthenticationService.validateNewUser(): password is empty
     */
    @Test
    public void checkValidateNewUserRepeatedPasswordEmpty() {
        testHelper.mockContext();

        NewUserModel newUserModel = createDummyNewUserModel();
        newUserModel.setPasswordRepeat("");

        jpaApi.withTransaction(() -> {
            List<ValidationError> errorList = authenticationValidation
                    .validateNewUser(newUserModel, UserService.ADMIN_EMAIL);
            assertThat(errorList).isNotEmpty();
            assertThat(errorList.get(0).message()).isEqualTo(MessagesStrings.PASSWORDS_SHOULDNT_BE_EMPTY_STRINGS);
        });
    }

    /**
     * Test AuthenticationService.validateNewUser(): password length min but okay
     */
    @Test
    public void checkValidateNewUserPasswordMinLength() {
        testHelper.mockContext();

        NewUserModel newUserModel = createDummyNewUserModel();
        newUserModel.setPassword(StringUtils.leftPad("aA1$", Common.getUserPasswordMinLength(), 'a'));
        newUserModel.setPasswordRepeat(newUserModel.getPassword());

        jpaApi.withTransaction(() -> {
            List<ValidationError> errorList = authenticationValidation
                    .validateNewUser(newUserModel, UserService.ADMIN_EMAIL);
            assertThat(errorList).isEmpty();
        });
    }

    /**
     * Test AuthenticationService.validateNewUser(): password not long enough
     */
    @Test
    public void checkValidateNewUserPasswordNotLongEnough() {
        testHelper.mockContext();

        NewUserModel newUserModel = createDummyNewUserModel();
        newUserModel.setPassword(StringUtils.leftPad("aA1$", Common.getUserPasswordMinLength() - 1, 'a'));
        newUserModel.setPasswordRepeat(newUserModel.getPassword());

        jpaApi.withTransaction(() -> {
            List<ValidationError> errorList = authenticationValidation
                    .validateNewUser(newUserModel, UserService.ADMIN_EMAIL);
            assertThat(errorList).isNotEmpty();
            assertThat(errorList.get(0).message())
                    .isEqualTo(MessagesStrings.userPasswordMinLength(Common.getUserPasswordMinLength()));
        });
    }

    /**
     * Test AuthenticationService.validateNewUser(): password strong enough (assumes strength 3)
     */
    @Test
    public void checkValidateNewUserPasswordStrongEnough() {
        testHelper.mockContext();

        NewUserModel newUserModel = createDummyNewUserModel();
        newUserModel.setPassword("abcABC1$");
        newUserModel.setPasswordRepeat(newUserModel.getPassword());

        jpaApi.withTransaction(() -> {
            List<ValidationError> errorList = authenticationValidation
                    .validateNewUser(newUserModel, UserService.ADMIN_EMAIL);
            assertThat(errorList).isEmpty();
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
            NewUserModel newUserModel = createDummyNewUserModel();
            newUserModel.setPassword("abcabc1$");
            newUserModel.setPasswordRepeat(newUserModel.getPassword());
            List<ValidationError> errorList = authenticationValidation
                    .validateNewUser(newUserModel, UserService.ADMIN_EMAIL);
            assertThat(errorList).isNotEmpty();
            assertThat(errorList.get(0).message()).isEqualTo(Common.getUserPasswordStrengthRegex().getLeft());
        });

        // No lower case
        jpaApi.withTransaction(() -> {
            NewUserModel newUserModel = createDummyNewUserModel();
            newUserModel.setPassword("ABCABC1$");
            newUserModel.setPasswordRepeat(newUserModel.getPassword());
            List<ValidationError> errorList = authenticationValidation
                    .validateNewUser(newUserModel, UserService.ADMIN_EMAIL);
            assertThat(errorList).isNotEmpty();
            assertThat(errorList.get(0).message()).isEqualTo(Common.getUserPasswordStrengthRegex().getLeft());
        });

        // No number
        jpaApi.withTransaction(() -> {
            NewUserModel newUserModel = createDummyNewUserModel();
            newUserModel.setPassword("abcABC$$");
            newUserModel.setPasswordRepeat(newUserModel.getPassword());
            List<ValidationError> errorList = authenticationValidation
                    .validateNewUser(newUserModel, UserService.ADMIN_EMAIL);
            assertThat(errorList).isNotEmpty();
            assertThat(errorList.get(0).message()).isEqualTo(Common.getUserPasswordStrengthRegex().getLeft());
        });

        // No special character
        jpaApi.withTransaction(() -> {
            NewUserModel newUserModel = createDummyNewUserModel();
            newUserModel.setPassword("abcABC11");
            newUserModel.setPasswordRepeat(newUserModel.getPassword());
            List<ValidationError> errorList = authenticationValidation
                    .validateNewUser(newUserModel, UserService.ADMIN_EMAIL);
            assertThat(errorList).isNotEmpty();
            assertThat(errorList.get(0).message()).isEqualTo(Common.getUserPasswordStrengthRegex().getLeft());
        });
    }

    /**
     * Test AuthenticationService.validateNewUser(): users passwords are different
     */
    @Test
    public void checkValidateNewUserPasswordsNotEqual() {
        testHelper.mockContext();

        NewUserModel newUserModel = createDummyNewUserModel();
        newUserModel.setPasswordRepeat("different");

        jpaApi.withTransaction(() -> {
            List<ValidationError> errorList = authenticationValidation
                    .validateNewUser(newUserModel, UserService.ADMIN_EMAIL);
            assertThat(errorList).isNotEmpty();
            assertThat(errorList.get(0).message()).isEqualTo(MessagesStrings.PASSWORDS_DONT_MATCH);
        });
    }

    /**
     * Test AuthenticationService.validateNewUser(): user exists already
     */
    @Test
    public void checkValidateNewUserUserExistsAlready() {
        testHelper.mockContext();

        NewUserModel newUserModel = createDummyNewUserModel();
        newUserModel.setEmail("admin");

        jpaApi.withTransaction(() -> {
            List<ValidationError> errorList = authenticationValidation
                    .validateNewUser(newUserModel, UserService.ADMIN_EMAIL);
            assertThat(errorList).hasSize(1);
            assertThat(errorList.get(0).message()).isEqualTo(MessagesStrings.THIS_EMAIL_IS_ALREADY_REGISTERED);
        });
    }

    /**
     * Test AuthenticationService.validateNewUser(): wrong admin password
     */
    @Test
    public void checkValidateNewUserWrongAdminPassword() {
        testHelper.mockContext();

        NewUserModel newUserModel = createDummyNewUserModel();
        newUserModel.setAdminPassword("wrongPw");

        jpaApi.withTransaction(() -> {
            List<ValidationError> errorList = authenticationValidation
                    .validateNewUser(newUserModel, UserService.ADMIN_EMAIL);
            assertThat(errorList).hasSize(1);
            assertThat(errorList.get(0).message()).isEqualTo(MessagesStrings.WRONG_PASSWORD);
        });
    }

    /**
     * AuthenticationService.validateChangePassword(): change password of an user via user manager and an admin user
     * must be logged-in
     */
    @Test
    public void checkValidateChangePasswordViaAdmin() {
        prepareChangePasswordWithAdminTest();

        ChangePasswordModel model = new ChangePasswordModel();
        model.setAdminPassword(UserService.ADMIN_PASSWORD);
        model.setNewPassword("abc123A$");
        model.setNewPasswordRepeat("abc123A$");

        jpaApi.withTransaction(() -> {
            List<ValidationError> errorList = authenticationValidation
                    .validateChangePassword("tester.test@test.com", model);
            assertThat(errorList).isEmpty();
        });
    }

    /**
     * AuthenticationService.validateChangePassword(): passwords not empty
     */
    @Test
    public void checkValidateChangePasswordNotEmpty() {
        prepareChangePasswordWithAdminTest();

        ChangePasswordModel model = new ChangePasswordModel();
        model.setAdminPassword(UserService.ADMIN_PASSWORD);
        model.setNewPassword("");

        jpaApi.withTransaction(() -> {
            List<ValidationError> errorList = authenticationValidation
                    .validateChangePassword("tester.test@test.com", model);
            assertThat(errorList).isNotEmpty();
            assertThat(errorList.get(0).message()).isEqualTo(MessagesStrings.PASSWORDS_SHOULDNT_BE_EMPTY_STRINGS);
        });
    }

    /**
     * AuthenticationService.validateChangePassword(): passwords not empty
     */
    @Test
    public void checkValidateChangePasswordRepeatNotEmpty() {
        prepareChangePasswordWithAdminTest();

        ChangePasswordModel model = new ChangePasswordModel();
        model.setAdminPassword(UserService.ADMIN_PASSWORD);
        model.setNewPassword("abc");
        model.setNewPasswordRepeat("");

        jpaApi.withTransaction(() -> {
            List<ValidationError> errorList = authenticationValidation
                    .validateChangePassword("tester.test@test.com", model);
            assertThat(errorList).isNotEmpty();
            assertThat(errorList.get(0).message()).isEqualTo(MessagesStrings.PASSWORDS_SHOULDNT_BE_EMPTY_STRINGS);
        });
    }

    /**
     * AuthenticationService.validateChangePassword(): different passwords
     */
    @Test
    public void checkValidateChangePasswordViaAdminNotMatch() {
        prepareChangePasswordWithAdminTest();

        ChangePasswordModel model = new ChangePasswordModel();
        model.setAdminPassword(UserService.ADMIN_PASSWORD);
        model.setNewPassword("abc123A$");
        model.setNewPasswordRepeat("wer345B$");

        jpaApi.withTransaction(() -> {
            List<ValidationError> errorList = authenticationValidation
                    .validateChangePassword("tester.test@test.com", model);
            assertThat(errorList).isNotEmpty();
            assertThat(errorList.get(0).message()).isEqualTo(MessagesStrings.PASSWORDS_DONT_MATCH);
        });
    }

    /**
     * AuthenticationService.validateChangePassword(): exactly min length
     */
    @Test
    public void checkValidateChangePasswordMinLength() {
        prepareChangePasswordWithAdminTest();

        ChangePasswordModel model = new ChangePasswordModel();
        model.setAdminPassword(UserService.ADMIN_PASSWORD);
        model.setNewPassword(StringUtils.leftPad("aA1$", Common.getUserPasswordMinLength(), 'a'));
        model.setNewPasswordRepeat(model.getNewPassword());

        jpaApi.withTransaction(() -> {
            List<ValidationError> errorList = authenticationValidation
                    .validateChangePassword("tester.test@test.com", model);
            assertThat(errorList).isEmpty();
        });
    }

    /**
     * AuthenticationService.validateChangePassword(): not long enough
     */
    @Test
    public void checkValidateChangePasswordNotLongEnough() {
        prepareChangePasswordWithAdminTest();

        ChangePasswordModel model = new ChangePasswordModel();
        model.setAdminPassword(UserService.ADMIN_PASSWORD);
        model.setNewPassword(StringUtils.leftPad("aA1$", Common.getUserPasswordMinLength() - 1, 'a'));
        model.setNewPasswordRepeat(model.getNewPassword());

        jpaApi.withTransaction(() -> {
            List<ValidationError> errorList = authenticationValidation
                    .validateChangePassword("tester.test@test.com", model);
            assertThat(errorList.get(0).message())
                    .isEqualTo(MessagesStrings.userPasswordMinLength(Common.getUserPasswordMinLength()));
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
            ChangePasswordModel model = new ChangePasswordModel();
            model.setAdminPassword(UserService.ADMIN_PASSWORD);
            model.setNewPassword("abcabc1$");
            model.setNewPasswordRepeat(model.getNewPassword());
            List<ValidationError> errorList = authenticationValidation
                    .validateChangePassword("tester.test@test.com", model);
            assertThat(errorList).isNotEmpty();
            assertThat(errorList.get(0).message()).isEqualTo(Common.getUserPasswordStrengthRegex().getLeft());
        });

        // No lower case
        jpaApi.withTransaction(() -> {
            ChangePasswordModel model = new ChangePasswordModel();
            model.setAdminPassword(UserService.ADMIN_PASSWORD);
            model.setNewPassword("ABCABC1$");
            model.setNewPasswordRepeat(model.getNewPassword());
            List<ValidationError> errorList = authenticationValidation
                    .validateChangePassword("tester.test@test.com", model);
            assertThat(errorList).isNotEmpty();
            assertThat(errorList.get(0).message()).isEqualTo(Common.getUserPasswordStrengthRegex().getLeft());
        });

        // No number
        jpaApi.withTransaction(() -> {
            ChangePasswordModel model = new ChangePasswordModel();
            model.setAdminPassword(UserService.ADMIN_PASSWORD);
            model.setNewPassword("abcABC$$");
            model.setNewPasswordRepeat(model.getNewPassword());
            List<ValidationError> errorList = authenticationValidation
                    .validateChangePassword("tester.test@test.com", model);
            assertThat(errorList).isNotEmpty();
            assertThat(errorList.get(0).message()).isEqualTo(Common.getUserPasswordStrengthRegex().getLeft());
        });

        // No special character
        jpaApi.withTransaction(() -> {
            ChangePasswordModel model = new ChangePasswordModel();
            model.setAdminPassword(UserService.ADMIN_PASSWORD);
            model.setNewPassword("abcABC11");
            model.setNewPasswordRepeat(model.getNewPassword());
            List<ValidationError> errorList = authenticationValidation
                    .validateChangePassword("tester.test@test.com", model);
            assertThat(errorList).isNotEmpty();
            assertThat(errorList.get(0).message()).isEqualTo(Common.getUserPasswordStrengthRegex().getLeft());
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

        ChangePasswordModel model = new ChangePasswordModel();
        model.setOldPassword("password");
        model.setNewPassword("abc123A$");
        model.setNewPasswordRepeat("abc123A$");

        jpaApi.withTransaction(() -> {
            List<ValidationError> errorList = authenticationValidation
                    .validateChangePassword("tester.test@test.com", model);
            assertThat(errorList).isEmpty();
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

        ChangePasswordModel model = new ChangePasswordModel();
        model.setOldPassword("password");
        model.setNewPassword("abc123A$");
        model.setNewPasswordRepeat("abc123A$");

        jpaApi.withTransaction(() -> {
            List<ValidationError> errorList = authenticationValidation
                    .validateChangePassword("different.test@test.com", model);
            assertThat(errorList).isNotEmpty();
            assertThat(errorList.get(0).message()).isEqualTo(MessagesStrings.NOT_ALLOWED_TO_CHANGE_PASSWORDS);
        });
    }

    private void prepareChangePasswordWithAdminTest() {
        testHelper.mockContext();

        testHelper.createAndPersistUser("tester.test@test.com", "Test Tester", "password");
        User admin = testHelper.getAdmin();
        RequestScope.put(AuthenticationService.LOGGED_IN_USER, admin);
    }

    private NewUserModel createDummyNewUserModel() {
        NewUserModel newUserModel = new NewUserModel();
        newUserModel.setEmail("george@bla.com");
        newUserModel.setName("Georg Lange");
        newUserModel.setPassword("123äbcA$"); // all UTF-8 allowed
        newUserModel.setPasswordRepeat("123äbcA$");
        newUserModel.setAdminRole(true);
        newUserModel.setAdminPassword("admin");
        return newUserModel;
    }

}
