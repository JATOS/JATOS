package gui.controllers;

import static org.fest.assertions.Assertions.assertThat;
import static play.test.Helpers.callAction;
import static play.test.Helpers.fakeRequest;
import static play.test.Helpers.redirectLocation;
import static play.test.Helpers.status;

import java.io.IOException;

import gui.AbstractTest;
import models.StudyModel;
import models.StudyResult;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import common.Global;
import persistance.StudyResultDao;
import play.mvc.HandlerRef;
import play.mvc.Http;
import play.mvc.Result;
import publix.controllers.jatos.JatosPublixUtils;
import publix.exceptions.ForbiddenPublixException;
import publix.exceptions.ForbiddenReloadException;
import services.StudyService;
import utils.IOUtils;
import controllers.Users;

/**
 * Testing actions if study is locked
 * 
 * @author Kristian Lange
 */
public class LockedStudyControllerTest extends AbstractTest {

	private static StudyModel studyTemplate;
	private JatosPublixUtils jatosPublixUtils;
	private StudyResultDao studyResultDao;

	@Rule
	public ExpectedException thrown = ExpectedException.none();

	@Override
	public void before() throws Exception {
		studyResultDao = Global.INJECTOR.getInstance(StudyResultDao.class);
		jatosPublixUtils = Global.INJECTOR.getInstance(JatosPublixUtils.class);
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
		HandlerRef ref = controllers.routes.ref.Studies
				.submitEdited(studyClone.getId());
		checkDenyLocked(ref, Http.Status.SEE_OTHER,
				"/jatos/" + studyClone.getId());
		removeStudy(studyClone);
	}

	@Test
	public void callStudiesRemove() throws Exception {
		StudyModel studyClone = cloneAndPersistStudy(studyTemplate);
		lockStudy(studyClone);
		HandlerRef ref = controllers.routes.ref.Studies.remove(studyClone
				.getId());
		checkDenyLocked(ref, Http.Status.FORBIDDEN, null);
		removeStudy(studyClone);
	}

	@Test
	public void callStudiesChangeComponentOrder() throws Exception {
		StudyModel studyClone = cloneAndPersistStudy(studyTemplate);
		lockStudy(studyClone);
		HandlerRef ref = controllers.routes.ref.Studies
				.changeComponentOrder(studyClone.getId(), studyClone
						.getComponent(1).getId(),
						StudyService.COMPONENT_POSITION_DOWN);
		checkDenyLocked(ref, Http.Status.FORBIDDEN, null);
		removeStudy(studyClone);
	}
	
	@Test
	public void callExportComponentResults() throws IOException,
			ForbiddenPublixException, ForbiddenReloadException {
		StudyModel studyClone = cloneAndPersistStudy(studyTemplate);
		lockStudy(studyClone);
		
		// Create some results
		entityManager.getTransaction().begin();
		StudyResult studyResult = studyResultDao.create(studyClone,
				admin.getWorker());
		// Have to set worker manually in test - don't know why
		studyResult.setWorker(admin.getWorker());
		// Have to set study manually in test - don't know why
		studyClone.getFirstComponent().setStudy(studyClone);
		jatosPublixUtils.startComponent(studyClone.getFirstComponent(), studyResult);
		jatosPublixUtils.startComponent(studyClone.getFirstComponent(), studyResult);
		entityManager.getTransaction().commit();
		
		HandlerRef ref = controllers.routes.ref.ImportExport
				.exportDataOfComponentResults("1");
		Result result = callAction(ref,
				fakeRequest()
						.withSession(Users.SESSION_EMAIL, admin.getEmail()));
		assertThat(status(result)).isEqualTo(Http.Status.OK);
		
		// Clean up
		removeStudy(studyClone);
	}

}
