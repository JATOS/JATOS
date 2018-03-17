package models.gui;

/**
 * Model used by the GUI to change a user password. Validation is handled in the
 * AuthenticationValidation (unlike other models where it is handled in the model
 * itself).
 *
 * @author Kristian Lange (2017)
 */
public class ChangePasswordModel {

    public static final String NEW_PASSWORD = "newPassword";
    public static final String NEW_PASSWORD_REPEAT = "newPasswordRepeat";
    public static final String OLD_PASSWORD = "oldPassword";
    public static final String ADMIN_PASSWORD = "adminPassword";

    private String newPassword;

    private String newPasswordRepeat;

    /**
     * In case an admin wants to change the password of an user the admin has to
     * send their own password too for authentication
     */
    private String adminPassword;

    /**
     * In case an user wants to change their own password we need their old
     * password for authentication.
     */
    private String oldPassword;

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

    public String getAdminPassword() {
        return adminPassword;
    }

    public void setAdminPassword(String adminPassword) {
        this.adminPassword = adminPassword;
    }

    public String getOldPassword() {
        return oldPassword;
    }

    public void setOldPassword(String oldPassword) {
        this.oldPassword = oldPassword;
    }

    @Override
    public String toString() {
        return "ChangePasswordModel [newPassword=" + newPassword
                + ", newPasswordRepeat=" + newPasswordRepeat
                + ", adminPassword=" + adminPassword + ", oldPassword="
                + oldPassword + "]";
    }

}
