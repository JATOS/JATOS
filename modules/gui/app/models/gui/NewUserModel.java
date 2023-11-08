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
    public static final String EMAIL = "email";
    public static final String PASSWORD = "password";
    public static final String PASSWORD_REPEAT = "passwordRepeat";
    public static final String AUTH_BY_LDAP = "authByLdap";

    private String username;

    private String name;

    private String email;

    private String password;

    private String passwordRepeat;

    private User.AuthMethod authMethod = User.AuthMethod.DB;

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

    public String getEmail() {
        return this.email;
    }

    public void setEmail(String email) {
        this.email = email;
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

    public void setAuthMethod(User.AuthMethod authMethod) {
        this.authMethod = authMethod;
    }

    public User.AuthMethod getAuthMethod() {
        return authMethod;
    }

    public boolean getAuthByLdap() {
        return authMethod == User.AuthMethod.LDAP;
    }

    public void setAuthByLdap(boolean authByLdap) {
        this.authMethod = authByLdap ? User.AuthMethod.LDAP : User.AuthMethod.DB;
    }

    public boolean getAuthByDb() {
        return authMethod == User.AuthMethod.DB;
    }

    @Override
    public String toString() {
        return "NewUserModel [username=" + username + ", name=" + name + "]";
    }

}
