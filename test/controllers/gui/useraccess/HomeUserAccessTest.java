package controllers.gui.useraccess;

import javax.inject.Inject;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

import general.TestHelper;
import play.Application;
import play.ApplicationLoader;
import play.Environment;
import play.api.mvc.Call;
import play.inject.guice.GuiceApplicationBuilder;
import play.inject.guice.GuiceApplicationLoader;
import play.test.Helpers;

/**
 * Testing controller actions of Home whether they have proper access control:
 * only the right user should be allowed to do the action. For most actions only
 * the denial of access is tested here - the actual function of the action (that
 * includes positive access) is tested in the specific test class.
 * 
 * JATOS actions mostly use its @Authenticated annotation (specified in
 * AuthenticationAction).
 * 
 * @author Kristian Lange (2015 - 2017)
 */
public class HomeUserAccessTest {

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
	public void callHome() throws Exception {
		Call call = controllers.gui.routes.Home.home();
		userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
		userAccessTestHelpers.checkAccessGranted(call, Helpers.GET,
				testHelper.getAdmin());
	}

	@Test
	public void callSidebarStudyList() throws Exception {
		Call call = controllers.gui.routes.Home.sidebarStudyList();
		userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
		userAccessTestHelpers.checkAccessGranted(call, Helpers.GET,
				testHelper.getAdmin());
	}

	@Test
	public void callLog() throws Exception {
		Call call = controllers.gui.routes.Home.log(1000);
		userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
		userAccessTestHelpers.checkDeniedAccessDueToAuthorization(call,
				Helpers.GET);
		userAccessTestHelpers.checkAccessGranted(call, Helpers.GET,
				testHelper.getAdmin());
	}

}
