import static org.fest.assertions.Assertions.assertThat;
import static play.test.Helpers.callAction;
import static play.test.Helpers.fakeRequest;

import java.io.IOException;

import models.StudyModel;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import play.mvc.HandlerRef;
import services.IOUtils;
import controllers.Studies;
import controllers.Users;
import exceptions.ResultException;

/**
 * Testing actions if study is locked
 * 
 * @author Kristian Lange
 */
public class LockedStudyControllerTest {

	private static ControllerTestUtils utils = new ControllerTestUtils();
	private static StudyModel studyTemplate;

	@BeforeClass
	public static void startApp() throws Exception {
		utils.startApp();
		studyTemplate = utils.importExampleStudy();
	}

	@AfterClass
	public static void stopApp() throws IOException {
		IOUtils.removeStudyAssetsDir(studyTemplate.getDirName());
		utils.stopApp();
	}

	private void checkDenyLocked(HandlerRef ref) {
		utils.thrown.expect(RuntimeException.class);
		try {
			callAction(
					ref,
					fakeRequest().withSession(Users.SESSION_EMAIL,
							utils.admin.getEmail()));
		} catch (RuntimeException e) {
			assertThat(e.getMessage()).contains(
					"Unlock it if you want to make changes.");
			assertThat(e.getCause() instanceof ResultException);
		}
	}

	@Test
	public void callStudiesSubmitEdited() throws Exception {
		StudyModel studyClone = utils.cloneAndPersistStudy(studyTemplate);
		utils.lockStudy(studyClone);
		HandlerRef ref = controllers.routes.ref.Studies.submitEdited(studyClone
				.getId());
		checkDenyLocked(ref);
		utils.removeStudy(studyClone);
	}

	@Test
	public void callStudiesRemove() throws Exception {
		StudyModel studyClone = utils.cloneAndPersistStudy(studyTemplate);
		utils.lockStudy(studyClone);
		HandlerRef ref = controllers.routes.ref.Studies.remove(studyClone
				.getId());
		checkDenyLocked(ref);
		utils.removeStudy(studyClone);
	}

	@Test
	public void callStudiesChangeComponentOrder() throws Exception {
		StudyModel studyClone = utils.cloneAndPersistStudy(studyTemplate);
		utils.lockStudy(studyClone);
		HandlerRef ref = controllers.routes.ref.Studies.changeComponentOrder(
				studyClone.getId(), studyClone.getComponent(1).getId(),
				Studies.COMPONENT_ORDER_DOWN);
		checkDenyLocked(ref);
		utils.removeStudy(studyClone);
	}

}
