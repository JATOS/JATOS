import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static play.test.Helpers.callAction;
import static play.test.Helpers.fakeRequest;
import static play.test.Helpers.session;
import static play.test.Helpers.status;

import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;

import javax.persistence.EntityManager;

import models.UserModel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import play.db.jpa.JPA;
import play.db.jpa.JPAPlugin;
import play.mvc.Result;
import play.test.FakeApplication;
import play.test.Helpers;
import play.test.WithApplication;
import scala.Option;

import com.google.common.collect.ImmutableMap;
import common.Initializer;

/**
 * Testing controller.Studies
 * 
 * @author Kristian Lange
 */
public class AuthenticationControllerTest extends WithApplication {

	private static FakeApplication app;
	private static EntityManager em;

	@Before
	public void setUp() throws UnsupportedEncodingException,
			NoSuchAlgorithmException {
		app = Helpers.fakeApplication();
		Helpers.start(app);
		
		Option<JPAPlugin> jpaPlugin = app.getWrappedApplication().plugin(
				JPAPlugin.class);
		em = jpaPlugin.get().em("default");
		JPA.bindForCurrentThread(em);
	}

	@After
	public void tearDown() {
		em.close();
		JPA.bindForCurrentThread(null);
		Helpers.stop(app);
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

}
