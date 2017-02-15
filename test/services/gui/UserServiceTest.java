package services.gui;

import static org.fest.assertions.Assertions.assertThat;

import java.util.List;

import javax.inject.Inject;

import org.fest.assertions.Fail;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

import daos.common.UserDao;
import exceptions.gui.NotFoundException;
import general.TestHelper;
import general.common.MessagesStrings;
import models.common.User;
import play.ApplicationLoader;
import play.Environment;
import play.data.validation.ValidationError;
import play.db.jpa.JPAApi;
import play.inject.guice.GuiceApplicationBuilder;
import play.inject.guice.GuiceApplicationLoader;
import utils.common.HashUtils;

/**
 * Tests UserService
 * 
 * @author Kristian Lange
 */
public class UserServiceTest {

	private Injector injector;

	@Inject
	private TestHelper testHelper;

	@Inject
	private JPAApi jpaApi;

	@Inject
	private UserService userService;

	@Inject
	private UserDao userDao;

	@Before
	public void startApp() throws Exception {
		GuiceApplicationBuilder builder = new GuiceApplicationLoader()
				.builder(new ApplicationLoader.Context(Environment.simple()));
		injector = Guice.createInjector(builder.applicationModule());
		injector.injectMembers(this);
	}

	@After
	public void stopApp() throws Exception {
		// Clean up
		testHelper.removeAllStudies();
		testHelper.removeStudyAssetsRootDir();
	}

	@Test
	public void simpleCheck() {
		int a = 1 + 1;
		assertThat(a).isEqualTo(2);
	}

	@Test
	public void checkRetrieveUser() {
		// Check retrieval of admin user
		jpaApi.withTransaction(() -> {
			User admin = testHelper.getAdmin();
			User user = null;
			try {
				user = userService.retrieveUser("admin");
			} catch (NotFoundException e) {
				Fail.fail();
			}
			assertThat(user).isEqualTo(admin);
		});

		// Unknown user should throw NotFoundException
		jpaApi.withTransaction(() -> {
			try {
				userService.retrieveUser("bla");
				Fail.fail();
			} catch (NotFoundException e) {
				assertThat(e.getMessage())
						.isEqualTo(MessagesStrings.userNotExist("bla"));
			}
		});
	}

	@Test
	public void checkGetHashMDFive() {
		String hash = null;
		try {
			hash = HashUtils.getHashMDFive("bla");
		} catch (RuntimeException e) {
			Fail.fail();
		}
		assertThat(hash).isEqualTo("128ecf542a35ac5270a87dc740918404");
	}

	@Test
	public void checkValidateNewUser() {
		User testUser = new User("bla@bla.com", "Bla", "bla");

		jpaApi.withTransaction(() -> {
			List<ValidationError> errorList = userService
					.validateNewUser(testUser, "bla", "bla");
			assertThat(errorList).isEmpty();
		});

		jpaApi.withTransaction(() -> {
			List<ValidationError> errorList = userService
					.validateNewUser(testUser, "", "foo");
			assertThat(errorList).hasSize(2);
			assertThat(errorList.get(0).message()).isEqualTo(
					MessagesStrings.PASSWORDS_SHOULDNT_BE_EMPTY_STRINGS);
			assertThat(errorList.get(1).message())
					.isEqualTo(MessagesStrings.PASSWORDS_DONT_MATCH);
		});

		jpaApi.withTransaction(() -> {
			List<ValidationError> errorList = userService
					.validateNewUser(testUser, "bla", "foo");
			assertThat(errorList).hasSize(1);
			assertThat(errorList.get(0).message())
					.isEqualTo(MessagesStrings.PASSWORDS_DONT_MATCH);
		});

		jpaApi.withTransaction(() -> {
			User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);
			List<ValidationError> errorList = userService.validateNewUser(admin,
					"bla", "bla");
			assertThat(errorList).hasSize(1);
			assertThat(errorList.get(0).message()).isEqualTo(
					MessagesStrings.THIS_EMAIL_IS_ALREADY_REGISTERED);
		});
	}

	@Test
	public void checkValidateChangePassword() {
		jpaApi.withTransaction(() -> {
			User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);
			List<ValidationError> errorList = userService
					.validateChangePassword(admin, "bla", "bla",
							admin.getPasswordHash());
			assertThat(errorList).isEmpty();
		});

		jpaApi.withTransaction(() -> {
			User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);
			List<ValidationError> errorList = userService
					.validateChangePassword(admin, "bla", "bla",
							"wrongPasswordhash");
			assertThat(errorList).hasSize(1);
			assertThat(errorList.get(0).message())
					.isEqualTo(MessagesStrings.WRONG_OLD_PASSWORD);
		});
	}

}
