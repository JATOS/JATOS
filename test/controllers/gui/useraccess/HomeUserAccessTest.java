package controllers.gui.useraccess;

import com.google.inject.Guice;
import com.google.inject.Injector;
import controllers.gui.routes;
import general.TestHelper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import play.Application;
import play.ApplicationLoader;
import play.Environment;
import play.api.mvc.Call;
import play.inject.guice.GuiceApplicationBuilder;
import play.inject.guice.GuiceApplicationLoader;
import play.test.Helpers;

import javax.inject.Inject;

/**
 * Testing controller actions of Home whether they have proper access control:
 * only the right user should be allowed to do the action. For most actions only
 * the denial of access is tested here - the actual function of the action (that
 * includes positive access) is tested in the specific test class.
 * <p>
 * JATOS actions mostly use its @Authenticated annotation (specified in
 * AuthenticationAction).
 *
 * @author Kristian Lange (2015 - 2017)
 */
public class HomeUserAccessTest {

    @Inject
    private Application fakeApplication;

    @Inject
    private TestHelper testHelper;

    @Inject
    private UserAccessTestHelpers userAccessTestHelpers;

    @Before
    public void startApp() throws Exception {
        fakeApplication = Helpers.fakeApplication();

        GuiceApplicationBuilder builder = new GuiceApplicationLoader()
                .builder(new ApplicationLoader.Context(Environment.simple()));
        Injector injector = Guice.createInjector(builder.applicationModule());
        injector.injectMembers(this);

        Helpers.start(fakeApplication);
    }

    @After
    public void stopApp() throws Exception {
        // Clean up
        testHelper.removeAllStudies();

        Helpers.stop(fakeApplication);
        testHelper.removeStudyAssetsRootDir();
        testHelper.removeAllStudyLogs();
    }

    @Test
    public void callHome() {
        Call call = routes.Home.home();
        userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
        userAccessTestHelpers.checkAccessGranted(call, Helpers.GET, testHelper.getAdmin());
    }

    @Test
    public void callSidebarStudyList() {
        Call call = routes.Home.sidebarStudyList();
        userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
        userAccessTestHelpers.checkAccessGranted(call, Helpers.GET, testHelper.getAdmin());
    }

    @Test
    public void callLog() {
        Call call = routes.Home.log(1000);
        userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
        userAccessTestHelpers.checkDeniedAccessDueToAuthorization(call, Helpers.GET);
        userAccessTestHelpers.checkAccessGranted(call, Helpers.GET, testHelper.getAdmin());
    }

}
