package publix.controllers;

import static org.fest.assertions.Assertions.assertThat;
import static play.mvc.Http.Status.NOT_FOUND;
import static play.mvc.Http.Status.OK;
import static play.test.Helpers.contentAsString;
import static play.test.Helpers.route;

import java.io.File;
import java.io.IOException;

import org.fest.assertions.Fail;
import org.junit.Test;

import controllers.publix.StudyAssets;
import exceptions.publix.NotFoundPublixException;
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
	public void testAt() throws IOException {
		Study studyClone = cloneAndPersistStudy(studyExample);

		Call call = controllers.publix.routes.StudyAssets
				.versioned("basic_example_study/quit_button.html");
		RequestBuilder request = new RequestBuilder().method(Helpers.GET)
				.uri(call.url());
		Result result = route(request);
		assertThat(result.status()).isEqualTo(OK);

		// Clean up
		removeStudy(studyClone);
	}

	@Test
	public void testAtNotFound() {
		Call call = controllers.publix.routes.StudyAssets
				.versioned("non/existend/filepath");
		RequestBuilder request = new RequestBuilder().method(Helpers.GET)
				.uri(call.url());
		Result result = route(request);
		assertThat(result.status()).isEqualTo(NOT_FOUND);

		call = controllers.publix.routes.StudyAssets
				.versioned("non/&?/filepath");
		request = new RequestBuilder().method(Helpers.GET).uri(call.url());
		result = route(request);
		assertThat(result.status()).isEqualTo(NOT_FOUND);
	}

	@Test
	public void testAtPathTraversalAttack() throws IOException {
		Study studyClone = cloneAndPersistStudy(studyExample);

		// Although this file exists, it shouldn't be found
		Call call = controllers.publix.routes.StudyAssets
				.versioned("../../conf/application.conf");
		RequestBuilder request = new RequestBuilder().method(Helpers.GET)
				.uri(call.url());
		Result result = route(request);
		assertThat(result.status()).isEqualTo(NOT_FOUND);

		// Clean up
		removeStudy(studyClone);
	}

	@Test
	public void testGetComponentUrlPath()
			throws IOException, NotFoundPublixException {
		Study studyClone = cloneAndPersistStudy(studyExample);

		String urlPath = StudyAssets.getComponentUrlPath(
				studyClone.getDirName(), studyClone.getFirstComponent());

		assertThat(urlPath).isEqualTo("/" + StudyAssets.URL_STUDY_ASSETS + "/"
				+ studyClone.getDirName() + "/"
				+ studyClone.getFirstComponent().getHtmlFilePath());

		// Clean up
		removeStudy(studyClone);
	}

	@Test
	public void testGetComponentUrlPathNotFound() throws IOException {
		Study studyClone = cloneAndPersistStudy(studyExample);
		studyClone.getFirstComponent().setHtmlFilePath(null);

		try {
			StudyAssets.getComponentUrlPath(studyClone.getDirName(),
					studyClone.getFirstComponent());
			Fail.fail();
		} catch (NotFoundPublixException e) {
			assertThat(e.getMessage()).isEqualTo(MessagesStrings
					.htmlFilePathEmpty(studyClone.getFirstComponent().getId()));
		}

		// Clean up
		removeStudy(studyClone);
	}

	@Test
	public void testGetUrlWithRequestQueryString() throws IOException {
		String url = StudyAssets.getUrlWithQueryString(
				"oldCall?para=foo&puru=bar", "localhost:9000/", "newCall");
		assertThat(url)
				.isEqualTo("http://localhost:9000/newCall?para=foo&puru=bar");
	}

	@Test
	public void testForwardTo() throws IOException, NotFoundPublixException {
		Study studyClone = cloneAndPersistStudy(studyExample);
		mockContext();

		String urlPath = StudyAssets.getComponentUrlPath(
				studyClone.getDirName(), studyClone.getFirstComponent());
		Result result = studyAssets
				.forwardTo("http://localhost:"
						+ play.api.test.Helpers.testServerPort() + urlPath)
				.get(10000);

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
	public void testForwardToNotFound()
			throws IOException, NotFoundPublixException {
		Study studyClone = cloneAndPersistStudy(studyExample);
		mockContext();

		Result result = studyAssets.forwardTo(
				"http://localhost:" + play.api.test.Helpers.testServerPort()
						+ "/someNotExistingPath")
				.get(10000);

		assertThat(result.status()).isEqualTo(OK);
		assertThat(contentAsString(result)).contains(
				"Requested page &quot;/someNotExistingPath&quot; couldn&#x27;t be found.");

		// Clean up
		removeStudy(studyClone);
	}

}
