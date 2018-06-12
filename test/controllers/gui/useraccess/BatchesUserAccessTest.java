package controllers.gui.useraccess;

import com.google.inject.Guice;
import com.google.inject.Injector;
import general.TestHelper;
import models.common.Batch;
import models.common.Study;
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
 * Testing controller actions of Batches whether they have proper access
 * control: only the right user should be allowed to do the action. For most
 * actions only the denial of access is tested here - the actual function of the
 * action (that includes positive access) is tested in the specific test class.
 * <p>
 * JATOS actions mostly use its @Authenticated annotation (specified in
 * AuthenticationAction).
 *
 * @author Kristian Lange (2015 - 2017)
 */
public class BatchesUserAccessTest {

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
    public void callBatchesWorkerAndBatchManager() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Call call = controllers.gui.routes.Batches.workerAndBatchManager(study.getId());
        userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
        userAccessTestHelpers.checkNotTheRightUserForStudy(call, study.getId(), Helpers.GET);
        userAccessTestHelpers.checkAccessGranted(call, Helpers.GET, testHelper.getAdmin());
    }

    @Test
    public void callBatchesBatchesById() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Batch batch = study.getDefaultBatch();
        Call call = controllers.gui.routes.Batches.batchById(study.getId(), batch.getId());
        userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
        userAccessTestHelpers.checkNotTheRightUserForStudy(call, study.getId(), Helpers.GET);
        userAccessTestHelpers.checkAccessGranted(call, Helpers.GET, testHelper.getAdmin());
    }

    @Test
    public void callBatchesBatchesByStudy() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Call call = controllers.gui.routes.Batches.batchesByStudy(study.getId());
        userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
        userAccessTestHelpers.checkNotTheRightUserForStudy(call, study.getId(), Helpers.GET);
        userAccessTestHelpers.checkAccessGranted(call, Helpers.GET, testHelper.getAdmin());
    }

    @Test
    public void callBatchesSubmitCreated() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Call call = controllers.gui.routes.Batches.submitCreated(study.getId());
        userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
        userAccessTestHelpers.checkNotTheRightUserForStudy(call, study.getId(), Helpers.POST);
    }

    @Test
    public void callBatchesBatchSessionData() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Call call = controllers.gui.routes.Batches
                .batchSessionData(study.getId(), study.getDefaultBatch().getId());
        userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
        userAccessTestHelpers.checkNotTheRightUserForStudy(call, study.getId(), Helpers.GET);
        userAccessTestHelpers.checkAccessGranted(call, Helpers.GET, testHelper.getAdmin());
    }

    @Test
    public void callBatchesSubmitEditedBatchSessionData() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Call call = controllers.gui.routes.Batches
                .submitEditedBatchSessionData(study.getId(), study.getDefaultBatch().getId());
        userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
        userAccessTestHelpers.checkNotTheRightUserForStudy(call, study.getId(), Helpers.GET);
        userAccessTestHelpers.checkAccessGranted(call, Helpers.GET, testHelper.getAdmin());
    }

    @Test
    public void callBatchesProperties() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Call call = controllers.gui.routes.Batches
                .properties(study.getId(), study.getDefaultBatch().getId());
        userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
        userAccessTestHelpers.checkNotTheRightUserForStudy(call, study.getId(), Helpers.GET);
        userAccessTestHelpers.checkAccessGranted(call, Helpers.GET, testHelper.getAdmin());
    }

    @Test
    public void callBatchesSubmitEditedProperties() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Call call = controllers.gui.routes.Batches.submitEditedProperties(
                study.getId(), study.getDefaultBatch().getId());
        userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
        userAccessTestHelpers.checkNotTheRightUserForStudy(call, study.getId(), Helpers.POST);
    }

    @Test
    public void callBatchesToggleActive() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Call call = controllers.gui.routes.Batches
                .toggleActive(study.getId(), study.getDefaultBatch().getId(), true);
        userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
        userAccessTestHelpers.checkNotTheRightUserForStudy(call, study.getId(), Helpers.POST);
    }

    @Test
    public void callBatchesToggleWorkerType() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Call call = controllers.gui.routes.Batches.toggleAllowedWorkerType(
                study.getId(), study.getDefaultBatch().getId(), JatosWorker.WORKER_TYPE, true);
        userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
        userAccessTestHelpers.checkNotTheRightUserForStudy(call, study.getId(), Helpers.POST);
    }

    @Test
    public void callBatchesRemove() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Call call = controllers.gui.routes.Batches
                .remove(study.getId(), study.getDefaultBatch().getId());
        userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
        userAccessTestHelpers.checkNotTheRightUserForStudy(call, study.getId(), Helpers.DELETE);
    }

    @Test
    public void callBatchesCreatePersonalSingleRun() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Call call = controllers.gui.routes.Batches.createPersonalSingleRun(study.getId(), -1L);
        userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
        userAccessTestHelpers.checkNotTheRightUserForStudy(call, study.getId(), Helpers.POST);
    }

    @Test
    public void callBatchesCreatePersonalMultipleRun() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Call call = controllers.gui.routes.Batches.createPersonalMultipleRun(study.getId(), -1L);
        userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
        userAccessTestHelpers.checkNotTheRightUserForStudy(call, study.getId(), Helpers.POST);
    }

    @Test
    public void callBatchesWorkersTableDataByStudy() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Call call = controllers.gui.routes.Batches.workersTableDataByStudy(study.getId());
        userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
        userAccessTestHelpers.checkNotTheRightUserForStudy(call, study.getId(), Helpers.GET);
        userAccessTestHelpers.checkAccessGranted(call, Helpers.GET, testHelper.getAdmin());
    }

    @Test
    public void callBatchesAllWorkersTableDataByStudy() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Call call = controllers.gui.routes.Batches.allWorkersTableDataByStudy(study.getId());
        userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
        userAccessTestHelpers.checkNotTheRightUserForStudy(call, study.getId(), Helpers.GET);
        userAccessTestHelpers.checkAccessGranted(call, Helpers.GET, testHelper.getAdmin());
    }

    @Test
    public void workerBatchesWorkerSetupData() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Batch batch = study.getDefaultBatch();
        Call call = controllers.gui.routes.Batches.workerSetupData(study.getId(), batch.getId());
        userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
        userAccessTestHelpers.checkNotTheRightUserForStudy(call, study.getId(), Helpers.GET);
        userAccessTestHelpers.checkAccessGranted(call, Helpers.GET, testHelper.getAdmin());
    }

}
