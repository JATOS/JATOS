package models.gui;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.Strings;
import general.common.Common;
import json.common.StrictStringDeserializer;
import models.common.User;
import models.common.User.AuthMethod;
import models.common.User.Role;
import org.apache.commons.lang3.tuple.Pair;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import play.data.validation.Constraints;
import play.data.validation.Constraints.Validatable;
import play.data.validation.ValidationError;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static models.common.User.Role.USER;
import static models.common.User.Role.VIEWER;

/**
 * DTO of new user properties + validation rules
 */
@Constraints.Validate
public class NewUserProperties implements Validatable<List<ValidationError>> {

    public static final String USERNAME = "username";
    public static final String NAME = "name";
    public static final String EMAIL = "email";
    public static final String PASSWORD = "password";
    public static final String ROLE = "role";

    private String username;

    private String name;

    private String email;

    /**
     * Deserialize this field strictly as a JSON string: 1) accept only JSON string or null, 2) reject
     * numeric/boolean/object/array values (no implicit coercion like 123 -> "123")
     */
    @JsonDeserialize(using = StrictStringDeserializer.class)
    private String password;

    private AuthMethod authMethod = AuthMethod.DB;

    private Role role = USER;

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

    public void setAuthMethod(AuthMethod authMethod) {
        this.authMethod = authMethod;
    }

    public AuthMethod getAuthMethod() {
        return authMethod;
    }

    public boolean isAuthByDb() {
        return authMethod == AuthMethod.DB;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    @Override
    public List<ValidationError> validate() {
        List<ValidationError> errorList = new ArrayList<>();

        if (username == null || username.isEmpty()) {
            errorList.add(new ValidationError(USERNAME, "Missing username"));
        } else {
            if (!username.matches("^[\\p{IsAlphabetic}\\p{IsDigit}-_@.+&'=~]+$")) {
                errorList.add(new ValidationError(USERNAME, "Use only letters, numbers, and these characters: -_@.+&'=~"));
            }

            if (username.length() > 255) {
                errorList.add(new ValidationError(USERNAME, "Username too long"));
            }

            // Check with Jsoup for illegal HTML
            if (!Jsoup.isValid(username, Safelist.none())) {
                errorList.add(new ValidationError(USERNAME, "HTML is not allowed"));
            }
        }

        if (name == null || name.trim().isEmpty()) {
            errorList.add(new ValidationError(NAME, "Missing name"));
        } else {
            if (name.length() > 255) {
                errorList.add(new ValidationError(NAME, "Name too long"));
            }

            // Check with Jsoup for illegal HTML
            if (!Jsoup.isValid(name, Safelist.none())) {
                errorList.add(new ValidationError(NAME, "HTML is not allowed"));
            }
        }

        // Check with Jsoup for illegal HTML
        if (!Strings.isNullOrEmpty(email) && !Jsoup.isValid(email, Safelist.none())) {
            errorList.add(new ValidationError(EMAIL, "HTML is not allowed"));
        }

        // Check password only if authenticated by DB
        if (isAuthByDb()) {
            if (password == null || password.trim().isEmpty()) {
                errorList.add(new ValidationError(PASSWORD, "Password must not be empty"));
            } else {
                // Check password length as specified in config
                if (password.length() < Common.getUserPasswordMinLength()) {
                    errorList.add(new ValidationError(PASSWORD,
                            "Passwords must be at least " + Common.getUserPasswordMinLength() + " characters long."));
                }

                // Check password strength as specified in config
                Pair<String, String> regex = Common.getUserPasswordStrengthRegex();
                if (!password.matches(regex.getRight())) {
                    errorList.add(new ValidationError(PASSWORD, regex.getLeft()));
                }
            }
        }

        if (role == null || !Arrays.asList(VIEWER, USER).contains(role)) {
            errorList.add(new ValidationError(ROLE, "Invalid role"));
        }

        return errorList.isEmpty() ? null : errorList;
    }

    @Override
    public String toString() {
        return "NewUserModel [username=" + username + ", name=" + name + "]";
    }

}
