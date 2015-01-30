package services;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import models.UserDao;
import models.UserModel;
import play.data.validation.ValidationError;

import com.google.inject.Inject;

/**
 * Service class mostly for Users controller. Handles everything around
 * UserModel.
 * 
 * @author Kristian Lange
 */
public class UserService {

	public static final String ADMIN_EMAIL = "admin";
	public static final String ADMIN_PASSWORD = "admin";
	public static final String ADMIN_NAME = "Admin";

	private final UserDao userDao;
	private final PersistanceUtils persistanceUtils;

	@Inject
	public UserService(UserDao userDao, PersistanceUtils persistanceUtils) {
		this.userDao = userDao;
		this.persistanceUtils = persistanceUtils;
	}

	public UserModel createAdmin() throws UnsupportedEncodingException,
			NoSuchAlgorithmException {
		String passwordHash = getHashMDFive(ADMIN_PASSWORD);
		UserModel adminUser = new UserModel(ADMIN_EMAIL, ADMIN_NAME,
				passwordHash);
		persistanceUtils.addUser(adminUser);
		return adminUser;
	}

	public static String getHashMDFive(String str)
			throws UnsupportedEncodingException, NoSuchAlgorithmException {
		byte[] strBytes = str.getBytes("UTF-8");
		MessageDigest md = MessageDigest.getInstance("MD5");
		byte[] hashByte = md.digest(strBytes);

		// Convert the byte to hex format
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < hashByte.length; i++) {
			sb.append(Integer.toString((hashByte[i] & 0xff) + 0x100, 16)
					.substring(1));
		}
		return sb.toString();
	}

	public List<ValidationError> validateNewUser(UserModel newUser,
			String password, String passwordRepeat)
			throws UnsupportedEncodingException, NoSuchAlgorithmException {
		List<ValidationError> errorList = new ArrayList<ValidationError>();

		// Check if user with this email already exists.
		if (userDao.findByEmail(newUser.getEmail()) != null) {
			errorList.add(new ValidationError(UserModel.EMAIL,
					ErrorMessages.THIS_EMAIL_IS_ALREADY_REGISTERED));
		}

		checkPasswords(password, passwordRepeat, errorList);
		return errorList;
	}

	public List<ValidationError> validateChangePassword(UserModel user,
			String password, String passwordRepeat, String oldPasswordHash)
			throws UnsupportedEncodingException, NoSuchAlgorithmException {
		List<ValidationError> errorList = new ArrayList<ValidationError>();

		// Authenticate
		if (UserModel.authenticate(user.getEmail(), oldPasswordHash) == null) {
			errorList.add(new ValidationError(UserModel.OLD_PASSWORD,
					ErrorMessages.WRONG_OLD_PASSWORD));
		}

		checkPasswords(password, passwordRepeat, errorList);
		return errorList;
	}

	public void checkPasswords(String password, String passwordRepeat,
			List<ValidationError> errorList)
			throws UnsupportedEncodingException, NoSuchAlgorithmException {

		// Check for non empty passwords
		if (password.trim().isEmpty() || passwordRepeat.trim().isEmpty()) {
			errorList.add(new ValidationError(UserModel.PASSWORD,
					ErrorMessages.PASSWORDS_SHOULDNT_BE_EMPTY_STRINGS));
		}

		// Check that both passwords are the same
		String passwordHash = getHashMDFive(password);
		String passwordHashRepeat = getHashMDFive(passwordRepeat);
		if (!passwordHash.equals(passwordHashRepeat)) {
			errorList.add(new ValidationError(UserModel.PASSWORD,
					ErrorMessages.PASSWORDS_DONT_MATCH));
		}
	}

}
