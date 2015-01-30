import static play.test.Helpers.callAction;
import static play.test.Helpers.fakeRequest;

import java.io.IOException;

import models.StudyModel;
import models.UserModel;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import play.mvc.HandlerRef;
import services.IOUtils;

import common.Global;

import controllers.Studies;
import controllers.Users;
import exceptions.ResultException;

/**
 * Testing whether actions do proper access control
 * 
 * @author Kristian Lange
 */
public class AccessControllerTest {

	private static ControllerTestUtils utils = Global.INJECTOR
			.getInstance(ControllerTestUtils.class);
	private static StudyModel studyTemplate;
	private static UserModel testUser;

	@Rule
	public ExpectedException thrown = ExpectedException.none();

	@BeforeClass
	public static void startApp() throws Exception {
		utils.startApp();
		studyTemplate = utils.importExampleStudy();
		testUser = utils.createAndPersistUser("bla@bla.com", "Bla", "bla");
	}

	@AfterClass
	public static void stopApp() throws IOException {
		IOUtils.removeStudyAssetsDir(studyTemplate.getDirName());
		utils.stopApp();
	}

	private void checkDeniedAccess(HandlerRef ref) {
		thrown.expect(ResultException.class);
		thrown.expectMessage("No user logged in");
		// Call action without testUser in session
		callAction(ref);
	}

	private void checkNotMember(HandlerRef ref, StudyModel study) {
		utils.removeMember(study, utils.admin);
		thrown.expect(ResultException.class);
		thrown.expectMessage("isn't member of study");
		callAction(
				ref,
				fakeRequest().withSession(Users.SESSION_EMAIL,
						utils.admin.getEmail()));
	}

	private void checkRightUser(HandlerRef ref) {
		thrown.expect(ResultException.class);
		thrown.expectMessage("You must be logged in as");
		callAction(
				ref,
				fakeRequest().withSession(Users.SESSION_EMAIL,
						utils.admin.getEmail()));
	}

	private void checkRemoveJatosWorker(HandlerRef ref) {
		thrown.expect(ResultException.class);
		thrown.expectMessage("is a worker of JATOS");
		callAction(
				ref,
				fakeRequest().withSession(Users.SESSION_EMAIL,
						utils.admin.getEmail()));
	}

	@Test
	public void callStudiesIndex() throws Exception {
		StudyModel studyClone = utils.cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.routes.ref.Studies.index(
				studyClone.getId(), null);
		checkDeniedAccess(ref);
		checkNotMember(ref, studyClone);
		utils.removeStudy(studyClone);
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
		StudyModel studyClone = utils.cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.routes.ref.Studies
				.edit(studyClone.getId());
		checkDeniedAccess(ref);
		checkNotMember(ref, studyClone);
		utils.removeStudy(studyClone);
	}

	@Test
	public void callStudiesSubmitEdited() throws Exception {
		StudyModel studyClone = utils.cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.routes.ref.Studies.submitEdited(studyClone
				.getId());
		checkDeniedAccess(ref);
		checkNotMember(ref, studyClone);
		utils.removeStudy(studyClone);
	}

	@Test
	public void callStudiesSwapLock() throws Exception {
		StudyModel studyClone = utils.cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.routes.ref.Studies.swapLock(studyClone
				.getId());
		checkDeniedAccess(ref);
		checkNotMember(ref, studyClone);
		utils.removeStudy(studyClone);
	}

	@Test
	public void callStudiesRemove() throws Exception {
		StudyModel studyClone = utils.cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.routes.ref.Studies.remove(studyClone
				.getId());
		checkDeniedAccess(ref);
		checkNotMember(ref, studyClone);
		utils.removeStudy(studyClone);
	}

	@Test
	public void callStudiesCloneStudy() throws Exception {
		StudyModel studyClone = utils.cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.routes.ref.Studies.cloneStudy(studyClone
				.getId());
		checkDeniedAccess(ref);
		checkNotMember(ref, studyClone);
		utils.removeStudy(studyClone);
	}

	@Test
	public void callStudiesChangeMember() throws Exception {
		StudyModel studyClone = utils.cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.routes.ref.Studies
				.changeMembers(studyClone.getId());
		checkDeniedAccess(ref);
		checkNotMember(ref, studyClone);
		utils.removeStudy(studyClone);
	}

	@Test
	public void callStudiesSubmitChangedMembers() throws Exception {
		StudyModel studyClone = utils.cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.routes.ref.Studies
				.submitChangedMembers(studyClone.getId());
		checkDeniedAccess(ref);
		checkNotMember(ref, studyClone);
		utils.removeStudy(studyClone);
	}

	@Test
	public void callStudiesChangeComponentOrder() throws Exception {
		StudyModel studyClone = utils.cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.routes.ref.Studies.changeComponentOrder(
				studyClone.getId(), studyClone.getComponentList().get(0)
						.getId(), Studies.COMPONENT_ORDER_DOWN);
		checkDeniedAccess(ref);
		checkNotMember(ref, studyClone);
		utils.removeStudy(studyClone);
	}

	@Test
	public void callStudiesShowStudy() throws Exception {
		StudyModel studyClone = utils.cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.routes.ref.Studies.showStudy(studyClone
				.getId());
		checkDeniedAccess(ref);
		checkNotMember(ref, studyClone);
		utils.removeStudy(studyClone);
	}

	@Test
	public void callStudiesCreateClosedStandaloneRun() throws Exception {
		StudyModel studyClone = utils.cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.routes.ref.Studies
				.createClosedStandaloneRun(studyClone.getId());
		checkDeniedAccess(ref);
		checkNotMember(ref, studyClone);
		utils.removeStudy(studyClone);
	}

	@Test
	public void callStudiesCreateTesterRun() throws Exception {
		StudyModel studyClone = utils.cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.routes.ref.Studies
				.createTesterRun(studyClone.getId());
		checkDeniedAccess(ref);
		checkNotMember(ref, studyClone);
		utils.removeStudy(studyClone);
	}

	@Test
	public void callStudiesShowMTurkSourceCode() throws Exception {
		StudyModel studyClone = utils.cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.routes.ref.Studies
				.showMTurkSourceCode(studyClone.getId());
		checkDeniedAccess(ref);
		checkNotMember(ref, studyClone);
		utils.removeStudy(studyClone);
	}

	@Test
	public void callStudiesWorkers() throws Exception {
		StudyModel studyClone = utils.cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.routes.ref.Studies.workers(studyClone
				.getId());
		checkDeniedAccess(ref);
		checkNotMember(ref, studyClone);
		utils.removeStudy(studyClone);
	}

	@Test
	public void callComponentsShowComponent() throws Exception {
		StudyModel studyClone = utils.cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.routes.ref.Components.showComponent(
				studyClone.getId(), studyClone.getComponent(1).getId());
		checkDeniedAccess(ref);
		checkNotMember(ref, studyClone);
		utils.removeStudy(studyClone);
	}

	@Test
	public void callComponentsCreate() throws IOException {
		StudyModel studyClone = utils.cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.routes.ref.Components.create(studyClone
				.getId());
		checkDeniedAccess(ref);
		checkNotMember(ref, studyClone);
		utils.removeStudy(studyClone);
	}

	@Test
	public void callComponentsSubmit() throws Exception {
		StudyModel studyClone = utils.cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.routes.ref.Components.submit(studyClone
				.getId());
		checkDeniedAccess(ref);
		checkNotMember(ref, studyClone);
		utils.removeStudy(studyClone);
	}

	@Test
	public void callComponentsChangeProperties() throws Exception {
		StudyModel studyClone = utils.cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.routes.ref.Components.changeProperty(
				studyClone.getId(), studyClone.getComponent(1).getId(), true);
		checkDeniedAccess(ref);
		checkNotMember(ref, studyClone);
		utils.removeStudy(studyClone);
	}

	@Test
	public void callComponentsCloneComponent() throws Exception {
		StudyModel studyClone = utils.cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.routes.ref.Components.cloneComponent(
				studyClone.getId(), studyClone.getComponent(1).getId());
		checkDeniedAccess(ref);
		checkNotMember(ref, studyClone);
		utils.removeStudy(studyClone);
	}

	@Test
	public void callComponentsRemove() throws Exception {
		StudyModel studyClone = utils.cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.routes.ref.Components.remove(
				studyClone.getId(), studyClone.getComponent(1).getId());
		checkDeniedAccess(ref);
		checkNotMember(ref, studyClone);
		utils.removeStudy(studyClone);
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
		StudyModel studyClone = utils.cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.routes.ref.ImportExport
				.importComponent(studyClone.getId());
		checkDeniedAccess(ref);
		checkNotMember(ref, studyClone);
		utils.removeStudy(studyClone);
	}

	@Test
	public void callImportExportExportComponent() throws Exception {
		StudyModel studyClone = utils.cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.routes.ref.ImportExport.exportComponent(
				studyClone.getId(), studyClone.getComponent(1).getId());
		checkDeniedAccess(ref);
		checkNotMember(ref, studyClone);
		utils.removeStudy(studyClone);
	}

	@Test
	public void callImportExportExportStudy() throws Exception {
		StudyModel studyClone = utils.cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.routes.ref.ImportExport
				.exportStudy(studyClone.getId());
		checkDeniedAccess(ref);
		checkNotMember(ref, studyClone);
		utils.removeStudy(studyClone);
	}

	@Test
	public void callComponentResultsIndex() throws Exception {
		StudyModel studyClone = utils.cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.routes.ref.ComponentResults.index(
				studyClone.getId(), studyClone.getComponent(1).getId());
		checkDeniedAccess(ref);
		checkNotMember(ref, studyClone);
		utils.removeStudy(studyClone);
	}

	@Test
	public void callComponentResultsRemove() throws Exception {
		HandlerRef ref = controllers.routes.ref.ComponentResults.remove("1");
		checkDeniedAccess(ref);
		// TODO check whether result's study has appropriate member
	}

	@Test
	public void callComponentResultsTableDataByComponent() throws Exception {
		StudyModel studyClone = utils.cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.routes.ref.ComponentResults
				.tableDataByComponent(studyClone.getId(), studyClone
						.getComponent(1).getId());
		checkDeniedAccess(ref);
		checkNotMember(ref, studyClone);
		utils.removeStudy(studyClone);
	}

	@Test
	public void callComponentResultsExportData() throws Exception {
		HandlerRef ref = controllers.routes.ref.ComponentResults
				.exportData("1");
		checkDeniedAccess(ref);
		// TODO check whether result's study has appropriate member
	}

	@Test
	public void callStudyResultsIndex() throws Exception {
		StudyModel studyClone = utils.cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.routes.ref.StudyResults.index(studyClone
				.getId());
		checkDeniedAccess(ref);
		checkNotMember(ref, studyClone);
		utils.removeStudy(studyClone);
	}

	@Test
	public void callStudyResultsRemove() throws Exception {
		HandlerRef ref = controllers.routes.ref.StudyResults.remove("1");
		checkDeniedAccess(ref);
		// TODO check whether result's study has appropriate member
	}

	@Test
	public void callStudyResultsTableDataByStudy() throws Exception {
		StudyModel studyClone = utils.cloneAndPersistStudy(studyTemplate);
		HandlerRef ref = controllers.routes.ref.StudyResults
				.tableDataByStudy(studyClone.getId());
		checkDeniedAccess(ref);
		checkNotMember(ref, studyClone);
		utils.removeStudy(studyClone);
	}

	@Test
	public void callStudyResultsTableDataByWorker() throws Exception {
		HandlerRef ref = controllers.routes.ref.StudyResults
				.tableDataByWorker(1l);
		checkDeniedAccess(ref);
		// TODO check whether result's study has appropriate member
	}

	@Test
	public void callStudyResultsExportData() throws Exception {
		HandlerRef ref = controllers.routes.ref.StudyResults.exportData("1");
		checkDeniedAccess(ref);
		// TODO check whether result's study has appropriate member
	}

	@Test
	public void callUsersProfile() throws Exception {
		HandlerRef ref = controllers.routes.ref.Users.profile(testUser
				.getEmail());
		checkDeniedAccess(ref);
		checkRightUser(ref);
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
		checkRightUser(ref);
	}

	@Test
	public void callUsersSubmitEditedProfile() throws Exception {
		HandlerRef ref = controllers.routes.ref.Users
				.submitEditedProfile(testUser.getEmail());
		checkDeniedAccess(ref);
		checkRightUser(ref);
	}

	@Test
	public void callUsersChangePassword() throws Exception {
		HandlerRef ref = controllers.routes.ref.Users.changePassword(testUser
				.getEmail());
		checkDeniedAccess(ref);
		checkRightUser(ref);
	}

	@Test
	public void callUsersSubmitChangedPassword() throws Exception {
		HandlerRef ref = controllers.routes.ref.Users
				.submitChangedPassword(testUser.getEmail());
		checkDeniedAccess(ref);
		checkRightUser(ref);
	}

	@Test
	public void callWorkersIndex() throws Exception {
		HandlerRef ref = controllers.routes.ref.Workers.index(utils.admin
				.getWorker().getId());
		checkDeniedAccess(ref);
	}

	@Test
	public void callWorkersRemove() throws Exception {
		HandlerRef ref = controllers.routes.ref.Workers.remove(utils.admin
				.getWorker().getId());
		checkDeniedAccess(ref);
		checkRemoveJatosWorker(ref);
	}

}
