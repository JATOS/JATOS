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
import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

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
 * Testing controller.Studies
 * 
 * @author Kristian Lange
 */
public class StudiesControllerTest extends WithApplication {

	private static final String DIRNAME_TEST = "dirname_test";
	private static FakeApplication app;
	private static EntityManager em;
	private static UserModel admin;
	private static StudyModel studyTemplate;
	private static StudyModel studyClone;

	@Rule
	public ExpectedException thrown = ExpectedException.none();

	@BeforeClass
	public static void startApp() throws UnsupportedEncodingException,
			NoSuchAlgorithmException {
		app = Helpers.fakeApplication();
		Helpers.start(app);

		Option<JPAPlugin> jpaPlugin = app.getWrappedApplication().plugin(
				JPAPlugin.class);
		em = jpaPlugin.get().em("default");
		JPA.bindForCurrentThread(null);
		JPA.bindForCurrentThread(em);
		JPA.em().getTransaction().begin();

		// Get admin (admin is automatically created during initialization)
		admin = UserModel.findByEmail(Initializer.ADMIN_EMAIL);

		importStudyTemplate();
	}

	@Before
	public void setUp() {
		addStudy();
	}

	@After
	public void tearDown() throws IOException {
		clearDB();
		IOUtils.removeStudyAssetsDir(DIRNAME_TEST);
	}

	@AfterClass
	public static void stopApp() {
		JPA.em().flush();
		JPA.em().getTransaction().commit();
		em.close();
		JPA.bindForCurrentThread(null);
		Helpers.stop(app);
	}

	private static void importStudyTemplate()
			throws UnsupportedEncodingException, NoSuchAlgorithmException {
		UploadUnmarshaller uploadUnmarshaller = new UploadUnmarshaller();
		File studyFile = new File("test/basic_example_study.jas");
		studyTemplate = uploadUnmarshaller.unmarshalling(studyFile,
				StudyModel.class);
	}

	private void addStudy() {
		studyClone = new StudyModel(studyTemplate);
		PersistanceUtils.addStudy(studyClone, admin);
		persist();
	}

	private static void clearDB() {
		List<StudyModel> studyList = StudyModel.findAll();
		for (StudyModel study : studyList) {
			study.remove();
		}
		persist();
	}

	private static void persist() {
		JPA.em().flush();
		JPA.em().getTransaction().commit();
		JPA.em().getTransaction().begin();
	}

	@Test
	public void callIndex() {
		Result result = callAction(
				controllers.routes.ref.Studies.index(studyClone.getId(), null),
				fakeRequest().withSession(Users.SESSION_EMAIL,
						Initializer.ADMIN_EMAIL));
		assertThat(status(result)).isEqualTo(OK);
		assertThat(charset(result)).isEqualTo("utf-8");
		assertThat(contentType(result)).isEqualTo("text/html");
		assertThat(contentAsString(result)).contains("Components");
	}

	@Test
	public void callIndexButNotMember() {
		StudyModel.findById(studyClone.getId()).removeMember(admin);
		persist();
		thrown.expect(RuntimeException.class);
		thrown.expectMessage("isn't member of study");
		thrown.expectCause(IsInstanceOf
				.<Throwable> instanceOf(ResultException.class));
		callAction(controllers.routes.ref.Studies.index(1, null), fakeRequest()
				.withSession(Users.SESSION_EMAIL, Initializer.ADMIN_EMAIL));
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
	public void callSubmit() throws IOException {
		FakeRequest request = fakeRequest().withSession(Users.SESSION_EMAIL,
				Initializer.ADMIN_EMAIL).withFormUrlEncodedBody(
				ImmutableMap.of(StudyModel.TITLE, "Title Test",
						StudyModel.DESCRIPTION, "Description test.",
						StudyModel.DIRNAME, DIRNAME_TEST, StudyModel.JSON_DATA,
						"{}", StudyModel.ALLOWED_WORKER_LIST, ""));
		Result result = callAction(controllers.routes.ref.Studies.submit(),
				request);
		assertEquals(303, status(result));

		// Get study ID from responses header
		String[] locationArray = headers(result).get("Location").split("/");
		Long studyId = Long.valueOf(locationArray[locationArray.length - 1]);

		StudyModel study = StudyModel.findById(studyId);
		assertEquals("Title Test", study.getTitle());
		assertEquals("Description test.", study.getDescription());
		assertEquals("dirname_test", study.getDirName());
		assertEquals("{ }", study.getJsonData());
		assert (study.getComponentList().isEmpty());
		assert (study.getMemberList().contains(admin));
		assert (!study.isLocked());
		assert (study.getAllowedWorkerList().isEmpty());
	}

}
