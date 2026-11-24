package models.gui;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import general.common.Common;
import json.common.StrictStringDeserializer;
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
 * DTO of a user profile used by the GUI + validation rules.
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

    /**
     * Deserialize this field strictly as a JSON string:
     * - accept only JSON string or null
     * - reject numeric/boolean/object/array values (no implicit coercion like 123 -> "123")
     */
    @JsonDeserialize(using = StrictStringDeserializer.class)
    @JsonProperty(access = WRITE_ONLY)
    private String password;

    private String name;

    private String email;

    private boolean active = true;

    /**
     * Optional credential update; omission leaves the user's credentials unchanged.
     */
    private GitCredentialsProperties gitCredentials;

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

    public GitCredentialsProperties getGitCredentials() {
        return gitCredentials;
    }

    public void setGitCredentials(GitCredentialsProperties gitCredentials) {
        this.gitCredentials = gitCredentials;
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
            errorList.add(new ValidationError(PASSWORD, "Password must not be empty"));
        }
        if (password != null && password.length() < Common.getUserPasswordMinLength()) {
            errorList.add(new ValidationError(PASSWORD, "Passwords must be at least " + Common.getUserPasswordMinLength() + " characters long."));
        }
        Pair<String, String> regex = Common.getUserPasswordStrengthRegex();
        if (password != null && !password.matches(regex.getRight())) {
            errorList.add(new ValidationError(PASSWORD, regex.getLeft()));
        }
        if (name == null || name.trim().isEmpty()) {
            errorList.add(new ValidationError(NAME, "Missing name"));
        }
        if (name != null && name.length() > 255) {
            errorList.add(new ValidationError(NAME, "Name too long"));
        }
        if (name != null && !Jsoup.isValid(name, Safelist.none())) {
            errorList.add(new ValidationError(NAME, "HTML is not allowed"));
        }
        if (email != null && !Jsoup.isValid(email, Safelist.none())) {
            errorList.add(new ValidationError(EMAIL, "HTML is not allowed"));
        }
        return errorList.isEmpty() ? null : errorList;
    }

    @Override
    public String toString() {
        return "ChangeUserProfileModel [name=" + name + "]";
    }

}
