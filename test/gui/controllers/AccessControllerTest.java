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
import gui.AbstractTest;

import java.io.IOException;

import models.StudyModel;
import models.UserModel;

import org.junit.Test;

import play.mvc.HandlerRef;
import play.mvc.Result;
import services.FlashScopeMessaging;
import services.gui.StudyService;
import utils.IOUtils;
import controllers.gui.Users;

/**
 * Testing whether actions do proper access control
 * 
 * @author Kristian Lange
 */
public class AccessControllerTest extends AbstractTest {

	private static StudyModel studyTemplate;
	private static UserModel testUser;

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

	private void checkNotMember(HandlerRef ref, StudyModel study) {
		removeMember(study, admin);
		Result result = callAction(ref,
				fakeRequest()
						.withSession(Users.SESSION_EMAIL, admin.getEmail()));
		assertThat(status(result)).isEqualTo(FORBIDDEN);
		assertThat(contentAsString(result)).contains("isn't member of study");
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
		StudyModel studyClone = cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.gui.routes.ref.Studies.index(studyClone
				.getId());
		checkDeniedAccess(ref);
		checkNotMember(ref, studyClone);
		removeStudy(studyClone);
	}

	@Test
	public void callStudiesCreate() {
		HandlerRef ref = controllers.gui.routes.ref.Studies.create();
		checkDeniedAccess(ref);
	}

	@Test
	public void callStudiesSubmit() throws Exception {
		HandlerRef ref = controllers.gui.routes.ref.Studies.submit();
		checkDeniedAccess(ref);
	}

	@Test
	public void callStudiesEdit() throws Exception {
		StudyModel studyClone = cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.gui.routes.ref.Studies.edit(studyClone
				.getId());
		checkDeniedAccess(ref);
		checkNotMember(ref, studyClone);
		removeStudy(studyClone);
	}

	@Test
	public void callStudiesSubmitEdited() throws Exception {
		StudyModel studyClone = cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.gui.routes.ref.Studies
				.submitEdited(studyClone.getId());
		checkDeniedAccess(ref);
		removeMember(studyClone, admin);
		Result result = callAction(ref,
				fakeRequest()
						.withSession(Users.SESSION_EMAIL, admin.getEmail()));
		assertThat(status(result)).isEqualTo(SEE_OTHER);
		assertThat(flash(result).get(FlashScopeMessaging.ERROR)).contains(
				"isn't member of study");
		removeStudy(studyClone);
	}

	@Test
	public void callStudiesSwapLock() throws Exception {
		StudyModel studyClone = cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.gui.routes.ref.Studies.swapLock(studyClone
				.getId());
		checkDeniedAccess(ref);
		checkNotMember(ref, studyClone);
		removeStudy(studyClone);
	}

	@Test
	public void callStudiesRemove() throws Exception {
		StudyModel studyClone = cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.gui.routes.ref.Studies.remove(studyClone
				.getId());
		checkDeniedAccess(ref);
		checkNotMember(ref, studyClone);
		removeStudy(studyClone);
	}

	@Test
	public void callStudiesCloneStudy() throws Exception {
		StudyModel studyClone = cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.gui.routes.ref.Studies
				.cloneStudy(studyClone.getId());
		checkDeniedAccess(ref);
		checkNotMember(ref, studyClone);
		removeStudy(studyClone);
	}

	@Test
	public void callStudiesChangeMember() throws Exception {
		StudyModel studyClone = cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.gui.routes.ref.Studies
				.changeMembers(studyClone.getId());
		checkDeniedAccess(ref);
		checkNotMember(ref, studyClone);
		removeStudy(studyClone);
	}

	@Test
	public void callStudiesSubmitChangedMembers() throws Exception {
		StudyModel studyClone = cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.gui.routes.ref.Studies
				.submitChangedMembers(studyClone.getId());
		checkDeniedAccess(ref);
		// Check not member
		removeMember(studyClone, admin);
		Result result = callAction(ref,
				fakeRequest()
						.withSession(Users.SESSION_EMAIL, admin.getEmail()));
		assertThat(status(result)).isEqualTo(SEE_OTHER);
		assertThat(flash(result).get(FlashScopeMessaging.ERROR)).contains(
				"isn't member of study");
		removeStudy(studyClone);
	}

	@Test
	public void callStudiesChangeComponentOrder() throws Exception {
		StudyModel studyClone = cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.gui.routes.ref.Studies
				.changeComponentOrder(studyClone.getId(), studyClone
						.getComponentList().get(0).getId(),
						StudyService.COMPONENT_POSITION_DOWN);
		checkDeniedAccess(ref);
		checkNotMember(ref, studyClone);
		removeStudy(studyClone);
	}

	@Test
	public void callStudiesShowStudy() throws Exception {
		StudyModel studyClone = cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.gui.routes.ref.Studies
				.showStudy(studyClone.getId());
		checkDeniedAccess(ref);
		checkNotMember(ref, studyClone);
		removeStudy(studyClone);
	}

	@Test
	public void callStudiesCreatePersonalSingleRun() throws Exception {
		StudyModel studyClone = cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.gui.routes.ref.Studies
				.createPersonalSingleRun(studyClone.getId());
		checkDeniedAccess(ref);
		checkNotMember(ref, studyClone);
		removeStudy(studyClone);
	}

	@Test
	public void callStudiesCreatePersonalMultipleRun() throws Exception {
		StudyModel studyClone = cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.gui.routes.ref.Studies
				.createPersonalMultipleRun(studyClone.getId());
		checkDeniedAccess(ref);
		checkNotMember(ref, studyClone);
		removeStudy(studyClone);
	}

	@Test
	public void callStudiesShowMTurkSourceCode() throws Exception {
		StudyModel studyClone = cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.gui.routes.ref.Studies
				.showMTurkSourceCode(studyClone.getId());
		checkDeniedAccess(ref);
		checkNotMember(ref, studyClone);
		removeStudy(studyClone);
	}

	@Test
	public void callStudiesWorkers() throws Exception {
		StudyModel studyClone = cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.gui.routes.ref.Studies.workers(studyClone
				.getId());
		checkDeniedAccess(ref);
		checkNotMember(ref, studyClone);
		removeStudy(studyClone);
	}

	@Test
	public void callComponentsShowComponent() throws Exception {
		StudyModel studyClone = cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.gui.routes.ref.Components.showComponent(
				studyClone.getId(), studyClone.getComponent(1).getId());
		checkDeniedAccess(ref);
		checkNotMember(ref, studyClone);
		removeStudy(studyClone);
	}

	@Test
	public void callComponentsCreate() throws IOException {
		StudyModel studyClone = cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.gui.routes.ref.Components
				.create(studyClone.getId());
		checkDeniedAccess(ref);
		checkNotMember(ref, studyClone);
		removeStudy(studyClone);
	}

	@Test
	public void callComponentsSubmit() throws Exception {
		StudyModel studyClone = cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.gui.routes.ref.Components
				.submit(studyClone.getId());
		checkDeniedAccess(ref);
		checkNotMember(ref, studyClone);
		removeStudy(studyClone);
	}

	@Test
	public void callComponentsChangeProperties() throws Exception {
		StudyModel studyClone = cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.gui.routes.ref.Components.changeProperty(
				studyClone.getId(), studyClone.getComponent(1).getId(), true);
		checkDeniedAccess(ref);
		checkNotMember(ref, studyClone);
		removeStudy(studyClone);
	}

	@Test
	public void callComponentsCloneComponent() throws Exception {
		StudyModel studyClone = cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.gui.routes.ref.Components.cloneComponent(
				studyClone.getId(), studyClone.getComponent(1).getId());
		checkDeniedAccess(ref);
		checkNotMember(ref, studyClone);
		removeStudy(studyClone);
	}

	@Test
	public void callComponentsRemove() throws Exception {
		StudyModel studyClone = cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.gui.routes.ref.Components.remove(
				studyClone.getId(), studyClone.getComponent(1).getId());
		checkDeniedAccess(ref);
		checkNotMember(ref, studyClone);
		removeStudy(studyClone);
	}

	@Test
	public void callHome() throws Exception {
		HandlerRef ref = controllers.gui.routes.ref.Home.home();
		checkDeniedAccess(ref);
	}

	@Test
	public void callImportExportImportStudy() throws Exception {
		HandlerRef ref = controllers.gui.routes.ref.ImportExport.importStudy();
		checkDeniedAccess(ref);
	}

	@Test
	public void callImportExportImportStudyConfirmed() throws Exception {
		HandlerRef ref = controllers.gui.routes.ref.ImportExport
				.importStudyConfirmed();
		checkDeniedAccess(ref);
	}

	@Test
	public void callImportExportImportComponent() throws Exception {
		StudyModel studyClone = cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.gui.routes.ref.ImportExport
				.importComponent(studyClone.getId());
		checkDeniedAccess(ref);
		checkNotMember(ref, studyClone);
		removeStudy(studyClone);
	}

	@Test
	public void callImportExportExportComponent() throws Exception {
		StudyModel studyClone = cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.gui.routes.ref.ImportExport
				.exportComponent(studyClone.getId(), studyClone.getComponent(1)
						.getId());
		checkDeniedAccess(ref);
		checkNotMember(ref, studyClone);
		removeStudy(studyClone);
	}

	@Test
	public void callImportExportExportStudy() throws Exception {
		StudyModel studyClone = cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.gui.routes.ref.ImportExport
				.exportStudy(studyClone.getId());
		checkDeniedAccess(ref);
		checkNotMember(ref, studyClone);
		removeStudy(studyClone);
	}

	@Test
	public void callComponentResultsIndex() throws Exception {
		StudyModel studyClone = cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.gui.routes.ref.ComponentResults.index(
				studyClone.getId(), studyClone.getComponent(1).getId());
		checkDeniedAccess(ref);
		checkNotMember(ref, studyClone);
		removeStudy(studyClone);
	}

	@Test
	public void callComponentResultsRemove() throws Exception {
		HandlerRef ref = controllers.gui.routes.ref.ComponentResults
				.remove("1");
		checkDeniedAccess(ref);
		// TODO check whether result's study has appropriate member
	}

	@Test
	public void callComponentResultsTableDataByComponent() throws Exception {
		StudyModel studyClone = cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.gui.routes.ref.ComponentResults
				.tableDataByComponent(studyClone.getId(), studyClone
						.getComponent(1).getId());
		checkDeniedAccess(ref);
		checkNotMember(ref, studyClone);
		removeStudy(studyClone);
	}

	@Test
	public void callComponentResultsExportResultData() throws Exception {
		HandlerRef ref = controllers.gui.routes.ref.ImportExport
				.exportDataOfComponentResults("1");
		checkDeniedAccess(ref);
		// TODO check whether result's study has appropriate member
	}

	@Test
	public void callStudyResultsIndex() throws Exception {
		StudyModel studyClone = cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.gui.routes.ref.StudyResults
				.index(studyClone.getId());
		checkDeniedAccess(ref);
		checkNotMember(ref, studyClone);
		removeStudy(studyClone);
	}

	@Test
	public void callStudyResultsRemove() throws Exception {
		HandlerRef ref = controllers.gui.routes.ref.StudyResults.remove("1");
		checkDeniedAccess(ref);
		// TODO check whether result's study has appropriate member
	}

	@Test
	public void callStudyResultsTableDataByStudy() throws Exception {
		StudyModel studyClone = cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.gui.routes.ref.StudyResults
				.tableDataByStudy(studyClone.getId());
		checkDeniedAccess(ref);
		checkNotMember(ref, studyClone);
		removeStudy(studyClone);
	}

	@Test
	public void callStudyResultsTableDataByWorker() throws Exception {
		HandlerRef ref = controllers.gui.routes.ref.StudyResults
				.tableDataByWorker(1l);
		checkDeniedAccess(ref);
		// TODO check whether result's study has appropriate member
	}

	@Test
	public void callStudyResultsExportResultData() throws Exception {
		HandlerRef ref = controllers.gui.routes.ref.ImportExport
				.exportDataOfStudyResults("1");
		checkDeniedAccess(ref);
		// TODO check whether result's study has appropriate member
	}

	@Test
	public void callUsersProfile() throws Exception {
		HandlerRef ref = controllers.gui.routes.ref.Users.profile(testUser
				.getEmail());
		checkDeniedAccess(ref);
		checkRightUserWithRedirect(ref);
	}

	@Test
	public void callUsersCreate() throws Exception {
		HandlerRef ref = controllers.gui.routes.ref.Users.create();
		checkDeniedAccess(ref);
	}

	@Test
	public void callUsersSubmit() throws Exception {
		HandlerRef ref = controllers.gui.routes.ref.Users.submit();
		checkDeniedAccess(ref);
	}

	@Test
	public void callUsersEditProfile() throws Exception {
		HandlerRef ref = controllers.gui.routes.ref.Users.editProfile(testUser
				.getEmail());
		checkDeniedAccess(ref);
		checkRightUserWithRedirect(ref);
	}

	@Test
	public void callUsersSubmitEditedProfile() throws Exception {
		HandlerRef ref = controllers.gui.routes.ref.Users
				.submitEditedProfile(testUser.getEmail());
		checkDeniedAccess(ref);
		checkRightUserWithRedirect(ref);
	}

	@Test
	public void callUsersChangePassword() throws Exception {
		HandlerRef ref = controllers.gui.routes.ref.Users
				.changePassword(testUser.getEmail());
		checkDeniedAccess(ref);
		checkRightUserWithRedirect(ref);
	}

	@Test
	public void callUsersSubmitChangedPassword() throws Exception {
		HandlerRef ref = controllers.gui.routes.ref.Users
				.submitChangedPassword(testUser.getEmail());
		checkDeniedAccess(ref);
		checkRightUserWithRedirect(ref);
	}

	@Test
	public void callWorkersIndex() throws Exception {
		HandlerRef ref = controllers.gui.routes.ref.Workers.index(admin
				.getWorker().getId());
		checkDeniedAccess(ref);
	}

	@Test
	public void callWorkersRemove() throws Exception {
		HandlerRef ref = controllers.gui.routes.ref.Workers.remove(admin
				.getWorker().getId());
		checkDeniedAccess(ref);
		checkRemoveJatosWorker(ref);
	}

}
