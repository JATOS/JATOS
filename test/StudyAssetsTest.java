import static org.fest.assertions.Assertions.assertThat;
import static play.mvc.Http.Status.NOT_FOUND;
import static play.mvc.Http.Status.OK;
import static play.test.Helpers.status;

import java.io.File;
import java.io.IOException;

import models.StudyModel;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import play.mvc.Result;
import utils.IOUtils;

import com.google.inject.Guice;
import com.google.inject.Injector;

import controllers.publix.StudyAssets;

/**
 * Testing controller.publix.StudyAssets
 * 
 * @author Kristian Lange
 */
public class StudyAssetsTest {

	private static ControllerTestUtils utils;
	private static StudyAssets studyAssets;
	private static StudyModel studyTemplate;

	@BeforeClass
	public static void startApp() throws Exception {
		Injector injector = Guice.createInjector();
		utils = injector.getInstance(ControllerTestUtils.class);
		utils.startApp();
		studyTemplate = utils.importExampleStudy();
		studyAssets = injector.getInstance(StudyAssets.class);
	}

	@AfterClass
	public static void stopApp() throws IOException {
		IOUtils.removeStudyAssetsDir(studyTemplate.getDirName());
		utils.stopApp();
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
		StudyModel studyClone = utils.cloneAndPersistStudy(studyTemplate);

		Result result = studyAssets.at("basic_example_study/hello_world.html");
		assertThat(status(result)).isEqualTo(OK);

		// Clean up
		utils.removeStudy(studyClone);
	}

	@Test
	public void testAtNotFound() {
		Result result = studyAssets.at("non/existend/filepath");
		assertThat(status(result)).isEqualTo(NOT_FOUND);

		result = studyAssets.at("non/&?/filepath");
		assertThat(status(result)).isEqualTo(NOT_FOUND);
	}

	@Test
	public void testAtPathTraversalAttack() throws IOException {
		StudyModel studyClone = utils.cloneAndPersistStudy(studyTemplate);

		// Although this file exists, it shouldn't be found
		Result result = studyAssets.at("../../conf/application.conf");
		assertThat(status(result)).isEqualTo(NOT_FOUND);

		// Clean up
		utils.removeStudy(studyClone);
	}

	@Test
	public void testGetUrlWithRequestQueryString() throws IOException {
		String url = StudyAssets.getUrlWithQueryString(
				"oldCall?para=foo&puru=bar", "localhost:9000/", "newCall");
		assertThat(url).isEqualTo(
				"http://localhost:9000/newCall?para=foo&puru=bar");
	}

}
