package gui.controllers;

import static org.fest.assertions.Assertions.assertThat;
import static play.test.Helpers.callAction;
import static play.test.Helpers.fakeRequest;
import static play.test.Helpers.redirectLocation;
import static play.test.Helpers.status;

import java.io.IOException;

import gui.AbstractGuiTest;
import models.StudyModel;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import play.mvc.HandlerRef;
import play.mvc.Http;
import play.mvc.Result;
import services.gui.StudyService;
import utils.IOUtils;
import controllers.gui.Users;

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

	private void checkDenyLocked(HandlerRef ref, int statusCode,
			String redirectPath) {
		Result result = callAction(ref,
				fakeRequest()
						.withSession(Users.SESSION_EMAIL, admin.getEmail()));
		assertThat(status(result)).isEqualTo(statusCode);
		if (statusCode == Http.Status.SEE_OTHER) {
			assertThat(redirectLocation(result)).isEqualTo(redirectPath);
		}
	}

	@Test
	public void callStudiesSubmitEdited() throws Exception {
		StudyModel studyClone = cloneAndPersistStudy(studyTemplate);
		lockStudy(studyClone);
		HandlerRef ref = controllers.gui.routes.ref.Studies
				.submitEdited(studyClone.getId());
		checkDenyLocked(ref, Http.Status.SEE_OTHER,
				"/jatos/" + studyClone.getId());
		removeStudy(studyClone);
	}

	@Test
	public void callStudiesRemove() throws Exception {
		StudyModel studyClone = cloneAndPersistStudy(studyTemplate);
		lockStudy(studyClone);
		HandlerRef ref = controllers.gui.routes.ref.Studies.remove(studyClone
				.getId());
		checkDenyLocked(ref, Http.Status.FORBIDDEN, null);
		removeStudy(studyClone);
	}

	@Test
	public void callStudiesChangeComponentOrder() throws Exception {
		StudyModel studyClone = cloneAndPersistStudy(studyTemplate);
		lockStudy(studyClone);
		HandlerRef ref = controllers.gui.routes.ref.Studies
				.changeComponentOrder(studyClone.getId(), studyClone
						.getComponent(1).getId(),
						StudyService.COMPONENT_POSITION_DOWN);
		checkDenyLocked(ref, Http.Status.FORBIDDEN, null);
		removeStudy(studyClone);
	}
	
	@Test
	public void callExportComponentResults() throws IOException {
		StudyModel studyClone = cloneAndPersistStudy(studyTemplate);
		lockStudy(studyClone);
		
		HandlerRef ref = controllers.gui.routes.ref.ComponentResults
				.exportData("1");
		Result result = callAction(ref,
				fakeRequest()
						.withSession(Users.SESSION_EMAIL, admin.getEmail()));
		assertThat(status(result)).isEqualTo(Http.Status.OK);
	}

}
