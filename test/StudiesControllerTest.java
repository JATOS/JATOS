import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static play.mvc.Http.Status.OK;
import static play.test.Helpers.callAction;
import static play.test.Helpers.charset;
import static play.test.Helpers.contentAsString;
import static play.test.Helpers.contentType;
import static play.test.Helpers.fakeRequest;
import static play.test.Helpers.headers;
import static play.test.Helpers.status;

import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import javax.persistence.EntityManager;

import models.StudyModel;
import models.UserModel;
import models.workers.ClosedStandaloneWorker;

import org.hamcrest.core.IsInstanceOf;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import play.db.jpa.JPA;
import play.db.jpa.JPAPlugin;
import play.mvc.Result;
import play.test.FakeApplication;
import play.test.FakeRequest;
import play.test.Helpers;
import play.test.WithApplication;
import scala.Option;
import services.Breadcrumbs;
import services.IOUtils;
import services.JsonUtils;
import services.ZipUtil;
import services.JsonUtils.UploadUnmarshaller;
import services.PersistanceUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;

import common.Initializer;
import controllers.Studies;
import controllers.Users;
import controllers.publix.StudyAssets;
import exceptions.ResultException;

/**
 * Testing actions of controller.Studies. We set up a fake application with it's
 * own database.
 * 
 * @author Kristian Lange
 */
public class StudiesControllerTest extends WithApplication {

	private static FakeApplication app;
	private static EntityManager em;
	private static UserModel admin;
	private static StudyModel studyTemplate;

	@Rule
	public ExpectedException thrown = ExpectedException.none();

	@BeforeClass
	public static void startApp() throws Exception {
		app = Helpers.fakeApplication();
		Helpers.start(app);

		Option<JPAPlugin> jpaPlugin = app.getWrappedApplication().plugin(
				JPAPlugin.class);
		em = jpaPlugin.get().em("default");
		JPA.bindForCurrentThread(em);

		// Get admin (admin is automatically created during initialization)
		admin = UserModel.findByEmail(Initializer.ADMIN_EMAIL);

		studyTemplate = importStudyTemplate2();
	}

	@Before
	public void setUp() {
	}

	@After
	public void tearDown() throws IOException {
	}

	@AfterClass
	public static void stopApp() throws IOException {
		em.close();
		JPA.bindForCurrentThread(null);
		IOUtils.removeStudyAssetsDir(studyTemplate.getDirName());
		new File(StudyAssets.STUDY_ASSETS_ROOT_PATH).delete();
		Helpers.stop(app);
	}

	private static StudyModel importStudyTemplate2()
			throws NoSuchAlgorithmException, IOException {
		File studyZip = new File("test/basic_example_study.zip");
		File tempUnzippedStudyDir = ZipUtil.unzip(studyZip);
		File[] studyFileList = IOUtils.findFiles(tempUnzippedStudyDir, "",
				IOUtils.STUDY_FILE_SUFFIX);
		File studyFile = studyFileList[0];
		UploadUnmarshaller uploadUnmarshaller = new UploadUnmarshaller();
		StudyModel importedStudy = uploadUnmarshaller.unmarshalling(studyFile,
				StudyModel.class);
		studyFile.delete();

		File[] dirArray = IOUtils.findDirectories(tempUnzippedStudyDir);
		IOUtils.moveStudyAssetsDir(dirArray[0], importedStudy.getDirName());

		PersistanceUtils.addStudy(importedStudy, admin);
		tempUnzippedStudyDir.delete();
		return importedStudy;
	}

	private synchronized StudyModel cloneStudy() throws IOException {
		JPA.em().getTransaction().begin();
		StudyModel studyClone = new StudyModel(studyTemplate);
		String destDirName;
		destDirName = IOUtils
				.cloneStudyAssetsDirectory(studyClone.getDirName());
		studyClone.setDirName(destDirName);
		PersistanceUtils.addStudy(studyClone, admin);
		JPA.em().getTransaction().commit();
		return studyClone;
	}

	private synchronized void removeStudy(StudyModel study) throws IOException {
		IOUtils.removeStudyAssetsDir(study.getDirName());
		JPA.em().getTransaction().begin();
		PersistanceUtils.removeStudy(study);
		JPA.em().getTransaction().commit();
	}

	@Test
	public void callIndex() throws Exception {
		StudyModel studyClone = cloneStudy();

		Result result = callAction(
				controllers.routes.ref.Studies.index(studyClone.getId(), null),
				fakeRequest().withSession(Users.SESSION_EMAIL,
						Initializer.ADMIN_EMAIL));
		assertThat(status(result)).isEqualTo(OK);
		assertThat(charset(result)).isEqualTo("utf-8");
		assertThat(contentType(result)).isEqualTo("text/html");
		assertThat(contentAsString(result)).contains("Components");

		// Clean up
		removeStudy(studyClone);
	}

	@Test
	public void callIndexButNotMember() throws Exception {
		StudyModel studyClone = cloneStudy();

		JPA.em().getTransaction().begin();
		StudyModel.findById(studyClone.getId()).removeMember(admin);
		JPA.em().getTransaction().commit();

		try {
			callAction(
					controllers.routes.ref.Studies.index(studyClone.getId(),
							null),
					fakeRequest().withSession(Users.SESSION_EMAIL,
							Initializer.ADMIN_EMAIL));
		} catch (RuntimeException e) {
			assert (e.getMessage().contains("isn't member of study"));
			assert (e.getCause() instanceof ResultException);
		} finally {
			removeStudy(studyClone);
		}
	}

	@Test
	public void callCreate() {
		Result result = callAction(
				controllers.routes.ref.Studies.create(),
				fakeRequest().withSession(Users.SESSION_EMAIL,
						Initializer.ADMIN_EMAIL));
		assertThat(status(result)).isEqualTo(OK);
		assertThat(charset(result)).isEqualTo("utf-8");
		assertThat(contentType(result)).isEqualTo("text/html");
		assertThat(contentAsString(result)).contains(Breadcrumbs.NEW_STUDY);
	}

	@Test
	public void callSubmit() throws Exception {
		FakeRequest request = fakeRequest().withSession(Users.SESSION_EMAIL,
				Initializer.ADMIN_EMAIL).withFormUrlEncodedBody(
				ImmutableMap.of(StudyModel.TITLE, "Title Test",
						StudyModel.DESCRIPTION, "Description test.",
						StudyModel.DIRNAME, "dirName_submit",
						StudyModel.JSON_DATA, "{}",
						StudyModel.ALLOWED_WORKER_LIST, ""));
		Result result = callAction(controllers.routes.ref.Studies.submit(),
				request);
		assertEquals(303, status(result));

		// Get study ID of created study from response's header
		String[] locationArray = headers(result).get("Location").split("/");
		Long studyId = Long.valueOf(locationArray[locationArray.length - 1]);

		StudyModel study = StudyModel.findById(studyId);
		assertEquals("Title Test", study.getTitle());
		assertEquals("Description test.", study.getDescription());
		assertEquals("dirName_submit", study.getDirName());
		assertEquals("{ }", study.getJsonData());
		assert (study.getComponentList().isEmpty());
		assert (study.getMemberList().contains(admin));
		assert (!study.isLocked());
		assert (study.getAllowedWorkerList().isEmpty());

		// Clean up
		removeStudy(study);
	}

	@Test
	public void callSubmitValidationError() {
		FakeRequest request = fakeRequest().withSession(Users.SESSION_EMAIL,
				Initializer.ADMIN_EMAIL).withFormUrlEncodedBody(
				ImmutableMap.of(StudyModel.TITLE, " ", StudyModel.DESCRIPTION,
						"Description test.", StudyModel.DIRNAME, "%.test",
						StudyModel.JSON_DATA, "{",
						StudyModel.ALLOWED_WORKER_LIST, "WrongWorker"));

		thrown.expect(RuntimeException.class);
		thrown.expectCause(IsInstanceOf
				.<Throwable> instanceOf(ResultException.class));
		callAction(controllers.routes.ref.Studies.submit(), request);
	}

	@Test
	public void callSubmitStudyAssetsDirExists() throws Exception {
		StudyModel studyClone = cloneStudy();

		FakeRequest request = fakeRequest().withSession(Users.SESSION_EMAIL,
				Initializer.ADMIN_EMAIL).withFormUrlEncodedBody(
				ImmutableMap.of(StudyModel.TITLE, "Title Test",
						StudyModel.DESCRIPTION, "Description test.",
						StudyModel.DIRNAME, studyClone.getDirName(),
						StudyModel.JSON_DATA, "{}",
						StudyModel.ALLOWED_WORKER_LIST, ""));

		try {
			callAction(controllers.routes.ref.Studies.submit(), request);
		} catch (RuntimeException e) {
			assert (e.getCause() instanceof ResultException);
		} finally {
			removeStudy(studyClone);
		}
	}

	@Test
	public void callEdit() throws Exception {
		StudyModel studyClone = cloneStudy();

		Result result = callAction(
				controllers.routes.ref.Studies.edit(studyClone.getId()),
				fakeRequest().withSession(Users.SESSION_EMAIL,
						Initializer.ADMIN_EMAIL));
		assertThat(status(result)).isEqualTo(OK);
		assertThat(charset(result)).isEqualTo("utf-8");
		assertThat(contentType(result)).isEqualTo("text/html");
		assertThat(contentAsString(result)).contains(
				Breadcrumbs.EDIT_PROPERTIES);

		// Clean up
		removeStudy(studyClone);
	}

	@Test
	public void callSubmitEdited() throws Exception {
		StudyModel studyClone = cloneStudy();

		FakeRequest request = fakeRequest().withSession(Users.SESSION_EMAIL,
				Initializer.ADMIN_EMAIL).withFormUrlEncodedBody(
				ImmutableMap.of(StudyModel.TITLE, "Title Test",
						StudyModel.DESCRIPTION, "Description test.",
						StudyModel.DIRNAME, "dirName_submitEdited",
						StudyModel.JSON_DATA, "{}",
						StudyModel.ALLOWED_WORKER_LIST, ""));
		Result result = callAction(
				controllers.routes.ref.Studies.submitEdited(studyClone.getId()),
				request);
		assertEquals(303, status(result));

		// It would be nice to test the edited study here
		// Clean up
		studyClone.setDirName("dirName_submitEdited");
		removeStudy(studyClone);
	}

	@Test
	public void callSwapLock() throws Exception {
		StudyModel studyClone = cloneStudy();

		Result result = callAction(
				controllers.routes.ref.Studies.swapLock(studyClone.getId()),
				fakeRequest().withSession(Users.SESSION_EMAIL,
						Initializer.ADMIN_EMAIL));
		assertThat(status(result)).isEqualTo(OK);
		assertThat(contentAsString(result)).contains("true");

		// Clean up
		removeStudy(studyClone);
	}

	@Test
	public void callRemove() throws Exception {
		StudyModel studyClone = cloneStudy();

		Result result = callAction(
				controllers.routes.ref.Studies.remove(studyClone.getId()),
				fakeRequest().withSession(Users.SESSION_EMAIL,
						Initializer.ADMIN_EMAIL));
		assertThat(status(result)).isEqualTo(OK);
	}

	@Test
	public void callCloneStudy() throws Exception {
		StudyModel study = cloneStudy();

		Result result = callAction(
				controllers.routes.ref.Studies.cloneStudy(study.getId()),
				fakeRequest().withSession(Users.SESSION_EMAIL,
						Initializer.ADMIN_EMAIL));
		assertThat(status(result)).isEqualTo(OK);

		// Clean up
		IOUtils.removeStudyAssetsDir(study.getDirName() + "_clone");
		removeStudy(study);
	}

	@Test
	public void callChangeMember() throws Exception {
		StudyModel studyClone = cloneStudy();

		Result result = callAction(
				controllers.routes.ref.Studies
						.changeMembers(studyClone.getId()),
				fakeRequest().withSession(Users.SESSION_EMAIL,
						Initializer.ADMIN_EMAIL));
		assertThat(status(result)).isEqualTo(OK);

		// Clean up
		removeStudy(studyClone);
	}

	@Test
	public void callSubmitChangedMembers() throws Exception {
		StudyModel studyClone = cloneStudy();

		Result result = callAction(
				controllers.routes.ref.Studies.submitChangedMembers(studyClone
						.getId()),
				fakeRequest().withFormUrlEncodedBody(
						ImmutableMap.of(StudyModel.MEMBERS, "admin"))
						.withSession(Users.SESSION_EMAIL,
								Initializer.ADMIN_EMAIL));
		assertEquals(303, status(result));

		// Clean up
		removeStudy(studyClone);
	}

	@Test
	public void callSubmitChangedMembersZeroMembers() throws Exception {
		StudyModel studyClone = cloneStudy();

		try {
			callAction(
					controllers.routes.ref.Studies.submitChangedMembers(studyClone
							.getId()),
					fakeRequest().withFormUrlEncodedBody(
					// Just put some gibberish in the map
							ImmutableMap.of("bla", "blu")).withSession(
							Users.SESSION_EMAIL, Initializer.ADMIN_EMAIL));
		} catch (RuntimeException e) {
			assert (e.getMessage()
					.contains("An study should have at least one member."));
			assert (e.getCause() instanceof ResultException);
		} finally {
			removeStudy(studyClone);
		}
	}

	@Test
	public void callChangeComponentOrder() throws Exception {
		StudyModel studyClone = cloneStudy();

		// Move first component one down
		Result result = callAction(
				controllers.routes.ref.Studies.changeComponentOrder(
						studyClone.getId(), studyClone.getComponentList()
								.get(0).getId(), Studies.COMPONENT_ORDER_DOWN),
				fakeRequest().withFormUrlEncodedBody(
						ImmutableMap.of(StudyModel.MEMBERS, "admin"))
						.withSession(Users.SESSION_EMAIL,
								Initializer.ADMIN_EMAIL));
		assertThat(status(result)).isEqualTo(OK);

		// Move second component one up
		result = callAction(
				controllers.routes.ref.Studies.changeComponentOrder(
						studyClone.getId(), studyClone.getComponentList()
								.get(1).getId(), Studies.COMPONENT_ORDER_UP),
				fakeRequest().withFormUrlEncodedBody(
						ImmutableMap.of(StudyModel.MEMBERS, "admin"))
						.withSession(Users.SESSION_EMAIL,
								Initializer.ADMIN_EMAIL));
		assertThat(status(result)).isEqualTo(OK);

		// Clean up
		removeStudy(studyClone);
	}

	@Test
	public void callShowStudy() throws Exception {
		StudyModel studyClone = cloneStudy();

		Result result = callAction(
				controllers.routes.ref.Studies.showStudy(studyClone.getId()),
				fakeRequest().withSession(Users.SESSION_EMAIL,
						Initializer.ADMIN_EMAIL));
		assertEquals(303, status(result));

		// Clean up
		removeStudy(studyClone);
	}

	@Test
	public void callCreateClosedStandaloneRun() throws Exception {
		StudyModel studyClone = cloneStudy();

		JsonNode jsonNode = JsonUtils.OBJECTMAPPER.readTree("{ \""
				+ ClosedStandaloneWorker.COMMENT + "\": \"testcomment\" }");
		Result result = callAction(
				controllers.routes.ref.Studies.createClosedStandaloneRun(studyClone
						.getId()),
				fakeRequest().withJsonBody(jsonNode).withSession(
						Users.SESSION_EMAIL, Initializer.ADMIN_EMAIL));
		assertThat(status(result)).isEqualTo(OK);

		// Clean up
		removeStudy(studyClone);
	}

	@Test
	public void callCreateTesterRun() throws Exception {
		StudyModel studyClone = cloneStudy();

		JsonNode jsonNode = JsonUtils.OBJECTMAPPER.readTree("{ \""
				+ ClosedStandaloneWorker.COMMENT + "\": \"testcomment\" }");
		Result result = callAction(
				controllers.routes.ref.Studies.createTesterRun(studyClone
						.getId()),
				fakeRequest().withJsonBody(jsonNode).withSession(
						Users.SESSION_EMAIL, Initializer.ADMIN_EMAIL));
		assertThat(status(result)).isEqualTo(OK);

		// Clean up
		removeStudy(studyClone);
	}

	@Test
	public void callShowMTurkSourceCode() throws Exception {
		StudyModel studyClone = cloneStudy();

		Result result = callAction(
				controllers.routes.ref.Studies.showMTurkSourceCode(studyClone
						.getId()),
				fakeRequest().withHeader("Referer",
						"http://www.example.com:9000").withSession(
						Users.SESSION_EMAIL, Initializer.ADMIN_EMAIL));
		assertThat(status(result)).isEqualTo(OK);
		assertThat(contentAsString(result)).contains(
				Breadcrumbs.MECHANICAL_TURK_HIT_LAYOUT_SOURCE_CODE);

		// Clean up
		removeStudy(studyClone);
	}

	@Test
	public void callWorkers() throws Exception {
		StudyModel studyClone = cloneStudy();

		Result result = callAction(
				controllers.routes.ref.Studies.workers(studyClone.getId()),
				fakeRequest().withSession(Users.SESSION_EMAIL,
						Initializer.ADMIN_EMAIL));
		assertThat(status(result)).isEqualTo(OK);
		assertThat(contentAsString(result)).contains(Breadcrumbs.WORKERS);

		// Clean up
		removeStudy(studyClone);
	}

}
