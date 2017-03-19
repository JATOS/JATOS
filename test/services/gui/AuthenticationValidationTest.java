package services.gui;

import static org.fest.assertions.Assertions.assertThat;

import java.util.List;

import javax.inject.Inject;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

import controllers.gui.Authentication;
import general.TestHelper;
import general.common.MessagesStrings;
import general.common.RequestScope;
import models.common.User;
import models.gui.ChangePasswordModel;
import models.gui.NewUserModel;
import play.ApplicationLoader;
import play.Environment;
import play.data.validation.ValidationError;
import play.db.jpa.JPAApi;
import play.inject.guice.GuiceApplicationBuilder;
import play.inject.guice.GuiceApplicationLoader;

/**
 * Tests UserService
 * 
 * @author Kristian Lange
 */
public class AuthenticationValidationTest {

	private Injector injector;

	@Inject
	private TestHelper testHelper;

	@Inject
	private JPAApi jpaApi;

	@Inject
	private AuthenticationValidation authenticationValidation;

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

	/**
	 * Test AuthenticationService.validateNewUser(): successful validation
	 */
	@Test
	public void checkValidateNewUser() {
		testHelper.mockContext();

		NewUserModel newUserModel = createDummyNewUserModel();

		jpaApi.withTransaction(() -> {
			List<ValidationError> errorList = authenticationValidation
					.validateNewUser(newUserModel, UserService.ADMIN_EMAIL);
			assertThat(errorList).isEmpty();
		});
	}

	/**
	 * Test AuthenticationService.validateNewUser(): users passwords are
	 * different
	 */
	@Test
	public void checkValidateNewUserPasswordsNotEqual() {
		testHelper.mockContext();

		NewUserModel newUserModel = createDummyNewUserModel();
		newUserModel.setPasswordRepeat("different");

		jpaApi.withTransaction(() -> {
			List<ValidationError> errorList = authenticationValidation
					.validateNewUser(newUserModel, UserService.ADMIN_EMAIL);
			assertThat(errorList).isNotEmpty();
			assertThat(errorList.get(0).message())
					.isEqualTo(MessagesStrings.PASSWORDS_DONT_MATCH);
		});
	}

	/**
	 * Test AuthenticationService.validateNewUser(): user exists already
	 */
	@Test
	public void checkValidateNewUserUserExistsAlready() {
		testHelper.mockContext();

		NewUserModel newUserModel = createDummyNewUserModel();
		newUserModel.setEmail("admin");

		jpaApi.withTransaction(() -> {
			List<ValidationError> errorList = authenticationValidation
					.validateNewUser(newUserModel, UserService.ADMIN_EMAIL);
			assertThat(errorList).hasSize(1);
			assertThat(errorList.get(0).message()).isEqualTo(
					MessagesStrings.THIS_EMAIL_IS_ALREADY_REGISTERED);
		});
	}

	/**
	 * Test AuthenticationService.validateNewUser(): wrong admin password
	 */
	@Test
	public void checkValidateNewUserWrongAdminPassword() {
		testHelper.mockContext();

		NewUserModel newUserModel = createDummyNewUserModel();
		newUserModel.setAdminPassword("wrongPw");

		jpaApi.withTransaction(() -> {
			List<ValidationError> errorList = authenticationValidation
					.validateNewUser(newUserModel, UserService.ADMIN_EMAIL);
			assertThat(errorList).hasSize(1);
			assertThat(errorList.get(0).message())
					.isEqualTo(MessagesStrings.WRONG_PASSWORD);
		});
	}

	/**
	 * AuthenticationService.validateChangePassword(): change password of an
	 * user via user manager and an admin user must be logged-in
	 */
	@Test
	public void checkValidateChangePasswordViaAdmin() {
		testHelper.mockContext();

		testHelper.createAndPersistUser("tester.test@test.com", "Test Tester",
				"password");
		User admin = testHelper.getAdmin();
		RequestScope.put(Authentication.LOGGED_IN_USER, admin);

		ChangePasswordModel model = new ChangePasswordModel();
		model.setAdminPassword(UserService.ADMIN_PASSWORD);
		model.setNewPassword("abc123");
		model.setNewPasswordRepeat("abc123");

		jpaApi.withTransaction(() -> {
			List<ValidationError> errorList = authenticationValidation
					.validateChangePassword("tester.test@test.com", model);
			assertThat(errorList).isEmpty();
		});
	}

	/**
	 * AuthenticationService.validateChangePassword(): different passwords
	 */
	@Test
	public void checkValidateChangePasswordViaAdminNotAdmin() {
		testHelper.mockContext();

		testHelper.createAndPersistUser("tester.test@test.com", "Test Tester",
				"password");
		User admin = testHelper.getAdmin();
		RequestScope.put(Authentication.LOGGED_IN_USER, admin);

		ChangePasswordModel model = new ChangePasswordModel();
		model.setAdminPassword(UserService.ADMIN_PASSWORD);
		model.setNewPassword("abc123");
		model.setNewPasswordRepeat("different");

		jpaApi.withTransaction(() -> {
			List<ValidationError> errorList = authenticationValidation
					.validateChangePassword("tester.test@test.com", model);
			assertThat(errorList).isNotEmpty();
			assertThat(errorList.get(0).message())
					.isEqualTo(MessagesStrings.PASSWORDS_DONT_MATCH);
		});
	}

	/**
	 * AuthenticationService.validateChangePassword(): different passwords
	 */
	@Test
	public void checkValidateChangePasswordViaLoggedInUser() {
		testHelper.mockContext();

		User loggedInUser = testHelper.createAndPersistUser(
				"tester.test@test.com", "Test Tester", "password");
		RequestScope.put(Authentication.LOGGED_IN_USER, loggedInUser);

		ChangePasswordModel model = new ChangePasswordModel();
		model.setOldPassword("password");
		model.setNewPassword("abc123");
		model.setNewPasswordRepeat("abc123");

		jpaApi.withTransaction(() -> {
			List<ValidationError> errorList = authenticationValidation
					.validateChangePassword("tester.test@test.com", model);
			assertThat(errorList).isEmpty();
		});
	}

	private NewUserModel createDummyNewUserModel() {
		NewUserModel newUserModel = new NewUserModel();
		newUserModel.setEmail("george@bla.com");
		newUserModel.setName("Georg Lange");
		newUserModel.setPassword("123abc");
		newUserModel.setPasswordRepeat("123abc");
		newUserModel.setAdminRole(true);
		newUserModel.setAdminPassword("admin");
		return newUserModel;
	}

}
