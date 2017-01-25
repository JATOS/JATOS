package controllers.gui;

import static org.fest.assertions.Assertions.assertThat;
import static play.test.Helpers.route;

import java.io.IOException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import controllers.gui.Users;
import exceptions.publix.ForbiddenReloadException;
import general.AbstractTest;
import models.common.Study;
import models.common.StudyResult;
import models.common.workers.JatosWorker;
import play.mvc.Call;
import play.mvc.Http;
import play.mvc.Http.RequestBuilder;
import play.mvc.Result;
import play.test.Helpers;
import services.gui.StudyService;
import services.publix.workers.JatosPublixUtils;

/**
 * A study can be locked. Then it shouldn't be possible to change its
 * properties, or its component's properties, or its batch's properties.
 * 
 * @author Kristian Lange
 */
public class LockedStudyControllerTest extends AbstractTest {

	private JatosPublixUtils jatosPublixUtils;
	private static Study studyTemplate;

	@Rule
	public ExpectedException thrown = ExpectedException.none();

	@Override
	public void before() throws Exception {
		jatosPublixUtils = application.injector()
				.instanceOf(JatosPublixUtils.class);
		studyTemplate = importExampleStudy();
	}

	@Override
	public void after() throws Exception {
		ioUtils.removeStudyAssetsDir(studyTemplate.getDirName());
	}

	private void checkDenyLocked(Call call, int statusCode, String redirectPath,
			String method) {
		RequestBuilder request = new RequestBuilder().method(method)
				.session(Users.SESSION_EMAIL, admin.getEmail()).uri(call.url());
		Result result = route(request);

		assertThat(result.status()).isEqualTo(statusCode);
		if (statusCode == Http.Status.SEE_OTHER) {
			assertThat(result.redirectLocation()).isEqualTo(redirectPath);
		}
	}

	/**
	 * Check that Batches.submitCreated() doesn't work if study is locked
	 */
	@Test
	public void callBatchesSubmitCreated() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		lockStudy(studyClone);
		Call call = controllers.gui.routes.Batches
				.submitCreated(studyClone.getId());
		checkDenyLocked(call, Http.Status.FORBIDDEN, null, Helpers.POST);
		removeStudy(studyClone);
	}

	/**
	 * Check that Batches.submitEditedProperties() doesn't work if study is
	 * locked
	 */
	@Test
	public void callBatchesSubmitEditedProperties() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		lockStudy(studyClone);
		Call call = controllers.gui.routes.Batches.submitEditedProperties(
				studyClone.getId(), studyClone.getDefaultBatch().getId());
		checkDenyLocked(call, Http.Status.FORBIDDEN, null, Helpers.POST);
		removeStudy(studyClone);
	}

	/**
	 * Check that Batches.toggleActive() doesn't work if study is locked
	 */
	@Test
	public void callBatchesToggleActive() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		lockStudy(studyClone);
		Call call = controllers.gui.routes.Batches.toggleActive(
				studyClone.getId(), studyClone.getDefaultBatch().getId(), true);
		checkDenyLocked(call, Http.Status.FORBIDDEN, null, Helpers.POST);
		removeStudy(studyClone);
	}

	/**
	 * Check that Batches.toggleAllowedWorkerType() doesn't work if study is
	 * locked
	 */
	@Test
	public void callBatchesToggleAllowedWorkerType() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		lockStudy(studyClone);
		Call call = controllers.gui.routes.Batches.toggleAllowedWorkerType(
				studyClone.getId(), studyClone.getDefaultBatch().getId(),
				JatosWorker.WORKER_TYPE, true);
		checkDenyLocked(call, Http.Status.FORBIDDEN, null, Helpers.POST);
		removeStudy(studyClone);
	}

	/**
	 * Check that Batches.remove() doesn't work if study is locked
	 * 
	 * TODO returns a 404 - complete mystery; although the method works
	 */
	// @Test
	public void callBatchesRemove() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		lockStudy(studyClone);
		Call call = controllers.gui.routes.Batches.remove(studyClone.getId(),
				studyClone.getDefaultBatch().getId());
		checkDenyLocked(call, Http.Status.FORBIDDEN, null, Helpers.POST);
		removeStudy(studyClone);
	}

	/**
	 * Check that Batches.createPersonalSingleRun() doesn't work if study is
	 * locked
	 */
	@Test
	public void callBatchesCreatePersonalSingleRun() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		lockStudy(studyClone);
		Call call = controllers.gui.routes.Batches.createPersonalSingleRun(
				studyClone.getId(), studyClone.getDefaultBatch().getId());
		checkDenyLocked(call, Http.Status.FORBIDDEN, null, Helpers.POST);
		removeStudy(studyClone);
	}

	/**
	 * Check that Batches.createPersonalMultipleRun() doesn't work if study is
	 * locked
	 */
	@Test
	public void callBatchesCreatePersonalMultipleRun() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		lockStudy(studyClone);
		Call call = controllers.gui.routes.Batches.createPersonalMultipleRun(
				studyClone.getId(), studyClone.getDefaultBatch().getId());
		checkDenyLocked(call, Http.Status.FORBIDDEN, null, Helpers.POST);
		removeStudy(studyClone);
	}

	/**
	 * Check that Components.submitCreated() doesn't work if study is locked
	 */
	@Test
	public void callComponentsSubmitCreated() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		lockStudy(studyClone);
		Call call = controllers.gui.routes.Components
				.submitCreated(studyClone.getId());
		checkDenyLocked(call, Http.Status.FORBIDDEN, null, Helpers.POST);
		removeStudy(studyClone);
	}

	/**
	 * Check that Components.submitEdited() doesn't work if study is locked
	 */
	@Test
	public void callComponentsSubmitEdited() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		lockStudy(studyClone);
		Call call = controllers.gui.routes.Components.submitEdited(
				studyClone.getId(), studyClone.getFirstComponent().getId());
		checkDenyLocked(call, Http.Status.FORBIDDEN, null, Helpers.POST);
		removeStudy(studyClone);
	}

	/**
	 * Check that Components.toggleActive() doesn't work if study is locked
	 */
	@Test
	public void callComponentsToggleActive() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		lockStudy(studyClone);
		Call call = controllers.gui.routes.Components.toggleActive(
				studyClone.getId(), studyClone.getFirstComponent().getId(),
				true);
		checkDenyLocked(call, Http.Status.FORBIDDEN, null, Helpers.POST);
		removeStudy(studyClone);
	}

	/**
	 * Check that Components.cloneComponent() doesn't work if study is locked
	 * 
	 * TODO returns a 404 - complete mystery; although the method works
	 */
	// @Test
	public void callComponentsCloneComponent() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		lockStudy(studyClone);
		Call call = controllers.gui.routes.Components.cloneComponent(
				studyClone.getId(), studyClone.getFirstComponent().getId());
		checkDenyLocked(call, Http.Status.FORBIDDEN, null, Helpers.POST);
		removeStudy(studyClone);
	}

	/**
	 * Check that Components.remove() doesn't work if study is locked
	 * 
	 * TODO returns a 404 - complete mystery; although the method works
	 */
	// @Test
	public void callComponentsRemove() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		lockStudy(studyClone);
		Call call = controllers.gui.routes.Components.remove(studyClone.getId(),
				studyClone.getFirstComponent().getId());
		checkDenyLocked(call, Http.Status.FORBIDDEN, null, Helpers.POST);
		removeStudy(studyClone);
	}

	/**
	 * Check that ImportExport.importComponent() doesn't work if study is locked
	 */
	@Test
	public void callImportExportImportComponent() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		lockStudy(studyClone);
		Call call = controllers.gui.routes.ImportExport
				.importComponent(studyClone.getId());
		checkDenyLocked(call, Http.Status.FORBIDDEN, null, Helpers.POST);
		removeStudy(studyClone);
	}

	/**
	 * Check that ImportExport.importComponentConfirmed() doesn't work if study
	 * is locked
	 */
	@Test
	public void callImportExportImportComponentConfirmed() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		lockStudy(studyClone);
		Call call = controllers.gui.routes.ImportExport
				.importComponentConfirmed(studyClone.getId());
		checkDenyLocked(call, Http.Status.FORBIDDEN, null, Helpers.POST);
		removeStudy(studyClone);
	}

	@Test
	public void callStudiesSubmitEdited() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		lockStudy(studyClone);
		Call call = controllers.gui.routes.Studies
				.submitEdited(studyClone.getId());
		checkDenyLocked(call, Http.Status.FORBIDDEN, null, Helpers.POST);
		removeStudy(studyClone);
	}

	@Test
	public void callStudiesRemove() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		lockStudy(studyClone);
		Call call = controllers.gui.routes.Studies.remove(studyClone.getId());
		checkDenyLocked(call, Http.Status.FORBIDDEN, null, Helpers.DELETE);
		removeStudy(studyClone);
	}

	@Test
	public void callStudiesChangeComponentOrder() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		lockStudy(studyClone);
		Call call = controllers.gui.routes.Studies.changeComponentOrder(
				studyClone.getId(), studyClone.getComponent(1).getId(),
				StudyService.COMPONENT_POSITION_DOWN);
		checkDenyLocked(call, Http.Status.FORBIDDEN, null, Helpers.POST);
		removeStudy(studyClone);
	}

	@Test
	public void callExportComponentResults()
			throws IOException, ForbiddenReloadException {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		lockStudy(studyClone);

		// Create some results
		entityManager.getTransaction().begin();
		StudyResult studyResult = resultCreator.createStudyResult(studyClone,
				studyClone.getDefaultBatch(), admin.getWorker());
		// Have to set worker manually in test - don't know why
		studyResult.setWorker(admin.getWorker());
		// Have to set study manually in test - don't know why
		studyClone.getFirstComponent().setStudy(studyClone);
		jatosPublixUtils.startComponent(studyClone.getFirstComponent(),
				studyResult);
		jatosPublixUtils.startComponent(studyClone.getFirstComponent(),
				studyResult);
		entityManager.getTransaction().commit();

		RequestBuilder request = new RequestBuilder().method("GET")
				.session(Users.SESSION_EMAIL, admin.getEmail())
				.uri(controllers.gui.routes.ImportExport
						.exportDataOfComponentResults("1").url());
		Result result = route(request);

		assertThat(result.status()).isEqualTo(Http.Status.OK);

		// Clean up
		removeStudy(studyClone);
	}

}
