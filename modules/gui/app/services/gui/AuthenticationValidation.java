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
import play.data.Form;
import play.data.validation.ValidationError;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.naming.NamingException;

/**
 * Service class that validates models that create, change or delete users.
 * Usually this validation is part of the model class, but since this is
 * concerns important user authentication and it used other service and DAO
 * classes I put it in an extra class.
 *
 * @author Kristian Lange
 */
@Singleton
public class AuthenticationValidation {

    private final AuthenticationService authenticationService;
    private final UserDao userDao;

    @Inject
    AuthenticationValidation(AuthenticationService authenticationService, UserDao userDao) {
        this.authenticationService = authenticationService;
        this.userDao = userDao;
    }

    /**
     * Validates a NewUserModel and returns a Form with errors. Usually this is
     * done in the model class, but since here the user DAO is needed I put it
     * in an extra class. In the NewUserModel are still some simple validations.
     */
    public Form<NewUserModel> validateNewUser(String normalizedLoggedInAdminUsername, Form<NewUserModel> form)
            throws NamingException {
        String normalizeUsername = User.normalizeUsername(form.get().getUsername());
        String password = form.get().getPassword();
        String passwordRepeat = form.get().getPasswordRepeat();
        String name = form.get().getName();
        String adminPassword = form.get().getAdminPassword();
        boolean authByLdap = form.get().getAuthByLdap();

        if (normalizeUsername == null || normalizeUsername.isEmpty()) {
            return form.withError(new ValidationError(NewUserModel.USERNAME, MessagesStrings.MISSING_USERNAME));
        }

        if (!normalizeUsername.matches("^[\\p{IsAlphabetic}\\p{IsDigit}-_@.+&'=~]+$")) {
            return form.withError(new ValidationError(NewUserModel.USERNAME, MessagesStrings.USERNAME_INVALID));
        }

        if (normalizeUsername.length() > 255) {
            form = form.withError(new ValidationError(NewUserModel.USERNAME, MessagesStrings.USERNAME_TOO_LONG));
        }

        // Check with Jsoup for illegal HTML
        if (!Jsoup.isValid(normalizeUsername, Whitelist.none())) {
            form = form.withError(new ValidationError(NewUserModel.USERNAME, MessagesStrings.NO_HTML_ALLOWED));
        }

        if (name == null || name.trim().isEmpty()) {
            return form.withError(new ValidationError(NewUserModel.NAME, MessagesStrings.MISSING_NAME));
        }

        if (name.length() > 255) {
            form = form.withError(new ValidationError(NewUserModel.NAME, MessagesStrings.NAME_TOO_LONG));
        }

        // Check with Jsoup for illegal HTML
        if (!Jsoup.isValid(name, Whitelist.none())) {
            form = form.withError(new ValidationError(NewUserModel.NAME, MessagesStrings.NO_HTML_ALLOWED));
        }

        // Check if user with this username already exists
        User existingUser = userDao.findByUsername(normalizeUsername);
        if (existingUser != null) {
            form = form.withError(
                    new ValidationError(NewUserModel.USERNAME, MessagesStrings.THIS_USERNAME_IS_ALREADY_REGISTERED));
        }

        // Check password only if not authenticated by LDAP
        if (!authByLdap) {
            if (password == null || password.trim().isEmpty()) {
                return form.withError(new ValidationError(NewUserModel.PASSWORD,
                        MessagesStrings.PASSWORDS_SHOULDNT_BE_EMPTY_STRINGS));
            }
            if (passwordRepeat == null || passwordRepeat.trim().isEmpty()) {
                return form.withError(new ValidationError(NewUserModel.PASSWORD_REPEAT,
                        MessagesStrings.PASSWORDS_SHOULDNT_BE_EMPTY_STRINGS));
            }

            // Check password length as specified in config
            if (password.length() < Common.getUserPasswordMinLength()) {
                form = form.withError(new ValidationError(NewUserModel.PASSWORD,
                        MessagesStrings.userPasswordMinLength(Common.getUserPasswordMinLength())));
            }

            // Check password strength as specified in config
            Pair<String, String> regex = Common.getUserPasswordStrengthRegex();
            if (!password.matches(regex.getRight())) {
                form = form.withError(new ValidationError(NewUserModel.PASSWORD, regex.getLeft()));
            }

            // Check both passwords equal
            if (!password.equals(passwordRepeat)) {
                form = form.withError(new ValidationError(NewUserModel.PASSWORD, MessagesStrings.PASSWORDS_DONT_MATCH));
            }
        }

        // Authenticate: check admin password
        if (!authenticationService.authenticate(normalizedLoggedInAdminUsername, adminPassword)) {
            form = form.withError(new ValidationError(NewUserModel.ADMIN_PASSWORD, MessagesStrings.WRONG_PASSWORD));
        }

        return form;
    }

    /**
     * Validates a ChangePasswordModel and returns the Form with errors. It can
     * either originate in the GUI in the user manager or in the user profile.
     * Both cases have to be validated differently. Usually this would be done
     * in the ChangePasswordModel class, but since here the user DAO is needed I
     * put it in an extra class.
     */
    public Form<ChangePasswordModel> validateChangePassword(String normalizedUsernameOfUserToChange,
            Form<ChangePasswordModel> form) throws NamingException {
        ChangePasswordModel model = form.get();
        User loggedInUser = authenticationService.getLoggedInUser();

        // Only user 'admin' is allowed to change his password
        if (normalizedUsernameOfUserToChange.equals(UserService.ADMIN_USERNAME) &&
                !loggedInUser.getUsername().equals(UserService.ADMIN_USERNAME)) {
            return form.withError(new ValidationError(ChangePasswordModel.ADMIN_PASSWORD,
                    MessagesStrings.NOT_ALLOWED_CHANGE_PW_ADMIN));
        }

        String newPassword = model.getNewPassword();
        String newPasswordRepeat = model.getNewPasswordRepeat();

        // Check both not empty
        if (newPassword == null || newPassword.trim().isEmpty()) {
            form = form.withError(new ValidationError(ChangePasswordModel.NEW_PASSWORD,
                    MessagesStrings.PASSWORDS_SHOULDNT_BE_EMPTY_STRINGS));
            return form;
        }
        if (newPasswordRepeat == null || newPasswordRepeat.trim().isEmpty()) {
            form = form.withError(new ValidationError(ChangePasswordModel.NEW_PASSWORD_REPEAT,
                    MessagesStrings.PASSWORDS_SHOULDNT_BE_EMPTY_STRINGS));
            return form;
        }

        // Check both match
        if (!newPassword.equals(newPasswordRepeat)) {
            form = form.withError(
                    new ValidationError(ChangePasswordModel.NEW_PASSWORD, MessagesStrings.PASSWORDS_DONT_MATCH));
        }

        // Check length as specified in conf
        if (newPassword.length() < Common.getUserPasswordMinLength()) {
            form = form.withError(new ValidationError(ChangePasswordModel.NEW_PASSWORD,
                    MessagesStrings.userPasswordMinLength(Common.getUserPasswordMinLength())));
        }

        // Check strength as specified in conf
        Pair<String, String> regex = Common.getUserPasswordStrengthRegex();
        if (!newPassword.matches(regex.getRight())) {
            form = form.withError(new ValidationError(ChangePasswordModel.NEW_PASSWORD, regex.getLeft()));
        }

        // Authenticate: Either admin changes a password for some other user
        // or an user changes their own password
        if (loggedInUser.hasRole(Role.ADMIN) && model.getAdminPassword() != null) {
            String adminUsername = loggedInUser.getUsername();
            String adminPassword = model.getAdminPassword();
            if (!authenticationService.authenticate(adminUsername, adminPassword)) {
                form = form.withError(
                        new ValidationError(ChangePasswordModel.ADMIN_PASSWORD, MessagesStrings.WRONG_PASSWORD));
            }

        } else if (loggedInUser.getUsername().equals(normalizedUsernameOfUserToChange) && model.getOldPassword() != null) {
            String oldPassword = model.getOldPassword();
            if (!authenticationService.authenticate(normalizedUsernameOfUserToChange, oldPassword)) {
                form = form.withError(
                        new ValidationError(ChangePasswordModel.OLD_PASSWORD, MessagesStrings.WRONG_OLD_PASSWORD));
            }

        } else {
            // Should never happen since we checked role ADMIN already
            form = form.withError(new ValidationError(ChangePasswordModel.ADMIN_PASSWORD,
                    MessagesStrings.NOT_ALLOWED_TO_CHANGE_PASSWORDS));
        }

        return form;
    }

}
