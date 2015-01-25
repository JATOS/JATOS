import static org.fest.assertions.Assertions.assertThat;
import static play.mvc.Http.Status.OK;
import static play.test.Helpers.callAction;
import static play.test.Helpers.contentAsString;
import static play.test.Helpers.fakeRequest;
import static play.test.Helpers.session;
import static play.test.Helpers.status;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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

	private File studyZip = new File("test/basic_example_study.zip");
	private String studyZipBkpName = "test/basic_example_study_bkp.zip";
	private File studyZipBkp = new File(studyZipBkpName);

	@BeforeClass
	public static void startApp() throws Exception {
		utils.startApp();
	}

	@AfterClass
	public static void stopApp() throws IOException {
		utils.stopApp();
	}

	@Test
	public synchronized void callImportStudy() throws Exception {
		// First call: importStudy()
		Result result = callAction(
				controllers.routes.ref.ImportExport.importStudy(),
				fakeRequest().withSession(Users.SESSION_EMAIL,
						Initializer.ADMIN_EMAIL).withAnyContent(
						getMultiPartFormDataForFileUpload(studyZip,
								studyZipBkpName, studyZipBkp),
						"multipart/form-data", "POST"));

		// Tests
		assertThat(status(result)).isEqualTo(OK);
		// Check returned JSON
		JsonNode jsonNode = JsonUtils.OBJECTMAPPER
				.readTree(contentAsString(result));
		assertThat(!jsonNode.get(ImportExport.STUDY_EXISTS).asBoolean());
		assertThat(jsonNode.has(ImportExport.STUDY_TITLE));
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

		// Clean up
		if (studyZipBkp.exists()) {
			studyZipBkp.delete();
		}

		ObjectNode jsonObj = JsonUtils.OBJECTMAPPER.createObjectNode();
		jsonObj.put(ImportExport.STUDYS_PROPERTIES_CONFIRM, true);
		jsonObj.put(ImportExport.STUDYS_DIR_CONFIRM, true);

		// Second call: importStudyConfirmed()
		result = callAction(
				controllers.routes.ref.ImportExport.importStudyConfirmed(),
				fakeRequest()
						.withSession(Users.SESSION_EMAIL,
								Initializer.ADMIN_EMAIL)
						.withSession(ImportExport.SESSION_UNZIPPED_STUDY_DIR,
								unzippedStudyDirName).withJsonBody(jsonObj));
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

	@Test
	public synchronized void callImportStudyPropertiesOnly() throws Exception {

	}

	@Test
	public synchronized void callImportStudyDirOnly() throws Exception {

	}

	private AnyContent getMultiPartFormDataForFileUpload(File studyZip,
			String studyZipBkpName, File studyZipBkp) throws IOException {
		FileUtils.copyFile(studyZip, studyZipBkp);
		FilePart<TemporaryFile> part = new MultipartFormData.FilePart<>(
				StudyModel.STUDY, studyZipBkpName,
				Scala.Option("application/zip"), new TemporaryFile(studyZipBkp));
		List<FilePart<TemporaryFile>> fileParts = new ArrayList<>();
		fileParts.add(part);
		scala.collection.immutable.List<FilePart<TemporaryFile>> files = scala.collection.JavaConversions
				.asScalaBuffer(fileParts).toList();
		MultipartFormData<TemporaryFile> formData = new MultipartFormData<TemporaryFile>(
				null, files, null, null);
		AnyContent anyContent = new AnyContentAsMultipartFormData(formData);
		return anyContent;
	}
}
