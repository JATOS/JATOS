package controllers.gui.useraccess;

import com.google.inject.Guice;
import com.google.inject.Injector;
import controllers.gui.routes;
import general.TestHelper;
import models.common.User;
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
 * Testing controller actions of Users whether they have proper access control:
 * only the right user should be allowed to do the action. For most actions only
 * the denial of access is tested here - the actual function of the action (that
 * includes positive access) is tested in the specific test class.
 * <p>
 * JATOS actions mostly use its @Authenticated annotation (specified in
 * AuthenticationAction).
 *
 * @author Kristian Lange (2015 - 2017)
 */
public class UsersUserAccessTest {

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
    public void callUserManager() {
        Call call = routes.Users.userManager();
        userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
        userAccessTestHelpers.checkDeniedAccessDueToAuthorization(call, Helpers.GET);
        userAccessTestHelpers.checkAccessGranted(call, Helpers.GET, testHelper.getAdmin());
    }

    @Test
    public void callAllUserData() {
        Call call = routes.Users.allUserData();
        userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
        userAccessTestHelpers.checkDeniedAccessDueToAuthorization(call, Helpers.GET);
        userAccessTestHelpers.checkAccessGranted(call, Helpers.GET, testHelper.getAdmin());
    }

    @Test
    public void callToggleAdmin() {
        testHelper.createAndPersistUser(TestHelper.BLA_EMAIL, "Bla", "bla");
        Call call = routes.Users.toggleAdmin(TestHelper.BLA_EMAIL, true);
        userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
        userAccessTestHelpers.checkDeniedAccessDueToAuthorization(call, Helpers.POST);
        userAccessTestHelpers.checkAccessGranted(call, Helpers.POST, testHelper.getAdmin());
        testHelper.removeUser(TestHelper.BLA_EMAIL);
    }

    @Test
    public void callProfile() {
        User someUser =
                testHelper.createAndPersistUser(TestHelper.BLA_EMAIL, "Bla", "bla");
        Call call = routes.Users.profile(someUser.getUsername());
        userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
        userAccessTestHelpers.checkThatCallLeadsToRedirect(call, Helpers.GET);
        userAccessTestHelpers.checkAccessGranted(call, Helpers.GET, someUser);
        testHelper.removeUser(TestHelper.BLA_EMAIL);
    }

    @Test
    public void callCreate() {
        Call call = routes.Users.create();
        userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
        userAccessTestHelpers.checkDeniedAccessDueToAuthorization(call, Helpers.POST);
    }

    @Test
    public void callSingleUserData() {
        User someUser = testHelper.createAndPersistUser(TestHelper.BLA_EMAIL, "Bla", "bla");
        Call call = routes.Users.singleUserData(TestHelper.BLA_EMAIL);
        userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
        userAccessTestHelpers.checkAccessGranted(call, Helpers.GET, someUser);
    }

    @Test
    public void callEdit() {
        Call call = routes.Users.edit(TestHelper.BLA_EMAIL);
        userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
    }

    @Test
    public void callChangePasswordByAdmin() {
        Call call = routes.Users.changePasswordByAdmin();
        userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
    }

    @Test
    public void callChangePasswordByUser() {
        Call call = routes.Users.changePasswordByUser();
        userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
    }

    @Test
    public void callRemove() {
        Call call = controllers.gui.routes.Users.remove(TestHelper.BLA_EMAIL);
        userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
        userAccessTestHelpers.checkDeniedAccessDueToAuthorization(call, Helpers.POST);
    }

}
