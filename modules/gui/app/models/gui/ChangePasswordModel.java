package models.gui;

import models.common.User;

/**
 * Model used by the GUI to change a user password. Validation is handled in the
 * AuthenticationValidation (unlike other models where it is handled in the model
 * itself).
 *
 * @author Kristian Lange
 */
public class ChangePasswordModel {

    public static final String USERNAME = "username";
    public static final String NEW_PASSWORD = "newPassword";
    public static final String NEW_PASSWORD_REPEAT = "newPasswordRepeat";
    public static final String OLD_PASSWORD = "oldPassword";

    private String username;

    private String newPassword;

    private String newPasswordRepeat;

    /**
     * In case an user wants to change their own password we need their old
     * password for authentication.
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
    public String toString() {
        return "ChangePasswordModel [username=" + username + "]";
    }

}
