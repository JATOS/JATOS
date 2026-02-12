package models.gui;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import general.common.Common;
import general.common.MessagesStrings;
import general.gui.StrictJsonMapper;
import org.apache.commons.lang3.tuple.Pair;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import play.data.validation.Constraints;
import play.data.validation.Constraints.Validatable;
import play.data.validation.ValidationError;

import java.util.ArrayList;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY;
import static com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY;

/**
 * Model used by the GUI to change a user's profile.
 *
 * @author Kristian Lange
 */
@Constraints.Validate
public class UserProperties implements Validatable<List<ValidationError>> {

    public static final String PASSWORD = "password";
    public static final String NAME = "name";
    public static final String EMAIL = "email";

    @JsonProperty(access = READ_ONLY)
    private Long id;

    @JsonProperty(access = READ_ONLY)
    private String username;

    @JsonDeserialize(using = StrictJsonMapper.class)
    @JsonProperty(access = WRITE_ONLY)
    private String password;

    private String name;

    private String email;

    private boolean active = true;

    public void setId(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getUsername() {
        return username;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPassword() {
        return password;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isActive() {
        return active;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    @Override
    public List<ValidationError> validate() {
        List<ValidationError> errorList = new ArrayList<>();
        if (password != null && password.trim().isEmpty()) {
            errorList.add(new ValidationError(PASSWORD, MessagesStrings.PASSWORDS_SHOULDNT_BE_EMPTY_STRINGS));
        }
        if (password != null && password.length() < Common.getUserPasswordMinLength()) {
            errorList.add(new ValidationError(PASSWORD, MessagesStrings.userPasswordMinLength(Common.getUserPasswordMinLength())));
        }
        Pair<String, String> regex = Common.getUserPasswordStrengthRegex();
        if (password != null && !password.matches(regex.getRight())) {
            errorList.add(new ValidationError(PASSWORD, regex.getLeft()));
        }
        if (name == null || name.trim().isEmpty()) {
            errorList.add(new ValidationError(NAME, MessagesStrings.MISSING_NAME));
        }
        if (name != null && name.length() > 255) {
            errorList.add(new ValidationError(NAME, MessagesStrings.NAME_TOO_LONG));
        }
        if (name != null && !Jsoup.isValid(name, Safelist.none())) {
            errorList.add(new ValidationError(NAME, MessagesStrings.NO_HTML_ALLOWED));
        }
        if (email != null && !Jsoup.isValid(email, Safelist.none())) {
            errorList.add(new ValidationError(EMAIL, MessagesStrings.NO_HTML_ALLOWED));
        }
        return errorList.isEmpty() ? null : errorList;
    }

    @Override
    public String toString() {
        return "ChangeUserProfileModel [name=" + name + "]";
    }

}
