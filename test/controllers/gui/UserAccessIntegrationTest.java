package controllers.gui;

import static org.fest.assertions.Assertions.assertThat;
import static play.mvc.Http.Status.SEE_OTHER;
import static play.test.Helpers.route;

import java.io.IOException;

import javax.inject.Inject;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

import daos.common.StudyDao;
import daos.common.UserDao;
import exceptions.publix.ForbiddenReloadException;
import general.TestHelper;
import models.common.ComponentResult;
import models.common.Study;
import models.common.StudyResult;
import models.common.User;
import play.Application;
import play.ApplicationLoader;
import play.Environment;
import play.api.mvc.Call;
import play.db.jpa.JPAApi;
import play.inject.guice.GuiceApplicationBuilder;
import play.inject.guice.GuiceApplicationLoader;
import play.mvc.Http;
import play.mvc.Http.RequestBuilder;
import play.mvc.Result;
import play.test.Helpers;
import services.gui.StudyService;
import services.gui.UserService;
import services.publix.ResultCreator;
import services.publix.workers.JatosPublixUtils;

/**
 * Testing controller actions whether they have proper access control: only the
 * right user should be allowed to do the action
 * 
 * @author Kristian Lange (2015 - 2017)
 */
public class UserAccessIntegrationTest {

	private Injector injector;
	
	@Inject
	private static Application fakeApplication;

	@Inject
	private TestHelper testHelper;

	@Inject
	private JPAApi jpaApi;

	@Inject
	private StudyDao studyDao;

	@Inject
	private UserDao userDao;

	@Inject
	private ResultCreator resultCreator;

	@Inject
	private JatosPublixUtils jatosPublixUtils;

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
	public void callStudiesIndex() throws Exception {
		Study study = testHelper
				.createAndPersistExampleStudyForAdmin(injector);

		Call call = controllers.gui.routes.Studies.study(study.getId());
		checkDeniedAccessAndRedirectToLogin(call, Helpers.GET);
		checkNotTheRightUser(call, study.getId(), Helpers.GET);
	}

	@Test
	public void callStudiesProperties() throws Exception {
		Study study = testHelper
				.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.Studies.properties(study.getId());
		checkDeniedAccessAndRedirectToLogin(call, Helpers.GET);
		checkNotTheRightUser(call, study.getId(), Helpers.GET);
	}

	@Test
	public void callStudiesSubmitCreated() throws Exception {
		Call call = controllers.gui.routes.Studies.submitCreated();
		checkDeniedAccessAndRedirectToLogin(call, Helpers.GET);
	}

	@Test
	public void callProperties() throws Exception {
		Study study = testHelper
				.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.Studies.properties(study.getId());
		checkDeniedAccessAndRedirectToLogin(call, Helpers.GET);
		checkNotTheRightUser(call, study.getId(), Helpers.GET);
	}

	@Test
	public void callStudiesSubmitEdited() throws Exception {
		Study study = testHelper
				.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.Studies.submitEdited(study.getId());
		checkDeniedAccessAndRedirectToLogin(call, Helpers.POST);
		checkNotTheRightUser(call, study.getId(), Helpers.POST);
	}

	/**
	 * Test action Studies.toggleLock()
	 */
	@Test
	public void callStudiesSwapLock() throws Exception {
		Study study = testHelper
				.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.Studies.toggleLock(study.getId());
		checkDeniedAccessAndRedirectToLogin(call, Helpers.POST);
		checkNotTheRightUser(call, study.getId(), Helpers.POST);
	}

	@Test
	public void callStudiesRemove() throws Exception {
		Study study = testHelper
				.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.Studies.remove(study.getId());
		checkDeniedAccessAndRedirectToLogin(call, Helpers.DELETE);
		checkNotTheRightUser(call, study.getId(), Helpers.DELETE);
	}

	@Test
	public void callStudiesCloneStudy() throws Exception {
		Study study = testHelper
				.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.Studies.cloneStudy(study.getId());
		checkDeniedAccessAndRedirectToLogin(call, Helpers.GET);
		checkNotTheRightUser(call, study.getId(), Helpers.GET);
	}

	@Test
	public void callStudiesUsers() throws Exception {
		Study study = testHelper
				.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.Studies.users(study.getId());
		checkDeniedAccessAndRedirectToLogin(call, Helpers.GET);
		checkNotTheRightUser(call, study.getId(), Helpers.GET);
	}

	@Test
	public void callUsers() throws Exception {
		Study study = testHelper
				.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.Studies.users(study.getId());
		checkDeniedAccessAndRedirectToLogin(call, Helpers.GET);
		checkNotTheRightUser(call, study.getId(), Helpers.GET);
	}

	@Test
	public void callStudiesSubmitChangedUsers() throws Exception {
		Study study = testHelper
				.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.Studies
				.submitChangedUsers(study.getId());
		checkDeniedAccessAndRedirectToLogin(call, Helpers.POST);
		checkNotTheRightUser(call, study.getId(), Helpers.POST);
	}

	@Test
	public void callStudiesTableDataByStudy() throws Exception {
		Study study = testHelper
				.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.Studies
				.tableDataByStudy(study.getId());
		checkDeniedAccessAndRedirectToLogin(call, Helpers.GET);
		checkNotTheRightUser(call, study.getId(), Helpers.GET);
	}

	@Test
	public void callStudiesChangeComponentOrder() throws Exception {
		Study study = testHelper
				.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.Studies.changeComponentOrder(
				study.getId(), study.getComponentList().get(0).getId(),
				StudyService.COMPONENT_POSITION_DOWN);
		checkDeniedAccessAndRedirectToLogin(call, Helpers.POST);
		checkNotTheRightUser(call, study.getId(), Helpers.POST);
	}

	@Test
	public void callStudiesRunStudy() throws Exception {
		Study study = testHelper
				.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.Studies.runStudy(study.getId(), -1l);
		checkDeniedAccessAndRedirectToLogin(call, Helpers.GET);
		checkNotTheRightUser(call, study.getId(), Helpers.GET);
	}

	@Test
	public void callStudiesWorkers() throws Exception {
		Study study = testHelper
				.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.Studies.workers(study.getId());
		checkDeniedAccessAndRedirectToLogin(call, Helpers.GET);
		checkNotTheRightUser(call, study.getId(), Helpers.GET);
	}

	@Test
	public void callComponentsRunComponent() throws Exception {
		Study study = testHelper
				.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.Components.runComponent(
				study.getId(), study.getComponent(1).getId(), -1l);
		checkDeniedAccessAndRedirectToLogin(call, Helpers.GET);
		checkNotTheRightUser(call, study.getId(), Helpers.GET);
	}

	@Test
	public void callComponentsSubmitCreated() throws Exception {
		Study study = testHelper
				.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.Components
				.submitCreated(study.getId());
		checkDeniedAccessAndRedirectToLogin(call, Helpers.POST);
		checkNotTheRightUser(call, study.getId(), Helpers.POST);
	}

	@Test
	public void callComponentsSubmitEdited() throws Exception {
		Study study = testHelper
				.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.Components
				.submitEdited(study.getId(), study.getComponent(1).getId());
		checkDeniedAccessAndRedirectToLogin(call, Helpers.POST);
		checkNotTheRightUser(call, study.getId(), Helpers.POST);
	}

	@Test
	public void callComponentsProperties() throws IOException {
		Study study = testHelper
				.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.Components.properties(study.getId(),
				study.getComponent(1).getId());
		checkDeniedAccessAndRedirectToLogin(call, Helpers.GET);
		checkNotTheRightUser(call, study.getId(), Helpers.GET);
	}

	@Test
	public void callComponentsChangeProperty() throws Exception {
		Study study = testHelper
				.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.Components.toggleActive(
				study.getId(), study.getComponent(1).getId(), true);
		checkDeniedAccessAndRedirectToLogin(call, Helpers.POST);
		checkNotTheRightUser(call, study.getId(), Helpers.POST);
	}

	@Test
	public void callComponentsCloneComponent() throws Exception {
		Study study = testHelper
				.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.Components
				.cloneComponent(study.getId(), study.getComponent(1).getId());
		checkDeniedAccessAndRedirectToLogin(call, Helpers.GET);
		checkNotTheRightUser(call, study.getId(), Helpers.GET);
	}

	@Test
	public void callComponentsRemove() throws Exception {
		Study study = testHelper
				.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.Components.remove(study.getId(),
				study.getComponent(1).getId());
		checkDeniedAccessAndRedirectToLogin(call, Helpers.DELETE);
		checkNotTheRightUser(call, study.getId(), Helpers.DELETE);
	}

	@Test
	public void callHome() throws Exception {
		Call call = controllers.gui.routes.Home.home();
		checkDeniedAccessAndRedirectToLogin(call, Helpers.GET);
	}

	@Test
	public void callImportExportImportStudy() throws Exception {
		Call call = controllers.gui.routes.ImportExport.importStudy();
		checkDeniedAccessAndRedirectToLogin(call, Helpers.GET);
	}

	@Test
	public void callImportExportImportStudyConfirmed() throws Exception {
		Call call = controllers.gui.routes.ImportExport.importStudyConfirmed();
		checkDeniedAccessAndRedirectToLogin(call, Helpers.GET);
	}

	@Test
	public void callImportExportImportComponent() throws Exception {
		Study study = testHelper
				.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.ImportExport
				.importComponent(study.getId());
		checkDeniedAccessAndRedirectToLogin(call, Helpers.POST);
		checkNotTheRightUser(call, study.getId(), Helpers.POST);
	}

	@Test
	public void callImportExportExportComponent() throws Exception {
		Study study = testHelper
				.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.ImportExport
				.exportComponent(study.getId(), study.getComponent(1).getId());
		checkDeniedAccessAndRedirectToLogin(call, Helpers.GET);
		checkNotTheRightUser(call, study.getId(), Helpers.GET);
	}

	@Test
	public void callImportExportExportStudy() throws Exception {
		Study study = testHelper
				.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.ImportExport
				.exportStudy(study.getId());
		checkDeniedAccessAndRedirectToLogin(call, Helpers.GET);
		checkNotTheRightUser(call, study.getId(), Helpers.GET);
	}

	@Test
	public void callComponentResultsIndex() throws Exception {
		Study study = testHelper
				.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.ComponentResults
				.componentResults(study.getId(), study.getComponent(1).getId());
		checkDeniedAccessAndRedirectToLogin(call, Helpers.GET);
		checkNotTheRightUser(call, study.getId(), Helpers.GET);
	}

	@Test
	public void callComponentResultsRemove() throws Exception {
		Study study = testHelper
				.createAndPersistExampleStudyForAdmin(injector);
		StudyResult studyResult = createTwoComponentResults(study);
		ComponentResult componentResult = studyResult.getComponentResultList()
				.get(0);
		Call call = controllers.gui.routes.ComponentResults
				.remove(componentResult.getId().toString());
		checkDeniedAccessAndRedirectToLogin(call, Helpers.DELETE);

		// Logged-in user must be an user of the study to which the
		// ComponentResult belongs that is to be deleted - if not an HTTP 403 is
		// expected
		User someUser = testHelper.createAndPersistUser("bla@bla.com",
				"Bla", "bla");
		checkThatCallIsForbidden(call, Helpers.DELETE, someUser);
	}

	private StudyResult createTwoComponentResults(Study study) {
		return jpaApi.withTransaction(() -> {
			User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);
			StudyResult studyResult = resultCreator.createStudyResult(study,
					study.getDefaultBatch(), admin.getWorker());
			try {
				jatosPublixUtils.startComponent(study.getFirstComponent(),
						studyResult);
				jatosPublixUtils.startComponent(study.getFirstComponent(),
						studyResult);
			} catch (ForbiddenReloadException e) {
				e.printStackTrace();
			}
			return studyResult;
		});
	}

	@Test
	public void callComponentResultsRemoveAllOfComponent() throws IOException {
		Study study = testHelper
				.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.ComponentResults
				.removeAllOfComponent(study.getId(),
						study.getComponent(1).getId());
		checkDeniedAccessAndRedirectToLogin(call, Helpers.DELETE);
		checkNotTheRightUser(call, study.getId(), Helpers.DELETE);
	}

	@Test
	public void callComponentResultsTableDataByComponent() throws Exception {
		Study study = testHelper
				.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.ComponentResults
				.tableDataByComponent(study.getId(),
						study.getComponent(1).getId());
		checkDeniedAccessAndRedirectToLogin(call, Helpers.GET);
		checkNotTheRightUser(call, study.getId(), Helpers.GET);
	}

	@Test
	public void callComponentResultsExportResultData() throws Exception {
		Call call = controllers.gui.routes.ImportExport
				.exportDataOfComponentResults("1");
		checkDeniedAccessAndRedirectToLogin(call, Helpers.GET);
	}

	@Test
	public void callStudyResultsIndex() throws Exception {
		Study study = testHelper
				.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.StudyResults
				.studysStudyResults(study.getId());
		checkDeniedAccessAndRedirectToLogin(call, Helpers.GET);
		checkNotTheRightUser(call, study.getId(), Helpers.GET);
	}

	@Test
	public void callStudyResultsRemove() throws Exception {
		Call call = controllers.gui.routes.StudyResults.remove("1");
		checkDeniedAccessAndRedirectToLogin(call, Helpers.GET);
	}

	@Test
	public void callStudyResultsTableDataByStudy() throws Exception {
		Study study = testHelper
				.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.StudyResults
				.tableDataByStudy(study.getId());
		checkDeniedAccessAndRedirectToLogin(call, Helpers.GET);
		checkNotTheRightUser(call, study.getId(), Helpers.GET);
	}

	@Test
	public void callStudyResultsTableDataByWorker() throws Exception {
		Call call = controllers.gui.routes.StudyResults.tableDataByWorker(1l);
		checkDeniedAccessAndRedirectToLogin(call, Helpers.GET);
	}

	@Test
	public void callStudyResultsExportResultData() throws Exception {
		Call call = controllers.gui.routes.ImportExport
				.exportDataOfStudyResults("1");
		checkDeniedAccessAndRedirectToLogin(call, Helpers.GET);
	}

	@Test
	public void callUsersProfile() throws Exception {
		User someUser = testHelper.createAndPersistUser("bla@bla.com",
				"Bla", "bla");
		Call call = controllers.gui.routes.Users.profile(someUser.getEmail());
		checkDeniedAccessAndRedirectToLogin(call, Helpers.GET);
		checkThatCallLeadsToRedirect(call, Helpers.GET);
	}

	@Test
	public void callUsersSubmit() throws Exception {
		Call call = controllers.gui.routes.Users.submit();
		checkDeniedAccessAndRedirectToLogin(call, Helpers.GET);
	}

	@Test
	public void callUsersSubmitEditedProfile() throws Exception {
		User someUser = testHelper.createAndPersistUser("bla@bla.com",
				"Bla", "bla");
		Call call = controllers.gui.routes.Users
				.submitEditedProfile(someUser.getEmail());
		checkDeniedAccessAndRedirectToLogin(call, Helpers.POST);
		checkThatCallLeadsToRedirect(call, Helpers.POST);
	}

	@Test
	public void callUsersSubmitChangedPassword() throws Exception {
		User someUser = testHelper.createAndPersistUser("bla@bla.com",
				"Bla", "bla");
		Call call = controllers.gui.routes.Users
				.submitChangedPassword(someUser.getEmail());
		checkDeniedAccessAndRedirectToLogin(call, Helpers.POST);
		checkThatCallLeadsToRedirect(call, Helpers.POST);
	}

	@Test
	public void callWorkersIndex() throws Exception {
		User admin = testHelper.getAdmin();
		Call call = controllers.gui.routes.StudyResults
				.workersStudyResults(admin.getWorker().getId());
		checkDeniedAccessAndRedirectToLogin(call, Helpers.GET);
	}

	@Test
	public void callWorkersRemove() throws Exception {
		User admin = testHelper.getAdmin();
		Call call = controllers.gui.routes.Workers
				.remove(admin.getWorker().getId());
		checkDeniedAccessAndRedirectToLogin(call, Helpers.DELETE);
		checkThatCallIsForbidden(call, Helpers.DELETE, admin);
	}

	@Test
	public void callBatchesRunManager() throws Exception {
		Study study = testHelper
				.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.Batches.batchManager(study.getId());
		checkDeniedAccessAndRedirectToLogin(call, Helpers.GET);
		checkNotTheRightUser(call, study.getId(), Helpers.GET);
	}

	@Test
	public void callBatchesBatchesByStudy() throws Exception {
		Study study = testHelper
				.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.Batches
				.batchesByStudy(study.getId());
		checkDeniedAccessAndRedirectToLogin(call, Helpers.GET);
		checkNotTheRightUser(call, study.getId(), Helpers.GET);
	}

	@Test
	public void callBatchesAllowedWorkers() throws Exception {
		Study study = testHelper
				.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.Workers.workerSetup(study.getId(),
				study.getDefaultBatch().getId());
		checkDeniedAccessAndRedirectToLogin(call, Helpers.GET);
		checkNotTheRightUser(call, study.getId(), Helpers.GET);
	}

	@Test
	public void callBatchesSubmitCreated() throws Exception {
		Study study = testHelper
				.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.Batches.submitCreated(study.getId());
		checkDeniedAccessAndRedirectToLogin(call, Helpers.POST);
		checkNotTheRightUser(call, study.getId(), Helpers.POST);
	}

	@Test
	public void callBatchesBatch() throws Exception {
		Study study = testHelper
				.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.Workers.workerSetup(study.getId(),
				study.getDefaultBatch().getId());
		checkDeniedAccessAndRedirectToLogin(call, Helpers.GET);
		checkNotTheRightUser(call, study.getId(), Helpers.GET);
	}

	@Test
	public void callBatchesProperties() throws Exception {
		Study study = testHelper
				.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.Batches.properties(study.getId(),
				study.getDefaultBatch().getId());
		checkDeniedAccessAndRedirectToLogin(call, Helpers.GET);
		checkNotTheRightUser(call, study.getId(), Helpers.GET);
	}

	@Test
	public void callBatchesSubmitEditedProperties() throws Exception {
		Study study = testHelper
				.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.Batches.submitEditedProperties(
				study.getId(), study.getDefaultBatch().getId());
		checkDeniedAccessAndRedirectToLogin(call, Helpers.POST);
		checkNotTheRightUser(call, study.getId(), Helpers.POST);
	}

	@Test
	public void callBatchesChangeProperty() throws Exception {
		Study study = testHelper
				.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.Batches.toggleActive(study.getId(),
				study.getDefaultBatch().getId(), true);
		checkDeniedAccessAndRedirectToLogin(call, Helpers.POST);
		checkNotTheRightUser(call, study.getId(), Helpers.POST);
	}

	@Test
	public void callBatchesRemove() throws Exception {
		Study study = testHelper
				.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.Batches.remove(study.getId(),
				study.getDefaultBatch().getId());
		checkDeniedAccessAndRedirectToLogin(call, Helpers.DELETE);
		checkNotTheRightUser(call, study.getId(), Helpers.DELETE);
	}

	@Test
	public void callBatchesCreatePersonalSingleRun() throws Exception {
		Study study = testHelper
				.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.Batches
				.createPersonalSingleRun(study.getId(), -1l);
		checkDeniedAccessAndRedirectToLogin(call, Helpers.POST);
		checkNotTheRightUser(call, study.getId(), Helpers.POST);
	}

	@Test
	public void callBatchesCreatePersonalMultipleRun() throws Exception {
		Study study = testHelper
				.createAndPersistExampleStudyForAdmin(injector);
		Call call = controllers.gui.routes.Batches
				.createPersonalMultipleRun(study.getId(), -1l);
		checkDeniedAccessAndRedirectToLogin(call, Helpers.POST);
		checkNotTheRightUser(call, study.getId(), Helpers.POST);
	}

	/**
	 * Call action without a user in the session: nobody is logged in. This
	 * should trigger a redirect to the log-in page and a HTTP status 303 (See
	 * Other).
	 */
	private void checkDeniedAccessAndRedirectToLogin(Call call, String method) {
		Result result = route(call);
		assertThat(result.status()).isEqualTo(SEE_OTHER);
		assertThat(result.redirectLocation().get()).contains("/jatos/login");
	}

	/**
	 * Removes the admin user (!) from the users who have permission in this
	 * study. Then calls the action with the admin user logged-in (in the
	 * session). This should trigger a JatosGuiException with a 403 HTTP code.
	 */
	private void checkNotTheRightUser(Call call, Long studyId, String method) {
		User admin = testHelper.getAdmin();
		// We have to get the study from the database again because it's
		// detached (Hibernate)
		jpaApi.withTransaction(() -> {
			Study study = studyDao.findById(studyId);
			study.removeUser(admin);
		});
		checkThatCallIsForbidden(call, method, admin);
	}

	/**
	 * Check that the given Call and HTTP method does lead to an
	 * JatosGuiException with a HTTP status code 303 (See Other). Uses the given
	 * user in the session for authentication.
	 */
	private void checkThatCallLeadsToRedirect(Call call, String method) {
		User admin = testHelper.getAdmin();
		RequestBuilder request = new RequestBuilder().method(method)
				.session(Users.SESSION_EMAIL, admin.getEmail()).uri(call.url());

		testHelper.assertJatosGuiException(request,
				Http.Status.SEE_OTHER);
	}

	/**
	 * Check that the given Call and HTTP method does lead to an
	 * JatosGuiException with a HTTP status code 403. Uses the given user in the
	 * session for authentication.
	 */
	private void checkThatCallIsForbidden(Call call, String method, User user) {
		RequestBuilder request = new RequestBuilder().method(method)
				.session(Users.SESSION_EMAIL, user.getEmail()).uri(call.url());
		testHelper.assertJatosGuiException(request,
				Http.Status.FORBIDDEN);
	}

}
