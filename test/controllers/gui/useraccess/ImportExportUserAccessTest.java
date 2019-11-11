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
 * Testing controller actions of ImportExport whether they have proper access
 * control: only the right user should be allowed to do the action. For most
 * actions only the denial of access is tested here - the actual function of the
 * action (that includes positive access) is tested in the specific test class.
 * <p>
 * JATOS actions mostly use its @Authenticated annotation (specified in
 * AuthenticationAction).
 *
 * @author Kristian Lange (2015 - 2017)
 */
public class ImportExportUserAccessTest {

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
    public void callImportStudy() {
        Call call = routes.ImportExport.importStudy();
        userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
    }

    @Test
    public void callImportStudyConfirmed() {
        Call call = routes.ImportExport.importStudyConfirmed();
        userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
    }

    @Test
    public void callExportStudy() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Call call = routes.ImportExport.exportStudy(study.getId());
        userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
        userAccessTestHelpers.checkNotTheRightUserForStudy(call, study.getId(), Helpers.GET);
    }

    @Test
    public void callExportComponent() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Call call =
                routes.ImportExport.exportComponent(study.getId(), study.getComponent(1).getId());
        userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
        userAccessTestHelpers.checkNotTheRightUserForStudy(call, study.getId(), Helpers.GET);
    }

    @Test
    public void callImportComponent() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Call call = routes.ImportExport.importComponent(study.getId());
        userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
        userAccessTestHelpers.checkNotTheRightUserForStudy(call, study.getId(), Helpers.POST);
    }

    @Test
    public void callImportComponentConfirmed() {
        Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
        Call call = routes.ImportExport.importComponentConfirmed(study.getId());
        userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
        userAccessTestHelpers.checkNotTheRightUserForStudy(call, study.getId(), Helpers.POST);
    }

    @Test
    public void callExportDataOfStudyResults() {
        Call call = routes.ImportExport.exportDataOfStudyResults();
        userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
    }

    @Test
    public void callExportDataOfComponentResults() {
        Call call = routes.ImportExport.exportDataOfComponentResults();
        userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
    }

}
