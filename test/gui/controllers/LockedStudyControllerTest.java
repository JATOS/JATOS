package gui.controllers;

import static org.fest.assertions.Assertions.assertThat;
import static play.test.Helpers.route;

import java.io.IOException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import controllers.gui.Users;
import gui.AbstractTest;
import models.common.Study;
import models.common.StudyResult;
import play.mvc.Call;
import play.mvc.Http;
import play.mvc.Http.RequestBuilder;
import play.test.Helpers;
import play.mvc.Result;
import services.gui.StudyService;

/**
 * Testing actions if study is locked
 * 
 * @author Kristian Lange
 */
public class LockedStudyControllerTest extends AbstractTest {

	private static Study studyTemplate;

	@Rule
	public ExpectedException thrown = ExpectedException.none();

	@Override
	public void before() throws Exception {
		studyTemplate = importExampleStudy();
	}

	@Override
	public void after() throws Exception {
		ioUtils.removeStudyAssetsDir(studyTemplate.getDirName());
	}

	private void checkDenyLocked(Call call, int statusCode,
			String redirectPath, String method) {
		RequestBuilder request = new RequestBuilder().method(method)
				.session(Users.SESSION_EMAIL, admin.getEmail()).uri(call.url());
		Result result = route(request);

		assertThat(result.status()).isEqualTo(statusCode);
		if (statusCode == Http.Status.SEE_OTHER) {
			assertThat(result.redirectLocation()).isEqualTo(redirectPath);
		}
	}

	@Test
	public void callStudiesSubmitEdited() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		lockStudy(studyClone);
		Call call = controllers.gui.routes.Studies
				.submitEdited(studyClone.getId());
		checkDenyLocked(call, Http.Status.SEE_OTHER,
				"/jatos/" + studyClone.getId(), Helpers.POST);
		removeStudy(studyClone);
	}

	@Test
	public void callStudiesRemove() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		lockStudy(studyClone);
		Call call = controllers.gui.routes.Studies.remove(studyClone.getId());
		checkDenyLocked(call, Http.Status.FORBIDDEN, null, Helpers.DELETE);
		removeStudy(studyClone);
	}

	@Test
	public void callStudiesChangeComponentOrder() throws Exception {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		lockStudy(studyClone);
		Call call = controllers.gui.routes.Studies.changeComponentOrder(
				studyClone.getId(), studyClone.getComponent(1).getId(),
				StudyService.COMPONENT_POSITION_DOWN);
		checkDenyLocked(call, Http.Status.FORBIDDEN, null, Helpers.POST);
		removeStudy(studyClone);
	}

	@Test
	public void callExportComponentResults() throws IOException {
		Study studyClone = cloneAndPersistStudy(studyTemplate);
		lockStudy(studyClone);

		// Create some results
		entityManager.getTransaction().begin();
		StudyResult studyResult = studyResultDao.create(studyClone,
				admin.getWorker());
		// Have to set worker manually in test - don't know why
		studyResult.setWorker(admin.getWorker());
		// Have to set study manually in test - don't know why
		studyClone.getFirstComponent().setStudy(studyClone);
		// TODO
		// jatosPublixUtils.startComponent(studyClone.getFirstComponent(),
		// studyResult);
		// jatosPublixUtils.startComponent(studyClone.getFirstComponent(),
		// studyResult);
		entityManager.getTransaction().commit();

		RequestBuilder request = new RequestBuilder().method("GET")
				.session(Users.SESSION_EMAIL, admin.getEmail())
				.uri(controllers.gui.routes.ImportExport
						.exportDataOfComponentResults("1").url());
		Result result = route(request);

		assertThat(result.status()).isEqualTo(Http.Status.OK);

		// Clean up
		removeStudy(studyClone);
	}

}
