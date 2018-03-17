package models.gui;

/**
 * Model used by the GUI to create a new user. Validation is done in AuthenticationValidation.
 * Emails are stored in lower case.
 *
 * @author Kristian Lange (2017)
 */
public class NewUserModel {

    public static final String NAME = "name";
    public static final String EMAIL = "email";
    public static final String PASSWORD = "password";
    public static final String PASSWORD_REPEAT = "passwordRepeat";
    public static final String ADMIN_ROLE = "adminRole";
    public static final String ADMIN_PASSWORD = "adminPassword";

    private String email;

    private String name;

    private String password;

    private String passwordRepeat;

    /**
     * True if the user has the Role ADMIN
     */
    private boolean adminRole = false;

    /**
     * Password from the logged-in admin user. Used for authentication.
     */
    private String adminPassword;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email.toLowerCase();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPasswordRepeat() {
        return passwordRepeat;
    }

    public void setPasswordRepeat(String passwordRepeat) {
        this.passwordRepeat = passwordRepeat;
    }

    public boolean getAdminRole() {
        return adminRole;
    }

    public void setAdminRole(boolean adminRole) {
        this.adminRole = adminRole;
    }

    public String getAdminPassword() {
        return adminPassword;
    }

    public void setAdminPassword(String adminPassword) {
        this.adminPassword = adminPassword;
    }

    @Override
    public String toString() {
        return "NewUserModel [email=" + email + ", name=" + name
                + ", adminRole=" + adminRole + "]";
    }

}
