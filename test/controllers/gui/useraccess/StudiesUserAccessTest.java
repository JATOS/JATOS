package controllers.gui.useraccess;

import javax.inject.Inject;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

import general.TestHelper;
import models.common.Study;
import play.Application;
import play.ApplicationLoader;
import play.Environment;
import play.api.mvc.Call;
import play.inject.guice.GuiceApplicationBuilder;
import play.inject.guice.GuiceApplicationLoader;
import play.test.Helpers;
import services.gui.StudyService;

/**
 * Testing controller actions of Studies whether they have proper access
 * control: only the right user should be allowed to do the action. For most
 * actions only the denial of access is tested here - the actual function of the
 * action (that includes positive access) is tested in the specific test class.
 * 
 * JATOS actions mostly use its @Authenticated annotation (specified in
 * AuthenticationAction).
 * 
 * @author Kristian Lange (2015 - 2017)
 */
public class StudiesUserAccessTest {

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
	public void callStudy() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

		Call call = controllers.gui.routes.Studies.study(study.getId());
		userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
		userAccessTestHelpers.checkNotTheRightUserForStudy(call, study.getId(),
				Helpers.GET);
		userAccessTestHelpers.checkAccessGranted(call, Helpers.GET,
				testHelper.getAdmin());
	}

	@Test
	public void callSubmitCreated() throws Exception {
		Call call = controllers.gui.routes.Studies.submitCreated();
		userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
	}

	@Test
	public void callProperties() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.Studies.properties(study.getId());
		userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
		userAccessTestHelpers.checkNotTheRightUserForStudy(call, study.getId(),
				Helpers.GET);
		userAccessTestHelpers.checkAccessGranted(call, Helpers.GET,
				testHelper.getAdmin());
	}

	@Test
	public void callSubmitEdited() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.Studies.submitEdited(study.getId());
		userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
		userAccessTestHelpers.checkNotTheRightUserForStudy(call, study.getId(),
				Helpers.POST);
	}

	/**
	 * Test action Studies.toggleLock()
	 */
	@Test
	public void callToggleLock() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.Studies.toggleLock(study.getId());
		userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
		userAccessTestHelpers.checkNotTheRightUserForStudy(call, study.getId(),
				Helpers.POST);
	}

	@Test
	public void callRemove() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.Studies.remove(study.getId());
		userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
		userAccessTestHelpers.checkNotTheRightUserForStudy(call, study.getId(),
				Helpers.DELETE);
	}

	@Test
	public void callCloneStudy() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.Studies.cloneStudy(study.getId());
		userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
		userAccessTestHelpers.checkNotTheRightUserForStudy(call, study.getId(),
				Helpers.GET);
	}

	@Test
	public void callMemberUsers() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.Studies.memberUsers(study.getId());
		userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
		userAccessTestHelpers.checkNotTheRightUserForStudy(call, study.getId(),
				Helpers.GET);
		userAccessTestHelpers.checkAccessGranted(call, Helpers.GET,
				testHelper.getAdmin());
	}

	@Test
	public void callToggleMemberUser() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.Studies
				.toggleMemberUser(study.getId(), "email", true);
		userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
		userAccessTestHelpers.checkNotTheRightUserForStudy(call, study.getId(),
				Helpers.POST);
	}

	@Test
	public void callTableDataByStudy() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.Studies
				.tableDataByStudy(study.getId());
		userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
		userAccessTestHelpers.checkNotTheRightUserForStudy(call, study.getId(),
				Helpers.GET);
		userAccessTestHelpers.checkAccessGranted(call, Helpers.GET,
				testHelper.getAdmin());
	}

	@Test
	public void callChangeComponentOrder() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.Studies.changeComponentOrder(
				study.getId(), study.getComponentList().get(0).getId(), "1");
		userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
		userAccessTestHelpers.checkNotTheRightUserForStudy(call, study.getId(),
				Helpers.POST);
	}

	@Test
	public void callRunStudy() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.Studies.runStudy(study.getId(), -1l);
		userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
		userAccessTestHelpers.checkNotTheRightUserForStudy(call, study.getId(),
				Helpers.GET);
	}

	@Test
	public void callWorkers() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.Studies.workers(study.getId());
		userAccessTestHelpers.checkDeniedAccessAndRedirectToLogin(call);
		userAccessTestHelpers.checkNotTheRightUserForStudy(call, study.getId(),
				Helpers.GET);
		userAccessTestHelpers.checkAccessGranted(call, Helpers.GET,
				testHelper.getAdmin());
	}

}
