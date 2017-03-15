package controllers.gui;

import static org.fest.assertions.Assertions.assertThat;
import static play.test.Helpers.route;

import java.io.IOException;
import java.io.UncheckedIOException;

import javax.inject.Inject;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.google.inject.Guice;
import com.google.inject.Injector;

import daos.common.StudyDao;
import daos.common.UserDao;
import exceptions.publix.ForbiddenReloadException;
import general.TestHelper;
import models.common.Study;
import models.common.StudyResult;
import models.common.User;
import models.common.workers.JatosWorker;
import play.Application;
import play.ApplicationLoader;
import play.Environment;
import play.db.jpa.JPAApi;
import play.inject.guice.GuiceApplicationBuilder;
import play.inject.guice.GuiceApplicationLoader;
import play.mvc.Call;
import play.mvc.Http;
import play.mvc.Http.RequestBuilder;
import play.mvc.Result;
import play.test.Helpers;
import services.gui.StudyService;
import services.gui.UserService;
import services.publix.ResultCreator;
import services.publix.workers.JatosPublixUtils;

/**
 * A study can be locked. Then it shouldn't be possible to change its
 * properties, or its component's properties, or its batch's properties.
 * 
 * @author Kristian Lange
 */
public class LockedStudyControllerTest {

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
	private StudyService studyService;

	@Inject
	private ResultCreator resultCreator;

	@Inject
	private JatosPublixUtils jatosPublixUtils;

	@Rule
	public ExpectedException thrown = ExpectedException.none();

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

	/**
	 * Checks that the given Call and method cause a JatosGuiException with the
	 * HTTP status 403.
	 */
	private void checkForbiddenBecauseLocked(Call call, String method) {
		User admin = testHelper.getAdmin();
		RequestBuilder request = new RequestBuilder().method(method)
				.session(Authentication.SESSION_USER_EMAIL, admin.getEmail())
				.uri(call.url());
		testHelper.assertJatosGuiException(request, Http.Status.FORBIDDEN);
	}

	private void lockStudy(Study study) {
		jpaApi.withTransaction(() -> {
			study.setLocked(true);
			studyDao.update(study);
		});
	}

	/**
	 * Check that Batches.submitCreated() doesn't work if study is locked
	 */
	@Test
	public void callBatchesSubmitCreated() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		lockStudy(study);
		Call call = controllers.gui.routes.Batches.submitCreated(study.getId());
		checkForbiddenBecauseLocked(call, Helpers.POST);
	}

	/**
	 * Check that Batches.submitEditedProperties() doesn't work if study is
	 * locked
	 */
	@Test
	public void callBatchesSubmitEditedProperties() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		lockStudy(study);
		Call call = controllers.gui.routes.Batches.submitEditedProperties(
				study.getId(), study.getDefaultBatch().getId());
		checkForbiddenBecauseLocked(call, Helpers.POST);
	}

	/**
	 * Check that Batches.toggleActive() doesn't work if study is locked
	 */
	@Test
	public void callBatchesToggleActive() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		lockStudy(study);
		Call call = controllers.gui.routes.Batches.toggleActive(study.getId(),
				study.getDefaultBatch().getId(), true);
		checkForbiddenBecauseLocked(call, Helpers.POST);
	}

	/**
	 * Check that Batches.toggleAllowedWorkerType() doesn't work if study is
	 * locked
	 */
	@Test
	public void callBatchesToggleAllowedWorkerType() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		lockStudy(study);
		Call call = controllers.gui.routes.Batches.toggleAllowedWorkerType(
				study.getId(), study.getDefaultBatch().getId(),
				JatosWorker.WORKER_TYPE, true);
		checkForbiddenBecauseLocked(call, Helpers.POST);
	}

	/**
	 * Check that Batches.remove() doesn't work if study is locked
	 */
	@Test
	public void callBatchesRemove() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		lockStudy(study);
		Call call = controllers.gui.routes.Batches.remove(study.getId(),
				study.getDefaultBatch().getId());
		checkForbiddenBecauseLocked(call, Helpers.POST);
	}

	/**
	 * Check that Batches.createPersonalSingleRun() doesn't work if study is
	 * locked
	 */
	@Test
	public void callBatchesCreatePersonalSingleRun() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		lockStudy(study);
		Call call = controllers.gui.routes.Batches.createPersonalSingleRun(
				study.getId(), study.getDefaultBatch().getId());
		checkForbiddenBecauseLocked(call, Helpers.POST);
	}

	/**
	 * Check that Batches.createPersonalMultipleRun() doesn't work if study is
	 * locked
	 */
	@Test
	public void callBatchesCreatePersonalMultipleRun() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		lockStudy(study);
		Call call = controllers.gui.routes.Batches.createPersonalMultipleRun(
				study.getId(), study.getDefaultBatch().getId());
		checkForbiddenBecauseLocked(call, Helpers.POST);
	}

	/**
	 * Check that Components.submitCreated() doesn't work if study is locked
	 */
	@Test
	public void callComponentsSubmitCreated() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		lockStudy(study);
		Call call = controllers.gui.routes.Components
				.submitCreated(study.getId());
		checkForbiddenBecauseLocked(call, Helpers.POST);
	}

	/**
	 * Check that Components.submitEdited() doesn't work if study is locked
	 */
	@Test
	public void callComponentsSubmitEdited() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		lockStudy(study);
		Call call = controllers.gui.routes.Components
				.submitEdited(study.getId(), study.getFirstComponent().getId());
		checkForbiddenBecauseLocked(call, Helpers.POST);
	}

	/**
	 * Check that Components.toggleActive() doesn't work if study is locked
	 */
	@Test
	public void callComponentsToggleActive() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		lockStudy(study);
		Call call = controllers.gui.routes.Components.toggleActive(
				study.getId(), study.getFirstComponent().getId(), true);
		checkForbiddenBecauseLocked(call, Helpers.POST);
	}

	/**
	 * Check that Components.cloneComponent() doesn't work if study is locked
	 */
	@Test
	public void callComponentsCloneComponent() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		lockStudy(study);
		Call call = controllers.gui.routes.Components.cloneComponent(
				study.getId(), study.getFirstComponent().getId());
		checkForbiddenBecauseLocked(call, Helpers.POST);
	}

	/**
	 * Check that Components.remove() doesn't work if study is locked
	 */
	@Test
	public void callComponentsRemove() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		lockStudy(study);
		Call call = controllers.gui.routes.Components.remove(study.getId(),
				study.getFirstComponent().getId());
		checkForbiddenBecauseLocked(call, Helpers.POST);
	}

	/**
	 * Check that ImportExport.importComponent() doesn't work if study is locked
	 */
	@Test
	public void callImportExportImportComponent() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		lockStudy(study);
		Call call = controllers.gui.routes.ImportExport
				.importComponent(study.getId());
		checkForbiddenBecauseLocked(call, Helpers.POST);
	}

	/**
	 * Check that ImportExport.importComponentConfirmed() doesn't work if study
	 * is locked
	 */
	@Test
	public void callImportExportImportComponentConfirmed() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		lockStudy(study);
		Call call = controllers.gui.routes.ImportExport
				.importComponentConfirmed(study.getId());
		checkForbiddenBecauseLocked(call, Helpers.POST);
	}

	@Test
	public void callStudiesSubmitEdited() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		lockStudy(study);
		Call call = controllers.gui.routes.Studies.submitEdited(study.getId());
		checkForbiddenBecauseLocked(call, Helpers.POST);
	}

	@Test
	public void callStudiesRemove() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		lockStudy(study);
		Call call = controllers.gui.routes.Studies.remove(study.getId());
		checkForbiddenBecauseLocked(call, Helpers.DELETE);
	}

	@Test
	public void callStudiesChangeComponentOrder() throws Exception {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		lockStudy(study);
		Call call = controllers.gui.routes.Studies.changeComponentOrder(
				study.getId(), study.getComponent(1).getId(),
				StudyService.COMPONENT_POSITION_DOWN);
		checkForbiddenBecauseLocked(call, Helpers.POST);
	}

	@Test
	public void callExportComponentResults()
			throws IOException, ForbiddenReloadException {
		// Create a study with a StudyResult
		jpaApi.withTransaction(() -> {
			User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);
			Study study;
			try {
				study = testHelper.importExampleStudy(injector);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
			study.setLocked(true);
			studyService.createAndPersistStudy(admin, study);

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
		});

		User admin = testHelper.getAdmin();
		RequestBuilder request = new RequestBuilder().method("GET")
				.session(Authentication.SESSION_USER_EMAIL, admin.getEmail())
				.uri(controllers.gui.routes.ImportExport
						.exportDataOfComponentResults("1").url());
		Result result = route(request);

		assertThat(result.status()).isEqualTo(Http.Status.OK);
	}

}
