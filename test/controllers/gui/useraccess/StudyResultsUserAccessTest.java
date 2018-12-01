package controllers.gui.useraccess;

import com.google.inject.Guice;
import com.google.inject.Injector;
import controllers.gui.routes;
import general.TestHelper;
import models.common.Batch;
import models.common.Study;
import models.common.User;
import models.common.workers.JatosWorker;
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
 * Testing controller actions of StudyResults whether they have proper access
 * control: only the right user should be allowed to do the action. For most
 * actions only the denial of access is tested here - the actual function of the
 * action (that includes positive access) is tested in the specific test class.
 * <p>
 * JATOS actions mostly use its @Authenticated annotation (specified in
 * AuthenticationAction).
 *
 * @author Kristian Lange (2015 - 2017)
 */
public class StudyResultsUserAccessTest {

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
        testHelper.removeAllStudyLogs();
    }

    @Test
    public void callStudysStudyResults() throws Exception {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Call call = routes.StudyResults.studysStudyResults(study.getId());
        userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
        userAccessTestHelpers.checkNotTheRightUserForStudy(call, study.getId(), Helpers.GET);
        userAccessTestHelpers.checkAccessGranted(call, Helpers.GET, testHelper.getAdmin());
    }

    @Test
    public void callBatchesStudyResults() throws Exception {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Batch batch = study.getDefaultBatch();
        Call call = routes.StudyResults.batchesStudyResults(
                study.getId(), batch.getId(), JatosWorker.WORKER_TYPE);
        userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
        userAccessTestHelpers.checkNotTheRightUserForStudy(call, study.getId(), Helpers.GET);
        userAccessTestHelpers.checkAccessGranted(call, Helpers.GET, testHelper.getAdmin());
    }

    @Test
    public void callWorkersStudyResults() throws Exception {
        User admin = testHelper.getAdmin();
        Call call = routes.StudyResults.workersStudyResults(admin.getWorker().getId());
        userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
        userAccessTestHelpers.checkAccessGranted(call, Helpers.GET, testHelper.getAdmin());
    }

    @Test
    public void callRemove() throws Exception {
        Call call = routes.StudyResults.remove("1");
        userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
    }

    @Test
    public void callRemoveAllOfStudy() throws Exception {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Call call = routes.StudyResults.removeAllOfStudy(study.getId());
        userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
        userAccessTestHelpers.checkNotTheRightUserForStudy(call, study.getId(), Helpers.DELETE);
    }

    @Test
    public void callRemoveAllOfWorker() throws Exception {
        User admin = testHelper.getAdmin();
        Call call = routes.StudyResults.removeAllOfWorker(admin.getWorker().getId());
        userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
    }

    @Test
    public void callTableDataByStudy() throws Exception {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Call call = routes.StudyResults.tableDataByStudy(study.getId());
        userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
        userAccessTestHelpers.checkNotTheRightUserForStudy(call, study.getId(), Helpers.GET);
        userAccessTestHelpers.checkAccessGranted(call, Helpers.GET, testHelper.getAdmin());
    }

    @Test
    public void callTableDataByBatch() throws Exception {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Batch batch = study.getDefaultBatch();
        Call call = routes.StudyResults
                .tableDataByBatch(study.getId(), batch.getId(), JatosWorker.WORKER_TYPE);
        userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
        userAccessTestHelpers.checkNotTheRightUserForStudy(call, study.getId(), Helpers.GET);
        userAccessTestHelpers.checkAccessGranted(call, Helpers.GET, testHelper.getAdmin());
    }

    @Test
    public void callTableDataByWorker() throws Exception {
        Call call = routes.StudyResults.tableDataByWorker(1l);
        userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
        userAccessTestHelpers.checkAccessGranted(call, Helpers.GET, testHelper.getAdmin());
    }

}
