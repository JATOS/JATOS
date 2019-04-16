package controllers.gui.useraccess;

import com.google.inject.Guice;
import com.google.inject.Injector;
import controllers.gui.routes;
import general.TestHelper;
import models.common.Study;
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
 * Testing controller actions of Studies whether they have proper access
 * control: only the right user should be allowed to do the action. For most
 * actions only the denial of access is tested here - the actual function of the
 * action (that includes positive access) is tested in the specific test class.
 * <p>
 * JATOS actions mostly use its @Authenticated annotation (specified in
 * AuthenticationAction).
 *
 * @author Kristian Lange (2015 - 2017)
 */
public class StudiesUserAccessTest {

    private Injector injector;

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
        testHelper.removeAllStudyLogs();
    }

    @Test
    public void callStudy() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

        Call call = routes.Studies.study(study.getId());
        userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
        userAccessTestHelpers.checkNotTheRightUserForStudy(call, study.getId(), Helpers.GET);
        userAccessTestHelpers.checkAccessGranted(call, Helpers.GET, testHelper.getAdmin());
    }

    @Test
    public void callSubmitCreated() {
        Call call = routes.Studies.submitCreated();
        userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
    }

    @Test
    public void callProperties() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Call call = routes.Studies.properties(study.getId());
        userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
        userAccessTestHelpers.checkNotTheRightUserForStudy(call, study.getId(), Helpers.GET);
        userAccessTestHelpers.checkAccessGranted(call, Helpers.GET, testHelper.getAdmin());
    }

    @Test
    public void callSubmitEdited() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Call call = routes.Studies.submitEdited(study.getId());
        userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
        userAccessTestHelpers.checkNotTheRightUserForStudy(call, study.getId(), Helpers.POST);
    }

    /**
     * Test action Studies.toggleLock()
     */
    @Test
    public void callToggleLock() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Call call = routes.Studies.toggleLock(study.getId());
        userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
        userAccessTestHelpers.checkNotTheRightUserForStudy(call, study.getId(), Helpers.POST);
    }

    @Test
    public void callRemove() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Call call = routes.Studies.remove(study.getId());
        userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
        userAccessTestHelpers.checkNotTheRightUserForStudy(call, study.getId(), Helpers.DELETE);
    }

    @Test
    public void callCloneStudy() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Call call = routes.Studies.cloneStudy(study.getId());
        userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
        userAccessTestHelpers.checkNotTheRightUserForStudy(call, study.getId(), Helpers.GET);
    }

    @Test
    public void callMemberUsers() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Call call = routes.Studies.memberUsers(study.getId());
        userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
        userAccessTestHelpers.checkNotTheRightUserForStudy(call, study.getId(), Helpers.GET);
        userAccessTestHelpers.checkAccessGranted(call, Helpers.GET, testHelper.getAdmin());
    }

    @Test
    public void callToggleMemberUser() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Call call = routes.Studies.toggleMemberUser(study.getId(), "email", true);
        userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
        userAccessTestHelpers.checkNotTheRightUserForStudy(call, study.getId(), Helpers.POST);
    }

    @Test
    public void callAddAllMemberUsers() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Call call = routes.Studies.addAllMemberUsers(study.getId());
        userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
        userAccessTestHelpers.checkNotTheRightUserForStudy(call, study.getId(), Helpers.POST);
    }

    @Test
    public void callRemoveAllMemberUsers() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Call call = routes.Studies.removeAllMemberUsers(study.getId());
        userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
        userAccessTestHelpers.checkNotTheRightUserForStudy(call, study.getId(), Helpers.DELETE);
    }

    @Test
    public void callTableDataByStudy() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Call call = routes.Studies
                .tableDataByStudy(study.getId());
        userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
        userAccessTestHelpers.checkNotTheRightUserForStudy(call, study.getId(), Helpers.GET);
        userAccessTestHelpers.checkAccessGranted(call, Helpers.GET, testHelper.getAdmin());
    }

    @Test
    public void callChangeComponentOrder() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Call call = routes.Studies.changeComponentOrder(
                study.getId(), study.getComponentList().get(0).getId(), "1");
        userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
        userAccessTestHelpers.checkNotTheRightUserForStudy(call, study.getId(), Helpers.POST);
    }

    @Test
    public void callRunStudy() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Call call = routes.Studies.runStudy(study.getId(), -1L);
        userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
        userAccessTestHelpers.checkNotTheRightUserForStudy(call, study.getId(), Helpers.GET);
    }

    @Test
    public void callStudyLog() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Call call = routes.Studies.studyLog(study.getId(), 100, false);
        userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
        userAccessTestHelpers.checkNotTheRightUserForStudy(call, study.getId(), Helpers.GET);
        userAccessTestHelpers.checkAccessGranted(call, Helpers.GET, testHelper.getAdmin());
    }

    @Test
    public void callStudiesAllWorkers() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Call call = routes.Studies.allWorkers(study.getId());
        userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
        userAccessTestHelpers.checkNotTheRightUserForStudy(call, study.getId(), Helpers.GET);
        userAccessTestHelpers.checkAccessGranted(call, Helpers.GET, testHelper.getAdmin());
    }

}
