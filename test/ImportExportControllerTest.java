import static org.fest.assertions.Assertions.assertThat;
import static play.mvc.Http.Status.OK;
import static play.test.Helpers.callAction;
import static play.test.Helpers.contentAsString;
import static play.test.Helpers.contentType;
import static play.test.Helpers.fakeRequest;
import static play.test.Helpers.session;
import static play.test.Helpers.status;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import models.ComponentModel;
import models.StudyModel;

import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import play.api.libs.Files.TemporaryFile;
import play.api.mvc.AnyContent;
import play.api.mvc.AnyContentAsMultipartFormData;
import play.api.mvc.MultipartFormData;
import play.api.mvc.MultipartFormData.FilePart;
import play.libs.Scala;
import play.mvc.Result;
import services.IOUtils;
import services.JsonUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import common.Initializer;
import controllers.ImportExport;
import controllers.Users;

/**
 * Testing actions of controller.Studies.
 * 
 * @author Kristian Lange
 */
public class ImportExportControllerTest {

	private static ControllerTestUtils utils = new ControllerTestUtils();

	@BeforeClass
	public static void startApp() throws Exception {
		utils.startApp();
	}

	@AfterClass
	public static void stopApp() throws IOException {
		utils.stopApp();
	}

	/**
	 * Checks call to ImportExportController.importStudy() and
	 * ImportExportController.importStudyConfirmed(). Both calls always happen
	 * one after another.
	 */
	@Test
	public synchronized void checkCallImportStudy() throws Exception {
		// First call
		Result result = callImportStudy();

		// Tests
		assertThat(status(result)).isEqualTo(OK);
		// Check returned JSON
		JsonNode jsonNode = JsonUtils.OBJECTMAPPER
				.readTree(contentAsString(result));
		// Study does not exist
		assertThat(!jsonNode.get(ImportExport.STUDY_EXISTS).asBoolean());
		assertThat(jsonNode.has(ImportExport.STUDY_TITLE));
		// Study assets dir does not exist
		assertThat(!jsonNode.get(ImportExport.DIR_EXISTS).asBoolean());
		assertThat(jsonNode.has(ImportExport.DIR_PATH));
		// Name of unzipped study dir in session
		String unzippedStudyDirName = session(result).get(
				ImportExport.SESSION_UNZIPPED_STUDY_DIR);
		assertThat(unzippedStudyDirName != null
				&& !unzippedStudyDirName.isEmpty());
		// There should be a unzipped study dir in tmp
		File unzippedStudyDir = new File(System.getProperty("java.io.tmpdir"),
				unzippedStudyDirName);
		assertThat(unzippedStudyDir.exists() && unzippedStudyDir.isDirectory());

		// Second call
		result = callImportStudyConfirmed(unzippedStudyDirName, true, true);

		// Tests
		assertThat(status(result)).isEqualTo(OK);
		// Should return the study ID
		assertThat(contentAsString(result).length() > 0);
		// Should have deleted the unzipped study dir in tmp
		assertThat(!unzippedStudyDir.exists());

		// Clean up, third call: remove()
		StudyModel importedStudy = StudyModel
				.findByUuid("5c85bd82-0258-45c6-934a-97ecc1ad6617");
		result = callAction(
				controllers.routes.ref.Studies.remove(importedStudy.getId()),
				fakeRequest().withSession(Users.SESSION_EMAIL,
						Initializer.ADMIN_EMAIL));
	}

	/**
	 * Like checkCallImportStudy(), but this time the study exists already in
	 * the DB and should be overridden or not.
	 */
	@Test
	public synchronized void checkCallImportStudyOverride() throws Exception {
		// Import study manually
		StudyModel study = utils.importExampleStudy();
		// Change study a little so we have something to check later
		study.setTitle("Different Title");
		// Change a file name
		File file_orig = IOUtils.getFileInStudyAssetsDir(study.getDirName(),
				"hello_world.html");
		File file_renamed = IOUtils.getFileInStudyAssetsDir(study.getDirName(),
				"hello_world_renamed.html");
		file_orig.renameTo(file_renamed);
		utils.commitStudy(study);

		// Check renaming
		assertThat(file_renamed.exists());
		assertThat(!file_orig.exists());

		// First call: Import same study second time
		Result result = callImportStudy();

		// Tests
		assertThat(status(result)).isEqualTo(OK);
		// Check returned JSON
		JsonNode jsonNode = JsonUtils.OBJECTMAPPER
				.readTree(contentAsString(result));
		// Study exists already
		assertThat(jsonNode.get(ImportExport.STUDY_EXISTS).asBoolean());
		// Study assets dir exists already
		assertThat(jsonNode.get(ImportExport.DIR_EXISTS).asBoolean());

		String unzippedStudyDirName = session(result).get(
				ImportExport.SESSION_UNZIPPED_STUDY_DIR);

		// Second call: confirm (allow override of properties and dir)
		result = callImportStudyConfirmed(unzippedStudyDirName, true, true);

		// Tests
		assertThat(status(result)).isEqualTo(OK);
		// TODO Would be nice to check. Changes within calls aren't persistent
		// in JUnit tests.
		// assertThat(study.getTitle()).isEqualTo("Basic Example Study");
		// Check that renaming is undone
		assertThat(!file_renamed.exists());
		assertThat(file_orig.exists());

		// Change study a little again so we have something to check later
		study.setTitle("Different Title");
		// Change a file name
		file_orig.renameTo(file_renamed);
		utils.commitStudy(study);

		// Third call: Import same study third time
		result = callImportStudy();

		unzippedStudyDirName = session(result).get(
				ImportExport.SESSION_UNZIPPED_STUDY_DIR);

		// Fourth call: confirm (do not allow override)
		result = callImportStudyConfirmed(unzippedStudyDirName, false, false);

		// Tests
		assertThat(status(result)).isEqualTo(OK);
		// TODO Would be nice to check. Changes within calls aren't persistent
		// in JUnit tests.
		// assertThat(study.getTitle()).isEqualTo("Different Title");
		// Check that renaming is undone
		assertThat(file_renamed.exists());
		assertThat(!file_orig.exists());

		// Clean-up
		utils.removeStudy(study);
	}

	/**
	 * Checks call to ImportExportController.importComponentStudy() and
	 * ImportExportController.importComponentConfirmed(). Both calls always
	 * happen one after another.
	 */
	@Test
	public synchronized void checkCallImportComponent() throws Exception {
		// Import study manually and remove first component (Hello World)
		StudyModel study = utils.importExampleStudy();
		study.removeComponent(study.getFirstComponent());
		utils.commitStudy(study);

		// Make a backup of our component file
		File componentFile = new File("test/hello_world.jac");
		File componentFileBkp = new File(System.getProperty("java.io.tmpdir"),
				"hello_world_bkp.jac");
		FileUtils.copyFile(componentFile, componentFileBkp);

		assertThat(study.getFirstComponent().getTitle()).doesNotContain(
				"Hello World");

		Result result = callAction(
				controllers.routes.ref.ImportExport.importComponent(study
						.getId()),
				fakeRequest().withSession(Users.SESSION_EMAIL,
						Initializer.ADMIN_EMAIL).withAnyContent(
						getMultiPartFormDataForFileUpload(componentFileBkp,
								ComponentModel.COMPONENT, "application/json"),
						"multipart/form-data", "POST"));

		// Tests
		assertThat(status(result)).isEqualTo(OK);
		// Check returned JSON
		JsonNode jsonNode = JsonUtils.OBJECTMAPPER
				.readTree(contentAsString(result));
		// Component does not exist
		assertThat(!jsonNode.get(ImportExport.COMPONENT_EXISTS).asBoolean());
		assertThat(jsonNode.has(ImportExport.COMPONENT_TITLE));

		// The component's file name should be in session
		String sessionFileName = session(result).get(
				ImportExport.SESSION_TEMP_COMPONENT_FILE);
		assertThat(sessionFileName != null && !sessionFileName.isEmpty());
		// The component file should exist in tmp
		File tmpComponentFile = new File(System.getProperty("java.io.tmpdir"),
				sessionFileName);
		assertThat(tmpComponentFile.exists() && !tmpComponentFile.isDirectory());

		result = callAction(
				controllers.routes.ref.ImportExport.importComponentConfirmed(study
						.getId()),
				fakeRequest().withSession(Users.SESSION_EMAIL,
						Initializer.ADMIN_EMAIL).withSession(
						ImportExport.SESSION_TEMP_COMPONENT_FILE,
						sessionFileName));

		// Tests
		assertThat(status(result)).isEqualTo(OK);

		// Clean-up
		if (componentFileBkp.exists()) {
			componentFileBkp.delete();
		}
		utils.removeStudy(study);
	}

	@Test
	public synchronized void checkCallExportStudy() throws Exception {
		StudyModel study = utils.importExampleStudy();
		utils.commitStudy(study);

		Result result = callAction(
				controllers.routes.ref.ImportExport.exportStudy(study.getId()),
				fakeRequest().withSession(Users.SESSION_EMAIL,
						Initializer.ADMIN_EMAIL));
		assertThat(status(result)).isEqualTo(OK);
		assertThat(contentType(result)).isEqualTo("application/x-download");

		// Clean-up
		utils.removeStudy(study);
	}

	@Test
	public synchronized void checkCallExportComponent() throws Exception {
		StudyModel study = utils.importExampleStudy();
		utils.commitStudy(study);

		Result result = callAction(
				controllers.routes.ref.ImportExport.exportComponent(
						study.getId(), study.getComponent(1).getId()),
				fakeRequest().withSession(Users.SESSION_EMAIL,
						Initializer.ADMIN_EMAIL));
		assertThat(status(result)).isEqualTo(OK);
		assertThat(contentType(result)).isEqualTo("application/x-download");

		// Clean-up
		utils.removeStudy(study);
	}

	private Result callImportStudy() throws IOException {
		// Make a backup of our study file
		File studyZip = new File("test/basic_example_study.zip");
		File studyZipBkp = new File("test/basic_example_study_bkp.zip");
		FileUtils.copyFile(studyZip, studyZipBkp);

		Result result = callAction(
				controllers.routes.ref.ImportExport.importStudy(),
				fakeRequest().withSession(Users.SESSION_EMAIL,
						Initializer.ADMIN_EMAIL).withAnyContent(
						getMultiPartFormDataForFileUpload(studyZipBkp,
								StudyModel.STUDY, "application/zip"),
						"multipart/form-data", "POST"));
		// Clean up
		if (studyZipBkp.exists()) {
			studyZipBkp.delete();
		}
		return result;
	}

	private AnyContent getMultiPartFormDataForFileUpload(File file,
			String filePartKey, String contentType) throws IOException {
		FilePart<TemporaryFile> part = new MultipartFormData.FilePart<>(
				filePartKey, file.getName(), Scala.Option(contentType),
				new TemporaryFile(file));
		List<FilePart<TemporaryFile>> fileParts = new ArrayList<>();
		fileParts.add(part);
		scala.collection.immutable.List<FilePart<TemporaryFile>> files = scala.collection.JavaConversions
				.asScalaBuffer(fileParts).toList();
		MultipartFormData<TemporaryFile> formData = new MultipartFormData<TemporaryFile>(
				null, files, null, null);
		AnyContent anyContent = new AnyContentAsMultipartFormData(formData);
		return anyContent;
	}

	private Result callImportStudyConfirmed(String unzippedStudyDirName,
			boolean overrideProperties, boolean overrideDir) {
		ObjectNode jsonObj = JsonUtils.OBJECTMAPPER.createObjectNode();
		jsonObj.put(ImportExport.STUDYS_PROPERTIES_CONFIRM, overrideProperties);
		jsonObj.put(ImportExport.STUDYS_DIR_CONFIRM, overrideDir);

		Result result = callAction(
				controllers.routes.ref.ImportExport.importStudyConfirmed(),
				fakeRequest()
						.withSession(Users.SESSION_EMAIL,
								Initializer.ADMIN_EMAIL)
						.withSession(ImportExport.SESSION_UNZIPPED_STUDY_DIR,
								unzippedStudyDirName).withJsonBody(jsonObj));
		return result;
	}

}
