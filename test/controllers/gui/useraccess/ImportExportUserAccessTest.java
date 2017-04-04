package controllers.gui.useraccess;

import javax.inject.Inject;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

import general.TestHelper;
import models.common.Component;
import models.common.Study;
import models.common.User;
import play.Application;
import play.ApplicationLoader;
import play.Environment;
import play.api.mvc.Call;
import play.inject.guice.GuiceApplicationBuilder;
import play.inject.guice.GuiceApplicationLoader;
import play.test.Helpers;

/**
 * Testing controller actions of ImportExport whether they have proper access
 * control: only the right user should be allowed to do the action.
 * 
 * JATOS actions mostly use its @Authenticated annotation (specified in
 * AuthenticationAction).
 * 
 * @author Kristian Lange (2015 - 2017)
 */
public class ImportExportUserAccessTest {

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
	public void callImportStudy() throws Exception {
		Call call = controllers.gui.routes.ImportExport.importStudy();
		userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
	}

	@Test
	public void callImportStudyConfirmed() throws Exception {
		Call call = controllers.gui.routes.ImportExport.importStudyConfirmed();
		userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
	}

	@Test
	public void callExportStudy() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.ImportExport
				.exportStudy(study.getId());
		userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
		userAccessTestHelpers.checkNotTheRightUser(call, study.getId(),
				Helpers.GET);
	}

	@Test
	public void callExportComponent() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.ImportExport
				.exportComponent(study.getId(), study.getComponent(1).getId());
		userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
		userAccessTestHelpers.checkNotTheRightUser(call, study.getId(),
				Helpers.GET);
	}

	@Test
	public void callImportComponent() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.ImportExport
				.importComponent(study.getId());
		userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
		userAccessTestHelpers.checkNotTheRightUser(call, study.getId(),
				Helpers.POST);
	}

	@Test
	public void callImportComponentConfirmed() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.ImportExport
				.importComponentConfirmed(study.getId());
		userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
		userAccessTestHelpers.checkNotTheRightUser(call, study.getId(),
				Helpers.POST);
	}

	@Test
	public void callExportDataOfStudyResults() throws Exception {
		Call call = controllers.gui.routes.ImportExport
				.exportDataOfStudyResults("1");
		userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
	}

	@Test
	public void callExportDataOfAllStudyResults() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.ImportExport
				.exportDataOfAllStudyResults(study.getId());
		userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
		userAccessTestHelpers.checkNotTheRightUser(call, study.getId(),
				Helpers.GET);
	}

	@Test
	public void callExportDataOfComponentResults() throws Exception {
		Call call = controllers.gui.routes.ImportExport
				.exportDataOfComponentResults("1");
		userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
	}

	@Test
	public void callExportDataOfAllComponentResults() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		Component component = study.getFirstComponent();
		Call call = controllers.gui.routes.ImportExport
				.exportDataOfAllComponentResults(study.getId(),
						component.getId());
		userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
		userAccessTestHelpers.checkNotTheRightUser(call, study.getId(),
				Helpers.GET);
	}

	@Test
	public void callExportAllResultDataOfWorker() throws Exception {
		User admin = testHelper.getAdmin();
		Call call = controllers.gui.routes.ImportExport
				.exportAllResultDataOfWorker(admin.getWorker().getId());
		userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
	}

}
