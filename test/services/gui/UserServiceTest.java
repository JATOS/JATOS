package services.gui;

import static org.fest.assertions.Assertions.assertThat;

import javax.inject.Inject;

import org.fest.assertions.Fail;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import com.google.inject.Guice;
import com.google.inject.Injector;

import exceptions.gui.NotFoundException;
import general.TestHelper;
import general.common.MessagesStrings;
import models.common.User;
import play.ApplicationLoader;
import play.Environment;
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
				userService.retrieveUser("user-not-exist");
				Fail.fail();
			} catch (NotFoundException e) {
				assertThat(e.getMessage()).isEqualTo(
						MessagesStrings.userNotExist("user-not-exist"));
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

}
