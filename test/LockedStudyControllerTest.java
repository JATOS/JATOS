import static org.fest.assertions.Assertions.assertThat;
import static play.mvc.Http.Status.SEE_OTHER;
import static play.test.Helpers.callAction;
import static play.test.Helpers.fakeRequest;
import static play.test.Helpers.redirectLocation;
import static play.test.Helpers.status;

import java.io.IOException;

import models.StudyModel;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import play.mvc.HandlerRef;
import play.mvc.Result;
import utils.IOUtils;

import com.google.inject.Guice;
import com.google.inject.Injector;

import controllers.Studies;
import controllers.Users;

/**
 * Testing actions if study is locked
 * 
 * @author Kristian Lange
 */
public class LockedStudyControllerTest {

	private static ControllerTestUtils utils;
	private static StudyModel studyTemplate;

	@Rule
	public ExpectedException thrown = ExpectedException.none();

	@BeforeClass
	public static void startApp() throws Exception {
		Injector injector = Guice.createInjector();
		utils = injector.getInstance(ControllerTestUtils.class);
		utils.startApp();
		studyTemplate = utils.importExampleStudy();
	}

	@AfterClass
	public static void stopApp() throws IOException {
		IOUtils.removeStudyAssetsDir(studyTemplate.getDirName());
		utils.stopApp();
	}

	private void checkDenyLocked(HandlerRef ref) {
		Result result = callAction(
				ref,
				fakeRequest().withSession(Users.SESSION_EMAIL,
						utils.admin.getEmail()));
		assertThat(status(result)).isEqualTo(SEE_OTHER);
		assertThat(redirectLocation(result)).contains("/jatos/");
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
