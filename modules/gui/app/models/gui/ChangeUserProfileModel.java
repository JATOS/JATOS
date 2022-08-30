package models.gui;

import general.common.MessagesStrings;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import play.data.validation.Constraints;
import play.data.validation.ValidationError;

import java.util.ArrayList;
import java.util.List;

/**
 * Model used by the GUI to change a user's profile.
 *
 * @author Kristian Lange
 */
@Constraints.Validate
public class ChangeUserProfileModel implements Constraints.Validatable<List<ValidationError>> {

    public static final String NAME = "name";
    public static final String EMAIL = "email";

    private String name;

    private String email;

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
