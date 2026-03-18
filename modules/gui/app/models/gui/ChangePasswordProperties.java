package models.gui;

import general.common.Common;
import general.common.MessagesStrings;
import models.common.User;
import org.apache.commons.lang3.tuple.Pair;
import play.data.validation.Constraints;
import play.data.validation.Constraints.Validatable;
import play.data.validation.ValidationError;

import java.util.ArrayList;
import java.util.List;

/**
 * Properties for changing a user's password + validation rules
 *
 * @author Kristian Lange
 */
@Constraints.Validate
public class ChangePasswordProperties implements Validatable<List<ValidationError>> {

    public static final String USERNAME = "username";
    public static final String NEW_PASSWORD = "newPassword";
    public static final String NEW_PASSWORD_REPEAT = "newPasswordRepeat";
    public static final String OLD_PASSWORD = "oldPassword";

    private String username;

    private String newPassword;

    private String newPasswordRepeat;

    /**
     * In case a user wants to change their own password, we need their old password for authentication.
     */
    private String oldPassword;

    public void setUsername(String username) {
        this.username = User.normalizeUsername(username);
    }

    public String getUsername() {
        return User.normalizeUsername(this.username);
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }

    public String getNewPasswordRepeat() {
        return newPasswordRepeat;
    }

    public void setNewPasswordRepeat(String newPasswordRepeat) {
        this.newPasswordRepeat = newPasswordRepeat;
    }

    public String getOldPassword() {
        return oldPassword;
    }

    public void setOldPassword(String oldPassword) {
        this.oldPassword = oldPassword;
    }

    @Override
    public List<ValidationError> validate() {
        List<ValidationError> errorList = new ArrayList<>();

        // Checks new password not empty
        if (newPassword == null || newPassword.trim().isEmpty()) {
            errorList.add(new ValidationError(NEW_PASSWORD, MessagesStrings.PASSWORDS_SHOULDNT_BE_EMPTY_STRINGS));
        }
        // Check new password repeat not empty
        if (newPasswordRepeat == null || newPasswordRepeat.trim().isEmpty()) {
            errorList.add(new ValidationError(NEW_PASSWORD_REPEAT, MessagesStrings.PASSWORDS_SHOULDNT_BE_EMPTY_STRINGS));
        }

        // Check both passwords match
        if (newPassword != null && newPasswordRepeat != null && !newPassword.equals(newPasswordRepeat)) {
            errorList.add(new ValidationError(NEW_PASSWORD, MessagesStrings.PASSWORDS_DONT_MATCH));
        }

        // Check length as specified in conf
        if (newPassword != null && newPassword.length() < Common.getUserPasswordMinLength()) {
            errorList.add(new ValidationError(NEW_PASSWORD,
                    MessagesStrings.userPasswordMinLength(Common.getUserPasswordMinLength())));
        }

        // Check strength as specified in conf
        Pair<String, String> regex = Common.getUserPasswordStrengthRegex();
        if (newPassword != null && !newPassword.matches(regex.getRight())) {
            errorList.add(new ValidationError(NEW_PASSWORD, regex.getLeft()));
        }

        return errorList.isEmpty() ? null : errorList;
    }

    @Override
    public String toString() {
        return "ChangePasswordModel [username=" + username + "]";
    }

}
