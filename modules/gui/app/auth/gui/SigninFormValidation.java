package auth.gui;

import com.google.common.base.Strings;
import daos.common.UserDao;
import general.common.Common;
import general.common.MessagesStrings;
import models.common.User;
import models.gui.ChangePasswordModel;
import models.gui.NewUserModel;
import org.apache.commons.lang3.tuple.Pair;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import play.data.Form;
import play.data.validation.ValidationError;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Service class that validates models that create, change or delete users. Usually this validation is part of the model
 * class, but since it's about (important) user authentication, and it is used by other services I put it in an extra
 * class.
 *
 * @author Kristian Lange
 */
@Singleton
public class SigninFormValidation {

    private final UserDao userDao;

    @Inject
    SigninFormValidation(UserDao userDao) {
        this.userDao = userDao;
    }

    /**
     * Validates a NewUserModel and returns a Form with errors. It also checks if the user already exists in the
     * database. Usually this is done in the model class, but since here the user DAO is needed I put it in an extra
     * class. In the NewUserModel are still some simple validations.
     */
    public Form<NewUserModel> validateNewUser(Form<NewUserModel> form) {
        String normalizedUsername = User.normalizeUsername(form.get().getUsername());
        String password = form.get().getPassword();
        String passwordRepeat = form.get().getPasswordRepeat();
        String name = form.get().getName();
        String email = form.get().getEmail();
        boolean authByDb = form.get().getAuthByDb();

        if (normalizedUsername == null || normalizedUsername.isEmpty()) {
            return form.withError(new ValidationError(NewUserModel.USERNAME, MessagesStrings.MISSING_USERNAME));
        }

        if (!normalizedUsername.matches("^[\\p{IsAlphabetic}\\p{IsDigit}-_@.+&'=~]+$")) {
            return form.withError(new ValidationError(NewUserModel.USERNAME, MessagesStrings.USERNAME_INVALID));
        }

        if (normalizedUsername.length() > 255) {
            return form.withError(new ValidationError(NewUserModel.USERNAME, MessagesStrings.USERNAME_TOO_LONG));
        }

        // Check with Jsoup for illegal HTML
        if (!Jsoup.isValid(normalizedUsername, Safelist.none())) {
            return form.withError(new ValidationError(NewUserModel.USERNAME, MessagesStrings.NO_HTML_ALLOWED));
        }

        if (name == null || name.trim().isEmpty()) {
            return form.withError(new ValidationError(NewUserModel.NAME, MessagesStrings.MISSING_NAME));
        }

        if (name.length() > 255) {
            form = form.withError(new ValidationError(NewUserModel.NAME, MessagesStrings.NAME_TOO_LONG));
        }

        // Check with Jsoup for illegal HTML
        if (!Jsoup.isValid(name, Safelist.none())) {
            form = form.withError(new ValidationError(NewUserModel.NAME, MessagesStrings.NO_HTML_ALLOWED));
        }

        // Check with Jsoup for illegal HTML
        if (!Strings.isNullOrEmpty(email) && !Jsoup.isValid(email, Safelist.none())) {
            form = form.withError(new ValidationError(NewUserModel.EMAIL, MessagesStrings.NO_HTML_ALLOWED));
        }

        // Check password only if authenticated by DB
        if (authByDb) {
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

        // Check if the user already exists in database
        User existingUser = userDao.findByUsername(normalizedUsername);
        if (existingUser != null) {
            form = form.withError(
                    new ValidationError(NewUserModel.USERNAME, MessagesStrings.THIS_USERNAME_IS_ALREADY_REGISTERED));
        }

        return form;
    }

    /**
     * Validates a ChangePasswordModel and returns the Form with errors. It can either originate in the GUI in the user
     * manager or in the user profile. Usually this would be done in the ChangePasswordModel class, but since here the
     * user DAO is needed I put it in an extra class.
     */
    public Form<ChangePasswordModel> validateChangePassword(Form<ChangePasswordModel> form) {
        ChangePasswordModel model = form.get();
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

        return form;
    }

}
