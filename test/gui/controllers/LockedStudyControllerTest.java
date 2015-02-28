package gui.controllers;

import static org.fest.assertions.Assertions.assertThat;
import static play.mvc.Http.Status.SEE_OTHER;
import static play.test.Helpers.callAction;
import static play.test.Helpers.fakeRequest;
import static play.test.Helpers.redirectLocation;
import static play.test.Helpers.status;
import gui.AbstractGuiTest;
import models.StudyModel;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import controllers.gui.Studies;
import controllers.gui.Users;
import play.mvc.HandlerRef;
import play.mvc.Result;
import utils.IOUtils;

/**
 * Testing actions if study is locked
 * 
 * @author Kristian Lange
 */
public class LockedStudyControllerTest extends AbstractGuiTest {

	private static StudyModel studyTemplate;

	@Rule
	public ExpectedException thrown = ExpectedException.none();

	@Override
	public void before() throws Exception {
		studyTemplate = importExampleStudy();
	}

	@Override
	public void after() throws Exception {
		IOUtils.removeStudyAssetsDir(studyTemplate.getDirName());
	}

	private void checkDenyLocked(HandlerRef ref) {
		Result result = callAction(ref,
				fakeRequest()
						.withSession(Users.SESSION_EMAIL, admin.getEmail()));
		assertThat(status(result)).isEqualTo(SEE_OTHER);
		assertThat(redirectLocation(result)).contains("/jatos/");
	}

	@Test
	public void callStudiesSubmitEdited() throws Exception {
		StudyModel studyClone = cloneAndPersistStudy(studyTemplate);
		lockStudy(studyClone);
		HandlerRef ref = controllers.gui.routes.ref.Studies
				.submitEdited(studyClone.getId());
		checkDenyLocked(ref);
		removeStudy(studyClone);
	}

	@Test
	public void callStudiesRemove() throws Exception {
		StudyModel studyClone = cloneAndPersistStudy(studyTemplate);
		lockStudy(studyClone);
		HandlerRef ref = controllers.gui.routes.ref.Studies.remove(studyClone
				.getId());
		checkDenyLocked(ref);
		removeStudy(studyClone);
	}

	@Test
	public void callStudiesChangeComponentOrder() throws Exception {
		StudyModel studyClone = cloneAndPersistStudy(studyTemplate);
		lockStudy(studyClone);
		HandlerRef ref = controllers.gui.routes.ref.Studies
				.changeComponentOrder(studyClone.getId(), studyClone
						.getComponent(1).getId(),
						Studies.COMPONENT_POSITION_DOWN);
		checkDenyLocked(ref);
		removeStudy(studyClone);
	}

}
