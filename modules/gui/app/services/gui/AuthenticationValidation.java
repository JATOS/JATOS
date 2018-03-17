package services.gui;

import daos.common.UserDao;
import general.common.Common;
import general.common.MessagesStrings;
import models.common.User;
import models.common.User.Role;
import models.gui.ChangePasswordModel;
import models.gui.NewUserModel;
import org.apache.commons.lang3.tuple.Pair;
import org.jsoup.Jsoup;
import org.jsoup.safety.Whitelist;
import play.data.validation.ValidationError;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

/**
 * Service class that validates models that create, change or delete users.
 * Usually this validation is part of the model class, but since this is
 * concerns important user authentication and it used other service and DAO
 * classes I put it in an extra class.
 *
 * @author Kristian Lange (2017)
 */
@Singleton
public class AuthenticationValidation {

    private final AuthenticationService authenticationService;
    private final UserDao userDao;

    @Inject
    AuthenticationValidation(AuthenticationService authenticationService,
            UserDao userDao) {
        this.authenticationService = authenticationService;
        this.userDao = userDao;
    }

    /**
     * Validates a NewUserModel and returns a list with errors. Usually this is
     * done in the model class, but since here the user DAO is needed I put it
     * in an extra class. In the NewUserModel are still some simple validations.
     */
    public List<ValidationError> validateNewUser(NewUserModel newUserModel,
            String loggedInAdminEmail) {
        List<ValidationError> errorList = new ArrayList<>();
        String email = newUserModel.getEmail();
        String password = newUserModel.getPassword();
        String passwordRepeat = newUserModel.getPasswordRepeat();
        String name = newUserModel.getName();

        if (email == null || email.trim().isEmpty()) {
            errorList.add(
                    new ValidationError(NewUserModel.EMAIL, MessagesStrings.MISSING_EMAIL));
            return errorList;
        }

        if (email.length() > 255) {
            errorList.add(
                    new ValidationError(NewUserModel.EMAIL, MessagesStrings.EMAIL_TOO_LONG));
        }

        // Check with Jsoup for illegal HTML
        if (!Jsoup.isValid(email, Whitelist.none())) {
            errorList.add(new ValidationError(NewUserModel.EMAIL, MessagesStrings.NO_HTML_ALLOWED));
        }

        if (name == null || name.trim().isEmpty()) {
            errorList.add(
                    new ValidationError(NewUserModel.NAME, MessagesStrings.MISSING_NAME));
            return errorList;
        }

        if (name.length() > 255) {
            errorList.add(
                    new ValidationError(NewUserModel.NAME, MessagesStrings.NAME_TOO_LONG));
        }

        // Check with Jsoup for illegal HTML
        if (!Jsoup.isValid(name, Whitelist.none())) {
            errorList.add(
                    new ValidationError(NewUserModel.NAME, MessagesStrings.NO_HTML_ALLOWED));
        }

        if (password == null || password.trim().isEmpty()) {
            errorList.add(new ValidationError(NewUserModel.PASSWORD,
                    MessagesStrings.PASSWORDS_SHOULDNT_BE_EMPTY_STRINGS));
            return errorList;
        }
        if (passwordRepeat == null || passwordRepeat.trim().isEmpty()) {
            errorList.add(new ValidationError(NewUserModel.PASSWORD_REPEAT,
                    MessagesStrings.PASSWORDS_SHOULDNT_BE_EMPTY_STRINGS));
            return errorList;
        }

        // Check password length as specified in config
        if (password.length() < Common.getUserPasswordMinLength()) {
            errorList.add(new ValidationError(NewUserModel.PASSWORD,
                    MessagesStrings.userPasswordMinLength(Common.getUserPasswordMinLength())));
        }

        // Check password strength as specified in config
        Pair<String, String> regex = Common.getUserPasswordStrengthRegex();
        if (!password.matches(regex.getRight())) {
            errorList.add(new ValidationError(NewUserModel.PASSWORD, regex.getLeft()));
        }

        // Check if user with this email already exists
        User existingUser = userDao.findByEmail(email);
        if (existingUser != null) {
            errorList.add(new ValidationError(NewUserModel.EMAIL,
                    MessagesStrings.THIS_EMAIL_IS_ALREADY_REGISTERED));
        }

        // Check both passwords equal
        if (!password.equals(passwordRepeat)) {
            errorList.add(new ValidationError(NewUserModel.PASSWORD,
                    MessagesStrings.PASSWORDS_DONT_MATCH));
        }

        // Authenticate: check admin password
        String adminPassword = newUserModel.getAdminPassword();
        if (!authenticationService.authenticate(loggedInAdminEmail, adminPassword)) {
            errorList.add(new ValidationError(NewUserModel.ADMIN_PASSWORD,
                    MessagesStrings.WRONG_PASSWORD));
        }

        return errorList;
    }

    /**
     * Validates a ChangePasswordModel and returns a list with errors. It can
     * either originate in the GUI in the user manager or in the user profile.
     * Both cases have to be validated differently. Usually this would be done
     * in the ChangePasswordModel class, but since here the user DAO is needed I
     * put it in an extra class.
     */
    public List<ValidationError> validateChangePassword(String emailOfUserToChange,
            ChangePasswordModel changePasswordModel) {
        emailOfUserToChange = emailOfUserToChange.toLowerCase();
        User loggedInUser = authenticationService.getLoggedInUser();

        // All form related errors go into errorList
        List<ValidationError> errorList = new ArrayList<>();

        // Only user 'admin' is allowed to change his password
        if (emailOfUserToChange.equals(UserService.ADMIN_EMAIL) &&
                !loggedInUser.getEmail().equals(UserService.ADMIN_EMAIL)) {
            errorList.add(new ValidationError(ChangePasswordModel.ADMIN_PASSWORD,
                    MessagesStrings.NOT_ALLOWED_CHANGE_PW_ADMIN));
            return errorList;
        }

        String newPassword = changePasswordModel.getNewPassword();
        String newPasswordRepeat = changePasswordModel.getNewPasswordRepeat();

        // Check both not empty
        if (newPassword == null || newPassword.trim().isEmpty()) {
            errorList.add(new ValidationError(ChangePasswordModel.NEW_PASSWORD,
                    MessagesStrings.PASSWORDS_SHOULDNT_BE_EMPTY_STRINGS));
            return errorList;
        }
        if (newPasswordRepeat == null || newPasswordRepeat.trim().isEmpty()) {
            errorList.add(new ValidationError(ChangePasswordModel.NEW_PASSWORD_REPEAT,
                    MessagesStrings.PASSWORDS_SHOULDNT_BE_EMPTY_STRINGS));
            return errorList;
        }

        // Check both match
        if (!newPassword.equals(newPasswordRepeat)) {
            errorList.add(new ValidationError(ChangePasswordModel.NEW_PASSWORD,
                    MessagesStrings.PASSWORDS_DONT_MATCH));
        }

        // Check length as specified in conf
        if (newPassword != null && newPassword.length() < Common.getUserPasswordMinLength()) {
            errorList.add(new ValidationError(ChangePasswordModel.NEW_PASSWORD,
                    MessagesStrings.userPasswordMinLength(Common.getUserPasswordMinLength())));
        }

        // Check strength as specified in conf
        Pair<String, String> regex = Common.getUserPasswordStrengthRegex();
        if (newPassword != null && !newPassword.matches(regex.getRight())) {
            errorList.add(new ValidationError(ChangePasswordModel.NEW_PASSWORD, regex.getLeft()));
        }

        // Authenticate: Either admin changes a password for some other user
        // or an user changes their own password
        if (loggedInUser.hasRole(Role.ADMIN)
                && changePasswordModel.getAdminPassword() != null) {
            String adminEmail = loggedInUser.getEmail();
            String adminPassword = changePasswordModel.getAdminPassword();
            if (!authenticationService.authenticate(adminEmail, adminPassword)) {
                errorList.add(new ValidationError(ChangePasswordModel.ADMIN_PASSWORD,
                        MessagesStrings.WRONG_PASSWORD));
            }

        } else if (loggedInUser.getEmail().equals(emailOfUserToChange)
                && changePasswordModel.getOldPassword() != null) {
            String oldPassword = changePasswordModel.getOldPassword();
            if (!authenticationService.authenticate(emailOfUserToChange, oldPassword)) {
                errorList.add(new ValidationError(ChangePasswordModel.OLD_PASSWORD,
                        MessagesStrings.WRONG_OLD_PASSWORD));
            }

        } else {
            // Should never happen since we checked role ADMIN already
            errorList.add(new ValidationError(ChangePasswordModel.ADMIN_PASSWORD,
                    MessagesStrings.NOT_ALLOWED_TO_CHANGE_PASSWORDS));
        }

        return errorList;
    }

}
