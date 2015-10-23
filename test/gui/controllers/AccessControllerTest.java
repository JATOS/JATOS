package gui.controllers;

import static org.fest.assertions.Assertions.assertThat;
import static play.mvc.Http.Status.FORBIDDEN;
import static play.mvc.Http.Status.SEE_OTHER;
import static play.test.Helpers.callAction;
import static play.test.Helpers.contentAsString;
import static play.test.Helpers.fakeRequest;
import static play.test.Helpers.flash;
import static play.test.Helpers.redirectLocation;
import static play.test.Helpers.status;
import common.AbstractTest;

import java.io.IOException;

import models.Study;
import models.User;

import org.junit.Test;

import common.FlashScopeMessaging;
import play.mvc.HandlerRef;
import play.mvc.Result;
import services.StudyService;
import utils.IOUtils;
import controllers.Users;

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
		IOUtils.removeStudyAssetsDir(studyTemplate.getDirName());
	}

	private void checkDeniedAccess(HandlerRef ref) {
		// Call action without testUser in session
		Result result = callAction(ref);
		assertThat(status(result)).isEqualTo(SEE_OTHER);
		assertThat(redirectLocation(result)).contains("/jatos/login");
	}

	private void checkNotUser(HandlerRef ref, Study study) {
		removeUser(study, admin);
		Result result = callAction(ref,
				fakeRequest()
						.withSession(Users.SESSION_EMAIL, admin.getEmail()));
		assertThat(status(result)).isEqualTo(FORBIDDEN);
		assertThat(contentAsString(result)).contains("isn't user of study");
	}

	private void checkRightUserWithRedirect(HandlerRef ref) {
		Result result = callAction(ref,
				fakeRequest()
						.withSession(Users.SESSION_EMAIL, admin.getEmail()));
		assertThat(status(result)).isEqualTo(SEE_OTHER);
		assertThat(flash(result).get(FlashScopeMessaging.ERROR)).contains(
				"You must be logged in as");
	}

	private void checkRemoveJatosWorker(HandlerRef ref) {
		Result result = callAction(ref,
				fakeRequest()
						.withSession(Users.SESSION_EMAIL, admin.getEmail()));
		assertThat(status(result)).isEqualTo(FORBIDDEN);
		assertThat(contentAsString(result)).contains("is a worker of JATOS");
	}

	@Test
	public void callStudiesIndex() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.routes.ref.Studies.index(studyClone
				.getId());
		checkDeniedAccess(ref);
		checkNotUser(ref, studyClone);
		removeStudy(studyClone);
	}

	@Test
	public void callStudiesCreate() {
		HandlerRef ref = controllers.routes.ref.Studies.create();
		checkDeniedAccess(ref);
	}

	@Test
	public void callStudiesSubmit() throws Exception {
		HandlerRef ref = controllers.routes.ref.Studies.submit();
		checkDeniedAccess(ref);
	}

	@Test
	public void callStudiesEdit() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.routes.ref.Studies.edit(studyClone
				.getId());
		checkDeniedAccess(ref);
		checkNotUser(ref, studyClone);
		removeStudy(studyClone);
	}

	@Test
	public void callStudiesSubmitEdited() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.routes.ref.Studies
				.submitEdited(studyClone.getId());
		checkDeniedAccess(ref);
		removeUser(studyClone, admin);
		Result result = callAction(ref,
				fakeRequest()
						.withSession(Users.SESSION_EMAIL, admin.getEmail()));
		assertThat(status(result)).isEqualTo(SEE_OTHER);
		assertThat(flash(result).get(FlashScopeMessaging.ERROR)).contains(
				"isn't user of study");
		removeStudy(studyClone);
	}

	@Test
	public void callStudiesSwapLock() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.routes.ref.Studies.swapLock(studyClone
				.getId());
		checkDeniedAccess(ref);
		checkNotUser(ref, studyClone);
		removeStudy(studyClone);
	}

	@Test
	public void callStudiesRemove() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.routes.ref.Studies.remove(studyClone
				.getId());
		checkDeniedAccess(ref);
		checkNotUser(ref, studyClone);
		removeStudy(studyClone);
	}

	@Test
	public void callStudiesCloneStudy() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.routes.ref.Studies
				.cloneStudy(studyClone.getId());
		checkDeniedAccess(ref);
		checkNotUser(ref, studyClone);
		removeStudy(studyClone);
	}

	@Test
	public void callStudiesChangeUser() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.routes.ref.Studies
				.changeUsers(studyClone.getId());
		checkDeniedAccess(ref);
		checkNotUser(ref, studyClone);
		removeStudy(studyClone);
	}

	@Test
	public void callStudiesSubmitChangedUsers() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.routes.ref.Studies
				.submitChangedUsers(studyClone.getId());
		checkDeniedAccess(ref);
		// Check not user of study
		removeUser(studyClone, admin);
		Result result = callAction(ref,
				fakeRequest()
						.withSession(Users.SESSION_EMAIL, admin.getEmail()));
		assertThat(status(result)).isEqualTo(SEE_OTHER);
		assertThat(flash(result).get(FlashScopeMessaging.ERROR)).contains(
				"isn't user of study");
		removeStudy(studyClone);
	}

	@Test
	public void callStudiesChangeComponentOrder() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.routes.ref.Studies
				.changeComponentOrder(studyClone.getId(), studyClone
						.getComponentList().get(0).getId(),
						StudyService.COMPONENT_POSITION_DOWN);
		checkDeniedAccess(ref);
		checkNotUser(ref, studyClone);
		removeStudy(studyClone);
	}

	@Test
	public void callStudiesShowStudy() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.routes.ref.Studies
				.showStudy(studyClone.getId());
		checkDeniedAccess(ref);
		checkNotUser(ref, studyClone);
		removeStudy(studyClone);
	}

	@Test
	public void callStudiesCreatePersonalSingleRun() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.routes.ref.Studies
				.createPersonalSingleRun(studyClone.getId());
		checkDeniedAccess(ref);
		checkNotUser(ref, studyClone);
		removeStudy(studyClone);
	}

	@Test
	public void callStudiesCreatePersonalMultipleRun() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.routes.ref.Studies
				.createPersonalMultipleRun(studyClone.getId());
		checkDeniedAccess(ref);
		checkNotUser(ref, studyClone);
		removeStudy(studyClone);
	}

	@Test
	public void callStudiesShowMTurkSourceCode() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.routes.ref.Studies
				.showMTurkSourceCode(studyClone.getId());
		checkDeniedAccess(ref);
		checkNotUser(ref, studyClone);
		removeStudy(studyClone);
	}

	@Test
	public void callStudiesWorkers() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.routes.ref.Studies.workers(studyClone
				.getId());
		checkDeniedAccess(ref);
		checkNotUser(ref, studyClone);
		removeStudy(studyClone);
	}

	@Test
	public void callComponentsShowComponent() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.routes.ref.Components.showComponent(
				studyClone.getId(), studyClone.getComponent(1).getId());
		checkDeniedAccess(ref);
		checkNotUser(ref, studyClone);
		removeStudy(studyClone);
	}

	@Test
	public void callComponentsCreate() throws IOException {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.routes.ref.Components
				.create(studyClone.getId());
		checkDeniedAccess(ref);
		checkNotUser(ref, studyClone);
		removeStudy(studyClone);
	}

	@Test
	public void callComponentsSubmit() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.routes.ref.Components
				.submit(studyClone.getId());
		checkDeniedAccess(ref);
		checkNotUser(ref, studyClone);
		removeStudy(studyClone);
	}

	@Test
	public void callComponentsChangeProperties() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.routes.ref.Components.changeProperty(
				studyClone.getId(), studyClone.getComponent(1).getId(), true);
		checkDeniedAccess(ref);
		checkNotUser(ref, studyClone);
		removeStudy(studyClone);
	}

	@Test
	public void callComponentsCloneComponent() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.routes.ref.Components.cloneComponent(
				studyClone.getId(), studyClone.getComponent(1).getId());
		checkDeniedAccess(ref);
		checkNotUser(ref, studyClone);
		removeStudy(studyClone);
	}

	@Test
	public void callComponentsRemove() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.routes.ref.Components.remove(
				studyClone.getId(), studyClone.getComponent(1).getId());
		checkDeniedAccess(ref);
		checkNotUser(ref, studyClone);
		removeStudy(studyClone);
	}

	@Test
	public void callHome() throws Exception {
		HandlerRef ref = controllers.routes.ref.Home.home();
		checkDeniedAccess(ref);
	}

	@Test
	public void callImportExportImportStudy() throws Exception {
		HandlerRef ref = controllers.routes.ref.ImportExport.importStudy();
		checkDeniedAccess(ref);
	}

	@Test
	public void callImportExportImportStudyConfirmed() throws Exception {
		HandlerRef ref = controllers.routes.ref.ImportExport
				.importStudyConfirmed();
		checkDeniedAccess(ref);
	}

	@Test
	public void callImportExportImportComponent() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.routes.ref.ImportExport
				.importComponent(studyClone.getId());
		checkDeniedAccess(ref);
		checkNotUser(ref, studyClone);
		removeStudy(studyClone);
	}

	@Test
	public void callImportExportExportComponent() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.routes.ref.ImportExport
				.exportComponent(studyClone.getId(), studyClone.getComponent(1)
						.getId());
		checkDeniedAccess(ref);
		checkNotUser(ref, studyClone);
		removeStudy(studyClone);
	}

	@Test
	public void callImportExportExportStudy() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.routes.ref.ImportExport
				.exportStudy(studyClone.getId());
		checkDeniedAccess(ref);
		checkNotUser(ref, studyClone);
		removeStudy(studyClone);
	}

	@Test
	public void callComponentResultsIndex() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.routes.ref.ComponentResults.index(
				studyClone.getId(), studyClone.getComponent(1).getId());
		checkDeniedAccess(ref);
		checkNotUser(ref, studyClone);
		removeStudy(studyClone);
	}

	@Test
	public void callComponentResultsRemove() throws Exception {
		HandlerRef ref = controllers.routes.ref.ComponentResults
				.remove("1");
		checkDeniedAccess(ref);
		// TODO check whether result's study has appropriate user
	}

	@Test
	public void callComponentResultsTableDataByComponent() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.routes.ref.ComponentResults
				.tableDataByComponent(studyClone.getId(), studyClone
						.getComponent(1).getId());
		checkDeniedAccess(ref);
		checkNotUser(ref, studyClone);
		removeStudy(studyClone);
	}

	@Test
	public void callComponentResultsExportResultData() throws Exception {
		HandlerRef ref = controllers.routes.ref.ImportExport
				.exportDataOfComponentResults("1");
		checkDeniedAccess(ref);
		// TODO check whether result's study has appropriate user
	}

	@Test
	public void callStudyResultsIndex() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.routes.ref.StudyResults
				.index(studyClone.getId());
		checkDeniedAccess(ref);
		checkNotUser(ref, studyClone);
		removeStudy(studyClone);
	}

	@Test
	public void callStudyResultsRemove() throws Exception {
		HandlerRef ref = controllers.routes.ref.StudyResults.remove("1");
		checkDeniedAccess(ref);
		// TODO check whether result's study has appropriate user
	}

	@Test
	public void callStudyResultsTableDataByStudy() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.routes.ref.StudyResults
				.tableDataByStudy(studyClone.getId());
		checkDeniedAccess(ref);
		checkNotUser(ref, studyClone);
		removeStudy(studyClone);
	}

	@Test
	public void callStudyResultsTableDataByWorker() throws Exception {
		HandlerRef ref = controllers.routes.ref.StudyResults
				.tableDataByWorker(1l);
		checkDeniedAccess(ref);
		// TODO check whether result's study has appropriate user
	}

	@Test
	public void callStudyResultsExportResultData() throws Exception {
		HandlerRef ref = controllers.routes.ref.ImportExport
				.exportDataOfStudyResults("1");
		checkDeniedAccess(ref);
		// TODO check whether result's study has appropriate user
	}

	@Test
	public void callUsersProfile() throws Exception {
		HandlerRef ref = controllers.routes.ref.Users.profile(testUser
				.getEmail());
		checkDeniedAccess(ref);
		checkRightUserWithRedirect(ref);
	}

	@Test
	public void callUsersCreate() throws Exception {
		HandlerRef ref = controllers.routes.ref.Users.create();
		checkDeniedAccess(ref);
	}

	@Test
	public void callUsersSubmit() throws Exception {
		HandlerRef ref = controllers.routes.ref.Users.submit();
		checkDeniedAccess(ref);
	}

	@Test
	public void callUsersEditProfile() throws Exception {
		HandlerRef ref = controllers.routes.ref.Users.editProfile(testUser
				.getEmail());
		checkDeniedAccess(ref);
		checkRightUserWithRedirect(ref);
	}

	@Test
	public void callUsersSubmitEditedProfile() throws Exception {
		HandlerRef ref = controllers.routes.ref.Users
				.submitEditedProfile(testUser.getEmail());
		checkDeniedAccess(ref);
		checkRightUserWithRedirect(ref);
	}

	@Test
	public void callUsersChangePassword() throws Exception {
		HandlerRef ref = controllers.routes.ref.Users
				.changePassword(testUser.getEmail());
		checkDeniedAccess(ref);
		checkRightUserWithRedirect(ref);
	}

	@Test
	public void callUsersSubmitChangedPassword() throws Exception {
		HandlerRef ref = controllers.routes.ref.Users
				.submitChangedPassword(testUser.getEmail());
		checkDeniedAccess(ref);
		checkRightUserWithRedirect(ref);
	}

	@Test
	public void callWorkersIndex() throws Exception {
		HandlerRef ref = controllers.routes.ref.Workers.index(admin
				.getWorker().getId());
		checkDeniedAccess(ref);
	}

	@Test
	public void callWorkersRemove() throws Exception {
		HandlerRef ref = controllers.routes.ref.Workers.remove(admin
				.getWorker().getId());
		checkDeniedAccess(ref);
		checkRemoveJatosWorker(ref);
	}

}
