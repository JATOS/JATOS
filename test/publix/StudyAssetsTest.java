package publix;

import static org.fest.assertions.Assertions.assertThat;
import static play.mvc.Http.Status.NOT_FOUND;
import static play.mvc.Http.Status.OK;
import static play.test.Helpers.callAction;
import static play.test.Helpers.status;
import gui.AbstractTest;

import java.io.File;
import java.io.IOException;

import models.StudyModel;

import org.junit.Test;

import play.mvc.Result;
import publix.controllers.StudyAssets;
import utils.IOUtils;

/**
 * Testing controller.publix.StudyAssets
 * 
 * @author Kristian Lange
 */
public class StudyAssetsTest extends AbstractTest {

	private static StudyModel studyExample;

	@Override
	public void before() throws Exception {
		studyExample = importExampleStudy();
	}

	@Override
	public void after() throws Exception {
		IOUtils.removeStudyAssetsDir(studyExample.getDirName());
	}

	@Test
	public void simpleCheck() {
		int a = 1 + 1;
		assertThat(a).isEqualTo(2);
	}

	@Test
	public void testStudyAssetsRootPath() {
		File studyAssetsRoot = new File(StudyAssets.STUDY_ASSETS_ROOT_PATH);
		assertThat(studyAssetsRoot.exists());
		assertThat(studyAssetsRoot.isDirectory());
		assertThat(studyAssetsRoot.isAbsolute());
	}

	@Test
	public void testAt() throws IOException {
		StudyModel studyClone = cloneAndPersistStudy(studyExample);

		Result result = callAction(publix.controllers.routes.ref.StudyAssets
				.at("basic_example_study/quit_button.html"));
		assertThat(status(result)).isEqualTo(OK);

		// Clean up
		removeStudy(studyClone);
	}

	@Test
	public void testAtNotFound() {
		Result result = callAction(publix.controllers.routes.ref.StudyAssets
				.at("non/existend/filepath"));
		assertThat(status(result)).isEqualTo(NOT_FOUND);

		result = callAction(publix.controllers.routes.ref.StudyAssets
				.at("non/&?/filepath"));
		assertThat(status(result)).isEqualTo(NOT_FOUND);
	}

	@Test
	public void testAtPathTraversalAttack() throws IOException {
		StudyModel studyClone = cloneAndPersistStudy(studyExample);

		// Although this file exists, it shouldn't be found
		Result result = callAction(publix.controllers.routes.ref.StudyAssets
				.at("../../conf/application.conf"));
		assertThat(status(result)).isEqualTo(NOT_FOUND);

		// Clean up
		removeStudy(studyClone);
	}

	@Test
	public void testGetUrlWithRequestQueryString() throws IOException {
		String url = StudyAssets.getUrlWithQueryString(
				"oldCall?para=foo&puru=bar", "localhost:9000/", "newCall");
		assertThat(url).isEqualTo(
				"http://localhost:9000/newCall?para=foo&puru=bar");
	}

}
