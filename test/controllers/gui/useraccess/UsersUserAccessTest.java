package controllers.gui.useraccess;

import javax.inject.Inject;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

import general.TestHelper;
import models.common.User;
import play.Application;
import play.ApplicationLoader;
import play.Environment;
import play.api.mvc.Call;
import play.inject.guice.GuiceApplicationBuilder;
import play.inject.guice.GuiceApplicationLoader;
import play.test.Helpers;

/**
 * Testing controller actions of Users whether they have proper access control:
 * only the right user should be allowed to do the action.
 * 
 * JATOS actions mostly use its @Authenticated annotation (specified in
 * AuthenticationAction).
 * 
 * @author Kristian Lange (2015 - 2017)
 */
public class UsersUserAccessTest {

	private Injector injector;

	@Inject
	private static Application fakeApplication;

	@Inject
	private TestHelper testHelper;

	@Inject
	private UserAccessTestHelpers userAccessTestHelpers;

	@Before
	public void startApp() throws Exception {
		fakeApplication = Helpers.fakeApplication();

		GuiceApplicationBuilder builder = new GuiceApplicationLoader()
				.builder(new ApplicationLoader.Context(Environment.simple()));
		injector = Guice.createInjector(builder.applicationModule());
		injector.injectMembers(this);

		Helpers.start(fakeApplication);
	}

	@After
	public void stopApp() throws Exception {
		// Clean up
		testHelper.removeAllStudies();

		Helpers.stop(fakeApplication);
		testHelper.removeStudyAssetsRootDir();
	}

	@Test
	public void callUserManager() throws Exception {
		Call call = controllers.gui.routes.Users.userManager();
		userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
		userAccessTestHelpers.checkDeniedAccessDueToAuthorization(call,
				Helpers.GET);
	}

	@Test
	public void callAllUserData() throws Exception {
		Call call = controllers.gui.routes.Users.allUserData();
		userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
		userAccessTestHelpers.checkDeniedAccessDueToAuthorization(call,
				Helpers.GET);
	}

	@Test
	public void callToggleAdmin() throws Exception {
		Call call = controllers.gui.routes.Users.toggleAdmin("some@email.org",
				true);
		userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
		userAccessTestHelpers.checkDeniedAccessDueToAuthorization(call,
				Helpers.GET);
	}

	@Test
	public void callProfile() throws Exception {
		User someUser = testHelper.createAndPersistUser("bla@bla.com", "Bla",
				"bla");
		Call call = controllers.gui.routes.Users.profile(someUser.getEmail());
		userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
		userAccessTestHelpers.checkThatCallLeadsToRedirect(call, Helpers.GET);
		testHelper.removeUser("bla@bla.com");
	}

	@Test
	public void callSubmitCreated() throws Exception {
		Call call = controllers.gui.routes.Users.submitCreated();
		userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
		userAccessTestHelpers.checkDeniedAccessDueToAuthorization(call,
				Helpers.POST);
	}

	@Test
	public void callSingleUserData() throws Exception {
		Call call = controllers.gui.routes.Users.singleUserData("bla@bla.com");
		userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
	}

	@Test
	public void callSubmitEditedProfile() throws Exception {
		Call call = controllers.gui.routes.Users
				.submitEditedProfile("bla@bla.com");
		userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
	}

	@Test
	public void callSubmitChangedPassword() throws Exception {
		Call call = controllers.gui.routes.Users
				.submitChangedPassword("bla@bla.com");
		userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
	}
	
	@Test
	public void callRemove() throws Exception {
		Call call = controllers.gui.routes.Users.remove("bla@bla.com");
		userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
		userAccessTestHelpers.checkDeniedAccessDueToAuthorization(call,
				Helpers.POST);
	}

}
