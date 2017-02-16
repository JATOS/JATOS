package services.publix.workers;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.UUID;

import javax.inject.Inject;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

import general.TestHelper;
import models.common.Study;
import models.common.workers.GeneralSingleWorker;
import play.ApplicationLoader;
import play.Environment;
import play.inject.guice.GuiceApplicationBuilder;
import play.inject.guice.GuiceApplicationLoader;
import play.mvc.Http.Cookie;

/**
 * @author Kristian Lange
 */
public class GeneralSingleCookieServiceTest {

	/**
	 * Dummy cookie value for three study runs with worker IDs 3, 4, 5
	 */
	private static final String DUMMY_COOKIE = "82be1411-9044-46ad-b0b6-8ce703792107=3&148a1fb1-8a2c-4d4b-ac4f-d63ff1f7037c=4&2998f8b2-8fda-4266-9806-88ad752f6ed3=5";

	private Injector injector;

	@Inject
	private TestHelper testHelper;

	@Inject
	private GeneralSingleCookieService generalSingleCookieService;

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
	public void retrieveWorkerByStudyAlreadyDone() throws IOException {
		Study study = mock(Study.class);
		when(study.getUuid()).thenReturn(UUID.randomUUID().toString());

		putCookieInContext(study.getUuid() + "=10");

		Long workerId = generalSingleCookieService.retrieveWorkerByStudy(study);
		assertThat(workerId).isEqualTo(10l);
	}

	@Test
	public void retrieveWorkerByStudyNotDone() throws IOException {
		Study study = mock(Study.class);
		when(study.getUuid()).thenReturn(UUID.randomUUID().toString());

		// Did studies but not this one
		putCookieInContext(DUMMY_COOKIE);
		Long workerId = generalSingleCookieService.retrieveWorkerByStudy(study);
		assertThat(workerId).isNull();
	}

	@Test
	public void retrieveWorkerByStudyEmptyValue() throws IOException {
		Study study = mock(Study.class);
		when(study.getUuid()).thenReturn(UUID.randomUUID().toString());

		// Empty cookie value is allowed
		putCookieInContext("");
		Long workerId = generalSingleCookieService.retrieveWorkerByStudy(study);
		assertThat(workerId).isNull();
	}

	@Test
	public void retrieveWorkerByStudyWeirdValue() throws IOException {
		Study study = mock(Study.class);
		when(study.getUuid()).thenReturn(UUID.randomUUID().toString());

		// Weird cookie value is allowed
		putCookieInContext("foo");
		Long workerId = generalSingleCookieService.retrieveWorkerByStudy(study);
		assertThat(workerId).isNull();
	}

	@Test
	public void retrieveWorkerByStudyWeirdValue2() throws IOException {
		Study study = mock(Study.class);
		when(study.getUuid()).thenReturn(UUID.randomUUID().toString());

		// Weird cookie value is allowed
		putCookieInContext("foo=3x");
		Long workerId = generalSingleCookieService.retrieveWorkerByStudy(study);
		assertThat(workerId).isNull();
	}

	@Test
	public void retrieveWorkerByStudyWeirdValue3() throws IOException {
		Study study = mock(Study.class);
		when(study.getUuid()).thenReturn(UUID.randomUUID().toString());

		// Weird cookie value is allowed
		putCookieInContext("foo=3=4");
		Long workerId = generalSingleCookieService.retrieveWorkerByStudy(study);
		assertThat(workerId).isNull();
	}

	@Test
	public void addStudy() throws IOException {
		Study study = mock(Study.class);
		when(study.getUuid()).thenReturn(UUID.randomUUID().toString());
		GeneralSingleWorker worker = mock(GeneralSingleWorker.class);
		when(worker.getId()).thenReturn(1l);
		Cookie cookie = mock(Cookie.class);
		when(cookie.value()).thenReturn(DUMMY_COOKIE);

		String cookieValue = generalSingleCookieService.addStudy(study, worker,
				cookie);
		assertThat(cookieValue).isEqualTo(
				DUMMY_COOKIE + "&" + study.getUuid() + "=" + worker.getId());
	}

	@Test
	public void addStudyEmptyCookie() throws IOException {
		Study study = mock(Study.class);
		when(study.getUuid()).thenReturn(UUID.randomUUID().toString());
		GeneralSingleWorker worker = mock(GeneralSingleWorker.class);
		when(worker.getId()).thenReturn(1l);

		// No cookie
		String cookieValue = generalSingleCookieService.addStudy(study, worker,
				null);
		assertThat(cookieValue)
				.isEqualTo(study.getUuid() + "=" + worker.getId());
	}

	private void putCookieInContext(String cookieValue) {
		Cookie cookie = new Cookie(GeneralSingleCookieService.COOKIE_NAME,
				cookieValue, Integer.MAX_VALUE, "/", "", false, true);
		testHelper.mockContext(cookie);
	}

}
