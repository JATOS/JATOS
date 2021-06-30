package models.gui;

import general.common.MessagesStrings;
import org.jsoup.Jsoup;
import org.jsoup.safety.Whitelist;
import play.data.validation.Constraints;
import play.data.validation.ValidationError;

import java.util.ArrayList;
import java.util.List;

/**
 * Model used by the GUI to change a user's profile. So far it only contains the
 * user's name.
 * 
 * @author Kristian Lange (2017)
 */
@Constraints.Validate
public class ChangeUserProfileModel implements Constraints.Validatable<List<ValidationError>> {

	public static final String NAME = "name";

	/**
	 * User's name
	 */
	private String name;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	@Override
	public List<ValidationError> validate() {
		List<ValidationError> errorList = new ArrayList<>();
		if (name == null || name.trim().isEmpty()) {
			errorList.add(
					new ValidationError(NAME, MessagesStrings.MISSING_NAME));
		}
		if (name != null && name.length() > 255) {
			errorList.add(
					new ValidationError(NAME, MessagesStrings.NAME_TOO_LONG));
		}
		if (name != null && !Jsoup.isValid(name, Whitelist.none())) {
			errorList.add(
					new ValidationError(NAME, MessagesStrings.NO_HTML_ALLOWED));
		}
		return errorList.isEmpty() ? null : errorList;
	}

	@Override
	public String toString() {
		return "ChangeUserProfileModel [name=" + name + "]";
	}

}
