package gui.services;

import static org.fest.assertions.Assertions.assertThat;

import java.util.List;

import org.fest.assertions.Fail;
import org.junit.Test;

import exceptions.gui.ForbiddenException;
import exceptions.gui.NotFoundException;
import general.common.MessagesStrings;
import gui.AbstractTest;
import models.common.User;
import play.data.validation.ValidationError;
import utils.common.HashUtils;

/**
 * Tests UserService
 * 
 * @author Kristian Lange
 */
public class UserServiceTest extends AbstractTest {

	@Override
	public void before() throws Exception {
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
		User user = null;
		entityManager.getTransaction().begin();
		try {
			user = userService.retrieveUser("admin");
		} catch (NotFoundException e) {
			Fail.fail();
		}
		entityManager.getTransaction().commit();
		assertThat(user).isEqualTo(admin);

		entityManager.getTransaction().begin();
		try {
			userService.retrieveUser("bla");
			Fail.fail();
		} catch (NotFoundException e) {
			assertThat(e.getMessage())
					.isEqualTo(MessagesStrings.userNotExist("bla"));
		}
		entityManager.getTransaction().commit();
	}

	@Test
	public void testCheckUserLoggedIn() {
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
					MessagesStrings.userMustBeLoggedInToSeeProfile(testUser));
		}
	}

	@Test
	public void checkGetHashMDFive() {
		String hash = null;
		try {
			hash = HashUtils.getHashMDFive("bla");
		} catch (RuntimeException e) {
			Fail.fail();
		}
		assertThat(hash).isNotEmpty();
	}

	@Test
	public void checkValidateNewUser() {
		User testUser = new User("bla@bla.com", "Bla", "bla");
		List<ValidationError> errorList = userService.validateNewUser(testUser,
				"bla", "bla");
		assertThat(errorList).isEmpty();

		errorList = userService.validateNewUser(testUser, "", "foo");
		assertThat(errorList).hasSize(2);
		assertThat(errorList.get(0).message())
				.isEqualTo(MessagesStrings.PASSWORDS_SHOULDNT_BE_EMPTY_STRINGS);
		assertThat(errorList.get(1).message())
				.isEqualTo(MessagesStrings.PASSWORDS_DONT_MATCH);

		errorList = userService.validateNewUser(testUser, "bla", "foo");
		assertThat(errorList).hasSize(1);
		assertThat(errorList.get(0).message())
				.isEqualTo(MessagesStrings.PASSWORDS_DONT_MATCH);

		errorList = userService.validateNewUser(admin, "bla", "bla");
		assertThat(errorList).hasSize(1);
		assertThat(errorList.get(0).message())
				.isEqualTo(MessagesStrings.THIS_EMAIL_IS_ALREADY_REGISTERED);
	}

	@Test
	public void checkValidateChangePassword() {
		List<ValidationError> errorList = userService.validateChangePassword(
				admin, "bla", "bla", admin.getPasswordHash());
		assertThat(errorList).isEmpty();

		errorList = userService.validateChangePassword(admin, "bla", "bla",
				"wrongPasswordhash");
		assertThat(errorList).hasSize(1);
		assertThat(errorList.get(0).message())
				.isEqualTo(MessagesStrings.WRONG_OLD_PASSWORD);
	}

}
