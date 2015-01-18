import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static play.mvc.Http.Status.OK;
import static play.test.Helpers.callAction;
import static play.test.Helpers.charset;
import static play.test.Helpers.contentAsString;
import static play.test.Helpers.contentType;
import static play.test.Helpers.fakeRequest;
import static play.test.Helpers.session;
import static play.test.Helpers.status;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;

import javax.persistence.EntityManager;

import models.StudyModel;
import models.UserModel;

import org.hamcrest.core.IsEqual;
import org.hamcrest.core.IsInstanceOf;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import play.db.jpa.JPA;
import play.db.jpa.JPAPlugin;
import play.mvc.Content;
import play.mvc.Result;
import play.test.FakeApplication;
import play.test.Helpers;
import play.test.WithApplication;
import scala.Option;
import services.JsonUtils.UploadUnmarshaller;
import services.PersistanceUtils;

import com.google.common.collect.ImmutableMap;

import common.Initializer;
import controllers.Users;
import exceptions.ResultException;

/**
 * 
 * Simple (JUnit) tests that can call all parts of a play app. If you are
 * interested in mocking a whole application, see the wiki for more details.
 * 
 */
public class ApplicationTest extends WithApplication {

	private EntityManager em;
	private UserModel admin;
	private StudyModel study;

	@Rule
	public ExpectedException thrown = ExpectedException.none();

	@Before
	public void setUp() throws UnsupportedEncodingException,
			NoSuchAlgorithmException {
		FakeApplication app = Helpers.fakeApplication();
		Helpers.start(app);
		Option<JPAPlugin> jpaPlugin = app.getWrappedApplication().plugin(
				JPAPlugin.class);
		em = jpaPlugin.get().em("default");
		JPA.bindForCurrentThread(em);

		admin = UserModel.findByEmail(Initializer.ADMIN_EMAIL);
		importExampleStudy();
		// We have to persist every DB change before we can do a callAction()
		persist();
	}

	private void importExampleStudy() throws UnsupportedEncodingException,
			NoSuchAlgorithmException {
		JPA.em().getTransaction().begin();
		UploadUnmarshaller uploadUnmarshaller = new UploadUnmarshaller();
		File studyFile = new File("test/basic_example_study.jas");
		study = uploadUnmarshaller.unmarshalling(studyFile, StudyModel.class);
		PersistanceUtils.addStudy(study, admin);
	}
	
	private void persist() {
		JPA.em().flush();
		JPA.em().getTransaction().commit();
		JPA.em().getTransaction().begin();
	}

	@After
	public void tearDown() {
		JPA.em().flush();
		JPA.em().getTransaction().commit();
		JPA.bindForCurrentThread(null);
		em.close();
	}

	@Test
	public void simpleCheck() {
		int a = 1 + 1;
		assertThat(a).isEqualTo(2);
	}

	@Test
	public void renderTemplate() {
		Content html = views.html.publix.confirmationCode.render("test");
		assertThat(contentType(html)).isEqualTo("text/html");
		assertThat(contentAsString(html)).contains("Confirmation code:");
	}

	@Test
	public void databaseH2Mem() {
		assertThat(PersistanceUtils.IN_MEMORY_DB);
	}

	@Test
	public void authenticateSuccess() {
		Result result = callAction(
				controllers.routes.ref.Authentication.authenticate(),
				fakeRequest().withFormUrlEncodedBody(
						ImmutableMap.of(UserModel.EMAIL,
								Initializer.ADMIN_EMAIL, UserModel.PASSWORD,
								Initializer.ADMIN_PASSWORD)));
		assertEquals(303, status(result));
		assertEquals(Initializer.ADMIN_EMAIL,
				session(result).get(UserModel.EMAIL));
	}

	@Test
	public void authenticateFailure() {
		Result result = callAction(
				controllers.routes.ref.Authentication.authenticate(),
				fakeRequest().withFormUrlEncodedBody(
						ImmutableMap.of(UserModel.EMAIL,
								Initializer.ADMIN_EMAIL, UserModel.PASSWORD,
								"bla")));
		assertEquals(400, status(result));
		assertNull(session(result).get(UserModel.EMAIL));
	}

	@Test
	public void callStudiesIndex() {
		Result result = callAction(
				controllers.routes.ref.Studies.index(1, null),
				fakeRequest().withSession(Users.SESSION_EMAIL,
						Initializer.ADMIN_EMAIL));
		assertThat(status(result)).isEqualTo(OK);
		assertThat(charset(result)).isEqualTo("utf-8");
		assertThat(contentType(result)).isEqualTo("text/html");
		assertThat(contentAsString(result)).contains("Components");
	}

	@Test
	public void callStudiesIndexNotMember() {
		study.removeMember(admin);
		persist();
		thrown.expect(RuntimeException.class);
		thrown.expectMessage("isn't member of study");
		thrown.expectCause(IsInstanceOf.<Throwable>instanceOf(ResultException.class));
		callAction(controllers.routes.ref.Studies.index(1, null), fakeRequest()
				.withSession(Users.SESSION_EMAIL, Initializer.ADMIN_EMAIL));
	}

}
