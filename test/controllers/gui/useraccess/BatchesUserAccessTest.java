package controllers.gui.useraccess;

import javax.inject.Inject;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

import general.TestHelper;
import models.common.Study;
import models.common.workers.JatosWorker;
import play.Application;
import play.ApplicationLoader;
import play.Environment;
import play.api.mvc.Call;
import play.inject.guice.GuiceApplicationBuilder;
import play.inject.guice.GuiceApplicationLoader;
import play.test.Helpers;

/**
 * Testing controller actions of Batches whether they have proper access
 * control: only the right user should be allowed to do the action.
 * 
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
	}

	@Test
	public void callBatchesRunManager() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.Batches.batchManager(study.getId());
		userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
		userAccessTestHelpers.checkNotTheRightUser(call, study.getId(),
				Helpers.GET);
	}

	@Test
	public void callBatchesBatchesByStudy() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.Batches
				.batchesByStudy(study.getId());
		userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
		userAccessTestHelpers.checkNotTheRightUser(call, study.getId(),
				Helpers.GET);
	}

	@Test
	public void callBatchesSubmitCreated() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.Batches.submitCreated(study.getId());
		userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
		userAccessTestHelpers.checkNotTheRightUser(call, study.getId(),
				Helpers.POST);
	}

	@Test
	public void callBatchesProperties() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.Batches.properties(study.getId(),
				study.getDefaultBatch().getId());
		userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
		userAccessTestHelpers.checkNotTheRightUser(call, study.getId(),
				Helpers.GET);
	}

	@Test
	public void callBatchesSubmitEditedProperties() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.Batches.submitEditedProperties(
				study.getId(), study.getDefaultBatch().getId());
		userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
		userAccessTestHelpers.checkNotTheRightUser(call, study.getId(),
				Helpers.POST);
	}

	@Test
	public void callBatchesToggleActive() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.Batches.toggleActive(study.getId(),
				study.getDefaultBatch().getId(), true);
		userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
		userAccessTestHelpers.checkNotTheRightUser(call, study.getId(),
				Helpers.POST);
	}

	@Test
	public void callBatchesToggleWorkerType() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.Batches.toggleAllowedWorkerType(
				study.getId(), study.getDefaultBatch().getId(),
				JatosWorker.WORKER_TYPE, true);
		userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
		userAccessTestHelpers.checkNotTheRightUser(call, study.getId(),
				Helpers.POST);
	}

	@Test
	public void callBatchesRemove() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.Batches.remove(study.getId(),
				study.getDefaultBatch().getId());
		userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
		userAccessTestHelpers.checkNotTheRightUser(call, study.getId(),
				Helpers.DELETE);
	}

	@Test
	public void callBatchesCreatePersonalSingleRun() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.Batches
				.createPersonalSingleRun(study.getId(), -1l);
		userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
		userAccessTestHelpers.checkNotTheRightUser(call, study.getId(),
				Helpers.POST);
	}

	@Test
	public void callBatchesCreatePersonalMultipleRun() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.Batches
				.createPersonalMultipleRun(study.getId(), -1l);
		userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
		userAccessTestHelpers.checkNotTheRightUser(call, study.getId(),
				Helpers.POST);
	}

}
