package models.gui;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.Strings;
import general.common.Common;
import general.common.MessagesStrings;
import general.gui.StrictJsonMapper;
import models.common.User;
import org.apache.commons.lang3.tuple.Pair;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import play.data.validation.Constraints;
import play.data.validation.Constraints.Validatable;
import play.data.validation.ValidationError;

import java.util.ArrayList;
import java.util.List;

/**
 * Properties of a new user + validation rules
 *
 * @author Kristian Lange
 */
@Constraints.Validate
public class NewUserProperties implements Validatable<List<ValidationError>> {

    public static final String USERNAME = "username";
    public static final String NAME = "name";
    public static final String EMAIL = "email";
    public static final String PASSWORD = "password";

    private String username;

    private String name;

    private String email;

    /**
     * Deserialize this field strictly as a JSON string:
     * - accept only JSON string or null
     * - reject numeric/boolean/object/array values (no implicit coercion like 123 -> "123")
     */
    @JsonDeserialize(using = StrictJsonMapper.class)
    private String password;

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

    public void setAuthMethod(User.AuthMethod authMethod) {
        this.authMethod = authMethod;
    }

    public User.AuthMethod getAuthMethod() {
        return authMethod;
    }

    public boolean isAuthByDb() {
        return authMethod == User.AuthMethod.DB;
    }

    @Override
    public List<ValidationError> validate() {
        List<ValidationError> errorList = new ArrayList<>();

        if (username == null || username.isEmpty()) {
            errorList.add(new ValidationError(USERNAME, MessagesStrings.MISSING_USERNAME));
        } else {
            if (!username.matches("^[\\p{IsAlphabetic}\\p{IsDigit}-_@.+&'=~]+$")) {
                errorList.add(new ValidationError(USERNAME, MessagesStrings.USERNAME_INVALID));
            }

            if (username.length() > 255) {
                errorList.add(new ValidationError(USERNAME, MessagesStrings.USERNAME_TOO_LONG));
            }

            // Check with Jsoup for illegal HTML
            if (!Jsoup.isValid(username, Safelist.none())) {
                errorList.add(new ValidationError(USERNAME, MessagesStrings.NO_HTML_ALLOWED));
            }
        }

        if (name == null || name.trim().isEmpty()) {
            errorList.add(new ValidationError(NAME, MessagesStrings.MISSING_NAME));
        } else {
            if (name.length() > 255) {
                errorList.add(new ValidationError(NAME, MessagesStrings.NAME_TOO_LONG));
            }

            // Check with Jsoup for illegal HTML
            if (!Jsoup.isValid(name, Safelist.none())) {
                errorList.add(new ValidationError(NAME, MessagesStrings.NO_HTML_ALLOWED));
            }
        }

        // Check with Jsoup for illegal HTML
        if (!Strings.isNullOrEmpty(email) && !Jsoup.isValid(email, Safelist.none())) {
            errorList.add(new ValidationError(EMAIL, MessagesStrings.NO_HTML_ALLOWED));
        }

        // Check password only if authenticated by DB
        if (isAuthByDb()) {
            if (password == null || password.trim().isEmpty()) {
                errorList.add(new ValidationError(PASSWORD, MessagesStrings.PASSWORDS_SHOULDNT_BE_EMPTY_STRINGS));
            } else {
                // Check password length as specified in config
                if (password.length() < Common.getUserPasswordMinLength()) {
                    errorList.add(new ValidationError(PASSWORD,
                            MessagesStrings.userPasswordMinLength(Common.getUserPasswordMinLength())));
                }

                // Check password strength as specified in config
                Pair<String, String> regex = Common.getUserPasswordStrengthRegex();
                if (!password.matches(regex.getRight())) {
                    errorList.add(new ValidationError(PASSWORD, regex.getLeft()));
                }
            }
        }

        return errorList.isEmpty() ? null : errorList;
    }

    @Override
    public String toString() {
        return "NewUserModel [username=" + username + ", name=" + name + "]";
    }

}
