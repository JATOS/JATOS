package gui.services;

import static org.fest.assertions.Assertions.assertThat;
import exceptions.gui.ForbiddenException;
import exceptions.gui.NotFoundException;
import general.common.MessagesStrings;
import gui.AbstractTest;
import gui.GuiTestGlobal;

import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import models.common.User;

import org.fest.assertions.Fail;
import org.junit.Test;

import play.data.validation.ValidationError;
import play.db.jpa.JPA;
import services.gui.UserService;
import utils.common.HashUtils;

/**
 * Tests UserService
 * 
 * @author Kristian Lange
 */
public class UserServiceTest extends AbstractTest {

	private UserService userService;

	@Override
	public void before() throws Exception {
		userService = GuiTestGlobal.INJECTOR.getInstance(UserService.class);
	}

	@Override
	public void after() throws Exception {
	}

	@Test
	public void simpleCheck() {
		int a = 1 + 1;
		assertThat(a).isEqualTo(2);
	}

	@Test
	public void checkRetrieveUser() {
		JPA.withTransaction(() -> {
			User user = null;
			try {
				user = userService.retrieveUser("admin");
			} catch (NotFoundException e) {
				Fail.fail();
			}
			assertThat(user).isEqualTo(admin);
		});

		JPA.withTransaction(() -> {
			try {
				userService.retrieveUser("bla");
				Fail.fail();
			} catch (NotFoundException e) {
				assertThat(e.getMessage()).isEqualTo(
						MessagesStrings.userNotExist("bla"));
			}
		});
	}

	@Test
	public void testCheckUserLoggedIn() throws UnsupportedEncodingException,
			NoSuchAlgorithmException {
		JPA.withTransaction(() -> {
			try {
				userService.checkUserLoggedIn(admin, admin);
			} catch (ForbiddenException e) {
				Fail.fail();
			}

			User testUser = createAndPersistUser("bla@bla.com", "Bla", "bla");
			try {
				userService.checkUserLoggedIn(testUser, admin);
				Fail.fail();
			} catch (ForbiddenException e) {
				assertThat(e.getMessage()).isEqualTo(
						MessagesStrings
								.userMustBeLoggedInToSeeProfile(testUser));
			}
		});
	}

	@Test
	public void checkGetHashMDFive() {
		String hash = null;
		try {
			hash = HashUtils.getHashMDFive("bla");
		} catch (UnsupportedEncodingException | NoSuchAlgorithmException e) {
			Fail.fail();
		}
		assertThat(hash).isNotEmpty();
	}

	@Test
	public void checkValidateNewUser() throws UnsupportedEncodingException,
			NoSuchAlgorithmException {
		JPA.withTransaction(() -> {
			User testUser = new User("bla@bla.com", "Bla", "bla");
			List<ValidationError> errorList = userService.validateNewUser(
					testUser, "bla", "bla");
			assertThat(errorList).isEmpty();

			errorList = userService.validateNewUser(testUser, "", "foo");
			assertThat(errorList).hasSize(2);
			assertThat(errorList.get(0).message()).isEqualTo(
					MessagesStrings.PASSWORDS_SHOULDNT_BE_EMPTY_STRINGS);
			assertThat(errorList.get(1).message()).isEqualTo(
					MessagesStrings.PASSWORDS_DONT_MATCH);

			errorList = userService.validateNewUser(testUser, "bla", "foo");
			assertThat(errorList).hasSize(1);
			assertThat(errorList.get(0).message()).isEqualTo(
					MessagesStrings.PASSWORDS_DONT_MATCH);

			errorList = userService.validateNewUser(admin, "bla", "bla");
			assertThat(errorList).hasSize(1);
			assertThat(errorList.get(0).message()).isEqualTo(
					MessagesStrings.THIS_EMAIL_IS_ALREADY_REGISTERED);
		});
	}

	@Test
	public void checkValidateChangePassword()
			throws UnsupportedEncodingException, NoSuchAlgorithmException {
		JPA.withTransaction(() -> {
			List<ValidationError> errorList = userService
					.validateChangePassword(admin, "bla", "bla",
							admin.getPasswordHash());
			assertThat(errorList).isEmpty();

			errorList = userService.validateChangePassword(admin, "bla", "bla",
					"wrongPasswordhash");
			assertThat(errorList).hasSize(1);
			assertThat(errorList.get(0).message()).isEqualTo(
					MessagesStrings.WRONG_OLD_PASSWORD);
		});
	}

}
