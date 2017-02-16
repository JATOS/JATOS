package services.publix.workers;

import static org.fest.assertions.Assertions.assertThat;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import javax.inject.Inject;

import org.fest.assertions.Fail;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

import exceptions.publix.ForbiddenPublixException;
import exceptions.publix.PublixException;
import general.TestHelper;
import models.common.Batch;
import models.common.Study;
import models.common.User;
import play.ApplicationLoader;
import play.Environment;
import play.inject.guice.GuiceApplicationBuilder;
import play.inject.guice.GuiceApplicationLoader;
import play.mvc.Http;
import services.publix.PublixErrorMessages;

/**
 * @author Kristian Lange
 */
public class JatosStudyAuthorisationTest {

	private Injector injector;

	@Inject
	private TestHelper testHelper;

	@Inject
	private JatosStudyAuthorisation studyAuthorisation;

	@Before
	public void startApp() throws Exception {
		GuiceApplicationBuilder builder = new GuiceApplicationLoader()
				.builder(new ApplicationLoader.Context(Environment.simple()));
		injector = Guice.createInjector(builder.applicationModule());
		injector.injectMembers(this);
	}

	@After
	public void stopApp() throws Exception {
		// Clean up
		testHelper.removeAllStudies();
		testHelper.removeStudyAssetsRootDir();
	}

	@Test
	public void checkWorkerAllowedToDoStudy() throws NoSuchAlgorithmException,
			IOException, ForbiddenPublixException {
		testHelper.mockContext();
		User admin = testHelper.getAdmin();
		Http.Context.current().session().put("email", admin.getEmail());

		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		Batch batch = study.getDefaultBatch();

		studyAuthorisation.checkWorkerAllowedToDoStudy(admin.getWorker(), study,
				batch);
	}

	@Test
	public void checkWorkerAllowedToDoStudyWrongWorkerType()
			throws NoSuchAlgorithmException, IOException {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		User admin = testHelper.getAdmin();

		// Remove Jatos worker from allowed worker types
		Batch batch = study.getDefaultBatch();
		batch.removeAllowedWorkerType(admin.getWorker().getWorkerType());

		// Study doesn't allow this worker type
		try {
			studyAuthorisation.checkWorkerAllowedToDoStudy(admin.getWorker(),
					study, batch);
			Fail.fail();
		} catch (PublixException e) {
			assertThat(e.getMessage()).isEqualTo(PublixErrorMessages
					.workerTypeNotAllowed(admin.getWorker().getUIWorkerType(),
							study.getId(), batch.getId()));
		}
	}

	@Test
	public void checkWorkerAllowedToDoStudyNotUser()
			throws NoSuchAlgorithmException, IOException {
		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		Batch batch = study.getDefaultBatch();
		User admin = testHelper.getAdmin();

		study.removeUser(admin);

		// User has to be a user of this study
		try {
			studyAuthorisation.checkWorkerAllowedToDoStudy(admin.getWorker(),
					study, batch);
			Fail.fail();
		} catch (PublixException e) {
			assertThat(e.getMessage()).isEqualTo(PublixErrorMessages
					.workerNotAllowedStudy(admin.getWorker(), study.getId()));
		}
	}

	@Test
	public void checkWorkerAllowedToDoStudyNotLoggedIn()
			throws NoSuchAlgorithmException, IOException {
		testHelper.mockContext();

		Study study = testHelper.createAndPersistExampleStudyForAdmin(injector);
		Batch batch = study.getDefaultBatch();
		User admin = testHelper.getAdmin();

		// User has to be logged in
		try {
			studyAuthorisation.checkWorkerAllowedToDoStudy(admin.getWorker(),
					study, batch);
			Fail.fail();
		} catch (PublixException e) {
			assertThat(e.getMessage()).isEqualTo(PublixErrorMessages
					.workerNotAllowedStudy(admin.getWorker(), study.getId()));
		}
	}

}
