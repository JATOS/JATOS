package models.gui;

import models.common.User;

/**
 * Model used by the GUI to create a new user. Validation is done in AuthenticationValidation.
 *
 * @author Kristian Lange
 */
public class NewUserModel {

    public static final String USERNAME = "username";
    public static final String NAME = "name";
    public static final String PASSWORD = "password";
    public static final String PASSWORD_REPEAT = "passwordRepeat";
    public static final String ADMIN_ROLE = "adminRole";
    public static final String AUTH_BY_LDAP = "authByLdap";
    public static final String ADMIN_PASSWORD = "adminPassword";

    private String username;

    private String name;

    private String password;

    private String passwordRepeat;

    /**
     * True if the user has the Role ADMIN
     */
    private boolean adminRole = false;

    /**
     * If true LDAP authentication is used for this user
     */
    private boolean authByLdap = false;

    /**
     * Password from the logged-in admin user. Used for authentication.
     */
    private String adminPassword;

    public String getUsername() {
        return User.normalizeUsername(username);
    }

    public void setUsername(String username) {
        this.username = User.normalizeUsername(username);
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

    public boolean getAuthByLdap() {
        return authByLdap;
    }

    public void setAuthByLdap(boolean authByLdap) {
        this.authByLdap = authByLdap;
    }

    public String getAdminPassword() {
        return adminPassword;
    }

    public void setAdminPassword(String adminPassword) {
        this.adminPassword = adminPassword;
    }

    @Override
    public String toString() {
        return "NewUserModel [username=" + username + ", name=" + name + ", adminRole=" + adminRole + "]";
    }

}
