package gui.controllers;

import static org.fest.assertions.Assertions.assertThat;
import static play.mvc.Http.Status.FORBIDDEN;
import static play.mvc.Http.Status.SEE_OTHER;
import static play.test.Helpers.contentAsString;
import static play.test.Helpers.route;

import java.io.IOException;

import org.junit.Test;

import controllers.gui.Authentication;
import controllers.gui.Users;
import exceptions.publix.ForbiddenReloadException;
import general.AbstractTest;
import general.gui.FlashScopeMessaging;
import general.gui.RequestScope;
import models.common.ComponentResult;
import models.common.Study;
import models.common.StudyResult;
import models.common.User;
import play.api.mvc.Call;
import play.mvc.Http.RequestBuilder;
import play.mvc.Result;
import play.test.Helpers;
import services.gui.StudyService;
import services.publix.workers.JatosPublixUtils;

/**
 * Testing whether actions do proper access control
 * 
 * @author Kristian Lange
 */
public class AccessControllerTest extends AbstractTest {

	private Study studyTemplate;
	private User testUser;
	private JatosPublixUtils jatosPublixUtils;

	@Override
	public void before() throws Exception {
		jatosPublixUtils = application.injector()
				.instanceOf(JatosPublixUtils.class);
		testUser = createAndPersistUser("bla@bla.com", "Bla", "bla");
		RequestScope.put(Authentication.LOGGED_IN_USER, testUser);
		studyTemplate = importExampleStudy();
	}

	@Override
	public void after() throws Exception {
		ioUtils.removeStudyAssetsDir(studyTemplate.getDirName());
	}

	/**
	 * Call action without testUser in session: nobody is logged in. This should
	 * trigger a redirect to the logged in page. This is never an Ajax request.
	 * Even if it's an Ajax request in the application, here it's a normal
	 * request.
	 */
	private void checkDeniedAccess(Call call, String method) {
		Result result = route(call);
		assertThat(result.status()).isEqualTo(SEE_OTHER);
		assertThat(result.redirectLocation()).contains("/jatos/login");
	}

	/**
	 * Removes the admin user from the users who have permission in this study.
	 * Then calls the action with the admin user logged-in (in the session).
	 * This should trigger an response with a 403 return code.
	 */
	private void checkNotUser(Call call, Study study, String method) {
		removeUser(study, admin);
		RequestBuilder request = new RequestBuilder().method(method)
				.session(Users.SESSION_EMAIL, admin.getEmail()).uri(call.url());
		Result result = route(request);
		assertThat(result.status()).isEqualTo(FORBIDDEN);
		assertThat(contentAsString(result)).contains("isn't user of study");
	}

	private void checkRightUserWithRedirect(Call call, String method) {
		RequestBuilder request = new RequestBuilder().method(method)
				.session(Users.SESSION_EMAIL, admin.getEmail()).uri(call.url());
		Result result = route(request);
		assertThat(result.status()).isEqualTo(SEE_OTHER);
		assertThat(result.flash().get(FlashScopeMessaging.ERROR))
				.contains("You must be logged in as");
	}

	private void checkRemoveJatosWorker(Call call, String method) {
		RequestBuilder request = new RequestBuilder().method(method)
				.session(Users.SESSION_EMAIL, admin.getEmail()).uri(call.url());
		Result result = route(request);
		assertThat(result.status()).isEqualTo(FORBIDDEN);
		assertThat(contentAsString(result)).contains("is a worker of JATOS");
	}

	// @Test
	public void callStudiesIndex() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Call call = controllers.gui.routes.Studies.study(studyClone.getId());
		checkDeniedAccess(call, Helpers.GET);
		checkNotUser(call, studyClone, Helpers.GET);
		removeStudy(studyClone);
	}

	// @Test
	public void callStudiesProperties() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Call call = controllers.gui.routes.Studies
				.properties(studyClone.getId());
		checkDeniedAccess(call, Helpers.GET);
		checkNotUser(call, studyClone, Helpers.GET);
		removeStudy(studyClone);
	}

	// @Test
	public void callStudiesSubmitCreated() throws Exception {
		Call call = controllers.gui.routes.Studies.submitCreated();
		checkDeniedAccess(call, Helpers.GET);
	}

	// @Test
	public void callProperties() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Call call = controllers.gui.routes.Studies
				.properties(studyClone.getId());
		checkDeniedAccess(call, Helpers.GET);
		checkNotUser(call, studyClone, Helpers.GET);
		removeStudy(studyClone);
	}

	// @Test
	public void callStudiesSubmitEdited() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Call call = controllers.gui.routes.Studies
				.submitEdited(studyClone.getId());
		checkDeniedAccess(call, Helpers.POST);
		checkNotUser(call, studyClone, Helpers.POST);
		removeStudy(studyClone);
	}

	// @Test
	public void callStudiesSwapLock() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Call call = controllers.gui.routes.Studies.toggleLock(studyClone.getId());
		checkDeniedAccess(call, Helpers.POST);
		checkNotUser(call, studyClone, Helpers.POST);
		removeStudy(studyClone);
	}

	// @Test
	public void callStudiesRemove() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Call call = controllers.gui.routes.Studies.remove(studyClone.getId());
		checkDeniedAccess(call, Helpers.DELETE);
		checkNotUser(call, studyClone, Helpers.DELETE);
		removeStudy(studyClone);
	}

	// @Test
	public void callStudiesCloneStudy() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Call call = controllers.gui.routes.Studies
				.cloneStudy(studyClone.getId());
		checkDeniedAccess(call, Helpers.GET);
		checkNotUser(call, studyClone, Helpers.GET);
		removeStudy(studyClone);
	}

	// @Test
	public void callStudiesUsers() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Call call = controllers.gui.routes.Studies.users(studyClone.getId());
		checkDeniedAccess(call, Helpers.GET);
		checkNotUser(call, studyClone, Helpers.GET);
		removeStudy(studyClone);
	}

	// @Test
	public void callUsers() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Call call = controllers.gui.routes.Studies.users(studyClone.getId());
		checkDeniedAccess(call, Helpers.GET);
		checkNotUser(call, studyClone, Helpers.GET);
		removeStudy(studyClone);
	}

	// @Test
	public void callStudiesSubmitChangedUsers() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Call call = controllers.gui.routes.Studies
				.submitChangedUsers(studyClone.getId());
		checkDeniedAccess(call, Helpers.POST);
		checkNotUser(call, studyClone, Helpers.POST);
		removeStudy(studyClone);
	}

	// @Test
	public void callStudiesTableDataByStudy() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Call call = controllers.gui.routes.Studies
				.tableDataByStudy(studyClone.getId());
		checkDeniedAccess(call, Helpers.GET);
		checkNotUser(call, studyClone, Helpers.GET);
		removeStudy(studyClone);
	}

	// @Test
	public void callStudiesChangeComponentOrder() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Call call = controllers.gui.routes.Studies.changeComponentOrder(
				studyClone.getId(),
				studyClone.getComponentList().get(0).getId(),
				StudyService.COMPONENT_POSITION_DOWN);
		checkDeniedAccess(call, Helpers.POST);
		checkNotUser(call, studyClone, Helpers.POST);
		removeStudy(studyClone);
	}

	// @Test
	public void callStudiesRunStudy() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Call call = controllers.gui.routes.Studies.runStudy(studyClone.getId(),
				-1l);
		checkDeniedAccess(call, Helpers.GET);
		checkNotUser(call, studyClone, Helpers.GET);
		removeStudy(studyClone);
	}

	// @Test
	public void callStudiesWorkers() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Call call = controllers.gui.routes.Studies.workers(studyClone.getId());
		checkDeniedAccess(call, Helpers.GET);
		checkNotUser(call, studyClone, Helpers.GET);
		removeStudy(studyClone);
	}

	// @Test
	public void callComponentsRunComponent() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Call call = controllers.gui.routes.Components.runComponent(
				studyClone.getId(), studyClone.getComponent(1).getId(), -1l);
		checkDeniedAccess(call, Helpers.GET);
		checkNotUser(call, studyClone, Helpers.GET);
		removeStudy(studyClone);
	}

	// @Test
	public void callComponentsSubmitCreated() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Call call = controllers.gui.routes.Components
				.submitCreated(studyClone.getId());
		checkDeniedAccess(call, Helpers.POST);
		checkNotUser(call, studyClone, Helpers.POST);
		removeStudy(studyClone);
	}

	// @Test
	public void callComponentsSubmitEdited() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Call call = controllers.gui.routes.Components.submitEdited(
				studyClone.getId(), studyClone.getComponent(1).getId());
		checkDeniedAccess(call, Helpers.POST);
		checkNotUser(call, studyClone, Helpers.POST);
		removeStudy(studyClone);
	}

	// @Test
	public void callComponentsProperties() throws IOException {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Call call = controllers.gui.routes.Components.properties(
				studyClone.getId(), studyClone.getComponent(1).getId());
		checkDeniedAccess(call, Helpers.GET);
		checkNotUser(call, studyClone, Helpers.GET);
		removeStudy(studyClone);
	}

	// @Test
	public void callComponentsChangeProperty() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Call call = controllers.gui.routes.Components.toggleActive(
				studyClone.getId(), studyClone.getComponent(1).getId(), true);
		checkDeniedAccess(call, Helpers.POST);
		checkNotUser(call, studyClone, Helpers.POST);
		removeStudy(studyClone);
	}

	// @Test
	public void callComponentsCloneComponent() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Call call = controllers.gui.routes.Components.cloneComponent(
				studyClone.getId(), studyClone.getComponent(1).getId());
		checkDeniedAccess(call, Helpers.GET);
		checkNotUser(call, studyClone, Helpers.GET);
		removeStudy(studyClone);
	}

	// @Test
	public void callComponentsRemove() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Call call = controllers.gui.routes.Components.remove(studyClone.getId(),
				studyClone.getComponent(1).getId());
		checkDeniedAccess(call, Helpers.DELETE);
		checkNotUser(call, studyClone, Helpers.DELETE);
		removeStudy(studyClone);
	}

	// @Test
	public void callHome() throws Exception {
		Call call = controllers.gui.routes.Home.home();
		checkDeniedAccess(call, Helpers.GET);
	}

	// @Test
	public void callImportExportImportStudy() throws Exception {
		Call call = controllers.gui.routes.ImportExport.importStudy();
		checkDeniedAccess(call, Helpers.GET);
	}

	// @Test
	public void callImportExportImportStudyConfirmed() throws Exception {
		Call call = controllers.gui.routes.ImportExport.importStudyConfirmed();
		checkDeniedAccess(call, Helpers.GET);
	}

	// @Test
	public void callImportExportImportComponent() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Call call = controllers.gui.routes.ImportExport
				.importComponent(studyClone.getId());
		checkDeniedAccess(call, Helpers.POST);
		checkNotUser(call, studyClone, Helpers.POST);
		removeStudy(studyClone);
	}

	// @Test
	public void callImportExportExportComponent() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Call call = controllers.gui.routes.ImportExport.exportComponent(
				studyClone.getId(), studyClone.getComponent(1).getId());
		checkDeniedAccess(call, Helpers.GET);
		checkNotUser(call, studyClone, Helpers.GET);
		removeStudy(studyClone);
	}

	// @Test
	public void callImportExportExportStudy() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Call call = controllers.gui.routes.ImportExport
				.exportStudy(studyClone.getId());
		checkDeniedAccess(call, Helpers.GET);
		checkNotUser(call, studyClone, Helpers.GET);
		removeStudy(studyClone);
	}

	// @Test
	public void callComponentResultsIndex() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Call call = controllers.gui.routes.ComponentResults.componentResults(
				studyClone.getId(), studyClone.getComponent(1).getId());
		checkDeniedAccess(call, Helpers.GET);
		checkNotUser(call, studyClone, Helpers.GET);
		removeStudy(studyClone);
	}

	@Test
	public void callComponentResultsRemove() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		StudyResult studyResult = createTwoComponentResults(studyClone);
		ComponentResult componentResult = studyResult.getComponentResultList()
				.get(0);
		Call call = controllers.gui.routes.ComponentResults
				.remove(componentResult.getId().toString());
		checkDeniedAccess(call, Helpers.DELETE);

		// Logged-in user must be an user of the study to which the
		// ComponentResult belongs that is to be deleted - if not an HTTP 403
		// response is expected
		RequestBuilder request = new RequestBuilder().method(Helpers.DELETE)
				.session(Users.SESSION_EMAIL, testUser.getEmail())
				.uri(call.url());
		Result result = route(request);
		assertThat(result.status()).isEqualTo(FORBIDDEN);
		assertThat(contentAsString(result)).contains("isn't user of study");
		removeStudy(studyClone);
	}

	private StudyResult createTwoComponentResults(Study study)
			throws ForbiddenReloadException {
		entityManager.getTransaction().begin();
		StudyResult studyResult = resultCreator.createStudyResult(study,
				study.getDefaultBatch(), admin.getWorker());
		// Have to set worker manually in test - don't know why
		studyResult.setWorker(admin.getWorker());
		// Have to set study manually in test - don't know why
		study.getFirstComponent().setStudy(study);
		jatosPublixUtils.startComponent(study.getFirstComponent(), studyResult);
		jatosPublixUtils.startComponent(study.getFirstComponent(), studyResult);
		entityManager.getTransaction().commit();
		return studyResult;
	}

	// @Test
	public void callComponentResultsRemoveAllOfComponent() throws IOException {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Call call = controllers.gui.routes.ComponentResults
				.removeAllOfComponent(studyClone.getId(),
						studyClone.getComponent(1).getId());
		checkDeniedAccess(call, Helpers.DELETE);
		checkNotUser(call, studyClone, Helpers.DELETE);
		removeStudy(studyClone);
	}

	// @Test
	public void callComponentResultsTableDataByComponent() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Call call = controllers.gui.routes.ComponentResults
				.tableDataByComponent(studyClone.getId(),
						studyClone.getComponent(1).getId());
		checkDeniedAccess(call, Helpers.GET);
		checkNotUser(call, studyClone, Helpers.GET);
		removeStudy(studyClone);
	}

	// @Test
	public void callComponentResultsExportResultData() throws Exception {
		Call call = controllers.gui.routes.ImportExport
				.exportDataOfComponentResults("1");
		checkDeniedAccess(call, Helpers.GET);
	}

	// @Test
	public void callStudyResultsIndex() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Call call = controllers.gui.routes.StudyResults
				.studysStudyResults(studyClone.getId());
		checkDeniedAccess(call, Helpers.GET);
		checkNotUser(call, studyClone, Helpers.GET);
		removeStudy(studyClone);
	}

	// @Test
	public void callStudyResultsRemove() throws Exception {
		Call call = controllers.gui.routes.StudyResults.remove("1");
		checkDeniedAccess(call, Helpers.GET);
	}

	// @Test
	public void callStudyResultsTableDataByStudy() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Call call = controllers.gui.routes.StudyResults
				.tableDataByStudy(studyClone.getId());
		checkDeniedAccess(call, Helpers.GET);
		checkNotUser(call, studyClone, Helpers.GET);
		removeStudy(studyClone);
	}

	// @Test
	public void callStudyResultsTableDataByWorker() throws Exception {
		Call call = controllers.gui.routes.StudyResults.tableDataByWorker(1l);
		checkDeniedAccess(call, Helpers.GET);
	}

	// @Test
	public void callStudyResultsExportResultData() throws Exception {
		Call call = controllers.gui.routes.ImportExport
				.exportDataOfStudyResults("1");
		checkDeniedAccess(call, Helpers.GET);
	}

	// @Test
	public void callUsersProfile() throws Exception {
		Call call = controllers.gui.routes.Users.profile(testUser.getEmail());
		checkDeniedAccess(call, Helpers.GET);
		checkRightUserWithRedirect(call, Helpers.GET);
	}

	// @Test
	public void callUsersSubmit() throws Exception {
		Call call = controllers.gui.routes.Users.submit();
		checkDeniedAccess(call, Helpers.GET);
	}

	// @Test
	public void callUsersSubmitEditedProfile() throws Exception {
		Call call = controllers.gui.routes.Users
				.submitEditedProfile(testUser.getEmail());
		checkDeniedAccess(call, Helpers.POST);
		checkRightUserWithRedirect(call, Helpers.POST);
	}

	// @Test
	public void callUsersSubmitChangedPassword() throws Exception {
		Call call = controllers.gui.routes.Users
				.submitChangedPassword(testUser.getEmail());
		checkDeniedAccess(call, Helpers.POST);
		checkRightUserWithRedirect(call, Helpers.POST);
	}

	// @Test
	public void callWorkersIndex() throws Exception {
		Call call = controllers.gui.routes.StudyResults
				.workersStudyResults(admin.getWorker().getId());
		checkDeniedAccess(call, Helpers.GET);
	}

	// @Test
	public void callWorkersRemove() throws Exception {
		Call call = controllers.gui.routes.Workers
				.remove(admin.getWorker().getId());
		checkDeniedAccess(call, Helpers.DELETE);
		checkRemoveJatosWorker(call, Helpers.DELETE);
	}

	// @Test
	public void callBatchesRunManager() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Call call = controllers.gui.routes.Batches
				.batchManager(studyClone.getId());
		checkDeniedAccess(call, Helpers.GET);
		checkNotUser(call, studyClone, Helpers.GET);
		removeStudy(studyClone);
	}

	// @Test
	public void callBatchesBatchesByStudy() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Call call = controllers.gui.routes.Batches
				.batchesByStudy(studyClone.getId());
		checkDeniedAccess(call, Helpers.GET);
		checkNotUser(call, studyClone, Helpers.GET);
		removeStudy(studyClone);
	}

	// @Test
	public void callBatchesAllowedWorkers() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Call call = controllers.gui.routes.Workers.workerSetup(
				studyClone.getId(), studyClone.getDefaultBatch().getId());
		checkDeniedAccess(call, Helpers.GET);
		checkNotUser(call, studyClone, Helpers.GET);
		removeStudy(studyClone);
	}

	// @Test
	public void callBatchesSubmitCreated() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Call call = controllers.gui.routes.Batches
				.submitCreated(studyClone.getId());
		checkDeniedAccess(call, Helpers.POST);
		checkNotUser(call, studyClone, Helpers.POST);
		removeStudy(studyClone);
	}

	// @Test
	public void callBatchesBatch() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Call call = controllers.gui.routes.Workers.workerSetup(
				studyClone.getId(), studyClone.getDefaultBatch().getId());
		checkDeniedAccess(call, Helpers.GET);
		checkNotUser(call, studyClone, Helpers.GET);
		removeStudy(studyClone);
	}

	// @Test
	public void callBatchesProperties() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Call call = controllers.gui.routes.Batches.properties(
				studyClone.getId(), studyClone.getDefaultBatch().getId());
		checkDeniedAccess(call, Helpers.GET);
		checkNotUser(call, studyClone, Helpers.GET);
		removeStudy(studyClone);
	}

	// @Test
	public void callBatchesSubmitEditedProperties() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Call call = controllers.gui.routes.Batches.submitEditedProperties(
				studyClone.getId(), studyClone.getDefaultBatch().getId());
		checkDeniedAccess(call, Helpers.POST);
		checkNotUser(call, studyClone, Helpers.POST);
		removeStudy(studyClone);
	}

	// @Test
	public void callBatchesChangeProperty() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Call call = controllers.gui.routes.Batches.toggleActive(
				studyClone.getId(), studyClone.getDefaultBatch().getId(), true);
		checkDeniedAccess(call, Helpers.POST);
		checkNotUser(call, studyClone, Helpers.POST);
		removeStudy(studyClone);
	}

	// @Test
	public void callBatchesRemove() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Call call = controllers.gui.routes.Batches.remove(studyClone.getId(),
				studyClone.getDefaultBatch().getId());
		checkDeniedAccess(call, Helpers.DELETE);
		checkNotUser(call, studyClone, Helpers.DELETE);
		removeStudy(studyClone);
	}

	// @Test
	public void callBatchesCreatePersonalSingleRun() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Call call = controllers.gui.routes.Batches
				.createPersonalSingleRun(studyClone.getId(), -1l);
		checkDeniedAccess(call, Helpers.POST);
		checkNotUser(call, studyClone, Helpers.POST);
		removeStudy(studyClone);
	}

	// @Test
	public void callBatchesCreatePersonalMultipleRun() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Call call = controllers.gui.routes.Batches
				.createPersonalMultipleRun(studyClone.getId(), -1l);
		checkDeniedAccess(call, Helpers.POST);
		checkNotUser(call, studyClone, Helpers.POST);
		removeStudy(studyClone);
	}

}
