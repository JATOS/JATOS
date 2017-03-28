package services.gui;

import static org.fest.assertions.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

import daos.common.UserDao;
import general.TestHelper;
import general.common.Common;
import general.common.RequestScope;
import models.common.User;
import play.ApplicationLoader;
import play.Environment;
import play.db.jpa.JPAApi;
import play.inject.guice.GuiceApplicationBuilder;
import play.inject.guice.GuiceApplicationLoader;
import play.mvc.Http;

/**
 * Tests AuthenticationService
 * 
 * @author Kristian Lange (2017)
 */
public class AuthenticationServiceTest {

	private Injector injector;

	@Inject
	private TestHelper testHelper;

	@Inject
	private JPAApi jpaApi;

	@Inject
	private AuthenticationService authenticationService;

	@Inject
	private UserDao userDao;

	@Before
	public void startApp() throws Exception {
		GuiceApplicationBuilder builder = new GuiceApplicationLoader()
				.builder(new ApplicationLoader.Context(Environment.simple()));
		injector = Guice.createInjector(builder.applicationModule());
		injector.injectMembers(this);
	}

	@After
	public void stopApp() throws Exception {
	}

	@Test
	public void simpleCheck() {
		int a = 1 + 1;
		assertThat(a).isEqualTo(2);
	}

	/**
	 * Test AuthenticationService.authenticate(): check with admin user
	 */
	@Test
	public void checkAuthenticate() {
		jpaApi.withTransaction(() -> {
			assertThat(authenticationService.authenticate(
					UserService.ADMIN_EMAIL, UserService.ADMIN_PASSWORD))
							.isTrue();
			assertThat(authenticationService
					.authenticate(UserService.ADMIN_EMAIL, "wrongPassword"))
							.isFalse();
		});
	}

	/**
	 * Test AuthenticationService.authenticate(): check with a different user
	 * than the admin user (shouldn't be necessary - just to be sure)
	 */
	@Test
	public void checkAuthenticateNotAdminUser() {
		testHelper.createAndPersistUser("bla@bla.org", "Bla Bla", "bla");

		jpaApi.withTransaction(() -> {
			assertThat(authenticationService.authenticate("bla@bla.org", "bla"))
					.isTrue();
			assertThat(authenticationService.authenticate("bla@bla.org",
					"wrongPassword")).isFalse();
		});

		testHelper.removeUser("bla@bla.org");
	}

	/**
	 * Test AuthenticationService.getLoggedInUserBySession()
	 */
	@Test
	public void checkGetLoggedInUserBySession() {
		jpaApi.withTransaction(() -> {
			Map<String, String> data = new HashMap<>();
			data.put(AuthenticationService.SESSION_USER_EMAIL,
					UserService.ADMIN_EMAIL);
			Http.Session session = new Http.Session(data);
			assertThat(authenticationService.getLoggedInUserBySession(session))
					.isEqualTo(testHelper.getAdmin());
		});

		// Try again with another non-admin user
		User userBla = testHelper.createAndPersistUser("bla@bla.org", "Bla Bla",
				"bla");
		jpaApi.withTransaction(() -> {
			Map<String, String> data = new HashMap<>();
			data.put(AuthenticationService.SESSION_USER_EMAIL,
					userBla.getEmail());
			Http.Session session = new Http.Session(data);
			assertThat(authenticationService.getLoggedInUserBySession(session))
					.isEqualTo(userBla);
		});
	}

	/**
	 * Test AuthenticationService.getLoggedInUserBySession(): Returns null if
	 * user doesn't exists in the database
	 */
	@Test
	public void checkGetLoggedInUserBySessionNotFound() {
		jpaApi.withTransaction(() -> {
			Map<String, String> data = new HashMap<>();
			data.put(AuthenticationService.SESSION_USER_EMAIL,
					"user-not-exist@bla.org");
			Http.Session session = new Http.Session(data);
			assertThat(authenticationService.getLoggedInUserBySession(session))
					.isNull();
		});
	}

	/**
	 * Test AuthenticationService.getLoggedInUser(): Gets the user that was put
	 * into RequestScope by AuthenticationAction
	 */
	@Test
	public void checkGetLoggedInUser() {
		testHelper.mockContext();

		User userBla = testHelper.createAndPersistUser("bla@bla.org", "Bla Bla",
				"bla");
		RequestScope.put(AuthenticationService.LOGGED_IN_USER, userBla);

		assertThat(authenticationService.getLoggedInUser()).isEqualTo(userBla);
	}

	/**
	 * Test AuthenticationService.getLoggedInUser(): Returns null if no user is
	 * in RequestScope
	 */
	@Test
	public void checkGetLoggedInUserNotFound() {
		testHelper.mockContext();
		assertThat(authenticationService.getLoggedInUser()).isNull();
	}

	/**
	 * Test AuthenticationService.writeSessionCookieAndSessionId()
	 */
	@Test
	public void checkWriteSessionCookieAndSessionId() {
		User userBla = testHelper.createAndPersistUser("bla@bla.org", "Bla Bla",
				"bla");
		Map<String, String> data = new HashMap<>();
		Http.Session session = new Http.Session(data);

		jpaApi.withTransaction(() -> {
			authenticationService.writeSessionCookieAndSessionId(session,
					userBla.getEmail());
		});

		// Check session
		assertThat(session.get(AuthenticationService.SESSION_ID)).isNotEmpty();
		assertThat(session.get(AuthenticationService.SESSION_USER_EMAIL))
				.isEqualTo(userBla.getEmail());
		assertThat(session.get(AuthenticationService.SESSION_LOGIN_TIME))
				.isNotEmpty();
		assertThat(
				session.get(AuthenticationService.SESSION_LAST_ACTIVITY_TIME))
						.isNotEmpty();

		// Check that session ID is stored in user and that it is the same as in
		// the session
		jpaApi.withTransaction(() -> {
			User user = userDao.findByEmail(userBla.getEmail());
			assertThat(user.getSessionId())
					.isEqualTo(session.get(AuthenticationService.SESSION_ID));
		});
	}

	/**
	 * Test AuthenticationService.refreshSessionCookie(): writes a new last
	 * activity time into the session
	 */
	@Test
	public void checkRefreshSessionCookie() {
		Map<String, String> data = new HashMap<>();
		data.put(AuthenticationService.SESSION_LAST_ACTIVITY_TIME, "blafasel");
		Http.Session session = new Http.Session(data);
		authenticationService.refreshSessionCookie(session);

		assertThat(
				session.get(AuthenticationService.SESSION_LAST_ACTIVITY_TIME))
						.isNotEqualTo("blafasel");
	}

	/**
	 * Test AuthenticationService.clearSessionCookie(): removes all entries from
	 * the session
	 */
	@Test
	public void checkClearSessionCookie() {
		Map<String, String> data = new HashMap<>();
		data.put(AuthenticationService.SESSION_LAST_ACTIVITY_TIME, "blafasel");
		data.put(AuthenticationService.SESSION_ID, "blafasel");
		data.put(AuthenticationService.SESSION_LOGIN_TIME, "blafasel");
		data.put(AuthenticationService.SESSION_USER_EMAIL, "blafasel");
		Http.Session session = new Http.Session(data);
		authenticationService.clearSessionCookie(session);

		// Check that session is cleared
		assertThat(
				session.get(AuthenticationService.SESSION_LAST_ACTIVITY_TIME))
						.isNull();
		assertThat(session.get(AuthenticationService.SESSION_ID)).isNull();
		assertThat(session.get(AuthenticationService.SESSION_LOGIN_TIME))
				.isNull();
		assertThat(session.get(AuthenticationService.SESSION_USER_EMAIL))
				.isNull();
	}

	/**
	 * Test AuthenticationService.clearSessionCookieAndSessionId(): additional
	 * to removing all entries from the session it also removes the session ID
	 * from the user
	 */
	@Test
	public void checkClearSessionCookieAndSessionId() {
		User userBla = testHelper.createAndPersistUser("bla@bla.org", "Bla Bla",
				"bla");
		Map<String, String> data = new HashMap<>();
		data.put(AuthenticationService.SESSION_LAST_ACTIVITY_TIME, "blafasel");
		data.put(AuthenticationService.SESSION_ID, "blafasel");
		data.put(AuthenticationService.SESSION_LOGIN_TIME, "blafasel");
		data.put(AuthenticationService.SESSION_USER_EMAIL, "blafasel");
		Http.Session session = new Http.Session(data);
		jpaApi.withTransaction(() -> {
			authenticationService.clearSessionCookieAndSessionId(session,
					userBla);
		});

		// Check that session is cleared
		assertThat(
				session.get(AuthenticationService.SESSION_LAST_ACTIVITY_TIME))
						.isNull();
		assertThat(session.get(AuthenticationService.SESSION_ID)).isNull();
		assertThat(session.get(AuthenticationService.SESSION_LOGIN_TIME))
				.isNull();
		assertThat(session.get(AuthenticationService.SESSION_USER_EMAIL))
				.isNull();

		// Check that the session ID that was stored in user is removed
		jpaApi.withTransaction(() -> {
			User user = userDao.findByEmail(userBla.getEmail());
			assertThat(user.getSessionId()).isNull();
		});
	}

	/**
	 * Test AuthenticationService.isValidSessionId(): returns true if both
	 * session IDs (user's and in the session cookie) are the same
	 */
	@Test
	public void checkIsValidSessionIdIsValid() {
		User userBla = testHelper.createAndPersistUser("bla@bla.org", "Bla Bla",
				"bla");
		userBla.setSessionId("this-is-a-session-id");

		Map<String, String> data = new HashMap<>();
		data.put(AuthenticationService.SESSION_ID, "this-is-a-session-id");
		Http.Session session = new Http.Session(data);

		assertThat(authenticationService.isValidSessionId(session, userBla))
				.isTrue();
	}

	/**
	 * Test AuthenticationService.isValidSessionId(): returns false if both
	 * session IDs aren't the same
	 */
	@Test
	public void checkIsValidSessionIdIsNotValid() {
		User userBla = testHelper.createAndPersistUser("bla@bla.org", "Bla Bla",
				"bla");
		userBla.setSessionId("this-is-a-session-id");

		Map<String, String> data = new HashMap<>();
		data.put(AuthenticationService.SESSION_ID,
				"this-is-a-complete-different-session-id");
		Http.Session session = new Http.Session(data);

		assertThat(authenticationService.isValidSessionId(session, userBla))
				.isFalse();
	}

	/**
	 * Test AuthenticationService.isValidSessionId(): returns false if user's
	 * session ID is null
	 */
	@Test
	public void checkIsValidSessionIdUsersSessionIdNull() {
		User userBla = testHelper.createAndPersistUser("bla@bla.org", "Bla Bla",
				"bla");
		userBla.setSessionId(null);

		Map<String, String> data = new HashMap<>();
		data.put(AuthenticationService.SESSION_ID, "this-is-a-session-id");
		Http.Session session = new Http.Session(data);

		assertThat(authenticationService.isValidSessionId(session, userBla))
				.isFalse();
	}

	/**
	 * Test AuthenticationService.isValidSessionId(): returns false if session
	 * ID in session cookie is not set
	 */
	@Test
	public void checkIsValidSessionIdNullInSession() {
		User userBla = testHelper.createAndPersistUser("bla@bla.org", "Bla Bla",
				"bla");
		userBla.setSessionId("this-is-a-session-id");

		Map<String, String> data = new HashMap<>();
		Http.Session session = new Http.Session(data);

		assertThat(authenticationService.isValidSessionId(session, userBla))
				.isFalse();
	}

	/**
	 * Test AuthenticationService.isSessionTimeout(): returns false if the
	 * session timeout didn't happen yet - or in other words if the session
	 * login time is not older than specified in application.conf in
	 * 'jatos.session.timeout'
	 */
	@Test
	public void checkIsSessionTimeout() {
		Map<String, String> data = new HashMap<>();
		Instant loginTime = Instant.now();
		data.put(AuthenticationService.SESSION_LOGIN_TIME,
				String.valueOf(loginTime.toEpochMilli()));
		Http.Session session = new Http.Session(data);

		assertThat(authenticationService.isSessionTimeout(session)).isFalse();
	}

	/**
	 * Test AuthenticationService.isSessionTimeout(): returns true if the
	 * session timeout happened - or in other words if the session login time is
	 * older than specified in application.conf in 'jatos.session.timeout'
	 */
	@Test
	public void checkIsSessionTimeoutFail() {
		Map<String, String> data = new HashMap<>();
		// Make sure login time is older than what is allowed in the configured
		// session timeout
		Instant loginTime = Instant.now().minus(Common.getSessionTimeout() + 1,
				ChronoUnit.MINUTES);
		data.put(AuthenticationService.SESSION_LOGIN_TIME,
				String.valueOf(loginTime.toEpochMilli()));
		Http.Session session = new Http.Session(data);

		assertThat(authenticationService.isSessionTimeout(session)).isTrue();
	}

	/**
	 * Test AuthenticationService.isSessionTimeout(): returns false if the
	 * inactivity timeout didn't happen yet - or in other words if the session's
	 * last activity time is not older than specified in application.conf in
	 * 'jatos.session.inactivity'
	 */
	@Test
	public void checkIsSessionInactivity() {
		Map<String, String> data = new HashMap<>();
		Instant lastActivityTime = Instant.now();
		data.put(AuthenticationService.SESSION_LAST_ACTIVITY_TIME,
				String.valueOf(lastActivityTime.toEpochMilli()));
		Http.Session session = new Http.Session(data);

		assertThat(authenticationService.isInactivityTimeout(session))
				.isFalse();
	}

	/**
	 * Test AuthenticationService.isSessionTimeout(): returns true if the
	 * inactivity timeout happened - or in other words if the session's last
	 * activity time is older than specified in application.conf in
	 * 'jatos.session.inactivity'
	 */
	@Test
	public void checkIsSessionInactivityFail() {
		Map<String, String> data = new HashMap<>();
		Instant lastActivityTime = Instant.now()
				.minus(Common.getSessionInactivity() + 1, ChronoUnit.MINUTES);
		data.put(AuthenticationService.SESSION_LAST_ACTIVITY_TIME,
				String.valueOf(lastActivityTime.toEpochMilli()));
		Http.Session session = new Http.Session(data);

		assertThat(authenticationService.isInactivityTimeout(session)).isTrue();
	}

}
