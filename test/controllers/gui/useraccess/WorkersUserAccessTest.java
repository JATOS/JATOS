package controllers.gui.useraccess;

import javax.inject.Inject;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

import general.TestHelper;
import models.common.Batch;
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
 * Testing controller actions of Workers whether they have proper access
 * control: only the right user should be allowed to do the action.
 * 
 * JATOS actions mostly use its @Authenticated annotation (specified in
 * AuthenticationAction).
 * 
 * @author Kristian Lange (2015 - 2017)
 */
public class WorkersUserAccessTest {

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
	public void callRemove() throws Exception {
		User admin = testHelper.getAdmin();
		Call call = controllers.gui.routes.Workers
				.remove(admin.getWorker().getId());
		userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
		userAccessTestHelpers.checkThatCallIsForbidden(call, Helpers.DELETE,
				admin);
	}

	@Test
	public void callTableDataByStudy() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.Workers
				.tableDataByStudy(study.getId());
		userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
		userAccessTestHelpers.checkNotTheRightUser(call, study.getId(),
				Helpers.GET);
	}

	@Test
	public void callWorkerSetup() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		Batch batch = study.getDefaultBatch();
		Call call = controllers.gui.routes.Workers.workerSetup(study.getId(),
				batch.getId());
		userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
		userAccessTestHelpers.checkNotTheRightUser(call, study.getId(),
				Helpers.GET);
	}

	@Test
	public void callWorkerData() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		Batch batch = study.getDefaultBatch();
		Call call = controllers.gui.routes.Workers.workerData(study.getId(),
				batch.getId());
		userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
		userAccessTestHelpers.checkNotTheRightUser(call, study.getId(),
				Helpers.GET);
	}

}
