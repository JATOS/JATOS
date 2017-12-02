package models.gui;

import java.util.ArrayList;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.safety.Whitelist;

import general.common.MessagesStrings;
import play.data.validation.ValidationError;

/**
 * Model used by the GUI to create a new user. Validation is done in this class
 * in the validate() method and in the AuthenticationService. Emails are stored in lower case.
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

	public List<ValidationError> validate() {
		List<ValidationError> errorList = new ArrayList<>();
		if (email == null || email.trim().isEmpty()) {
			errorList.add(
					new ValidationError(EMAIL, MessagesStrings.MISSING_EMAIL));
		}
		if (email != null && email.length() > 255) {
			errorList.add(
					new ValidationError(EMAIL, MessagesStrings.EMAIL_TOO_LONG));
		}
		if (email != null && !Jsoup.isValid(email, Whitelist.none())) {
			errorList.add(new ValidationError(EMAIL,
					MessagesStrings.NO_HTML_ALLOWED));
		}
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
		if (password == null || password.trim().isEmpty()) {
			errorList.add(new ValidationError(PASSWORD,
					MessagesStrings.PASSWORDS_SHOULDNT_BE_EMPTY_STRINGS));
		}
		if (passwordRepeat == null || passwordRepeat.trim().isEmpty()) {
			errorList.add(new ValidationError(PASSWORD_REPEAT,
					MessagesStrings.PASSWORDS_SHOULDNT_BE_EMPTY_STRINGS));
		}
		return errorList.isEmpty() ? null : errorList;
	}

	@Override
	public String toString() {
		return "NewUserModel [email=" + email + ", name=" + name
				+ ", adminRole=" + adminRole + "]";
	}

}
