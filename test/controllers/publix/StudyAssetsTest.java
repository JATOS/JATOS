package controllers.publix;

import static org.fest.assertions.Assertions.assertThat;
import static play.mvc.Http.Status.FORBIDDEN;
import static play.mvc.Http.Status.NOT_FOUND;
import static play.mvc.Http.Status.OK;
import static play.test.Helpers.GET;
import static play.test.Helpers.contentAsString;
import static play.test.Helpers.route;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;

import javax.inject.Inject;

import org.fest.assertions.Fail;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

import akka.stream.Materializer;
import controllers.gui.Authentication;
import controllers.publix.workers.JatosPublix;
import controllers.publix.workers.JatosPublix.JatosRun;
import daos.common.UserDao;
import exceptions.publix.NotFoundPublixException;
import exceptions.publix.PublixException;
import general.TestHelper;
import general.common.Common;
import general.common.MessagesStrings;
import models.common.Study;
import models.common.User;
import play.Application;
import play.ApplicationLoader;
import play.Environment;
import play.db.jpa.JPAApi;
import play.inject.guice.GuiceApplicationBuilder;
import play.inject.guice.GuiceApplicationLoader;
import play.mvc.Call;
import play.mvc.Http.Cookie;
import play.mvc.Http.RequestBuilder;
import play.mvc.Result;
import play.test.Helpers;
import services.gui.StudyService;
import services.gui.UserService;

/**
 * Testing controller.publix.StudyAssets
 * 
 * @author Kristian Lange
 */
public class StudyAssetsTest {

	private Injector injector;

	@Inject
	private static Application fakeApplication;

	@Inject
	private TestHelper testHelper;

	@Inject
	private JPAApi jpaApi;

	@Inject
	private Common common;

	@Inject
	private StudyService studyService;

	@Inject
	private UserDao userDao;

	@Inject
	private StudyAssets studyAssets;

	@Inject
	private Materializer materializer;

	@Before
	public void startApp() throws Exception {
		fakeApplication = Helpers.fakeApplication();

		GuiceApplicationBuilder builder = new GuiceApplicationLoader()
				.builder(new ApplicationLoader.Context(Environment.simple()));
		injector = Guice.createInjector(builder.applicationModule());
		injector.injectMembers(this);

		Helpers.start(fakeApplication);
	}

	@After
	public void stopApp() throws Exception {
		// Clean up
		testHelper.removeAllStudies();

		Helpers.stop(fakeApplication);
		testHelper.removeStudyAssetsRootDir();
	}

	@Test
	public void simpleCheck() {
		int a = 1 + 1;
		assertThat(a).isEqualTo(2);
	}

	@Test
	public void testStudyAssetsRootPath() {
		File studyAssetsRoot = new File(common.getStudyAssetsRootPath());
		assertThat(studyAssetsRoot.exists());
		assertThat(studyAssetsRoot.isDirectory());
		assertThat(studyAssetsRoot.isAbsolute());
	}

	@Test
	public void testVersioned() throws IOException, PublixException {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

		Result startStudyResult = startStudy(study);
		Cookie idCookie = startStudyResult.cookie("JATOS_IDS_0");

		Call call = controllers.publix.routes.StudyAssets
				.versioned(study.getDirName() + "/"
						+ study.getFirstComponent().getHtmlFilePath());
		RequestBuilder request = new RequestBuilder().method(Helpers.GET)
				.uri(call.url()).cookie(idCookie);
		Result result = route(request);
		assertThat(result.status()).isEqualTo(OK);
	}

	private Result startStudy(Study study) {
		User admin = testHelper.getAdmin();
		String url = "/publix/" + study.getId() + "/start?"
				+ JatosPublix.JATOS_WORKER_ID + "=" + admin.getWorker().getId();
		RequestBuilder request = new RequestBuilder().method(GET).uri(url)
				.session(Authentication.SESSION_USER_EMAIL, admin.getEmail())
				.session(JatosPublix.SESSION_JATOS_RUN,
						JatosRun.RUN_STUDY.name());
		return route(request);
	}

	@Test
	public void testVersionedNotFound() throws IOException, PublixException {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		Result startStudyResult = startStudy(study);
		Cookie idCookie = startStudyResult.cookie("JATOS_IDS_0");

		Call call = controllers.publix.routes.StudyAssets
				.versioned(study.getDirName() + "/non_existend_file");
		RequestBuilder request = new RequestBuilder().method(Helpers.GET)
				.uri(call.url()).cookie(idCookie);
		Result result = route(request);
		assertThat(result.status()).isEqualTo(NOT_FOUND);
	}

	@Test
	public void testVersionedWrongStudyDir()
			throws IOException, PublixException {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		Result startStudyResult = startStudy(study);
		Cookie idCookie = startStudyResult.cookie("JATOS_IDS_0");

		Call call = controllers.publix.routes.StudyAssets
				.versioned("wrong_study_dir/"
						+ study.getFirstComponent().getHtmlFilePath());
		RequestBuilder request = new RequestBuilder().method(Helpers.GET)
				.uri(call.url()).cookie(idCookie);
		Result result = route(request);
		assertThat(result.status()).isEqualTo(FORBIDDEN);
	}

	@Test
	public void testVersionedWrongAssets() throws IOException, PublixException {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		Study otherStudy = cloneStudy(study);
		Result startStudyResult = startStudy(study);
		Cookie idCookie = startStudyResult.cookie("JATOS_IDS_0");

		// Started study but ask for asset of otherStudy
		Call call = controllers.publix.routes.StudyAssets
				.versioned(otherStudy.getDirName() + "/"
						+ otherStudy.getFirstComponent().getHtmlFilePath());
		RequestBuilder request = new RequestBuilder().method(Helpers.GET)
				.uri(call.url()).cookie(idCookie);
		Result result = route(request);
		assertThat(result.status()).isEqualTo(FORBIDDEN);
	}

	private Study cloneStudy(Study study) throws IOException {
		return jpaApi.withTransaction(() -> {
			try {
				User admin = userDao.findByEmail(UserService.ADMIN_EMAIL);
				Study clone = studyService.clone(study);
				studyService.createAndPersistStudy(admin, clone);
				return clone;
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		});
	}

	@Test
	public void testVersionedPathTraversalAttack()
			throws IOException, PublixException {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		Result startStudyResult = startStudy(study);
		Cookie idCookie = startStudyResult.cookie("JATOS_IDS_0");

		// Although this file exists, it shouldn't be found since all '/..' are
		// removed
		Call call = controllers.publix.routes.StudyAssets
				.versioned(study.getDirName() + "/../../conf/application.conf");
		RequestBuilder request = new RequestBuilder().method(Helpers.GET)
				.uri(call.url()).cookie(idCookie);
		Result result = route(request);
		assertThat(result.status()).isEqualTo(NOT_FOUND);
	}

	@Test
	public void testRetrieveComponentHtmlFile()
			throws IOException, NotFoundPublixException {
		testHelper.mockContext();

		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

		Result result = studyAssets.retrieveComponentHtmlFile(
				study.getDirName(),
				study.getFirstComponent().getHtmlFilePath());

		assertThat(result.status()).isEqualTo(OK);
		assertThat(result.charset().get()).isEqualTo("utf-8");
		assertThat(result.contentType().get()).isEqualTo("text/html");
		// And check a random line of the JS code
		assertThat(contentAsString(result, materializer))
				.contains("jatos.onLoad(function() {");
	}

	@Test
	public void testRetrieveComponentHtmlFileNotFound()
			throws IOException, PublixException {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);

		try {
			studyAssets.retrieveComponentHtmlFile(study.getDirName(),
					"/someNotExistingPath");
			Fail.fail();
		} catch (NotFoundPublixException e) {
			assertThat(e.getMessage()).isEqualTo(
					MessagesStrings.htmlFilePathNotExist(study.getDirName(),
							"/someNotExistingPath"));
		}
	}

}
