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

import javax.persistence.EntityManager;

import models.StudyModel;
import models.UserModel;

import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import play.Logger;
import play.api.libs.Files.TemporaryFile;
import play.api.mvc.AnyContent;
import play.api.mvc.AnyContentAsMultipartFormData;
import play.api.mvc.MultipartFormData;
import play.api.mvc.MultipartFormData.FilePart;
import play.db.jpa.JPA;
import play.db.jpa.JPAPlugin;
import play.libs.Scala;
import play.mvc.Result;
import play.test.FakeApplication;
import play.test.Helpers;
import scala.Option;
import services.JsonUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import common.Initializer;
import controllers.ImportExport;
import controllers.Users;
import controllers.publix.StudyAssets;

/**
 * Testing actions of controller.Studies.
 * 
 * @author Kristian Lange
 */
public class ImportExportControllerTest {

	private static final String CLASS_NAME = ImportExportControllerTest.class
			.getSimpleName();

	protected static FakeApplication application;
	protected static EntityManager entityManager;
	protected static UserModel admin;

	private File studyZip = new File("test/basic_example_study.zip");
	private String studyZipBkpName = "test/basic_example_study_bkp.zip";
	private File studyZipBkp = new File(studyZipBkpName);

	@BeforeClass
	public static void startApp() throws Exception {
		application = Helpers.fakeApplication();
		Helpers.start(application);

		Option<JPAPlugin> jpaPlugin = application.getWrappedApplication()
				.plugin(JPAPlugin.class);
		entityManager = jpaPlugin.get().em("default");
		JPA.bindForCurrentThread(entityManager);

		// Get admin (admin is automatically created during initialization)
		admin = UserModel.findByEmail(Initializer.ADMIN_EMAIL);
	}

	@AfterClass
	public static void stopApp() throws IOException {
		entityManager.close();
		JPA.bindForCurrentThread(null);
		boolean result = new File(StudyAssets.STUDY_ASSETS_ROOT_PATH).delete();
		if (!result) {
			Logger.warn(CLASS_NAME
					+ ".stopApp: Couldn't remove study assets root directory "
					+ StudyAssets.STUDY_ASSETS_ROOT_PATH
					+ " after finished testing. This should not happen.");
		}
		Helpers.stop(application);
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
