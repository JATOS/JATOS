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
import services.JsonUtils.UploadUnmarshaller;
import services.PersistanceUtils;

import com.google.common.collect.ImmutableMap;
import common.Initializer;

import controllers.Users;
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
		JPA.bindForCurrentThread(null);
		JPA.bindForCurrentThread(em);

		// Get admin (admin is automatically created during initialization)
		admin = UserModel.findByEmail(Initializer.ADMIN_EMAIL);

		importStudyTemplate();
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
		Helpers.stop(app);
	}

	private static void importStudyTemplate() throws NoSuchAlgorithmException,
			IOException {
		UploadUnmarshaller uploadUnmarshaller = new UploadUnmarshaller();
		File studyFile = new File("test/basic_example_study.jas");
		studyTemplate = uploadUnmarshaller.unmarshalling(studyFile,
				StudyModel.class);
		IOUtils.createStudyAssetsDir(studyTemplate.getDirName());
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

}
