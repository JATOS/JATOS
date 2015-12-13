package gui.controllers;

import static org.fest.assertions.Assertions.assertThat;
import static play.mvc.Http.Status.FORBIDDEN;
import static play.mvc.Http.Status.SEE_OTHER;
import static play.test.Helpers.contentAsString;
import static play.test.Helpers.route;

import java.io.IOException;

import org.junit.Test;

import controllers.gui.Users;
import general.AbstractTest;
import general.gui.FlashScopeMessaging;
import models.common.Study;
import models.common.User;
import play.api.mvc.Call;
import play.mvc.Http.RequestBuilder;
import play.mvc.Result;
import play.test.Helpers;
import services.gui.StudyService;

/**
 * Testing whether actions do proper access control
 * 
 * @author Kristian Lange
 */
public class AccessControllerTest extends AbstractTest {

	private static Study studyTemplate;
	private static User testUser;

	@Override
	public void before() throws Exception {
		studyTemplate = importExampleStudy();
		testUser = createAndPersistUser("bla@bla.com", "Bla", "bla");
	}

	@Override
	public void after() throws Exception {
		ioUtils.removeStudyAssetsDir(studyTemplate.getDirName());
	}

	private void checkDeniedAccess(Call call, String method) {
		// Call action without testUser in session
		Result result = route(call);
		assertThat(result.status()).isEqualTo(SEE_OTHER);
		assertThat(result.redirectLocation()).contains("/jatos/login");
	}

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

	@Test
	public void callStudiesIndex() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Call call = controllers.gui.routes.Studies.index(studyClone.getId());
		checkDeniedAccess(call, Helpers.GET);
		checkNotUser(call, studyClone, Helpers.GET);
		removeStudy(studyClone);
	}

	@Test
	public void callStudiesCreate() {
		Call call = controllers.gui.routes.Studies.create();
		checkDeniedAccess(call, Helpers.GET);
	}

	@Test
	public void callStudiesSubmit() throws Exception {
		Call call = controllers.gui.routes.Studies.submit();
		checkDeniedAccess(call, Helpers.GET);
	}

	@Test
	public void callStudiesEdit() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Call call = controllers.gui.routes.Studies.edit(studyClone.getId());
		checkDeniedAccess(call, Helpers.GET);
		checkNotUser(call, studyClone, Helpers.GET);
		removeStudy(studyClone);
	}

	@Test
	public void callStudiesSubmitEdited() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Call call = controllers.gui.routes.Studies
				.submitEdited(studyClone.getId());
		checkDeniedAccess(call, Helpers.POST);
		removeUser(studyClone, admin);

		RequestBuilder request = new RequestBuilder().method(Helpers.POST)
				.session(Users.SESSION_EMAIL, admin.getEmail()).uri(call.url());
		Result result = route(request);

		assertThat(result.status()).isEqualTo(SEE_OTHER);
		assertThat(result.flash().get(FlashScopeMessaging.ERROR))
				.contains("isn't user of study");
		removeStudy(studyClone);
	}

	@Test
	public void callStudiesSwapLock() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Call call = controllers.gui.routes.Studies.swapLock(studyClone.getId());
		checkDeniedAccess(call, Helpers.GET);
		checkNotUser(call, studyClone, Helpers.POST);
		removeStudy(studyClone);
	}

	@Test
	public void callStudiesRemove() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Call call = controllers.gui.routes.Studies.remove(studyClone.getId());
		checkDeniedAccess(call, Helpers.GET);
		checkNotUser(call, studyClone, Helpers.DELETE);
		removeStudy(studyClone);
	}

	@Test
	public void callStudiesCloneStudy() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Call call = controllers.gui.routes.Studies
				.cloneStudy(studyClone.getId());
		checkDeniedAccess(call, Helpers.GET);
		checkNotUser(call, studyClone, Helpers.GET);
		removeStudy(studyClone);
	}

	@Test
	public void callStudiesChangeUser() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Call call = controllers.gui.routes.Studies
				.changeUsers(studyClone.getId());
		checkDeniedAccess(call, Helpers.GET);
		checkNotUser(call, studyClone, Helpers.GET);
		removeStudy(studyClone);
	}

	@Test
	public void callStudiesSubmitChangedUsers() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Call call = controllers.gui.routes.Studies
				.submitChangedUsers(studyClone.getId());
		checkDeniedAccess(call, Helpers.POST);
		// Check not user of study
		removeUser(studyClone, admin);

		RequestBuilder request = new RequestBuilder().method(Helpers.POST)
				.session(Users.SESSION_EMAIL, admin.getEmail()).uri(call.url());
		Result result = route(request);

		assertThat(result.status()).isEqualTo(SEE_OTHER);
		assertThat(result.flash().get(FlashScopeMessaging.ERROR))
				.contains("isn't user of study");
		removeStudy(studyClone);
	}

	@Test
	public void callStudiesChangeComponentOrder() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Call call = controllers.gui.routes.Studies.changeComponentOrder(
				studyClone.getId(),
				studyClone.getComponentList().get(0).getId(),
				StudyService.COMPONENT_POSITION_DOWN);
		checkDeniedAccess(call, Helpers.GET);
		checkNotUser(call, studyClone, Helpers.POST);
		removeStudy(studyClone);
	}

	@Test
	public void callStudiesShowStudy() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Call call = controllers.gui.routes.Studies
				.showStudy(studyClone.getId());
		checkDeniedAccess(call, Helpers.GET);
		checkNotUser(call, studyClone, Helpers.GET);
		removeStudy(studyClone);
	}

	@Test
	public void callStudiesCreatePersonalSingleRun() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Call call = controllers.gui.routes.Studies
				.createPersonalSingleRun(studyClone.getId());
		checkDeniedAccess(call, Helpers.GET);
		checkNotUser(call, studyClone, Helpers.POST);
		removeStudy(studyClone);
	}

	@Test
	public void callStudiesCreatePersonalMultipleRun() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Call call = controllers.gui.routes.Studies
				.createPersonalMultipleRun(studyClone.getId());
		checkDeniedAccess(call, Helpers.GET);
		checkNotUser(call, studyClone, Helpers.POST);
		removeStudy(studyClone);
	}

	@Test
	public void callStudiesShowMTurkSourceCode() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Call call = controllers.gui.routes.Studies
				.showMTurkSourceCode(studyClone.getId());
		checkDeniedAccess(call, Helpers.GET);
		checkNotUser(call, studyClone, Helpers.GET);
		removeStudy(studyClone);
	}

	@Test
	public void callStudiesWorkers() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Call call = controllers.gui.routes.Studies.workers(studyClone.getId());
		checkDeniedAccess(call, Helpers.GET);
		checkNotUser(call, studyClone, Helpers.GET);
		removeStudy(studyClone);
	}

	@Test
	public void callComponentsShowComponent() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Call call = controllers.gui.routes.Components.showComponent(
				studyClone.getId(), studyClone.getComponent(1).getId());
		checkDeniedAccess(call, Helpers.GET);
		checkNotUser(call, studyClone, Helpers.GET);
		removeStudy(studyClone);
	}

	@Test
	public void callComponentsCreate() throws IOException {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Call call = controllers.gui.routes.Components
				.create(studyClone.getId());
		checkDeniedAccess(call, Helpers.GET);
		checkNotUser(call, studyClone, Helpers.GET);
		removeStudy(studyClone);
	}

	@Test
	public void callComponentsSubmit() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Call call = controllers.gui.routes.Components
				.submit(studyClone.getId());
		checkDeniedAccess(call, Helpers.GET);
		checkNotUser(call, studyClone, Helpers.POST);
		removeStudy(studyClone);
	}

	@Test
	public void callComponentsChangeProperties() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Call call = controllers.gui.routes.Components.changeProperty(
				studyClone.getId(), studyClone.getComponent(1).getId(), true);
		checkDeniedAccess(call, Helpers.GET);
		checkNotUser(call, studyClone, Helpers.POST);
		removeStudy(studyClone);
	}

	@Test
	public void callComponentsCloneComponent() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Call call = controllers.gui.routes.Components.cloneComponent(
				studyClone.getId(), studyClone.getComponent(1).getId());
		checkDeniedAccess(call, Helpers.GET);
		checkNotUser(call, studyClone, Helpers.GET);
		removeStudy(studyClone);
	}

	@Test
	public void callComponentsRemove() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Call call = controllers.gui.routes.Components.remove(studyClone.getId(),
				studyClone.getComponent(1).getId());
		checkDeniedAccess(call, Helpers.GET);
		checkNotUser(call, studyClone, Helpers.DELETE);
		removeStudy(studyClone);
	}

	@Test
	public void callHome() throws Exception {
		Call call = controllers.gui.routes.Home.home();
		checkDeniedAccess(call, Helpers.GET);
	}

	@Test
	public void callImportExportImportStudy() throws Exception {
		Call call = controllers.gui.routes.ImportExport.importStudy();
		checkDeniedAccess(call, Helpers.GET);
	}

	@Test
	public void callImportExportImportStudyConfirmed() throws Exception {
		Call call = controllers.gui.routes.ImportExport.importStudyConfirmed();
		checkDeniedAccess(call, Helpers.GET);
	}

	@Test
	public void callImportExportImportComponent() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Call call = controllers.gui.routes.ImportExport
				.importComponent(studyClone.getId());
		checkDeniedAccess(call, Helpers.GET);
		checkNotUser(call, studyClone, Helpers.POST);
		removeStudy(studyClone);
	}

	@Test
	public void callImportExportExportComponent() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Call call = controllers.gui.routes.ImportExport.exportComponent(
				studyClone.getId(), studyClone.getComponent(1).getId());
		checkDeniedAccess(call, Helpers.GET);
		checkNotUser(call, studyClone, Helpers.GET);
		removeStudy(studyClone);
	}

	@Test
	public void callImportExportExportStudy() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Call call = controllers.gui.routes.ImportExport
				.exportStudy(studyClone.getId());
		checkDeniedAccess(call, Helpers.GET);
		checkNotUser(call, studyClone, Helpers.GET);
		removeStudy(studyClone);
	}

	@Test
	public void callComponentResultsIndex() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Call call = controllers.gui.routes.ComponentResults
				.index(studyClone.getId(), studyClone.getComponent(1).getId());
		checkDeniedAccess(call, Helpers.GET);
		checkNotUser(call, studyClone, Helpers.GET);
		removeStudy(studyClone);
	}

	@Test
	public void callComponentResultsRemove() throws Exception {
		Call call = controllers.gui.routes.ComponentResults.remove("1");
		checkDeniedAccess(call, Helpers.GET);
		// TODO check whether result's study has appropriate user
	}

	@Test
	public void callComponentResultsTableDataByComponent() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Call call = controllers.gui.routes.ComponentResults
				.tableDataByComponent(studyClone.getId(),
						studyClone.getComponent(1).getId());
		checkDeniedAccess(call, Helpers.GET);
		checkNotUser(call, studyClone, Helpers.GET);
		removeStudy(studyClone);
	}

	@Test
	public void callComponentResultsExportResultData() throws Exception {
		Call call = controllers.gui.routes.ImportExport
				.exportDataOfComponentResults("1");
		checkDeniedAccess(call, Helpers.GET);
	}

	@Test
	public void callStudyResultsIndex() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Call call = controllers.gui.routes.StudyResults
				.index(studyClone.getId());
		checkDeniedAccess(call, Helpers.GET);
		checkNotUser(call, studyClone, Helpers.GET);
		removeStudy(studyClone);
	}

	@Test
	public void callStudyResultsRemove() throws Exception {
		Call call = controllers.gui.routes.StudyResults.remove("1");
		checkDeniedAccess(call, Helpers.GET);
	}

	@Test
	public void callStudyResultsTableDataByStudy() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		Call call = controllers.gui.routes.StudyResults
				.tableDataByStudy(studyClone.getId());
		checkDeniedAccess(call, Helpers.GET);
		checkNotUser(call, studyClone, Helpers.GET);
		removeStudy(studyClone);
	}

	@Test
	public void callStudyResultsTableDataByWorker() throws Exception {
		Call call = controllers.gui.routes.StudyResults.tableDataByWorker(1l);
		checkDeniedAccess(call, Helpers.GET);
	}

	@Test
	public void callStudyResultsExportResultData() throws Exception {
		Call call = controllers.gui.routes.ImportExport
				.exportDataOfStudyResults("1");
		checkDeniedAccess(call, Helpers.GET);
	}

	@Test
	public void callUsersProfile() throws Exception {
		Call call = controllers.gui.routes.Users.profile(testUser.getEmail());
		checkDeniedAccess(call, Helpers.GET);
		checkRightUserWithRedirect(call, Helpers.GET);
	}

	@Test
	public void callUsersSubmit() throws Exception {
		Call call = controllers.gui.routes.Users.submit();
		checkDeniedAccess(call, Helpers.GET);
	}

	@Test
	public void callUsersSubmitEditedProfile() throws Exception {
		Call call = controllers.gui.routes.Users
				.submitEditedProfile(testUser.getEmail());
		checkDeniedAccess(call, Helpers.POST);
		checkRightUserWithRedirect(call, Helpers.POST);
	}

	@Test
	public void callUsersSubmitChangedPassword() throws Exception {
		Call call = controllers.gui.routes.Users
				.submitChangedPassword(testUser.getEmail());
		checkDeniedAccess(call, Helpers.POST);
		checkRightUserWithRedirect(call, Helpers.POST);
	}

	@Test
	public void callWorkersIndex() throws Exception {
		Call call = controllers.gui.routes.Workers
				.index(admin.getWorker().getId());
		checkDeniedAccess(call, Helpers.GET);
	}

	@Test
	public void callWorkersRemove() throws Exception {
		Call call = controllers.gui.routes.Workers
				.remove(admin.getWorker().getId());
		checkDeniedAccess(call, Helpers.DELETE);
		checkRemoveJatosWorker(call, Helpers.DELETE);
	}

}
