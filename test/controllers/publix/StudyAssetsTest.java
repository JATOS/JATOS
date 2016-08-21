package controllers.publix;

import static org.fest.assertions.Assertions.assertThat;
import static play.mvc.Http.Status.FORBIDDEN;
import static play.mvc.Http.Status.NOT_FOUND;
import static play.mvc.Http.Status.OK;
import static play.test.Helpers.GET;
import static play.test.Helpers.contentAsString;
import static play.test.Helpers.route;

import java.io.File;
import java.io.IOException;

import org.fest.assertions.Fail;
import org.junit.Test;

import controllers.gui.Users;
import controllers.publix.StudyAssets;
import controllers.publix.workers.JatosPublix;
import controllers.publix.workers.JatosPublix.JatosRun;
import exceptions.publix.NotFoundPublixException;
import exceptions.publix.PublixException;
import general.AbstractTest;
import general.common.MessagesStrings;
import models.common.Study;
import play.mvc.Call;
import play.mvc.Http.RequestBuilder;
import play.mvc.Result;
import play.test.Helpers;

/**
 * Testing controller.publix.StudyAssets
 * 
 * @author Kristian Lange
 */
public class StudyAssetsTest extends AbstractTest {

	private static Study studyExample;
	private StudyAssets studyAssets;

	@Override
	public void before() throws Exception {
		studyAssets = application.injector().instanceOf(StudyAssets.class);
		studyExample = importExampleStudy();
	}

	@Override
	public void after() throws Exception {
		ioUtils.removeStudyAssetsDir(studyExample.getDirName());
	}

	@Test
	public void simpleCheck() {
		int a = 1 + 1;
		assertThat(a).isEqualTo(2);
	}

	@Test
	public void testStudyAssetsRootPath() {
		File studyAssetsRoot = new File(common.getStudyAssetsRootPath());
		assertThat(studyAssetsRoot.exists());
		assertThat(studyAssetsRoot.isDirectory());
		assertThat(studyAssetsRoot.isAbsolute());
	}

	@Test
	public void testVersioned() throws IOException, PublixException {
		Study studyClone = cloneAndPersistStudy(studyExample);
		startStudy(studyClone);

		Call call = controllers.publix.routes.StudyAssets
				.versioned(studyClone.getDirName() + "/"
						+ studyClone.getFirstComponent().getHtmlFilePath());
		RequestBuilder request = new RequestBuilder().method(Helpers.GET)
				.uri(call.url());
		Result result = route(request);
		assertThat(result.status()).isEqualTo(OK);

		// Clean up
		removeStudy(studyClone);
	}

	private void startStudy(Study studyClone) {
		String url = "/publix/" + studyClone.getId() + "/start?"
				+ JatosPublix.JATOS_WORKER_ID + "=" + admin.getWorker().getId();
		RequestBuilder request = new RequestBuilder().method(GET).uri(url)
				.session(Users.SESSION_EMAIL, admin.getEmail())
				.session(JatosPublix.SESSION_JATOS_RUN,
						JatosRun.RUN_STUDY.name());
		route(request);
	}

	@Test
	public void testVersionedNotFound() throws IOException, PublixException {
		Study studyClone = cloneAndPersistStudy(studyExample);
		startStudy(studyClone);

		Call call = controllers.publix.routes.StudyAssets
				.versioned(studyClone.getDirName() + "/non_existend_file");
		RequestBuilder request = new RequestBuilder().method(Helpers.GET)
				.uri(call.url());
		Result result = route(request);
		assertThat(result.status()).isEqualTo(NOT_FOUND);

		// Clean up
		removeStudy(studyClone);
	}

	@Test
	public void testVersionedWrongStudyDir()
			throws IOException, PublixException {
		Study studyClone = cloneAndPersistStudy(studyExample);
		startStudy(studyClone);

		Call call = controllers.publix.routes.StudyAssets
				.versioned("wrong_study_dir/"
						+ studyClone.getFirstComponent().getHtmlFilePath());
		RequestBuilder request = new RequestBuilder().method(Helpers.GET)
				.uri(call.url());
		Result result = route(request);
		assertThat(result.status()).isEqualTo(FORBIDDEN);

		// Clean up
		removeStudy(studyClone);
	}

	@Test
	public void testVersionedWrongAssets() throws IOException, PublixException {
		Study studyClone = cloneAndPersistStudy(studyExample);
		startStudy(studyClone);

		Call call = controllers.publix.routes.StudyAssets
				.versioned(studyClone.getDirName() + "/"
						+ studyClone.getFirstComponent().getHtmlFilePath());
		RequestBuilder request = new RequestBuilder().method(Helpers.GET)
				.uri(call.url());
		Result result = route(request);
		assertThat(result.status()).isEqualTo(FORBIDDEN);

		// Clean up
		removeStudy(studyClone);
	}

	@Test
	public void testVersionedPathTraversalAttack()
			throws IOException, PublixException {
		Study studyClone = cloneAndPersistStudy(studyExample);
		startStudy(studyClone);

		// Although this file exists, it shouldn't be found since all '/..' are
		// removed
		Call call = controllers.publix.routes.StudyAssets.versioned(
				studyClone.getDirName() + "/../../conf/application.conf");
		RequestBuilder request = new RequestBuilder().method(Helpers.GET)
				.uri(call.url());
		Result result = route(request);
		assertThat(result.status()).isEqualTo(NOT_FOUND);

		// Clean up
		removeStudy(studyClone);
	}

	@Test
	public void testRetrieveComponentHtmlFile()
			throws IOException, NotFoundPublixException {
		Study studyClone = cloneAndPersistStudy(studyExample);

		Result result = studyAssets.retrieveComponentHtmlFile(
				studyClone.getDirName(),
				studyClone.getFirstComponent().getHtmlFilePath());

		assertThat(result.status()).isEqualTo(OK);
		assertThat(result.charset()).isEqualTo("utf-8");
		assertThat(result.contentType()).isEqualTo("text/html");
		// And check a random line of the JS code
		assertThat(contentAsString(result))
				.contains("jatos.onLoad(function() {");

		// Clean up
		removeStudy(studyClone);
	}

	@Test
	public void testRetrieveComponentHtmlFileNotFound()
			throws IOException, PublixException {
		Study studyClone = cloneAndPersistStudy(studyExample);

		try {
			studyAssets.retrieveComponentHtmlFile(studyClone.getDirName(),
					"/someNotExistingPath");
			Fail.fail();
		} catch (NotFoundPublixException e) {
			assertThat(e.getMessage())
					.isEqualTo(MessagesStrings.htmlFilePathNotExist(
							studyClone.getDirName(), "/someNotExistingPath"));
		}

		// Clean up
		removeStudy(studyClone);
	}

}
